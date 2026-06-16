package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挖掘调度器
 * 根据模式分派不同的挖掘策略处理方块破坏事件
 * 基于1.21 neoforge版本的MiningDispatcher重构
 */
@Mod.EventBusSubscriber(modid = "useless_mod")
public class MiningDispatcher {
    private static final MiningStrategy DEFAULT_STRATEGY = new DefaultMiningStrategy();
    private static final MiningStrategy CHAIN_STRATEGY = new ChainMiningStrategy(false);
    private static final MiningStrategy ENHANCED_CHAIN_STRATEGY = new ChainMiningStrategy(true);
    private static final MiningStrategy FORCE_STRATEGY = new ForceBreakStrategy();
    private static final MiningStrategy FORCE_CHAIN_STRATEGY = new ForceChainMiningStrategy(false);
    private static final MiningStrategy FORCE_ENHANCED_CHAIN_STRATEGY = new ForceChainMiningStrategy(true);

    private static final Map<UUID, PlayerMiningData> playerDataMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建玩家的挖掘数据
     */
    public static PlayerMiningData getOrCreatePlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUUID(), PlayerMiningData::new);
    }

    /**
     * 设置玩家的Tab键状态
     */
    public static void setTabPressed(Player player, boolean pressed) {
        PlayerMiningData playerData = getOrCreatePlayerData(player);
        playerData.setTabPressed(pressed);
    }

    /**
     * 分派挖掘逻辑
     */
    public static void dispatchBreak(BlockEvent.BreakEvent event, ItemStack item, Player player) {
        if (player.isCreative()) return;

        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;

        MiningStrategy strategy = DEFAULT_STRATEGY;

        // 检查Tab键状态（连锁挖掘）
        PlayerMiningData playerData = playerDataMap.get(player.getUUID());
        if (playerData != null && playerData.isTabPressed()) {
            if (MiningUtils.isEnhancedChainMiningMode(item)) {
                strategy = ENHANCED_CHAIN_STRATEGY;
            } else {
                strategy = CHAIN_STRATEGY;
            }
        }

        strategy.handleBreak(event, item, player);
    }

    /**
     * 强制破坏分派方法（R键触发）
     */
    public static void dispatchForceBreak(Player player, boolean tabPressed) {
        if (player.isCreative()) return;
        if (player.level().isClientSide()) return;

        BlockPos targetPos = MiningUtils.getTargetBlockPos(player);
        if (targetPos == null) return;

        if (!(player.level() instanceof ServerLevel level)) return;
        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) return;

        ItemStack hand = player.getMainHandItem();
        MiningStrategy strategy;

        if (tabPressed) {
            if (MiningUtils.isEnhancedChainMiningMode(hand)) {
                strategy = FORCE_ENHANCED_CHAIN_STRATEGY;
            } else {
                strategy = FORCE_CHAIN_STRATEGY;
            }
        } else {
            strategy = FORCE_STRATEGY;
        }

        BlockEvent.BreakEvent dummyEvent = new BlockEvent.BreakEvent(level, targetPos, state, player);
        strategy.handleBreak(dummyEvent, hand, player);
    }

    /**
     * 玩家离开时清理数据
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            playerDataMap.remove(event.getEntity().getUUID());
        }
    }
}
