package com.sorrowmist.useless.client.gui.apple.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** 控件容器：按添加顺序绘制，点击从最上层（最后添加）开始派发。 */
public class WidgetGroup extends Widget {
    public final List<Widget> widgets = new ArrayList<>();

    public WidgetGroup(int x, int y, int w, int h) { super(x, y, w, h); }

    public WidgetGroup addWidget(Widget w) { return addWidget(widgets.size(), w); }
    public WidgetGroup addWidget(int index, Widget w) { w.setParent(this); widgets.add(index, w); return this; }
    public void removeWidget(Widget w) { if (widgets.remove(w)) w.setParent(null); }
    public void clearAllWidgets() { for (Widget w : widgets) w.setParent(null); widgets.clear(); }

    public Widget getFirstWidgetById(String id) {
        for (Widget w : widgets) {
            if (id.equals(w.getId())) return w;
            if (w instanceof WidgetGroup g) { Widget r = g.getFirstWidgetById(id); if (r != null) return r; }
        }
        return null;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mx, int my, float pt) {
        for (Widget w : new ArrayList<>(widgets)) if (w.isVisible()) w.drawInBackground(g, mx, my, pt);
    }

    @Override
    public void drawInForeground(GuiGraphics g, int mx, int my, float pt) {
        for (Widget w : new ArrayList<>(widgets)) if (w.isVisible()) w.drawInForeground(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (!isVisible()) return false;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.isVisible() && w.isActive() && w.mouseClicked(mx, my, b)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        boolean any = false;
        for (int i = widgets.size() - 1; i >= 0; i--) { Widget w = widgets.get(i); if (w.isVisible()) any |= w.mouseReleased(mx, my, b); }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        for (int i = widgets.size() - 1; i >= 0; i--) { Widget w = widgets.get(i); if (w.isVisible() && w.mouseDragged(mx, my, b, dx, dy)) return true; }
        return false;
    }

    @Override
    public boolean mouseWheelMove(double mx, double my, double d) {
        for (int i = widgets.size() - 1; i >= 0; i--) { Widget w = widgets.get(i); if (w.isVisible() && w.mouseWheelMove(mx, my, d)) return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        for (int i = widgets.size() - 1; i >= 0; i--) { Widget w = widgets.get(i); if (w.isVisible() && w.keyPressed(k, s, m)) return true; }
        return false;
    }

    @Override
    public void updateLayout() { for (Widget w : new ArrayList<>(widgets)) w.updateLayout(); }

    @Override
    public void updateScreen() { for (Widget w : new ArrayList<>(widgets)) w.updateScreen(); }
}
