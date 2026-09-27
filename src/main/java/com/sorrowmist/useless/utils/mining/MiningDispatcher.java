package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.network.MiningDataSyncPacket;
import com.sorrowmist.useless.utils.UComponentUtils;
import com.sorrowmist.useless.utils.mining.shape.ChainMiningShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
    // R键单方块破坏策略
    private static final MiningStrategy FORCE_STRATEGY = new ForceBreakStrategy();
    // R键普通连锁破坏策略
    private static final MiningStrategy FORCE_CHAIN_STRATEGY = new ForceChainMiningStrategy(false);
    // R键增强连锁破坏策略
    private static final MiningStrategy FORCE_ENHANCED_CHAIN_STRATEGY = new ForceChainMiningStrategy(true);

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
     * 把玩家当前的挖矿数据同步到其客户端。
     *
     * <p>形状与 Tab 状态都参与客户端预测，任何一处变化都必须下发，否则两端判定不一致
     * 会出现整片高亮闪烁或实际破坏范围与预览不符。
     *
     * @param player 玩家
     */
    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerMiningData data = getOrCreatePlayerData(serverPlayer);
            // 数量上限在此刷新而非构造时固化：配置可在运行时被 OP 修改，随每次同步取当前值
            data.setMaxBlocks(ConfigManager.getChainMiningMaxBlocks());
            PacketDistributor.sendToPlayer(serverPlayer, new MiningDataSyncPacket(data));
        }
    }

    /**
     * 按滚动方向循环切换连锁形状。
     *
     * <p>形状属于玩家状态而非物品状态：同一根造化杖在不同玩家手里、甚至同一玩家换手后，
     * 都应当保持各自的选择，因此写入 {@link PlayerMiningData} 而不是物品组件。
     * 切换后立即清空方块缓存并同步，避免沿用旧形状的扫描结果。
     *
     * @param player 玩家
     * @param delta  滚动步数，正数为向后一个形状
     */
    public static void cycleShape(Player player, int delta) {
        if (delta == 0) {
            return;
        }
        PlayerMiningData data = getOrCreatePlayerData(player);
        ChainMiningShapes shape = data.getShape().cycle(delta);
        data.setShape(shape);
        data.clearCache();
        syncToClient(player);

        // 切换后回一条 actionbar：形状名与方向说明缺一不可，隧道类形状的实际走向取决于
        // 点击面与玩家朝向，仅凭名称无法判断会向哪个方向挖掘。
        player.displayClientMessage(Component.translatable(
                "gui.useless_mod.shape_switched",
                Component.translatable(shape.getTranslationKey()),
                Component.translatable(shape.getDescriptionKey())), true);
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

        syncToClient(player);
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

        // 获取玩家的挖矿数据
        PlayerMiningData playerData = getOrCreatePlayerData(player);

        // 检查是否启用了连锁挖掘（仅Tab键，R键由dispatchForceBreak处理）
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

        // 获取玩家指向的方块
        BlockPos currentPos = MiningUtils.getTargetBlockPos(player);

        if (currentPos != null) {
            // 只有准星移动到了新方块，才重新计算
            if (data.getCachedPos() == null || !data.getCachedPos().equals(currentPos)) {
                BlockState state = level.getBlockState(currentPos);
                boolean enhancedChainMining = UComponentUtils.isEnhancedChainMiningEnabled(hand);

                List<BlockPos> blocks = MiningUtils.scanBlocksToMine(
                        currentPos, state, level, hand, false, enhancedChainMining,
                        data.getShape(), MiningUtils.getTargetFace(player), player);

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

    /**
     * 强制破坏分派方法（R键触发）
     * 直接执行破坏，不通过BlockEvent
     *
     * @param player     玩家
     * @param tabPressed 是否同时按下了Tab键
     */
    public static void dispatchForceBreak(Player player, boolean tabPressed) {
        if (player.isCreative()) return;
        if (player.level().isClientSide()) return;

        // 获取玩家指向的方块
        BlockPos targetPos = MiningUtils.getTargetBlockPos(player);
        if (targetPos == null) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) return;

        ItemStack hand = player.getMainHandItem();
        if (!UComponentUtils.isForceMiningEnabled(hand)) return;
        MiningStrategy strategy;

        // 根据是否按下Tab键选择策略
        if (tabPressed) {
            // R + Tab：R键连锁破坏
            if (UComponentUtils.isEnhancedChainMiningEnabled(hand)) {
                strategy = FORCE_ENHANCED_CHAIN_STRATEGY;
            } else {
                strategy = FORCE_CHAIN_STRATEGY;
            }
        } else {
            // 仅R：R键单方块破坏
            strategy = FORCE_STRATEGY;
        }

        BlockEvent.BreakEvent dummyEvent = new BlockEvent.BreakEvent(level, targetPos, state, player);
        strategy.handleBreak(dummyEvent, hand, player);
    }
}
