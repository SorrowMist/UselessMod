package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.api.tool.FunctionMode;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class MiningDispatcher {
    private static final MiningStrategy DEFAULT_STRATEGY = new DefaultMiningStrategy();
    // 连锁
    private static final MiningStrategy CHAIN_STRATEGY = new ChainMiningStrategy();
//    private static final MiningStrategy ENHANCED_CHAIN_STRATEGY = new EnhancedChainMiningStrategy();
//    private static final MiningStrategy FORCE_STRATEGY = new ForceMiningStrategy();
//    private static final MiningStrategy CHAIN_FORCE_STRATEGY = new ChainForceMiningStrategy();

    // 存储每个玩家的挖矿数据
    private static final Map<UUID, PlayerMiningData> playerDataMap = new ConcurrentHashMap<>();

    public static PlayerMiningData getPlayerData(Player player) {
        return playerDataMap.get(player.getUUID());
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
    }

    /**
     * 入口方法：根据NBT分派挖掘逻辑。
     *
     * @param event  BreakEvent事件
     * @param item   主手物品
     * @param player 玩家
     */
    public static void dispatchBreak(BlockEvent.BreakEvent event, ItemStack item, Player player) {
        if (player.isCreative()) return;

        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;

        MiningStrategy strategy = DEFAULT_STRATEGY;  // 默认普通

        // 获取玩家的挖矿数据，检查Tab键状态
        PlayerMiningData playerData = getOrCreatePlayerData(player);
        if (playerData.isTabPressed() && UComponentUtils.hasFunctionMode(item, FunctionMode.CHAIN_MINING)) {
            strategy = CHAIN_STRATEGY;
        }

        // 调用策略处理
        strategy.handleBreak(event, item, player);
    }

    public static void tickCacheUpdate(Player player) {
        PlayerMiningData data = getOrCreatePlayerData(player);

        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || !UComponentUtils.hasFunctionMode(hand, FunctionMode.CHAIN_MINING)) {
            data.clearCache();
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
            // 关键：只有准星移动到了新方块，才重新计算 BFS
            if (data.getCachedPos() == null || !data.getCachedPos().equals(currentPos)) {
                BlockState state = level.getBlockState(currentPos);
                boolean forceMining = UComponentUtils.hasFunctionMode(hand, FunctionMode.FORCE_MINING);

                // 重新扫描并存入缓存
                List<BlockPos> blocks = MiningUtils.findBlocksToMine(currentPos, state, level, hand, forceMining);
                data.setCachedPos(currentPos);
                data.setCachedBlocks(blocks);
            }
        } else {
            // 准星指天或指向空气，清理当前缓存
            data.clearCache();
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