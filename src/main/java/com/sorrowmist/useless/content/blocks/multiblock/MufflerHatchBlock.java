package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** Fixed upward exhaust; the output slot can be opened with either an empty or occupied hand. */
public final class MufflerHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MufflerHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState(); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, Direction.UP); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MufflerHatchBlockEntity(pos, state);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MufflerHatchBlockEntity hatch) {
            NetworkHooks.openScreen(server, hatch, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof MufflerHatchBlockEntity hatch) hatch.dropContents();
        super.onRemove(state, level, pos, next, moved);
    }
}
