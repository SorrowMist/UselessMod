package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.BeefMagnetHandler;
import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.stafflink.StaffLinkBinding;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkTargets;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.compat.ae.AeDeviceLinker;
import com.sorrowmist.useless.compat.ae.AeLinkChannelBypass;
import com.sorrowmist.useless.compat.constructionwand.ConstructionWandLogic;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.multiblock.OmniversalFurnaceAutoBuilder;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.core.common.FlyEffectedHolder;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.network.BeefInvulnerabilitySyncPacket;
import com.sorrowmist.useless.network.BeefInvulnerabilityStatePacket;
import com.sorrowmist.useless.network.StaffLinkStatusPacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import com.sorrowmist.useless.world.dimension.UselessDimensionConfigManager;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Abilities;
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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UselessMod.MODID)
public class EventHandler {
    private static final Set<UUID> BEEF_PROTECTED_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> BEEF_ADVANCED_STEALTH_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Integer> CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> RESTORING_BEEF_PROTECTED_PLAYERS = ConcurrentHashMap.newKeySet();

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
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player && hasBeefAdvancedStealthItem(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player
                && hasBeefInvulnerabilityItem(player)
                && isNonBeneficialEffect(event.getEffectInstance())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
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

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onForceKillDeathObserved(LivingDeathEvent event) {
        EndlessBeafItem.observeForceKillDeath(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBeefToolLivingDeath(LivingDeathEvent event) {
        if (EndlessBeafItem.handleForceKillDeath(event)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        UselessItemUtils.tryCaptureSpawnEgg(event.getEntity(), player.getMainHandItem(), player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBeefToolMagnetDeath(LivingDeathEvent event) {
        if (EndlessBeafItem.handleForceKillMagnetDeath(event)) {
            return;
        }
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() instanceof EndlessBeafItem) {
            BeefMagnetHandler.onEntityKilled(level, player, mainHandItem, event.getEntity().position());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof Player player && hasBeefAdvancedStealthItem(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onLivingDrops(LivingDropsEvent event) {
        EndlessBeafItem.observeForceKillDrops(event);
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                UselessItemUtils.tryAddCognizantDustDrop(event, mainHandItem);
                UselessItemUtils.onLivingDrops(event, mainHandItem, player);
                UselessItemUtils.tryAddBeheadingDrop(event, mainHandItem);
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

    private static final float DEFAULT_FLYING_SPEED = 0.05F;
    private static final float FLYING_SPEED_EPSILON = 1.0E-6F;
    /**
     * 游戏模式切换后，接下来这么多个 tick 内每 tick 都强制重发一次能力包。
     * <p>
     * 只补发一次是不够的：原版/NeoForge 在切换时重置 abilities，客户端收到 CHANGE_GAME_MODE 后
     * 还会通过 {@code MultiPlayerGameMode#setLocalMode} 在本地再重置一遍，而这次本地重置
     * 服务端完全不知情。留一个窗口反复对齐，才能盖住客户端那次重置。
     */
    private static final int FLIGHT_RESYNC_WINDOW = 20;
    private static final Map<UUID, Integer> FLIGHT_RESYNC_QUEUE = new ConcurrentHashMap<>();
    /**
     * 「粘滞飞行」玩家：<b>客户端自己上报正在飞行</b>的造化杖携带者。
     * <p>
     * 只要玩家还处在这个状态，服务端的 {@code flying} 就不允许被清掉。原因是实测 <b>Re-Avaritia</b> 的
     * {@code committee.nova.mods.avaritia.init.handler.AbilityHandler#updateClientServerFlight}
     * 会<b>每秒 1~2 次</b>把 {@code mayfly} / {@code flying} 一起清成 false 并回传客户端，
     * 把 NeoForge 保留的悬停（创造→生存切换）和玩家自己的起飞都冲掉。
     * <p>
     * 开关来自 {@link #onClientFlightIntent}（客户端发的 {@code ServerboundPlayerAbilitiesPacket}）：
     * 玩家落地或空中双击关飞行时客户端会上报 false，粘滞随之解除，
     * 所以<b>落地行走、普通起跳、主动关飞行都不受影响</b>；而被其它 mod 在服务端偷偷清掉的情况
     * 不经过那条上报路径，于是能被我们拉回来。
     * <p>
     * 注意不要用 {@code player.onGround()} 做落地判断：{@code handleMovePlayer} 里
     * {@code setOnGround(flag4)} 的 flag4 含 {@code !player.mayFly()}，只要 mayfly 开着，
     * 服务端 onGround 就恒为 false，粘滞将永远解除不掉。
     */
    private static final Set<UUID> STICKY_FLIGHT = ConcurrentHashMap.newKeySet();

    /** 客户端上报飞行意图时更新粘滞状态（由 {@code ServerGamePacketListenerImplFlightMixin} 调用）。 */
    public static void onClientFlightIntent(Player player, boolean flying) {
        if (flying) {
            STICKY_FLIGHT.add(player.getUUID());
        } else {
            STICKY_FLIGHT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            FLIGHT_RESYNC_QUEUE.put(serverPlayer.getUUID(), FLIGHT_RESYNC_WINDOW);
            // 本事件在 changeGameModeForPlayer 之前触发，此刻读到的就是「切换前」的状态
            if (serverPlayer.getAbilities().flying) {
                STICKY_FLIGHT.add(serverPlayer.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        updateBeefToolFlight(player);

        updateBeefInvulnerability(player);
        
        MiningDispatcher.tickCacheUpdate(player);
    }

    /**
     * 游戏模式切换的瞬间把飞行状态刷成目标值。
     * <p>
     * 供 {@code ServerPlayerGameModeMixin} 调用。服务端在切模式时会先按新模式重置 abilities，
     * 而紧接着 {@code ServerPlayer#setGameMode} 就会把这个状态发给客户端（能力包 A/C）。
     * 如果不在这里立刻补回去，客户端会先收到 {@code mayfly=false}，要等下一个 tick 模组的补发
     * 才恢复 —— 中间 1 帧双击空格起不了飞。在这里补掉，客户端从头到尾看不到 false。
     */
    public static void applyBeefToolFlightNow(Player player) {
        updateBeefToolFlight(player);
    }

    /**
     * 造化杖飞行状态维护。
     * <p>
     * 关键点：能力包只在「服务端状态发生变化」时才发是不够的——客户端那份 abilities 会被
     * {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#setLocalMode} 单独重置，
     * 服务端不知情也就永远不会补发。所以这里每 tick 把 abilities 对齐到目标值，
     * 只要有任何一项不一致、或处在游戏模式切换后的重发窗口内，就重发一次能力包。
     */
    private static void updateBeefToolFlight(Player player) {
        boolean hasItemInInventory = ConfigManager.shouldEnableFlightEffect()
                && UselessItemUtils.hasTargetToolInInventory(player);

        // 创造/旁观模式自带飞行，这份能力归原版管，模组不接管也不撤销。
        // 重发窗口故意保留到离开该模式之后才开始倒数，那才是真正需要补发能力包的时机。
        if (player.isCreative() || player.isSpectator()) {
            if (hasItemInInventory) {
                FlyEffectedHolder.add(player);
            } else {
                FlyEffectedHolder.remove(player);
            }
            return;
        }

        UUID uuid = player.getUUID();
        Integer remaining = FLIGHT_RESYNC_QUEUE.get(uuid);
        boolean resyncing = remaining != null && remaining > 0;
        if (resyncing) {
            if (remaining <= 1) {
                FLIGHT_RESYNC_QUEUE.remove(uuid);
            } else {
                FLIGHT_RESYNC_QUEUE.put(uuid, remaining - 1);
            }
        }

        if (hasItemInInventory) {
            FlyEffectedHolder.add(player);
            float flightSpeed = (float) ConfigManager.getBeefToolFlightSpeed();

            // 粘滞飞行（见 STICKY_FLIGHT 注释）：只要客户端自己还报着「我在飞」，
            // 就不允许 flying 被外力（Re-Avaritia 每秒一次）清掉。
            // 玩家落地 / 主动关飞行时客户端会上报 false，粘滞随之解除。
            boolean flying = player.getAbilities().flying || STICKY_FLIGHT.contains(uuid);

            applyFlightAbilities(player, true, flying, flightSpeed, resyncing);
            return;
        }

        // 没有造化杖时只回收模组自己授予过的飞行，避免抢走其它来源的飞行能力。
        // 归属标记落在玩家持久化数据里，所以「带着模组授予的飞行离线后重登」也能正确回收。
        STICKY_FLIGHT.remove(uuid);
        if (FlyEffectedHolder.remove(player)) {
            applyFlightAbilities(player, false, false, DEFAULT_FLYING_SPEED, true);
        }
    }

    private static void applyFlightAbilities(Player player,
                                             boolean mayfly,
                                             boolean flying,
                                             float flyingSpeed,
                                             boolean forceSync) {
        Abilities abilities = player.getAbilities();
        boolean abilitiesChanged = false;
        if (abilities.mayfly != mayfly) {
            abilities.mayfly = mayfly;
            abilitiesChanged = true;
        }
        if (abilities.flying != flying) {
            abilities.flying = flying;
            abilitiesChanged = true;
        }
        if (Math.abs(abilities.getFlyingSpeed() - flyingSpeed) > FLYING_SPEED_EPSILON) {
            abilities.setFlyingSpeed(flyingSpeed);
            abilitiesChanged = true;
        }
        if (abilitiesChanged || forceSync) {
            player.onUpdateAbilities();
        }
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
        boolean hasAdvancedStealth = UselessItemUtils.hasAdvancedStealthEnabledTargetToolInInventory(player);
        if (hasAdvancedStealth && !hasItemInInventory) {
            UselessItemUtils.enableInvulnerabilityForAdvancedStealth(player);
            hasItemInInventory = UselessItemUtils.hasInvulnerabilityEnabledTargetToolInInventory(player);
        }
        UUID uuid = player.getUUID();

        if (hasItemInInventory) {
            boolean newlyTracked = BEEF_PROTECTED_PLAYERS.add(uuid);
            claimBeefInvulnerability(player);
            if (newlyTracked) {
                clearBeefNegativeEffects(player);
            }
        } else {
            BEEF_PROTECTED_PLAYERS.remove(uuid);
            releaseBeefInvulnerability(player);
        }

        boolean newlyStealthTracked = hasAdvancedStealth && BEEF_ADVANCED_STEALTH_PLAYERS.add(uuid);
        boolean stealthReleased = !hasAdvancedStealth && BEEF_ADVANCED_STEALTH_PLAYERS.remove(uuid);
        if (newlyStealthTracked) {
            clearBeefAdvancedStealthState(player);
        }
        if (player instanceof ServerPlayer serverPlayer
                && (forceSync || newlyStealthTracked || stealthReleased || player.tickCount % 20 == 0)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    serverPlayer,
                    new BeefInvulnerabilityStatePacket(serverPlayer.getId(), hasAdvancedStealth)
            );
        }
    }

    private static void clearBeefNegativeEffects(Player player) {
        player.getActiveEffects().stream()
                .filter(EventHandler::isNonBeneficialEffect)
                .map(MobEffectInstance::getEffect)
                .toList()
                .forEach(player::removeEffect);
    }

    private static void clearBeefAdvancedStealthState(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof Warden warden
                        && (warden.getTarget() == player || warden.getEntityAngryAt().orElse(null) == player)) {
                    warden.setAttackTarget(null);
                    warden.clearAnger(player);
                } else if (entity instanceof Mob mob && mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }
    }

    static boolean isNonBeneficialEffect(MobEffectInstance effect) {
        return !effect.getEffect().value().isBeneficial();
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
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            BeefInvulnerabilityOwnership.rememberMaxHealthBase(ownershipData, maxHealth.getBaseValue());
        }
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

    public static boolean isRestoringBeefProtectedPlayer(Player player) {
        return RESTORING_BEEF_PROTECTED_PLAYERS.contains(player.getUUID());
    }

    public static boolean hasBeefInvulnerabilityItem(Player player) {
        if (player.level().isClientSide()) {
            return UselessItemUtils.hasInvulnerabilityEnabledTargetToolInInventory(player);
        }
        return BEEF_PROTECTED_PLAYERS.contains(player.getUUID())
                || UselessItemUtils.hasInvulnerabilityEnabledTargetToolInInventory(player);
    }

    public static boolean hasBeefAdvancedStealthItem(Player player) {
        if (player.level().isClientSide()) {
            return CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS.contains(player.getId())
                    || UselessItemUtils.hasAdvancedStealthEnabledTargetToolInInventory(player);
        }
        return BEEF_ADVANCED_STEALTH_PLAYERS.contains(player.getUUID())
                || UselessItemUtils.hasAdvancedStealthEnabledTargetToolInInventory(player);
    }

    public static boolean shouldApplyBeefAdvancedStealth(Player player) {
        return !player.level().isClientSide() && hasBeefAdvancedStealthItem(player);
    }

    public static boolean hasAnyBeefAdvancedStealthPlayers() {
        return !BEEF_ADVANCED_STEALTH_PLAYERS.isEmpty()
                || !CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS.isEmpty();
    }

    public static void setClientBeefAdvancedStealthState(int entityId, boolean stealthEnabled) {
        if (stealthEnabled) {
            CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS.add(entityId);
            return;
        }
        CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS.remove(entityId);
    }

    public static void clearClientBeefAdvancedStealthStates() {
        CLIENT_BEEF_ADVANCED_STEALTH_ENTITY_IDS.clear();
    }

    public static void restoreBeefProtectedPlayer(Player player) {
        if (!RESTORING_BEEF_PROTECTED_PLAYERS.add(player.getUUID())) {
            return;
        }

        try {
            if (!player.level().isClientSide()) {
                BEEF_PROTECTED_PLAYERS.add(player.getUUID());
                migrateLegacyBeefInvulnerability(player);
                claimBeefInvulnerability(player);
                restoreCorruptedMaxHealth(player);
            }

            float maxHealth = player.getMaxHealth();
            if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
                restoreCorruptedMaxHealth(player);
                maxHealth = player.getMaxHealth();
            }
            if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
                return;
            }

            player.dead = false;
            player.deathTime = 0;
            player.hurtTime = 0;
            player.hurtDuration = 0;
            player.setHealth(maxHealth);
            player.setPose(Pose.STANDING);
            player.clearFire();
            player.fallDistance = 0.0F;
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        serverPlayer,
                        new BeefInvulnerabilityStatePacket(
                                serverPlayer.getId(), BEEF_ADVANCED_STEALTH_PLAYERS.contains(serverPlayer.getUUID()))
                );
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new BeefInvulnerabilitySyncPacket(serverPlayer.getId(), maxHealth));
            }
        } finally {
            RESTORING_BEEF_PROTECTED_PLAYERS.remove(player.getUUID());
        }
    }

    private static void restoreCorruptedMaxHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        if (Double.isFinite(maxHealth.getBaseValue()) && maxHealth.getBaseValue() > 0.0D
                && Float.isFinite(player.getMaxHealth()) && player.getMaxHealth() > 0.0F) {
            return;
        }

        maxHealth.setBaseValue(BeefInvulnerabilityOwnership.previousMaxHealthBase(
                BeefInvulnerabilityOwnership.get(player)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        updateBeefInvulnerability(event.getEntity(), true);
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAdvancedStealthPlayersTo(player);
            GrassWandDropHandler.onPlayerLoggedIn(player);
            resetAutoClickState(player);
        }
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
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer trackingPlayer)
                || !(event.getTarget() instanceof ServerPlayer target)
                || !shouldApplyBeefAdvancedStealth(target)) {
            return;
        }

        sendAdvancedStealthState(trackingPlayer, target);
    }

    private static void syncAdvancedStealthPlayersTo(ServerPlayer viewer) {
        for (ServerPlayer target : viewer.serverLevel().players()) {
            if (target != viewer && shouldApplyBeefAdvancedStealth(target)) {
                sendAdvancedStealthState(viewer, target);
            }
        }
    }

    private static void sendAdvancedStealthState(ServerPlayer viewer, ServerPlayer target) {
        PacketDistributor.sendToPlayer(
                viewer,
                new BeefInvulnerabilityStatePacket(target.getId(), true)
        );
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
        BEEF_ADVANCED_STEALTH_PLAYERS.remove(event.getEntity().getUUID());
        // 玩家在创造模式下离线时，飞行重发窗口不会开始倒数，故在此一并清除
        FLIGHT_RESYNC_QUEUE.remove(event.getEntity().getUUID());
        STICKY_FLIGHT.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            GrassWandDropHandler.onPlayerLoggedOut(player);
        }
    }

    /**
     * 连点模式的状态存在物品组件上，会跨会话保留；若玩家带着「开启」状态重登，
     * 一进游戏就会立刻开始自动右键（叠加打火石等功能后果更明显）。
     * 这里在登录时统一重置一次，保证每次进入游戏都是关闭状态。
     */
    private static void resetAutoClickState(ServerPlayer player) {
        boolean changed = clearAutoClickFlag(player.getInventory().items);
        changed |= clearAutoClickFlag(player.getInventory().offhand);
        changed |= clearAutoClickFlag(player.getInventory().armor);
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean clearAutoClickFlag(Iterable<ItemStack> stacks) {
        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof EndlessBeafItem && EndlessBeafItem.isAutoClickEnabled(stack)) {
                EndlessBeafItem.setAutoClickEnabled(stack, false);
                changed = true;
            }
        }
        return changed;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;
        if (event.isCanceled() || ConstructionWandLogic.isEnabled(stack)) return;

        Player player = event.getEntity();
        if (BeefTimeAcceleration.shouldBlockOtherRightClick(stack, player)) return;
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
     * AE 连接模式：右键「能连入 AE 网络」的机器，把它的 AE 节点并入工具绑定的那张网。
     *
     * <p>必须拦在 {@link PlayerInteractEvent.RightClickBlock} 这一层，而不是 {@code Item#useOn}：
     * 右键 ME 设备默认会开方块 GUI，只有取消本次交互才拦得住。
     * 潜行时直接让位给既有的「Shift + 右键绑定无线访问点 / 自动装配合金炉」。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAeConnectInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getEntity().isShiftKeyDown()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;
        if (!stack.getOrDefault(UComponents.AeNetworkConnectComponent.get(), false)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        // 目标不是 AE 节点宿主就不接管，交给原有的右键链路（收菜 / 工具动作等）。
        if (!AeDeviceLinker.isLinkTarget(level, pos)) return;

        if (level instanceof ServerLevel serverLevel && event.getEntity() instanceof ServerPlayer serverPlayer) {
            AeDeviceLinker.toggle(serverLevel, serverPlayer, stack, pos);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    /**
     * AE 连接的续命闹钟：AE2 不保存非空间网格连接，区块 / 存档重载后要把登记过的连接补回来。
     * 每 20 tick 跑一次，链接表为空时几乎零开销。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        AeDeviceLinker.ensureLinks(server);
    }

    /**
     * 无线物流搬运引擎。
     *
     * <p>单独一个订阅而不是并进 {@link #onServerTick}：那条路径有 20 tick 的闸门，
     * 而线路的搬运周期最短是 1 tick。</p>
     */
    @SubscribeEvent
    public static void onStaffLinkTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        StaffLinkEngine.tick(server);
        if (server.getTickCount() % 20 == 0) {
            pushStaffLinkStatus(server);
        }
    }

    /** 把「上次搬了多少」推给开着无线物流界面的玩家，界面上有一行读数。 */
    private static void pushStaffLinkStatus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof StaffLinkMenu menu)) {
                continue;
            }
            StaffLinkEngine.TransferStats stats = StaffLinkEngine.lastTransfer(menu.getNetworkId());
            PacketDistributor.sendToPlayer(player, new StaffLinkStatusPacket(
                    menu.getNetworkId(), stats.requested(), stats.moved(), stats.targets(),
                    stats.tick(), stats.blocker()));
        }
    }

