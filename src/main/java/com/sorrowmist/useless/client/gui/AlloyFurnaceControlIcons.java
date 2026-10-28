package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import net.minecraft.client.gui.GuiGraphics;

final class AlloyFurnaceControlIcons {
    static final int WIDTH = 14;
    static final int HEIGHT = 15;

    private AlloyFurnaceControlIcons() {
    }

    static void drawRedstone(GuiGraphics graphics, int x, int y, RedstoneControlMode mode) {
        // Apple 风：圆角按钮 + 红点（高电平实心 / 低电平空心 / 忽略灰色划线）
        AppleUi.buttonSurface(graphics, x, y, WIDTH, HEIGHT, AppleUi.ButtonStyle.GRAY, false, false, true);
        float cx = x + WIDTH / 2f, cy = y + HEIGHT / 2f;
        switch (mode) {
            case HIGH_ACTIVE -> AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.RED);
            case LOW_ACTIVE -> {
                AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.RED);
                AppleUi.fillRound(graphics, cx - 2f, cy - 2f, 4, 4, 2f, AppleUi.CONTROL);
            }
            default -> {
                AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.QUATERNARY_LABEL);
                AppleUi.fillRound(graphics, cx - 3.5f, cy - 0.5f, 7, 1, 0.5f, AppleUi.CONTROL);
            }
        }
    }

    static void drawCancel(GuiGraphics graphics, int x, int y, boolean pressed) {
        AppleUi.buttonSurface(graphics, x, y, WIDTH, HEIGHT, AppleUi.ButtonStyle.DESTRUCTIVE, false, pressed, true);
        AppleUi.icon(graphics, AppleUi.Icon.XMARK, x + 3, y + 3, 9, AppleUi.RED);
    }
}
