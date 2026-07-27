package com.sorrowmist.useless.client.gui;

import appeng.client.gui.widgets.AE2Button;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class PressableAE2Button extends AE2Button {
    private boolean pressed;

    PressableAE2Button(int x, int y, int width, int height,
                       Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            pressed = true;
        }
        return handled;
    }

    void releaseVisualState() {
        pressed = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        graphics.blitSprite(SPRITES.get(active, pressed),
                getX(), getY(), getWidth(), getHeight());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int alphaChannel = Mth.ceil(alpha * 255.0F) << 24;
        if (!active) {
            renderButtonText(graphics, minecraft.font, 2,
                    0x413F54 | alphaChannel, -1);
        } else if (pressed) {
            renderButtonText(graphics, minecraft.font, 2,
                    0x517497 | alphaChannel, 0);
        } else {
            renderButtonText(graphics, minecraft.font, 2,
                    0xF2F2F2 | alphaChannel, 1);
        }
    }
}
