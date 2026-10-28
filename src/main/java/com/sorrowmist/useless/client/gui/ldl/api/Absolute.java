package com.sorrowmist.useless.client.gui.ldl.api;

import java.util.ArrayList;
import java.util.List;

/** 绝对定位容器：子节点按 (x,y,w,h) 相对本容器左上角摆放。迁移旧界面 / 特殊图形的逃生口。 */
public final class Absolute extends UiNode {
    private record Item(UiNode n, int x, int y, int w, int h) {}
    private final int w, h;
    private final List<Item> items = new ArrayList<>();
    Absolute(int w, int h) { this.w = w; this.h = h; }

    /** w/h 传 -1 表示取子节点自身测量值。 */
    public Absolute at(int x, int y, int w, int h, UiNode n) { items.add(new Item(n, x, y, w, h)); return this; }
    public Absolute at(int x, int y, UiNode n) { return at(x, y, -1, -1, n); }

    @Override protected int prefW() { return w; }
    @Override protected int prefH() { return h; }
    @Override protected void place(UiContext ctx, int x, int y, int ww, int hh) {
        for (Item i : items)
            i.n.layoutAt(ctx, x + i.x, y + i.y, i.w < 0 ? i.n.measureW() : i.w, i.h < 0 ? i.n.measureH() : i.h);
    }
}
