package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.EnumSet;

public class MiningDispatcher {
    private static final MiningStrategy DEFAULT_STRATEGY = new DefaultMiningStrategy();
//    private static final MiningStrategy CHAIN_STRATEGY = new ChainMiningStrategy();
//    private static final MiningStrategy ENHANCED_CHAIN_STRATEGY = new EnhancedChainMiningStrategy();
//    private static final MiningStrategy FORCE_STRATEGY = new ForceMiningStrategy();
//    private static final MiningStrategy CHAIN_FORCE_STRATEGY = new ChainForceMiningStrategy();

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

        EnumSet<FunctionMode> modes = item.getOrDefault(UComponents.FunctionModesComponent.get(),
                                                        EnumSet.noneOf(FunctionMode.class)
        );

        // 优先级逻辑：假设互斥，选最高优先级模式（可自定义顺序）
        MiningStrategy strategy = DEFAULT_STRATEGY;  // 默认普通
//        if (modes.contains(FunctionMode.ENHANCED_CHAIN_MINING)) {
//            strategy = ENHANCED_CHAIN_STRATEGY;  // 增强连锁优先
//        } else if (modes.contains(FunctionMode.CHAIN_MINING)) {
//            strategy =
//                    modes.contains(FunctionMode.FORCE_MINING) ? CHAIN_FORCE_STRATEGY : CHAIN_STRATEGY;  // 如果有强制，切换到连锁强制
//        } else if (modes.contains(FunctionMode.FORCE_MINING)) {
//            strategy = FORCE_STRATEGY;
//        }
//        // 忽略AE_STORAGE_PRIORITY，如果它是存储相关，可在别处处理

        // 调用策略处理
        strategy.handleBreak(event, item, player);
    }
}