package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.IGuiTexture;
import net.minecraft.client.gui.GuiGraphics;

/** 白色圆角卡片 / 任意纹理底板。 */
public class AppleCard extends AppleWidget {
    private final IGuiTexture tex;

    public AppleCard(int x, int y, int w, int h) { this(x, y, w, h, AppleTex.card(6)); }

    public AppleCard(int x, int y, int w, int h, IGuiTexture tex) {
        super(x, y, w, h);
        this.tex = tex;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        tex.draw(g, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
