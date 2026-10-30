package com.sorrowmist.useless.compat.neoecoae.compact.block;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 无用型紧凑 C9 的方块形态。
 *
 * <p>直接继承 ECO 的计算系统主机方块，复用 ECO 20.5.x 的 LDLib 原生界面入口。</p>
 */
public class CompactC9Block extends ECOComputationSystem {

    public CompactC9Block(Properties properties) {
        super(properties);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CompactMachineDrops.dropForCreativePlayer(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
}
