package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.multiblock.OmniversalFurnaceAutoBuilder;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.core.common.FlyEffectedHolder;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.network.BeefInvulnerabilitySyncPacket;
import com.sorrowmist.useless.network.BeefInvulnerabilityStatePacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import com.sorrowmist.useless.world.dimension.UselessDimensionConfigManager;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UselessMod.MODID)
public class EventHandler {
    private static final Set<UUID> BEEF_PROTECTED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Integer> CLIENT_BEEF_INVULNERABLE_ENTITY_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && shouldApplyBeefInvulnerability(player)) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Player player && shouldApplyBeefInvulnerability(player)) {
            event.setNewDamage(0.0F);
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && shouldApplyBeefInvulnerability(player)) {
            event.setCanceled(true);
            restoreBeefProtectedPlayer(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBeefToolLivingDeath(LivingDeathEvent event) {
        if (EndlessBeafItem.isForceKillDeathInProgress(event.getEntity())) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        UselessItemUtils.tryCaptureSpawnEgg(event.getEntity(), player.getMainHandItem(), player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof Player player && hasBeefInvulnerabilityItem(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                UselessItemUtils.tryAddCognizantDustDrop(event, mainHandItem);
                UselessItemUtils.onLivingDrops(event, mainHandItem, player);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() instanceof EndlessBeafItem) {
            MiningDispatcher.dispatchBreak(event, mainHandItem, player);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem)) return;

        float newSpeed = event.getOriginalSpeed();

        if (player.getAbilities().flying || player.isInWater()) {
            newSpeed *= 5.0F;
        }
        event.setNewSpeed(newSpeed);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        if (!player.isCreative()) {
            boolean hasItemInInventory = ConfigManager.shouldEnableFlightEffect()
                    && UselessItemUtils.hasTargetToolInInventory(player);

            if (hasItemInInventory) {
                FlyEffectedHolder.add(player.getUUID());
                float flightSpeed = (float) ConfigManager.getBeefToolFlightSpeed();
                boolean abilitiesChanged = player.getAbilities().getFlyingSpeed() != flightSpeed;
                if (abilitiesChanged) {
                    player.getAbilities().setFlyingSpeed(flightSpeed);
                }
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    abilitiesChanged = true;
                }
                if (abilitiesChanged) {
                    player.onUpdateAbilities();
                }
            } else {
                if (player.getAbilities().mayfly && FlyEffectedHolder.contains(player.getUUID())) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.getAbilities().setFlyingSpeed(0.05F);
                    player.onUpdateAbilities();
                }
                FlyEffectedHolder.remove(player.getUUID());
            }
        }

        updateBeefInvulnerability(player);
        
        MiningDispatcher.tickCacheUpdate(player);
    }

    public static void updateBeefInvulnerability(Player player) {
        updateBeefInvulnerability(player, false);
    }

    public static void updateBeefInvulnerability(Player player, boolean forceSync) {
        if (player.level().isClientSide()) {
            return;
        }

        migrateLegacyBeefInvulnerability(player);

        boolean hasItemInInventory = UselessItemUtils.hasInvulnerabilityEnabledTargetToolInInventory(player);
        UUID uuid = player.getUUID();

        if (hasItemInInventory) {
            boolean newlyTracked = BEEF_PROTECTED_PLAYERS.add(uuid);
            claimBeefInvulnerability(player);
            if (player instanceof ServerPlayer serverPlayer && (forceSync || newlyTracked || player.tickCount % 20 == 0)) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new BeefInvulnerabilityStatePacket(serverPlayer.getId(), true));
            }
            return;
        }

        boolean wasTracked = BEEF_PROTECTED_PLAYERS.remove(uuid);
        boolean released = releaseBeefInvulnerability(player);
        if (player instanceof ServerPlayer serverPlayer && (forceSync || wasTracked || released)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new BeefInvulnerabilityStatePacket(serverPlayer.getId(), false));
        }
    }

    private static void migrateLegacyBeefInvulnerability(Player player) {
        CompoundTag ownershipData = BeefInvulnerabilityOwnership.get(player);
        if (BeefInvulnerabilityOwnership.isMigrationComplete(ownershipData)) {
            return;
        }

        // Preserve an active ownership cycle while upgrading its metadata.
        if (BeefInvulnerabilityOwnership.isOwned(ownershipData)) {
            BeefInvulnerabilityOwnership.markMigrationComplete(ownershipData);
            return;
        }

        // Defer migration until an affected survival/adventure player carries the tool.
        if (player.isCreative()
                || player.isSpectator()
                || !UselessItemUtils.hasTargetToolInInventory(player)) {
            return;
        }

        // Old builds, including v1 ownership tracking, could leave this flag behind
        // after protection had already been released.
        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }
        BeefInvulnerabilityOwnership.markMigrationComplete(BeefInvulnerabilityOwnership.getOrCreate(player));
    }

    private static void claimBeefInvulnerability(Player player) {
        CompoundTag ownershipData = BeefInvulnerabilityOwnership.getOrCreate(player);
        BeefInvulnerabilityOwnership.claim(ownershipData, player.isInvulnerable());
        if (!player.isInvulnerable()) {
            player.setInvulnerable(true);
        }
    }

    private static boolean releaseBeefInvulnerability(Player player) {
        BeefInvulnerabilityOwnership.ReleaseResult result =
                BeefInvulnerabilityOwnership.release(BeefInvulnerabilityOwnership.get(player));
        if (!result.owned()) {
            return false;
        }

        player.setInvulnerable(result.previousInvulnerable());
        return true;
    }

    public static boolean shouldApplyBeefInvulnerability(Player player) {
        return !player.level().isClientSide() && hasBeefInvulnerabilityItem(player);
    }

    public static boolean hasBeefInvulnerabilityItem(Player player) {
        return UselessItemUtils.hasInvulnerabilityEnabledTargetToolInInventory(player) || player.level().isClientSide() && CLIENT_BEEF_INVULNERABLE_ENTITY_IDS.contains(player.getId());
    }

    public static void setClientBeefInvulnerabilityState(int entityId, boolean protectedState) {
        if (protectedState) {
            CLIENT_BEEF_INVULNERABLE_ENTITY_IDS.add(entityId);
            return;
        }
        CLIENT_BEEF_INVULNERABLE_ENTITY_IDS.remove(entityId);
    }

    public static void clearClientBeefInvulnerabilityStates() {
        CLIENT_BEEF_INVULNERABLE_ENTITY_IDS.clear();
    }

    public static void restoreBeefProtectedPlayer(Player player) {
        if (!player.level().isClientSide()) {
            BEEF_PROTECTED_PLAYERS.add(player.getUUID());
            migrateLegacyBeefInvulnerability(player);
            claimBeefInvulnerability(player);
        }

        float maxHealth = player.getMaxHealth();
        player.dead = false;
        player.deathTime = 0;
        player.hurtTime = 0;
        player.hurtDuration = 0;
        player.setHealth(maxHealth);
        player.setPose(Pose.STANDING);
        player.clearFire();
        player.fallDistance = 0.0F;
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new BeefInvulnerabilityStatePacket(serverPlayer.getId(), true));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new BeefInvulnerabilitySyncPacket(serverPlayer.getId(), maxHealth));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        updateBeefInvulnerability(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        updateBeefInvulnerability(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        updateBeefInvulnerability(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && UselessDimensions.isUselessDimension(level.dimension())) {
            UselessDimensionConfigManager.apply(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BEEF_PROTECTED_PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        Level world = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;

        if (be instanceof MultiblockAlloyFurnaceCoreBlockEntity) {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                OmniversalFurnaceAutoBuilder.Result result =
                        OmniversalFurnaceAutoBuilder.build(serverPlayer, stack, pos);
                player.displayClientMessage(result.message(), true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(world.isClientSide));
            return;
        }

        String className = be.getClass().getName();
        if (!className.contains("WirelessAccessPoint")) return;

        if (!world.isClientSide) {
            GlobalPos globalPos = GlobalPos.of(world.dimension(), pos);
            stack.set(UComponents.WIRELESS_LINK_TARGET.get(), globalPos);
            player.displayClientMessage(Component.translatable("gui.useless_mod.wireless_access_point_bound", pos.toShortString()), true);
        }
        // 取消事件，阻止方块本身的逻辑（如 AE2 的拆卸或旋转）
        event.setCanceled(true);
        // 设置结果，告知系统处理已成功，停止后续传播
        event.setCancellationResult(InteractionResult.sidedSuccess(world.isClientSide));
    }

    /**
     * 服务器启动时构建配方索引
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        UselessDimensionConfigManager.applyAll(event.getServer());
        AlloyFurnaceRecipeManager.getInstance().buildIndex(event.getServer().overworld());
        AlloyFurnaceRecipeCatalog.prewarm(event.getServer().overworld());
        event.getServer().getPlayerList().getPlayers().forEach(EndlessBeafItem::refreshAttackDamage);
    }

    /**
     * 数据重载时重建配方索引
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // 在配方数据重载后重建索引
        event.addListener((stage, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
            return stage.wait(Collections.emptyList()).thenRunAsync(() -> {
                // 配方数据已变更：清空查找缓存并标记索引需要重建
                // 索引会在下一次 findRecipe 时延迟重建（此处无法直接获取 Level）
                AlloyFurnaceRecipeManager.getInstance().clearCache();
                AlloyFurnaceRecipeManager.getInstance().invalidateIndex();
                AlloyFurnaceRecipeCatalog.invalidate();
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    AlloyFurnaceRecipeCatalog.prewarm(server.overworld());
                    server.getPlayerList().getPlayers().forEach(EndlessBeafItem::refreshAttackDamage);
                }
            }, gameExecutor);
        });
    }
}
