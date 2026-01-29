package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.utils.UComponentUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class MiningStatusGui {
    private static final int BG_MAIN = 0xB0202020;
    private static final int BG_SHADOW = 0x40000000;
    private static final int BORDER_LIGHT = 0x40FFFFFF;

    private static final int COLOR_ENHANCED = 0xFF4DD0E1;
    private static final int COLOR_NORMAL = 0xFF66BB6A;
    private static final int COLOR_OFF = 0xFFEF5350;
    private static final int COLOR_FORCE_ON = 0xFFFF7043;
    private static final int COLOR_MUTED = 0xFF9E9E9E;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!KeyBindings.TRIGGER_CHAIN_MINING_KEY.get().isDown()) return;

        ItemStack stack = player.getMainHandItem();
        EnumSet<FunctionMode> modes = UComponentUtils.getFunctionModes(stack);

        boolean enhanced = modes.contains(FunctionMode.ENHANCED_CHAIN_MINING);
        boolean normal = modes.contains(FunctionMode.CHAIN_MINING);
        boolean force = modes.contains(FunctionMode.FORCE_MINING);

        String statusKey = enhanced
                ? "gui.useless_mod.status.enhanced"
                : normal
                ? "gui.useless_mod.status.normal"
                : "gui.useless_mod.status.inactive";

        Component statusValue = Component.translatable(statusKey);
        Component statusLine = Component.translatable(
                "gui.useless_mod.ultimine_status",
                statusValue
        );

        Component forceLabel = Component.translatable("gui.useless_mod.force_mining_label");
        Component forceValue = Component.translatable(
                force ? "gui.useless_mod.force.enabled"
                        : "gui.useless_mod.force.disabled"
        );

        PlayerMiningData data = MiningDispatcher.getPlayerData(player);
        int count = data != null && data.getCachedBlocks() != null
                ? data.getCachedBlocks().size()
                : 0;

        Component countText = Component.translatable(
                "gui.useless_mod.mining_count",
                count
        );

        int statusColor = enhanced ? COLOR_ENHANCED
                : normal ? COLOR_NORMAL
                : COLOR_OFF;

        int forceColor = force ? COLOR_FORCE_ON : COLOR_MUTED;

        /* ========= 尺寸计算 ========= */
        int padding = 6;
        int lineSpacing = 4;
        int lineHeight = mc.font.lineHeight;

        int width = Math.max(
                mc.font.width(statusLine),
                Math.max(
                        mc.font.width(forceLabel) + mc.font.width(forceValue),
                        mc.font.width(countText)
                )
        ) + padding * 2 + 6;

        int height = padding * 2 + lineHeight * 3 + lineSpacing * 2 + 1;

        int x = 0;
        int y = 0;

        /* ========= 背景 ========= */
        g.fill(x + 2, y + 2, x + width + 2, y + height + 2, BG_SHADOW);
        g.fill(x, y, x + width, y + height, BG_MAIN);

        // 左侧状态强调条
        g.fill(x, y, x + 3, y + height, statusColor);

        /* ========= 文本 ========= */
        int textX = x + padding + 3;
        int textY = y + padding;

        // 第一行：状态
        g.drawString(mc.font, statusLine, textX, textY, statusColor, true);

        // 分割线
        int separatorY = textY + lineHeight + lineSpacing;
        g.fill(
                x + 3,
                separatorY,
                x + width,
                separatorY + 1,
                BORDER_LIGHT
        );

        // 第二行：强制挖掘
        int line2Y = separatorY + 1 + lineSpacing;
        g.drawString(mc.font, forceLabel,
                     textX,
                     line2Y,
                     0xFFFFFFFF,
                     true
        );

        g.drawString(mc.font, forceValue,
                     textX + mc.font.width(forceLabel),
                     line2Y,
                     forceColor,
                     true
        );

        // 第三行：挖掘数量
        int line3Y = line2Y + lineHeight + lineSpacing;
        g.drawString(mc.font, countText,
                     textX,
                     line3Y,
                     0xFFFFFFFF,
                     true
        );
    }
}
