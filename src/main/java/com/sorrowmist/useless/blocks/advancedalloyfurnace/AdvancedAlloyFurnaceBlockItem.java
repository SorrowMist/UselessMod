package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class AdvancedAlloyFurnaceBlockItem extends BlockItem {
    
    public AdvancedAlloyFurnaceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }
    
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        // 从物品的NBT中恢复方块实体数据
        if (level.isClientSide) {
            return false;
        }
        
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
            if (blockEntityTag != null) {
                furnace.load(blockEntityTag);
                furnace.setChanged();
            }
        }
        
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}