package com.sorrowmist.useless.compat.neoecoae.compact.block;

import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 无用型紧凑 L9 的方块形态。
 *
 * <p>直接继承 ECO 的存储系统主机方块，复用 ECO 20.5.x 的界面与无限域掉落物链路。</p>
 */
public class CompactL9Block extends ECOStorageSystemBlock {

    public CompactL9Block(Properties properties) {
        super(properties);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CompactMachineDrops.dropForCreativePlayer(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
}
