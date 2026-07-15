package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class IngotItem extends Item {
    private final int furnaceTier;

    public IngotItem(Properties properties, int furnaceTier) {
        super(properties);
        if (furnaceTier < 1 || furnaceTier > AdvancedAlloyFurnaceBlockEntity.MAX_FURNACE_TIER) {
            throw new IllegalArgumentException("Invalid furnace tier: " + furnaceTier);
        }
        this.furnaceTier = furnaceTier;
    }

    /**
     * 在方块上使用物品时的处理
     * 当玩家潜行右键万象合金炉时，手动触发升级逻辑
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        
        // 检查是否是万象合金炉且玩家正在潜行
        if (state.getBlock() instanceof AdvancedAlloyFurnaceBlock && player != null && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            
            // 获取方块实体
            if (level.getBlockEntity(pos) instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                int currentTier = furnace.getFurnaceTier();
                if (this.furnaceTier > currentTier) {
                    if (furnace.tryUpgrade(this.furnaceTier)) {
                        stack.shrink(1);
                        return InteractionResult.SUCCESS;
                    }
                }
                // 升级失败（阶级相同或更低），阻止继续打开界面。
                return InteractionResult.CONSUME;
            }
        }
        
        return super.useOn(context);
    }

    public int getFurnaceTier() {
        return this.furnaceTier;
    }
}
