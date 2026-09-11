package com.sorrowmist.useless.content.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class MultiblockAlloyFurnaceCoreBlockItem extends BlockItem {
    private static final ChatFormatting[] NAME_COLORS = {
            ChatFormatting.RED,
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.AQUA,
            ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE
    };
    private static final ChatFormatting[] PRAYER_LINE_COLORS = {
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.AQUA,
            ChatFormatting.AQUA,
            ChatFormatting.AQUA,
            ChatFormatting.BLUE,
            ChatFormatting.BLUE,
            ChatFormatting.BLUE,
            ChatFormatting.GOLD,
            ChatFormatting.GOLD,
            ChatFormatting.RED,
            ChatFormatting.RED,
            ChatFormatting.YELLOW,
            ChatFormatting.YELLOW,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.GREEN,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.WHITE,
            ChatFormatting.WHITE
    };
    private static final int PRAYER_LINE_COUNT = 22;

    public MultiblockAlloyFurnaceCoreBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return rainbowName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (int line = 1; line <= PRAYER_LINE_COUNT; line++) {
            tooltip.add(Component.translatable(
                    "tooltip.useless_mod.multiblock_alloy_furnace_core.prayer." + line)
                    .withStyle(PRAYER_LINE_COLORS[line - 1]));
        }
    }

    public static Component rainbowName(Component source) {
        String text = ChatFormatting.stripFormatting(source.getString());
        int offset = (int) Math.floor((System.currentTimeMillis() & 16383L) / 80.0D)
                % NAME_COLORS.length;
        MutableComponent result = Component.empty();
        for (int index = 0; index < text.length(); index++) {
            int colorIndex = (index + NAME_COLORS.length - offset) % NAME_COLORS.length;
            result.append(Component.literal(String.valueOf(text.charAt(index)))
                    .withStyle(NAME_COLORS[colorIndex]));
        }
        return result;
    }
}
