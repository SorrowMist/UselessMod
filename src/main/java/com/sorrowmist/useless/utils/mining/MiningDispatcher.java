package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.network.MiningDataSyncPacket;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class MiningDispatcher {
    private static final MiningStrategy DEFAULT_STRATEGY = new DefaultMiningStrategy();
    // 普通连锁挖掘
    private static final MiningStrategy CHAIN_STRATEGY = new ChainMiningStrategy(false);
    // 增强连锁挖掘
    private static final MiningStrategy ENHANCED_CHAIN_STRATEGY = new ChainMiningStrategy(true);

    // 存储每个玩家的挖矿数据（服务端）
    private static final Map<UUID, PlayerMiningData> playerDataMap = new ConcurrentHashMap<>();

    // 客户端数据存储
    private static PlayerMiningData clientPlayerData = null;

    public static PlayerMiningData getPlayerData(Player player) {
        if (player.level().isClientSide()) {
            return clientPlayerData;
        }
        return playerDataMap.get(player.getUUID());
    }

    public static void setClientPlayerData(PlayerMiningData data) {
        clientPlayerData = data;
    }

    /**
     * 获取或创建玩家的挖矿数据
     *
     * @param player 玩家
     * @return 玩家挖矿数据
     */
    static PlayerMiningData getOrCreatePlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUUID(), PlayerMiningData::new);
    }

    /**
     * 设置玩家的Tab键状态
     *
     * @param player  玩家
     * @param pressed 是否按下
     */
    public static void setTabPressed(Player player, boolean pressed) {
        PlayerMiningData playerData = getOrCreatePlayerData(player);
        playerData.setTabPressed(pressed);

        // 同步到客户端
        if (player instanceof ServerPlayer serverPlayer) {
            MiningDataSyncPacket packet = new MiningDataSyncPacket(playerData);
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    /**
     * 清空玩家的方块缓存
     * 在切换连锁模式时调用，避免使用旧模式的缓存数据
     *
     * @param player 玩家
     */
    public static void clearPlayerCache(ServerPlayer player) {
        PlayerMiningData playerData = playerDataMap.get(player.getUUID());
        if (playerData != null) {
            playerData.clearCache();
            // 同步到客户端
            MiningDataSyncPacket packet = new MiningDataSyncPacket(playerData);
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    /**
     * 入口方法：根据组件值分派挖掘逻辑。
     *
     * @param event  BreakEvent事件
     * @param item   主手物品
     * @param player 玩家
     */
    public static void dispatchBreak(BlockEvent.BreakEvent event, ItemStack item, Player player) {
        if (player.isCreative()) return;

        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;

        MiningStrategy strategy = DEFAULT_STRATEGY;  // 默认普通（不连锁）

        // 获取玩家的挖矿数据，检查Tab键状态
        PlayerMiningData playerData = getOrCreatePlayerData(player);

        // 检查是否启用了连锁挖掘
        if (playerData.isTabPressed()) {
            // false -> 普通连锁挖掘
            // true -> 增强连锁挖掘
            if (UComponentUtils.isEnhancedChainMiningEnabled(item)) {
                strategy = ENHANCED_CHAIN_STRATEGY;
            } else {
                strategy = CHAIN_STRATEGY;
            }
        }

        // 调用策略处理
        strategy.handleBreak(event, item, player);
    }

    public static void tickCacheUpdate(Player player) {
        PlayerMiningData data = getOrCreatePlayerData(player);

        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            data.clearCache();
            // 同步到客户端
            if (player instanceof ServerPlayer serverPlayer) {
                MiningDataSyncPacket packet = new MiningDataSyncPacket(data);
                PacketDistributor.sendToPlayer(serverPlayer, packet);
            }
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) return;

        // 只有按下Tab时才更新缓存，松开Tab时保留缓存
        if (!data.isTabPressed()) {
            return;
        }

        // 获取触及距离并进行射线检测
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        HitResult hitResult = player.pick(reach, 0.0f, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos currentPos = ((BlockHitResult) hitResult).getBlockPos();
            // 只有准星移动到了新方块，才重新计算
            if (data.getCachedPos() == null || !data.getCachedPos().equals(currentPos)) {
                BlockState state = level.getBlockState(currentPos);
                boolean forceMining = UComponentUtils.isForceMiningEnabled(hand);
                boolean enhancedChainMining = UComponentUtils.isEnhancedChainMiningEnabled(hand);

                // 重新扫描并存入缓存（根据是否启用增强连锁选择不同的扫描方式）
                List<BlockPos> blocks;
                if (enhancedChainMining) {
                    blocks = MiningUtils.findBlocksToMineEnhanced(currentPos, state, level, hand, forceMining);
                } else {
                    blocks = MiningUtils.findBlocksToMine(currentPos, state, level, hand, forceMining);
                }

                data.setCachedPos(currentPos);
                data.setCachedBlocks(blocks);

                // 同步到客户端
                if (player instanceof ServerPlayer serverPlayer) {
                    MiningDataSyncPacket packet = new MiningDataSyncPacket(data);
                    PacketDistributor.sendToPlayer(serverPlayer, packet);
                }
            }
        } else {
            // 准星指天或指向空气，清理当前缓存
            data.clearCache();
            // 同步到客户端
            if (player instanceof ServerPlayer serverPlayer) {
                MiningDataSyncPacket packet = new MiningDataSyncPacket(data);
                PacketDistributor.sendToPlayer(serverPlayer, packet);
            }
        }
    }

    /**
     * 处理玩家离开事件，清理玩家数据，避免内存泄漏
     *
     * @param event 玩家离开事件
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playerDataMap.remove(player.getUUID());
        }
    }
}