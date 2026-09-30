package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.UComponentUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
        boolean isEndlessBeaf = stack.getItem() instanceof EndlessBeafItem;
        boolean enhancedChainMining = UComponentUtils.isEnhancedChainMiningEnabled(stack);
        boolean forceMiningEnabled = UComponentUtils.isForceMiningEnabled(stack);

        String statusKey;
        int statusColor;

        if (isEndlessBeaf) {
            // 主手手持 EndlessBeafItem 时，根据 ChainMiningComponent 显示状态
            if (enhancedChainMining) {
                statusKey = "gui.useless_mod.status.enhanced";
                statusColor = COLOR_ENHANCED;
            } else {
                statusKey = "gui.useless_mod.status.normal";
                statusColor = COLOR_NORMAL;
            }
        } else {
            // 未手持 EndlessBeafItem 时，显示未激活
            statusKey = "gui.useless_mod.status.inactive";
            statusColor = COLOR_OFF;
        }

        Component statusValue = Component.translatable(statusKey);
        // 翻译键与显示名均使用本项目自有命名，不沿用 FTB Ultimine 的名称，以避免与该外部项目产生关联。
        Component statusLine = Component.translatable(
                "gui.useless_mod.chain_mining_status",
                statusValue
        );

        Component forceLabel = Component.translatable("gui.useless_mod.force_mining_label");
        Component forceValue = Component.translatable(
                forceMiningEnabled ? "gui.useless_mod.force.enabled" : "gui.useless_mod.force.disabled"
        );

        PlayerMiningData data = MiningDispatcher.getPlayerData(player);
        int count = data != null ? data.getCachedBlocks().size() : 0;

        // 形状行：仅在手持造化杖时显示，未持杖时该行无意义，直接留空以避免面板无谓增高。
        // 名称后附方向说明：隧道与对角类形状的走向取决于点击面与玩家朝向，仅凭名称无法预判。
        Component shapeText = isEndlessBeaf && data != null
                ? Component.translatable("gui.useless_mod.shape_label",
                                         Component.translatable(data.getShape().getTranslationKey()),
                                         Component.translatable(data.getShape().getDescriptionKey()))
                : Component.empty();

        // 切换提示行：与形状行同样只在手持造化杖时出现，说明滚轮组合键，
        // 否则玩家没有任何途径得知形状可以切换。
        Component hintText = isEndlessBeaf
                ? Component.translatable("gui.useless_mod.shape_hint")
                : Component.empty();

        // 数量行显示「本次预览 / 配置上限」。上限为 0 表示客户端尚未收到同步，
        // 此时只显示当前数量，避免出现「N / 0」这类无意义读数。
        int maxBlocks = data != null ? data.getMaxBlocks() : 0;
        Component countText = maxBlocks > 0
                ? Component.translatable("gui.useless_mod.mining_count_limit", count, maxBlocks)
                : Component.translatable("gui.useless_mod.mining_count", count);

        int forceColor = forceMiningEnabled ? COLOR_FORCE_ON : COLOR_MUTED;

        /* ========= 尺寸计算 ========= */
        int padding = 6;
        int lineSpacing = 4;
        int lineHeight = mc.font.lineHeight;

        int width = Math.max(
                mc.font.width(statusLine),
                Math.max(
                        mc.font.width(forceLabel) + mc.font.width(forceValue),
                        Math.max(mc.font.width(countText),
                                 Math.max(mc.font.width(shapeText), mc.font.width(hintText)))
                )
        ) + padding * 2 + 6;

        // 形状行与切换提示行仅在手持造化杖时存在，面板高度随之增减两行，避免空行占位
        int extraLines = shapeText.getString().isEmpty() ? 0 : 2;
        int height = padding * 2 + lineHeight * (3 + extraLines) + lineSpacing * (2 + extraLines) + 1;

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

        // 第四、五行：连锁形状与切换提示（仅手持造化杖时绘制）
        if (extraLines > 0) {
            int shapeY = line3Y + lineHeight + lineSpacing;
            g.drawString(mc.font, shapeText, textX, shapeY, 0xFFFFFFFF, true);
            g.drawString(mc.font, hintText, textX, shapeY + lineHeight + lineSpacing, COLOR_MUTED, true);
        }
    }
}
