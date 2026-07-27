package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class AlloyFurnaceControlIcons {
    static final int WIDTH = 14;
    static final int HEIGHT = 15;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UselessMod.MODID, "textures/gui/locate_picture.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 480;
    private static final int REDSTONE_V = 265;
    private static final int CANCEL_NORMAL_U = 140;
    private static final int CANCEL_PRESSED_U = 156;
    private static final int CANCEL_V = 283;

    private AlloyFurnaceControlIcons() {
    }

    static void drawRedstone(GuiGraphics graphics, int x, int y, RedstoneControlMode mode) {
        graphics.blit(TEXTURE, x, y, mode.getOverlayU(), REDSTONE_V,
                WIDTH, HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    static void drawCancel(GuiGraphics graphics, int x, int y, boolean pressed) {
        graphics.blit(TEXTURE, x, y, pressed ? CANCEL_PRESSED_U : CANCEL_NORMAL_U, CANCEL_V,
                WIDTH, HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
