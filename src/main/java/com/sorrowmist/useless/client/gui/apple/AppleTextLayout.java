package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Shared inset and centerline rules for compact pills and icon-labelled controls. */
public final class AppleTextLayout {
    private AppleTextLayout() {}

    public record Metrics(float scale, int textLimit, int iconSize, int gap,
                          float textY, float iconY) {
        public float contentWidth(int textWidth) {
            return textWidth * scale + iconSize + (textWidth > 0 ? gap : 0);
        }

        public float startX(int width, int textWidth) {
            return (width - contentWidth(textWidth)) / 2f;
        }
    }

    /** Shrink modestly before truncating; both font line box and icon share one centerline. */
    public static Metrics measure(int width, int height, int textWidth, int lineHeight,
                                  int iconSize, int paddingX, int paddingY) {
        int innerHeight = Math.max(0, height - paddingY * 2);
        int icon = Math.min(Math.max(0, iconSize), innerHeight);
        int gap = icon > 0 && textWidth > 0 ? 4 : 0;
        int available = Math.max(0, width - paddingX * 2 - icon - gap);
        float vertical = Math.min(1f, innerHeight / (float) Math.max(1, lineHeight));
        float horizontal = textWidth <= 0 ? 1f : Math.min(1f, available / (float) textWidth);
        float scale = Math.min(vertical, Math.max(0.8f, horizontal));
        int limit = scale > 0 ? (int) Math.floor(available / scale) : 0;
        return new Metrics(scale, limit, icon, gap,
                (height - lineHeight * scale) / 2f, (height - icon) / 2f);
    }

    /** Keep translated component styles and add an ellipsis only when it fits. */
    public static FormattedCharSequence fit(Font font, Component label, int limit) {
        if (label == null || limit <= 0) return FormattedCharSequence.EMPTY;
        if (font.width(label) <= limit) return label.getVisualOrderText();
        int ellipsisWidth = font.width("…");
        if (limit < ellipsisWidth) return FormattedCharSequence.EMPTY;
        FormattedText clipped = font.substrByWidth(label, limit - ellipsisWidth);
        return Language.getInstance().getVisualOrder(FormattedText.composite(clipped,
                Component.literal("…").withStyle(label.getStyle())));
    }

    /** Width-bounded text for manually rendered rows, using the same scaled fitting as labels. */
    public static void draw(GuiGraphics g, Font font, Component label, float x, float y,
                            int width, float scale, int color, boolean right) {
        if (width <= 0 || scale <= 0 || label == null) return;
        FormattedCharSequence text = fit(font, label, (int) Math.floor(width / scale));
        float tx = right ? x + width - font.width(text) * scale : x;
        g.pose().pushPose();
        g.pose().translate(tx, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void drawCentered(GuiGraphics g, Font font, Component label,
                                    int x, int y, int width, int height,
                                    int paddingX, int paddingY, AppleUi.Icon icon, int color) {
        if (width <= 0 || height <= 0 || label == null) return;
        Metrics m = measure(width, height, font.width(label), font.lineHeight,
                icon == null ? 0 : 8, paddingX, paddingY);
        if (m.scale() <= 0) return;
        FormattedCharSequence text = fit(font, label, m.textLimit());
        int textWidth = font.width(text);
        float start = x + m.startX(width, textWidth);
        if (icon != null && m.iconSize() > 0) {
            g.pose().pushPose();
            g.pose().translate(start, y + m.iconY(), 0);
            AppleUi.icon(g, icon, 0, 0, m.iconSize(), color);
            g.pose().popPose();
            start += m.iconSize() + (textWidth > 0 ? m.gap() : 0);
        }
        g.pose().pushPose();
        g.pose().translate(start, y + m.textY(), 0);
        g.pose().scale(m.scale(), m.scale(), 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
