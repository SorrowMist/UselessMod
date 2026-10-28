package com.sorrowmist.useless.client.gui.apple.core;

import net.minecraft.client.gui.GuiGraphics;

/** 纹理基类：子类实现 drawInternal。 */
public abstract class TransformTexture implements IGuiTexture {
    @Override
    public final void draw(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h) {
        drawInternal(g, mouseX, mouseY, x, y, w, h);
    }
    protected abstract void drawInternal(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h);
    public TransformTexture setColor(int c) { return this; }
}
