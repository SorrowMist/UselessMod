package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.UComponentUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class MiningStatusGui {
    private static final int BG_MAIN = 0xB0202020;
    private static final int BG_SHADOW = 0x40000000;
    private static final int BORDER_LIGHT = 0x40FFFFFF;

    private static final int COLOR_ENHANCED = 0xFF4DD0E1;
    private static final int COLOR_NORMAL = 0xFF66BB6A;
    private static final int COLOR_FORCE_ON = 0xFFFF7043;
    private static final int COLOR_MUTED = 0xFF9E9E9E;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!KeyBindings.isChainMiningKeyDown()) return;

        // 该面板描述的是造化杖自身的连锁状态，未持有该工具时不存在可展示的内容，
        // 因此在绘制前直接返回，避免出现与手持物品无关的状态读数。
        ItemStack stack = resolveHeldBeafTool(player);
        if (stack == null) return;

        boolean enhancedChainMining = UComponentUtils.isEnhancedChainMiningEnabled(stack);
        boolean forceMiningEnabled = UComponentUtils.isForceMiningEnabled(stack);

        String statusKey;
        int statusColor;

        if (enhancedChainMining) {
            statusKey = "gui.useless_mod.status.enhanced";
            statusColor = COLOR_ENHANCED;
        } else {
            statusKey = "gui.useless_mod.status.normal";
            statusColor = COLOR_NORMAL;
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

        // 名称后附方向说明：隧道与对角类形状的走向取决于点击面与玩家朝向，仅凭名称无法预判。
        Component shapeText = data != null
                ? Component.translatable("gui.useless_mod.shape_label",
                                         Component.translatable(data.getShape().getTranslationKey()),
                                         Component.translatable(data.getShape().getDescriptionKey()))
                : Component.empty();

        // 切换提示行：说明滚轮组合键，缺少该提示时玩家没有任何途径得知形状可以切换。
        Component hintText = Component.translatable("gui.useless_mod.shape_hint");

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

        // 形状行与切换提示行在持有造化杖期间恒定存在，面板高度固定包含这两行。
        int height = padding * 2 + lineHeight * 5 + lineSpacing * 4 + 1;

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

        // 第四、五行：连锁形状与切换提示
        int shapeY = line3Y + lineHeight + lineSpacing;
        g.drawString(mc.font, shapeText, textX, shapeY, 0xFFFFFFFF, true);
        g.drawString(mc.font, hintText, textX, shapeY + lineHeight + lineSpacing, COLOR_MUTED, true);
    }

    /**
     * 取玩家当前持有的造化杖。
     * <p>
     * 主手与副手依次查找，任一持有即返回对应物品；双手均未持有时返回 {@code null}。
     * 判定范围与连锁高亮一致，覆盖基础形态与全部工具模式变体。
     */
    @Nullable
    private static ItemStack resolveHeldBeafTool(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (BeefToolVariants.isBeafTool(stack)) {
                return stack;
            }
        }
        return null;
    }
}
