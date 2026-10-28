package com.sorrowmist.useless.client.gui.apple;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 进度胶囊：灰轨道 + 彩色胶囊填充（带顶部高光）。可选右侧/内嵌文字。ratio 0..1。 */
public class AppleProgress extends AppleWidget {
    private final DoubleSupplier ratio;
    private final IntSupplier color;
    private Supplier<Component> inner;
    private boolean vertical;

    public AppleProgress(int x, int y, int w, int h, DoubleSupplier ratio, IntSupplier color) {
        super(x, y, w, h);
        this.ratio = ratio; this.color = color;
    }

    /** 文字画在轨道中央（轨道高度 ≥ 12 时使用）。 */
    public AppleProgress inner(Supplier<Component> s) { inner = s; return this; }
    public AppleProgress vertical() { vertical = true; return this; }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, double r, int col) {
        r = Math.max(0, Math.min(1, r));
        com.sorrowmist.useless.client.gui.AppleUi.progress(g, x, y, w, h, (float) r, col);
    }

    public static void drawVertical(GuiGraphics g, int x, int y, int w, int h, double r, int col) {
        r = Math.max(0, Math.min(1, r));
        AppleUi2.round(g, x, y, w, h, w / 2f, AppleStyle.TRACK);
        if (r <= 0) return;
        float fh = (float) (h * r);
        AppleUi2.round(g, x, y + h - fh, w, fh, w / 2f, col);
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        if (vertical) { drawVertical(g, x, y, w, h, ratio.getAsDouble(), color.getAsInt()); return; }
        draw(g, x, y, w, h, ratio.getAsDouble(), color.getAsInt());
        if (inner != null) {
            Component c = inner.get();
            if (c != null) text(g, c, x + (w - font().width(c)) / 2, cy(y, h), AppleStyle.LABEL);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
