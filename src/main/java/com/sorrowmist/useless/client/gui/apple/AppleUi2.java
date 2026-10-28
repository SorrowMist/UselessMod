package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;

/** 浮点坐标的绘制捷径（内部用）。 */
public final class AppleUi2 {
    private AppleUi2() {}
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int c) {
        AppleUi.fillRound(g, x, y, w, h, Math.max(0, r), c);
    }
}
