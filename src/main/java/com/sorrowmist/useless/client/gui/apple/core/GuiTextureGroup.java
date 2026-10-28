package com.sorrowmist.useless.client.gui.apple.core;

import net.minecraft.client.gui.GuiGraphics;

/** 多层纹理按顺序叠加。 */
public final class GuiTextureGroup implements IGuiTexture {
    private final IGuiTexture[] layers;
    public GuiTextureGroup(IGuiTexture... layers) { this.layers = layers; }
    @Override
    public void draw(GuiGraphics g, int mouseX, int mouseY, float x, float y, int w, int h) {
        for (IGuiTexture t : layers) t.draw(g, mouseX, mouseY, x, y, w, h);
    }
}
