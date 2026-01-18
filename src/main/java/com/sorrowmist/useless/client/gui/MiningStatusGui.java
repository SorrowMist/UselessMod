package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class MiningStatusGui {

    public static void render(GuiGraphics guiGraphics,  DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;

        boolean isTabPressed = KeyBindings.TRIGGER_CHAIN_MINING_KEY.get().isDown();
        if (!isTabPressed) return;

        ItemStack mainHandItem = player.getMainHandItem();
        var modes = UComponentUtils.getFunctionModes(mainHandItem);

        String status = getCurrentStatus(modes);
        String mode = getCurrentMode(modes);
        int color = switch (status) {
            case "激活" -> 0xFF00FF00;
            case "冷却中" -> 0xFFFFFF00;
            default -> 0xFFFF0000;
        };

        String statusText = "Ultimine: " + status;
        String modeText = "模式: " + mode;
        int statusWidth = mc.font.width(statusText);
        int modeWidth = mc.font.width(modeText);
        int maxWidth = Math.max(statusWidth, modeWidth);
        int padding = 4;
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = (mc.font.lineHeight * 2) + padding * 2;

        int x = 10;
        int y = 10;

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0x80404040);
        guiGraphics.renderOutline(x, y, boxWidth, boxHeight, 0xFFFFFFFF);

        guiGraphics.drawString(mc.font, statusText, x + padding, y + padding, color, true);
        guiGraphics.drawString(mc.font, modeText, x + padding, y + padding + mc.font.lineHeight, 0xFFFFFFFF, true);
    }

    private static String getCurrentStatus(EnumSet<FunctionMode> modes) {
        if (modes.contains(FunctionMode.CHAIN_MINING)) {
            return "激活";
        }
        return "未激活";
    }

    private static String getCurrentMode(EnumSet<FunctionMode> modes) {
        if (modes.contains(FunctionMode.CHAIN_MINING)) {
            return "连锁挖掘";
        }
        if (modes.contains(FunctionMode.FORCE_MINING)) {
            return "强制挖掘";
        }
        return "普通挖掘";
    }
}
