package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.core.common.FlyEffectedHolder;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UselessMod.MODID)
public class EventHandler {
    private static final Set<UUID> BEEF_INVULNERABLE_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && shouldApplyBeefInvulnerability(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Player player && shouldApplyBeefInvulnerability(player)) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
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
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else {
                if (player.getAbilities().mayfly && FlyEffectedHolder.contains(player.getUUID())) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                FlyEffectedHolder.remove(player.getUUID());
            }
        }

        updateBeefInvulnerability(player);
        
        MiningDispatcher.tickCacheUpdate(player);
    }

    private static void updateBeefInvulnerability(Player player) {
        boolean hasItemInInventory = UselessItemUtils.hasTargetToolInInventory(player);
        UUID uuid = player.getUUID();

        if (hasItemInInventory) {
            if (!player.isInvulnerable()) {
                BEEF_INVULNERABLE_PLAYERS.add(uuid);
                player.setInvulnerable(true);
            }
            return;
        }

        if (BEEF_INVULNERABLE_PLAYERS.remove(uuid)) {
            player.setInvulnerable(false);
        }
    }

    private static boolean shouldApplyBeefInvulnerability(Player player) {
        return !player.level().isClientSide() && UselessItemUtils.hasTargetToolInInventory(player);
    }

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        Level world = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;

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
        AlloyFurnaceRecipeManager.getInstance().buildIndex(event.getServer().overworld());
    }

    /**
     * 数据重载时重建配方索引
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // 在配方数据重载后重建索引
        event.addListener((stage, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
            return stage.wait(Collections.emptyList()).thenRun(() -> {
                // 配方数据已变更：清空查找缓存并标记索引需要重建
                // 索引会在下一次 findRecipe 时延迟重建（此处无法直接获取 Level）
                AlloyFurnaceRecipeManager.getInstance().clearCache();
                AlloyFurnaceRecipeManager.getInstance().invalidateIndex();
            });
        });
    }
}