    /**
     * 无线物流模式：潜行右键容器方块，把它绑进/解绑出这把杖的物流网络。
     *
     * <p>与 {@link #onBlockInteract} 分开实现：那个方法分支多且早返回，这里目标类型完全不同
     * （探测的是物品/流体/能量/化学品/魔源能力，无线访问点不具备这些）。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStaffLinkBind(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof EndlessBeafItem)) return;
        if (!EndlessBeafItem.isStaffLinkEnabled(stack)) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!StaffLinkTargets.isBindable(level, pos)) return;

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            StaffLinkBinding.toggle(serverLevel, serverPlayer, stack, pos);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    /**
     * 服务器启动时构建配方索引
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GrassWandDropHandler.clearCache();
        UselessDimensionConfigManager.applyAll(event.getServer());
        AlloyFurnaceRecipeManager.getInstance().buildIndex(event.getServer().overworld());
        // 目录构建是数万条配方的 CPU 密集工作（实测耗时 20 秒），同步执行会阻塞世界加载。
        // 改为后台构建：查询路径读取 snapshotIfReady，未就绪时返回空，不会读到半成品。
        AlloyFurnaceRecipeCatalog.prewarmAsync(event.getServer().overworld());
        event.getServer().getPlayerList().getPlayers().forEach(EndlessBeafItem::refreshAttackDamage);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BEEF_PROTECTED_PLAYERS.clear();
        BEEF_ADVANCED_STEALTH_PLAYERS.clear();
        GrassWandDropHandler.clearCache();
        // 通道豁免索引里存的是网格节点引用，别把它们留到下一局。
        AeLinkChannelBypass.clear();
        // 无线物流的调度表按 tick 计数，同样不能跨局沿用。
        StaffLinkEngine.clearRuntimeState();
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
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    AlloyFurnaceRecipeManager.getInstance().invalidateIndex(server.overworld());
                    // 同上：数据包重载不该被整段目录重建阻塞，交给后台构建。
                    AlloyFurnaceRecipeCatalog.prewarmAsync(server.overworld());
                    server.getPlayerList().getPlayers().forEach(EndlessBeafItem::refreshAttackDamage);
                } else {
                    AlloyFurnaceRecipeManager.getInstance().invalidateIndex();
                }
            }, gameExecutor);
        });
    }
}
