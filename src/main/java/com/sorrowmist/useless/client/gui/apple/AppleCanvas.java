package com.sorrowmist.useless.client.gui.apple;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 自绘区域控件：把界面特有的图形（结构立方体、六面十字、配方行、热力图……）挂进 Widget 树，
 * 颜色/圆角仍取自 {@link AppleStyle}。clicker / scroller 可空。
 */
public class AppleCanvas extends AppleWidget {
    public interface Painter { void paint(GuiGraphics g, int mx, int my, int x, int y, int w, int h); }
    public interface Clicker { boolean click(double mx, double my, int button); }
    public interface Scroller { boolean scroll(double mx, double my, double delta); }

    private final Painter painter;
    private Painter foreground;
    private Clicker clicker;
    private Scroller scroller;

    public AppleCanvas(int x, int y, int w, int h, Painter painter) {
        super(x, y, w, h);
        this.painter = painter;
    }

    public AppleCanvas onClick(Clicker c) { clicker = c; return this; }
    public AppleCanvas onScroll(Scroller s) { scroller = s; return this; }
    public AppleCanvas foreground(Painter p) { foreground = p; return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible() || painter == null) return;
        painter.paint(g, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public void drawInForeground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible() || foreground == null) return;
        foreground.paint(g, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (clicker == null || !isVisible() || !isEnabled() || !isMouseOverElement(mx, my)) return false;
        return clicker.click(mx, my, button);
    }

    @Override
    public boolean mouseWheelMove(double mx, double my, double delta) {
        if (scroller == null || !isVisible() || !isMouseOverElement(mx, my)) return false;
        return scroller.scroll(mx, my, delta);
    }
}
