package com.sorrowmist.useless.client.render;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class PatternSlotRenderer {
    private PatternSlotRenderer() {
    }

    public static boolean renderPattern(GuiGraphics graphics, Font font, ItemStack pattern,
                                        int x, int y, int seed, @Nullable Level level) {
        GenericStack output = getPrimaryOutput(pattern, level);
        if (output == null) return false;
        ItemStack display = GenericStack.wrapInItemStack(new GenericStack(output.what(), 1));
        graphics.renderItem(display, x, y, seed);
        renderAmount(graphics, font, x, y, output.amount());
        return true;
    }

    @Nullable
    public static GenericStack getPrimaryOutput(ItemStack pattern, @Nullable Level level) {
        if (pattern.isEmpty() || level == null || !PatternDetailsHelper.isEncodedPattern(pattern)) return null;
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        return details == null || details.getOutputs().isEmpty() ? null : details.getOutputs().getFirst();
    }

    private static void renderAmount(GuiGraphics graphics, Font font, int x, int y, long amount) {
        if (amount <= 1) return;
        String label = Long.toString(amount);
        int labelWidth = Math.max(1, font.width(label));
        float scale = Math.min(0.5F, 15.0F / labelWidth);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 17.0F, y + 17.0F, 200.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, label, -labelWidth, -8, 0xFFFFFF, true);
        pose.popPose();
    }
}
