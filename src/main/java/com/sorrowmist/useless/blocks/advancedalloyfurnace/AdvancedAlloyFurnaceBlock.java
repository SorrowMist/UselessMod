// AdvancedAlloyFurnaceBlock.java
package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class AdvancedAlloyFurnaceBlock extends Block implements EntityBlock {
    // 注册器
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 注册方块和物品
    public static final RegistryObject<Block> ADVANCED_ALLOY_FURNACE_BLOCK = BLOCKS.register("advanced_alloy_furnace_block",
            () -> new AdvancedAlloyFurnaceBlock());

    public static final RegistryObject<Item> ADVANCED_ALLOY_FURNACE_BLOCK_ITEM = ITEMS.register("advanced_alloy_furnace_block",
            () -> new BlockItem(ADVANCED_ALLOY_FURNACE_BLOCK.get(), new Item.Properties()));

    // 方块构造函数
    public AdvancedAlloyFurnaceBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(5.0F, 32768.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)); // 发光效果
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
                // 掉落方块实体中的物品和流体
                furnace.drops();
                // 通知客户端更新
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
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