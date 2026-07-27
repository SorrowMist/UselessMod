package com.sorrowmist.useless.client.gui;

import appeng.client.gui.style.BackgroundGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

final class MachineScreenStyle {
    static final int PANEL_COLOR = 0xFFCBCCD4;
    static final int HIGHLIGHT_COLOR = 0xFFF2F2F2;
    static final int SLOT_COLOR = 0xFFADB0C4;
    static final int SLOT_SHADOW_COLOR = 0xFF9A9FB4;
    static final int TEXT_COLOR = 0xFF413F54;
    static final int MUTED_TEXT_COLOR = 0xFF878FA5;
    static final int SUBTLE_TEXT_COLOR = 0xFF6D7287;
    static final int ERROR_TEXT_COLOR = 0xFFCE2401;

    private MachineScreenStyle() {
    }

    static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        BackgroundGenerator.draw(width, height, graphics, left, top);
    }

    static void drawInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, HIGHLIGHT_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL_COLOR);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos,
                              int x, int y, int columns, int rows) {
        int left = leftPos + x - 1;
        int top = topPos + y - 1;
        int right = leftPos + x + columns * 18 - 1;
        int bottom = topPos + y + rows * 18 - 1;
        drawInset(graphics, left, top, right, bottom);
    }

    static void drawSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        int left = leftPos + slot.x;
        int top = topPos + slot.y;
        graphics.fill(left, top, left + 16, top + 16, SLOT_COLOR);
        graphics.fill(left, top, left + 16, top + 1, SLOT_SHADOW_COLOR);
    }
}
