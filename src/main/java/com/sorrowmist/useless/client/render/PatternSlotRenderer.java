package com.sorrowmist.useless.client.render;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PatternSlotRenderer {
    private static final float AMOUNT_SCALE = 0.6F;
    private static final String[] AMOUNT_PREFIXES = {"k", "M", "G", "T", "P", "E", "Z", "Y"};

    private PatternSlotRenderer() {
    }

    public static boolean renderPattern(GuiGraphics graphics, Font font, ItemStack pattern,
                                        int x, int y, int seed, @Nullable Level level) {
        GenericStack output = getPrimaryOutput(pattern, level);
        if (output == null) return false;
        ItemStack display = output.what().wrapForDisplayOrFilter();
        graphics.renderItem(display, x, y, seed);
        renderAmount(graphics, font, x, y, output);
        return true;
    }

    @Nullable
    public static GenericStack getPrimaryOutput(ItemStack pattern, @Nullable Level level) {
        if (pattern.isEmpty() || level == null || !PatternDetailsHelper.isEncodedPattern(pattern)) return null;
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        return details == null || details.getOutputs().isEmpty() ? null : details.getOutputs().getFirst();
    }

    private static void renderAmount(GuiGraphics graphics, Font font, int x, int y, GenericStack output) {
        String label = formatAmount(output);
        if (label.isEmpty()) return;

        int scaledWidth = (int) (font.width(label) * AMOUNT_SCALE);
        int textX = x + 16 - scaledWidth;
        int textY = y + 11;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 300.0F);
        pose.scale(AMOUNT_SCALE, AMOUNT_SCALE, 1.0F);
        graphics.drawString(font, label,
                (int) (textX / AMOUNT_SCALE), (int) (textY / AMOUNT_SCALE), 0xFFFFFFFF, true);
        pose.popPose();
    }

    static String formatAmount(GenericStack output) {
        long amount = output.amount();
        int amountPerUnit = output.what().getAmountPerUnit();
        if (amount <= 0 || amountPerUnit <= 0) return "";

        double units = (double) amount / amountPerUnit;
        String unitSymbol = output.what().getUnitSymbol();
        return formatNumber(units) + (unitSymbol == null ? "" : unitSymbol);
    }

    private static String formatNumber(double value) {
        String prefix = "";
        for (int i = 0; value >= 1000.0D && i < AMOUNT_PREFIXES.length; i++) {
            value /= 1000.0D;
            prefix = AMOUNT_PREFIXES[i];
        }

        String number;
        if (value == Math.rint(value)) {
            number = Long.toString((long) value);
        } else {
            number = BigDecimal.valueOf(value)
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return number + prefix;
    }
}
