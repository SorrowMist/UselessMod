package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * 原 Menu 槽位的 Apple 底格：浅灰圆角格 + 极淡描边。槽位本身（点击/拖拽/Shift/JEI）仍是原版 Slot，
 * 这里只按 Menu 坐标画外观，所以 Menu/网络包零改动。
 */
public class AppleSlotGrid extends AppleWidget {
    private final AbstractContainerMenu menu;
    private final IntSupplier left, top;
    private final Predicate<Slot> filter;
    private Predicate<Slot> disabled = s -> false;
    private int cellSize = 18;

    /** Changes the background only; vanilla items and slot hit targets remain 16px. */
    public AppleSlotGrid cellSize(int size) {
        if (size < 16) throw new IllegalArgumentException("Slot cells must fit a 16px item");
        cellSize = size;
        return this;
    }

    /** 只读/禁用槽位（例如恢复页）：画成更深的平底格，悬停不高亮。 */
    public AppleSlotGrid disabledWhen(Predicate<Slot> p) { disabled = p; return this; }

    public AppleSlotGrid(AbstractContainerMenu menu, IntSupplier left, IntSupplier top, Predicate<Slot> filter) {
        super(0, 0, 0, 0);
        this.menu = menu; this.left = left; this.top = top; this.filter = filter;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int l = left.getAsInt(), t = top.getAsInt();
        for (Slot s : menu.slots) {
            if (!filter.test(s) || !s.isActive()) continue;
            int inset = (cellSize - 16) / 2;
            int x = l + s.x - inset, y = t + s.y - inset;
            String key = slotKey(disabled.test(s) ? "locked" : isHovered(mouseX, mouseY, x, y) ? "hover" : "empty", cellSize);
            DesignAssets.draw(g, key, x, y, cellSize, cellSize);
        }
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + cellSize && mouseY >= y && mouseY < y + cellSize;
    }

    /** 设计资源只有 18/32/36/40 四档底格；cellSize 不被改动，只是按最接近的一档缩放。 */
    static String slotKey(String state, int cellSize) {
        int[] sizes = {18, 32, 36, 40};
        int pick = sizes[0];
        for (int size : sizes) if (Math.abs(size - cellSize) <= Math.abs(pick - cellSize)) pick = size;
        return "slot_" + state + "_" + pick;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
