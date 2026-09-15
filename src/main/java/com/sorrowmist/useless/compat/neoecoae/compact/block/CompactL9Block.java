package com.sorrowmist.useless.compat.neoecoae.compact.block;

import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 无用型紧凑 L9 的方块形态。
 *
 * <p>直接继承 ECO 的存储系统主机方块，因此「拆掉时把无限域写进掉落物、放回去时恢复」
 * 这条 ECO 原生链路对紧凑方块同样生效。</p>
 */
public class CompactL9Block extends ECOStorageSystemBlock {

    public CompactL9Block(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CompactInterfaceAccess.INTERFACE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CompactInterfaceAccess.INTERFACE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!CompactInterfaceAccess.isPlayerCloseEnough(this, level, pos, player)) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                CompactInterfaceAccess.open(this, state, pos, serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CompactInterfaceAccess.openMain(this, state, pos, serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
