package com.sorrowmist.useless.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class SlotRendererUtil {


    public static void renderFluidAmount(GuiGraphics guiGraphics, int amount, int x, int y, int width, int height) {
        String text = NumberFormatUtil.formatFluidAmount(amount); // 使用完整单位
        Font font = Minecraft.getInstance().font;

        guiGraphics.pose().pushPose();
        float scale = 0.5f; // 缩小字体以适应完整单位
        guiGraphics.pose().translate(0, 0, 200.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        int textX = Math.round((x + width - font.width(text) * scale) / scale);
        int textY = Math.round((y + height - 8 * scale) / scale);

        guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, true);
        guiGraphics.pose().popPose();
    }
}