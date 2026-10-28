package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.core.IGuiTexture;
import com.sorrowmist.useless.client.gui.apple.AppleCard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 类 flex 容器：{@link Ui#row}/{@link Ui#column} 创建。主轴依次排列，gap 间距，padding 内边距，
 * 主轴剩余空间按 grow 分配（无 grow 时按 justify 对齐），交叉轴按 align 对齐（默认 STRETCH）。
 * 可带背景（{@link #card()} = 白色圆角卡片）。
 */
public class Flex extends UiNode {
    public enum Align { START, CENTER, END, STRETCH }
    public enum Justify { START, CENTER, END, BETWEEN }

    final boolean horizontal;
    final List<UiNode> children = new ArrayList<>();
    int gap, pl, pt, pr, pb;
    Align align = Align.STRETCH;
    Justify justify = Justify.START;
    IGuiTexture bg;
    boolean card;

    Flex(boolean horizontal, UiNode... kids) {
        this.horizontal = horizontal;
        children.addAll(Arrays.asList(kids));
        gap = horizontal ? AppleTheme.get().gap : AppleTheme.get().gap;
    }

    public Flex add(UiNode... kids) { children.addAll(Arrays.asList(kids)); return this; }
    public Flex gap(int g) { gap = g; return this; }
    public Flex padding(int p) { pl = pt = pr = pb = p; return this; }
    public Flex padding(int h, int v) { pl = pr = h; pt = pb = v; return this; }
    public Flex padding(int l, int t, int r, int b) { pl = l; pt = t; pr = r; pb = b; return this; }
    public Flex align(Align a) { align = a; return this; }
    public Flex center() { align = Align.CENTER; return this; }
    public Flex justify(Justify j) { justify = j; return this; }
    /** 主轴两端对齐（左标签 + 右控件 这种行最常用）。 */
    public Flex spread() { justify = Justify.BETWEEN; return this; }
    /** 白色圆角卡片背景，默认内边距 = theme.cardPad。 */
    public Flex card() { card = true; if (pl == 0 && pt == 0 && pr == 0 && pb == 0) padding(AppleTheme.get().cardPad); return this; }
    public Flex background(IGuiTexture t) { bg = t; return this; }

    private int main(UiNode n) { return horizontal ? n.measureW() : n.measureH(); }
    private int cross(UiNode n) { return horizontal ? n.measureH() : n.measureW(); }

    @Override
    protected int prefW() {
        int s = 0, m = 0;
        for (UiNode c : children) { s += c.measureW(); m = Math.max(m, c.measureW()); }
        return (horizontal ? s + gap * Math.max(0, children.size() - 1) : m) + pl + pr;
    }

    @Override
    protected int prefH() {
        int s = 0, m = 0;
        for (UiNode c : children) { s += c.measureH(); m = Math.max(m, c.measureH()); }
        return (horizontal ? m : s + gap * Math.max(0, children.size() - 1)) + pt + pb;
    }

    @Override
    protected void place(UiContext ctx, int x, int y, int w, int h) {
        if (card) ctx.add(new AppleCard(x, y, w, h));
        else if (bg != null) ctx.add(new AppleCard(x, y, w, h, bg));
        int ix = x + pl, iy = y + pt, iw = w - pl - pr, ih = h - pt - pb;
        int avail = horizontal ? iw : ih, crossAvail = horizontal ? ih : iw;
        int used = gap * Math.max(0, children.size() - 1);
        float grows = 0;
        for (UiNode c : children) { used += main(c); grows += c.grow; }
        int free = avail - used;
        int pos = 0, between = gap;
        if (grows == 0 && free > 0) {
            switch (justify) {
                case CENTER -> pos = free / 2;
                case END -> pos = free;
                case BETWEEN -> { if (children.size() > 1) between = gap + free / (children.size() - 1); }
                default -> {}
            }
        }
        int remaining = free;
        for (int i = 0; i < children.size(); i++) {
            UiNode c = children.get(i);
            int m = main(c);
            if (grows > 0 && c.grow > 0 && free > 0) {
                int extra = (int) (free * c.grow / grows);
                remaining -= extra;
                m += extra;
            }
            int crossFixed = horizontal ? c.fixedH : c.fixedW;
            int cs = cross(c), co = 0;
            switch (align) {
                case STRETCH -> cs = crossFixed >= 0 ? cs : crossAvail;
                case CENTER -> co = (crossAvail - cs) / 2;
                case END -> co = crossAvail - cs;
                default -> {}
            }
            if (horizontal) c.layoutAt(ctx, ix + pos, iy + co, m, cs);
            else c.layoutAt(ctx, ix + co, iy + pos, cs, m);
            pos += m + between;
        }
    }
}
