package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 消声仓方块。
 *
 * <p>消声仓只能位于结构最上层中心，排烟口固定朝上，因此放置时忽略玩家视线方向。
 * 冒烟由核心在工作状态下驱动，方块自身不注册 ticker。</p>
 *
 * <p>右键打开界面以取出仓内积存的物品，无论手持物品与否都能打开；拆除时把仓内物品掉落到
 * 世界中，避免库存随方块消失。</p>
 */
public final class MufflerHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MufflerHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, Direction.UP);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        openMenu(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 手持物品右键同样打开界面，不把手上的物品放进仓内：消声仓的输出槽位只允许取出。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void openMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MufflerHatchBlockEntity hatch) {
            hatch.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MufflerHatchBlockEntity(pos, state);
    }
}