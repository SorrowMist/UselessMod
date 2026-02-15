package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.core.component.FurnaceDataComponent;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 高级合金炉方块物品
 * 根据保存的阶级显示不同的名字
 */
public class AdvancedAlloyFurnaceBlockItem extends BlockItem {

    public AdvancedAlloyFurnaceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        
        FurnaceDataComponent component = stack.get(UComponents.FURNACE_DATA.get());
        if (component != null && component.tier() > 0) {
            tooltipComponents.add(Component.translatable("tooltip.useless_mod.furnace_tier", component.tier()));
        }
    }
}
