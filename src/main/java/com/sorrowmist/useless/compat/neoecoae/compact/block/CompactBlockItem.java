package com.sorrowmist.useless.compat.neoecoae.compact.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 紧凑方块的物品形态：额外挂一条说明，告诉玩家这个单方块等价于哪套满配多方块。
 *
 * <p>说明文本直接由物品 id 推导（`block.xxx` → `tooltip.xxx`），三份语言文件里已经写好。</p>
 */
public class CompactBlockItem extends BlockItem {

    public CompactBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String key = getDescriptionId().replaceFirst("^block\\.", "tooltip.");
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
