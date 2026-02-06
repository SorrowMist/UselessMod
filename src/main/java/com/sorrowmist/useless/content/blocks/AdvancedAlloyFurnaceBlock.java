package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdvancedAlloyFurnaceBlock extends Block implements EntityBlock {

    private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public AdvancedAlloyFurnaceBlock() {
        super(BlockBehaviour.Properties.of()
                                       .noOcclusion()
                                       .strength(5.0F, 32768.0F)
                                       .requiresCorrectToolForDrops()
                                       .lightLevel(state -> state.getValue(ACTIVE) ? 15 : 7));

        this.registerDefaultState(this.stateDefinition.any()
                                                      .setValue(FACING, Direction.NORTH)
                                                      .setValue(ACTIVE, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    /**
     * 方块被移除时的处理
     * <p>
     * 掉落方块实体中的所有物品
     *
     * @param state    当前状态
     * @param level    世界
     * @param pos      位置
     * @param newState 新状态
     * @param isMoving 是否在移动
     */
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                // 掉落物品
                IItemHandler itemHandler = furnace.getItemHandler();
                if (itemHandler != null) {
                    for (int i = 0; i < itemHandler.getSlots(); i++) {
                        ItemStack stack = itemHandler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            popResource(level, pos, stack);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 空手右键点击方块的处理
     * <p>
     * 打开方块 GUI
     *
     * @param state     方块状态
     * @param level     世界
     * @param pos       位置
     * @param player    玩家
     * @param hitResult 命中结果
     * @return 交互结果
     */
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state,
                                                        Level level,
                                                        @NotNull BlockPos pos,
                                                        @NotNull Player player,
                                                        @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(this.getMenuProvider(state, level, pos), pos);
        return InteractionResult.CONSUME;
    }

    /**
     * 手持物品右键点击方块的处理
     * <p>
     * 优先处理流体交互，然后打开 GUI
     *
     * @param stack     手持物品
     * @param state     方块状态
     * @param level     世界
     * @param pos       位置
     * @param player    玩家
     * @param hand      手
     * @param hitResult 命中结果
     * @return 物品交互结果
     */
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                       @NotNull BlockState state,
                                                       Level level,
                                                       @NotNull BlockPos pos,
                                                       @NotNull Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AdvancedAlloyFurnaceBlockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 流体交互
        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidHandler != null) {
            boolean isFluidContainer = !stack.isEmpty() &&
                    (stack.getCapability(Capabilities.FluidHandler.ITEM) != null ||
                            FluidUtil.getFluidContained(stack).isPresent());

            if (isFluidContainer) {
                boolean success = FluidUtil.interactWithFluidHandler(player, hand, fluidHandler);
                if (success) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        // 打开GUI
        player.openMenu(this.getMenuProvider(state, level, pos), pos);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, Level level, @NotNull BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AdvancedAlloyFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                AdvancedAlloyFurnaceBlockEntity.tick(lvl, pos, st, furnace);
            }
        };
    }
}
