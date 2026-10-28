package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.mixin.client.SlotPosAccessor;
import net.minecraft.world.inventory.Slot;

/**
 * 槽位网格节点：占 cols×rows 个 18px 格，并把 Menu 中对应槽位的客户端坐标改到这里
 * （服务端不关心槽位坐标，AE2 也是这么做的）。槽底由宿主统一的 AppleSlotGrid 绘制。
 */
public final class SlotsNode extends UiNode {
    private final int first, cols, rows; private final boolean move;
    SlotsNode(int first, int cols, int rows, boolean move) { this.first = first; this.cols = cols; this.rows = rows; this.move = move; }

    @Override protected int prefW() { return cols * AppleTheme.get().slot; }
    @Override protected int prefH() { return rows * AppleTheme.get().slot; }
    @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
        if (!move || ctx.menu == null) return;
        int s = AppleTheme.get().slot;
        int ox = x + (w - cols * s) / 2, oy = y + (h - rows * s) / 2;
        for (int i = 0; i < cols * rows && first + i < ctx.menu.slots.size(); i++)
            moveSlot(ctx.menu.slots.get(first + i), ox + (i % cols) * s + 1, oy + (i / cols) * s + 1);
        ctx.rects.add(new int[]{x, y, w, h});
    }

    public static void moveSlot(Slot slot, int x, int y) {
        ((SlotPosAccessor) slot).useless$setX(x);
        ((SlotPosAccessor) slot).useless$setY(y);
    }
}
