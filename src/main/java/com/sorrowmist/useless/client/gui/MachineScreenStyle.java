package com.sorrowmist.useless.client.gui;

import appeng.client.gui.style.BackgroundGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * 机器界面公共样式。Apple 风 v2：颜色常量改为浅色系统色，绘制全部委托给 {@link AppleScreenStyle}
 * （程序化圆角）。方法签名与常量名保持不变，调用方（各 Screen）无需修改即可换肤。
 */
final class MachineScreenStyle {
    static final int PANEL_COLOR = 0xFFF6F6F9;
    static final int HIGHLIGHT_COLOR = 0xFFFDFDFE;
    static final int SLOT_COLOR = AppleUi.SLOT_BODY;
    static final int SLOT_SHADOW_COLOR = AppleUi.SLOT_EDGE;
    static final int TEXT_COLOR = AppleUi.LABEL;
    static final int MUTED_TEXT_COLOR = AppleUi.TERTIARY_LABEL;
    static final int SUBTLE_TEXT_COLOR = AppleUi.SECONDARY_LABEL;
    static final int ERROR_TEXT_COLOR = AppleUi.RED_TEXT;
    static final int FIELD_BORDER_COLOR = AppleUi.FIELD_EDGE;
    static final int FIELD_FILL_COLOR = AppleUi.FIELD_BODY;

    private MachineScreenStyle() {
    }

    static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        AppleScreenStyle.drawPanel(graphics, left, top, width, height);
    }

    static void drawInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        AppleScreenStyle.drawInset(graphics, left, top, right, bottom);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos,
                              int x, int y, int columns, int rows) {
        drawSlotGroup(graphics, leftPos, topPos, x, y, columns, rows, 18, 18);
    }

    static void drawSlotGroup(GuiGraphics graphics, int leftPos, int topPos,
                              int x, int y, int columns, int rows,
                              int columnSpacing, int rowSpacing) {
        AppleScreenStyle.drawSlotGroup(graphics, leftPos, topPos, x, y, columns, rows, columnSpacing, rowSpacing);
    }

    static void drawSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        AppleScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
    }

    static void drawDisabledSlotBackground(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        AppleScreenStyle.drawDisabledSlotBackground(graphics, leftPos, topPos, slot);
    }

    static void drawRoadSlotBackground(GuiGraphics graphics, int leftPos, int topPos,
                                        Slot slot, boolean active, boolean pressed) {
        AppleScreenStyle.drawRoadSlotBackground(graphics, leftPos, topPos, slot, active, pressed);
    }

    /** v3 统一布局：标题栏高度（标题位于 y=6~8，分隔线位于 y=TITLE_BAR_HEIGHT）。 */
    static final int TITLE_BAR_HEIGHT = 22;
    /** v3 统一布局：物品栏标签在背包首行上方的偏移。 */
    static final int INVENTORY_LABEL_OFFSET = 11;

    /** 标题栏底部分隔线。 */
    static void drawTitleBar(GuiGraphics graphics, int leftPos, int topPos, int width) {
        AppleUi.separator(graphics, leftPos + 8, topPos + TITLE_BAR_HEIGHT, width - 16);
    }

    /**
     * v3 统一物品栏卡片：卡片从背包首行上方 15px 开始（容纳标签），到快捷栏下方 2px 结束，
     * 背包与快捷栏之间画一条细分隔线。槽位底板仍由各 Screen 逐个绘制。
     */
    static void drawInventoryCard(GuiGraphics graphics, int leftPos, int topPos,
                                  int inventoryX, int inventoryY, int hotbarY) {
        int left = leftPos + inventoryX - 4;
        int right = leftPos + inventoryX + 8 * 18 + 18 + 4;
        AppleUi.card(graphics, left, topPos + inventoryY - 15, right, topPos + hotbarY + 20);
        int sepY = topPos + (inventoryY + 3 * 18 + hotbarY) / 2;
        AppleUi.separator(graphics, left + 6, sepY, right - left - 12);
    }

    /** 分区卡片内的小标题。 */
    static void drawSectionLabel(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                 net.minecraft.network.chat.Component text, int x, int y) {
        graphics.drawString(font, text, x, y, AppleScreenStyle.MUTED_TEXT_COLOR, false);
    }
}
