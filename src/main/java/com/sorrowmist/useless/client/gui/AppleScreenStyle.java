package com.sorrowmist.useless.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Apple 风样式实现；{@link MachineScreenStyle} 全部委托到这里。
 * 槽位底板由各 Screen 逐个绘制，槽位组只画外层卡片。
 */
final class AppleScreenStyle {
    static final int PANEL_COLOR = 0xFFF7F7FA;
    static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
    static final int SLOT_COLOR = 0xFFE2E2E8;
    static final int SLOT_SHADOW_COLOR = 0xFFD1D1D6;
    static final int TEXT_COLOR = AppleUi.LABEL;
    static final int MUTED_TEXT_COLOR = AppleUi.SECONDARY_LABEL;
    static final int SUBTLE_TEXT_COLOR = AppleUi.TERTIARY_LABEL;
    static final int ERROR_TEXT_COLOR = AppleUi.RED_TEXT;
    static final int FIELD_BORDER_COLOR = 0xFFC7C7CC;
    static final int FIELD_FILL_COLOR = 0xFFE9E9EE;
    static final int ACCENT_COLOR = AppleUi.BLUE;
    static final int SUCCESS_COLOR = 0xFF248A3D;
    static final int WARNING_COLOR = 0xFFC93400;

    private AppleScreenStyle() {}

    /** 窗口：软阴影 + 玻璃面板 + 左上交通灯。 */
    static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        AppleUi.glassPanel(graphics, left, top, width, height);
    }

    static void drawInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        AppleUi.card(graphics, left, top, right, bottom);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos, int x, int y, int columns, int rows) {
        drawSlotGroup(graphics, leftPos, topPos, x, y, columns, rows, 18, 18);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos, int x, int y, int columns, int rows,
                              int columnSpacing, int rowSpacing) {
        int left = leftPos + x - 2;
        int top = topPos + y - 2;
        int right = leftPos + x + (columns - 1) * columnSpacing + 18;
        int bottom = topPos + y + (rows - 1) * rowSpacing + 18;
        AppleUi.card(graphics, left, top, right, bottom);
    }

    static void drawSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        AppleUi.slot(graphics, leftPos, topPos, slot);
    }

    static void drawDisabledSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        AppleUi.disabledSlot(graphics, leftPos, topPos, slot);
    }

    static void drawRoadSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot,
                                        boolean active, boolean pressed) {
        if (!active) { AppleUi.disabledSlot(graphics, leftPos, topPos, slot); return; }
        AppleUi.slot(graphics, leftPos + slot.x, topPos + slot.y, pressed);
    }
}
