package com.sorrowmist.useless.compat.neoecoae.compact.block;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
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
 * 无用型紧凑 C9 的方块形态。
 *
 * <p>直接继承 ECO 的计算系统主机方块，于是「右键开 GUI」「玩家距离校验」「掉落物带设置」
 * 这些行为全部沿用 ECO 的实现，本类只提供一个自己的方块实体。</p>
 */
public class CompactC9Block extends ECOComputationSystem {

    public CompactC9Block(Properties properties) {
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
