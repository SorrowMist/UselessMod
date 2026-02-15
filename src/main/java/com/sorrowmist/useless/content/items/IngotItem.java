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

    public IngotItem(Properties properties) {
        super(properties);
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
                // 获取目标阶级
                int targetTier = getIngotTier(stack);
                if (targetTier > 0) {
                    int currentTier = furnace.getFurnaceTier();
                    if (targetTier > currentTier) {
                        // 尝试升级
                        if (furnace.tryUpgrade(targetTier)) {
                            // 消耗一个锭
                            stack.shrink(1);
                            return InteractionResult.SUCCESS;
                        }
                    }
                    // 升级失败（阶级相同或更低），返回CONSUME阻止其他交互
                    return InteractionResult.CONSUME;
                }
            }
        }
        
        return super.useOn(context);
    }

    /**
     * 获取锭的阶级
     * @param stack 物品堆
     * @return 锭的阶级（1-9），如果不是无用锭则返回0
     */
    private static int getIngotTier(ItemStack stack) {
        // 这里需要通过物品注册名来判断阶级
        // 由于无法直接访问ModItems，我们使用物品的描述ID
        String itemId = stack.getItem().toString();
        if (itemId.contains("useless_ingot_tier_1")) return 1;
        if (itemId.contains("useless_ingot_tier_2")) return 2;
        if (itemId.contains("useless_ingot_tier_3")) return 3;
        if (itemId.contains("useless_ingot_tier_4")) return 4;
        if (itemId.contains("useless_ingot_tier_5")) return 5;
        if (itemId.contains("useless_ingot_tier_6")) return 6;
        if (itemId.contains("useless_ingot_tier_7")) return 7;
        if (itemId.contains("useless_ingot_tier_8")) return 8;
        if (itemId.contains("useless_ingot_tier_9")) return 9;
        return 0;
    }
}
