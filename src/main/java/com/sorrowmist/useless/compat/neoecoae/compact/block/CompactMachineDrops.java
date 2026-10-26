package com.sorrowmist.useless.compat.neoecoae.compact.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 紧凑机器在创造模式下也通过原有掉落链路保留机器数据。 */
final class CompactMachineDrops {
    private CompactMachineDrops() {
    }

    static void dropForCreativePlayer(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            // 必须在 onRemove 拆除集群和方块实体之前生成掉落，才能导出设置、存储域和样板引用。
            Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY);
        }
    }
}
