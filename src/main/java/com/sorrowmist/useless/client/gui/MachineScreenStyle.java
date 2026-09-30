package com.sorrowmist.useless.client.gui;

import appeng.client.gui.style.BackgroundGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

final class MachineScreenStyle {
    static final int PANEL_COLOR = 0xFFCBCCD4;
    static final int HIGHLIGHT_COLOR = 0xFFF2F2F2;
    static final int SLOT_COLOR = 0xFFADB0C4;
    static final int SLOT_SHADOW_COLOR = 0xFF9A9FB4;
    /**
     * 未启用槽位底板的透明度。
     *
     * <p>取值沿用 AE2 对未启用可选槽位的淡化系数：未放入容量卡而不可用的区域仍保留底板轮廓，
     * 但与面板按该透明度混合后明显浅于可用槽位。混合交由渲染管线完成，面板色调整时
     * 无需重新推导常量。</p>
     */
    private static final int DISABLED_SLOT_ALPHA = 0x33;
    private static final int ROAD_SLOT_PRESSED = 0xFF8F93A7;
    private static final int ROAD_SLOT_DISABLED = 0xFF777B8D;
    static final int TEXT_COLOR = 0xFF413F54;
    static final int MUTED_TEXT_COLOR = 0xFF878FA5;
    static final int SUBTLE_TEXT_COLOR = 0xFF6D7287;
    static final int ERROR_TEXT_COLOR = 0xFFCE2401;
    /** 输入框底 / 描边：比 PANEL_COLOR 深一档，用来在浅色面板上圈出「这里能填」。 */
    static final int FIELD_BORDER_COLOR = 0xFF8B8FA3;
    static final int FIELD_FILL_COLOR = 0xFFE4E5EA;

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
        drawSlotGroup(graphics, leftPos, topPos, x, y, columns, rows, 18, 18);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos,
                              int x, int y, int columns, int rows,
                              int columnSpacing, int rowSpacing) {
        int left = leftPos + x - 1;
        int top = topPos + y - 1;
        int right = leftPos + x + (columns - 1) * columnSpacing + 17;
        int bottom = topPos + y + (rows - 1) * rowSpacing + 17;
        drawInset(graphics, left, top, right, bottom);
    }

    static void drawSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        int left = leftPos + slot.x;
        int top = topPos + slot.y;
        graphics.fill(left, top, left + 16, top + 16, SLOT_COLOR);
        graphics.fill(left, top, left + 16, top + 1, SLOT_SHADOW_COLOR);
    }

    /**
     * 绘制未启用槽位的底板：保留与可用槽位相同的轮廓，仅整体淡化。
     *
     * <p>淡化由渲染管线的颜色混合完成，而非改用更浅的字面量颜色，使高亮边框与底部暗边在
     * 同一系数下一起变淡；颜色字面量各自减淡会让两层失去原有的相对明暗关系。</p>
     */
    static void drawDisabledSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        int left = leftPos + slot.x;
        int top = topPos + slot.y;
        graphics.fill(left, top, left + 16, top + 16, withAlpha(SLOT_COLOR));
        graphics.fill(left, top, left + 16, top + 1, withAlpha(SLOT_SHADOW_COLOR));
    }

    /** 以 {@link #DISABLED_SLOT_ALPHA} 替换颜色的透明度分量。 */
    private static int withAlpha(int color) {
        return color & 0x00FFFFFF | DISABLED_SLOT_ALPHA << 24;
    }

    static void drawRoadSlotBackground(GuiGraphics graphics, int leftPos, int topPos,
                                        Slot slot, boolean active, boolean pressed) {
        int left = leftPos + slot.x;
        int top = topPos + slot.y;
        int color = !active ? ROAD_SLOT_DISABLED : pressed ? ROAD_SLOT_PRESSED : SLOT_COLOR;
        graphics.fill(left, top, left + 16, top + 16, color);
    }
}
