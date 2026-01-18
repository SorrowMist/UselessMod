package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

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
        getOrCreatePlayerData(player).setTabPressed(pressed);
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