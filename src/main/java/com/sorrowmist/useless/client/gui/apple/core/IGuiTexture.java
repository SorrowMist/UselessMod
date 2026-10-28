package com.sorrowmist.useless.client.gui.apple.core;

import net.minecraft.client.gui.GuiGraphics;

/** 可绘制纹理（纯原版 GuiGraphics 实现，无第三方库）。 */
@FunctionalInterface
public interface IGuiTexture {
    void draw(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h);
}
