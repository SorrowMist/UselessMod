package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OreGeneratorBlock extends Block implements EntityBlock {

    // 方块构造函数
    public OreGeneratorBlock() {
        super(Properties.of()
                .strength(3.0F, 32768.0F)
                .requiresCorrectToolForDrops());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof OreGeneratorBlockEntity generator) {
                // 掉落方块实体中的物品
                generator.drops();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        var be = level.getBlockEntity(pos);
        if (!(be instanceof OreGeneratorBlockEntity blockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ItemStack held = player.getItemInHand(hand);

        // 玩家手上有东西 → 尝试放入（合并）
        if (!held.isEmpty()) {
            ItemStack remaining = blockEntity.insert(held);

            // 更新玩家手上物品
            player.setItemInHand(hand, remaining);
            return ItemInteractionResult.CONSUME;
        }

        // 玩家空手 → 取出全部
        if (player.isShiftKeyDown()) {
            ItemStack extracted = blockEntity.extractAll();
            if (!extracted.isEmpty()) {
                if (!player.addItem(extracted)) {
                    player.drop(extracted, false);
                }
                return ItemInteractionResult.CONSUME;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreGeneratorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null :
                blockEntityType == ModBlockEntities.ORE_GENERATOR.get()
                        ? (lvl, pos, st, be) -> ((OreGeneratorBlockEntity) be).tick()
                        : null;
    }
}