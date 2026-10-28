package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.core.Widget;

/** 叶子节点：给定首选尺寸 + 一个「在矩形里造控件」的工厂。所有内置控件都是 Leaf。 */
public class Leaf extends UiNode {
    @FunctionalInterface public interface Factory { Widget make(int x, int y, int w, int h); }

    private final int pw, ph;
    private final Factory factory;

    public Leaf(int prefW, int prefH, Factory factory) { this.pw = prefW; this.ph = prefH; this.factory = factory; }

    @Override protected int prefW() { return pw; }
    @Override protected int prefH() { return ph; }

    @Override
    protected void place(UiContext ctx, int x, int y, int w, int h) { ctx.add(this, factory.make(x, y, w, h)); }
}
