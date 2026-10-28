package com.sorrowmist.useless.client.gui.apple;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** 文字标签。align: 0 左 1 中 2 右。 */
public class AppleLabel extends AppleWidget {
    /** Reference typography hierarchy, expressed against Minecraft's 9px line box. */
    public enum Kind { TITLE, HEADLINE, LARGE_TITLE, BODY, CAPTION, FOOTNOTE }

    private final Supplier<Component> text;
    private final Kind kind;
    private int color = -1;
    private int align;
    private boolean wrap;
    private int maxLines = Integer.MAX_VALUE;

    public AppleLabel(int x, int y, int w, Kind kind, Supplier<Component> text) {
        super(x, y, w, 9);
        this.kind = kind; this.text = text;
    }

    public AppleLabel color(int c) { color = c; return this; }
    public AppleLabel right() { align = 2; return this; }
    public AppleLabel center() { align = 1; return this; }
    /** 按宽度自动换行（行距 10）。 */
    public AppleLabel wrap() { wrap = true; return this; }
    public AppleLabel lines(int count) { wrap = true; maxLines = Math.max(1, count); return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        Component c = text.get();
        if (c == null) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth();
        boolean bold = kind == Kind.TITLE || kind == Kind.HEADLINE || kind == Kind.LARGE_TITLE;
        Component cc = bold ? c.copy().withStyle(st -> st.withBold(true)) : c;
        float scale = textScale(kind);
        int def = switch (kind) {
            case CAPTION -> AppleStyle.TERTIARY;
            case FOOTNOTE -> AppleStyle.SECONDARY;
            default -> AppleStyle.LABEL;
        };
        int col = color == -1 ? def : color;
        if (w <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int budget = Math.max(1, (int) Math.floor(w / scale));
        if (wrap) {
            int ly = 0, lineIndex = 0;
            for (var line : font().split(cc, budget)) {
                if (lineIndex++ >= maxLines) break;
                int lw = font().width(line);
                int lx = align == 2 ? budget - lw : align == 1 ? (budget - lw) / 2 : 0;
                g.drawString(font(), line, lx, ly, col, false);
                ly += font().lineHeight + 1;
            }
        } else {
            var line = AppleTextLayout.fit(font(), cc, budget);
            int lw = font().width(line);
            int lx = align == 2 ? budget - lw : align == 1 ? (budget - lw) / 2 : 0;
            g.drawString(font(), line, lx, 0, col, false);
        }
        g.pose().popPose();
    }

    public static float textScale(Kind kind) {
        return switch (kind) {
            case TITLE -> 1f;
            case HEADLINE -> .9f;
            case BODY -> .85f;
            case CAPTION, FOOTNOTE -> .75f;
            case LARGE_TITLE -> 2f;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
