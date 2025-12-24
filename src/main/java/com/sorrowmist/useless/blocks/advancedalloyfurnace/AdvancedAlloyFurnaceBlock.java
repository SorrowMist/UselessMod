// AdvancedAlloyFurnaceBlock.java - 修改部分
package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class AdvancedAlloyFurnaceBlock extends Block implements EntityBlock {
    // 注册器
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 状态属性 - 添加ACTIVE属性
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    // 注册方块和物品
    public static final RegistryObject<Block> ADVANCED_ALLOY_FURNACE_BLOCK = BLOCKS.register("advanced_alloy_furnace_block",
            () -> new AdvancedAlloyFurnaceBlock());

    public static final RegistryObject<Item> ADVANCED_ALLOY_FURNACE_BLOCK_ITEM = ITEMS.register("advanced_alloy_furnace_block",
            () -> new AdvancedAlloyFurnaceBlockItem(ADVANCED_ALLOY_FURNACE_BLOCK.get(), new Item.Properties()));

    // 方块构造函数
    public AdvancedAlloyFurnaceBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(5.0F, 32768.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(ACTIVE) ? 15 : 7)); // 活动时更亮
        // 注册默认状态
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    // 添加状态定义
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    // 放置时设置朝向
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity) {
                // 使用 NetworkHooks 来确保数据正确传输
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, (MenuProvider) blockEntity, pos);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                // 确保方块实体的数据已保存，以便在精准采集模式下能正确获取NBT数据
                furnace.setChanged();
                
                // 对于有方块实体的方块，需要特殊处理以确保数据正确保存
                // 这里不直接创建物品实体，而是让掉落物处理系统来处理
                // BlockBreakUtils.getBlockDrops方法会正确处理战利品表
                // ForceBreakUtils.createSilkTouchDrop方法会保存方块实体的NBT数据
            }
        }
        // 调用父类方法，让方块正常破坏并触发掉落事件
        // 现在战利品表已修复，方块会有正常的掉落物
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedAlloyFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                furnace.tick();
            }
        };
    }
}