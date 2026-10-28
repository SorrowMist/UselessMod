package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.content.menus.ContainerPatternConverter;
import com.sorrowmist.useless.content.menus.HostPatternConverter;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 万象样板转换器（半尺寸 236×417，单栏 212）。
 *
 * <p>自上而下：说明卡（图标 + 标题 + 说明）、绑定卡（显示转换器真实绑定的目标方块与坐标，
 * 未绑定时给出绑定方式）、样板卡（27 个样板槽 9×3 + 进度 + 转换按钮）、物品栏卡。
 * 样板槽节距 22 = 20px 底格 + 2px 间隙，物品 16px 居中于底格 +2，槽位坐标全部来自菜单。</p>
 */
public final class PatternConverterScreen extends AppleContainerScreen<ContainerPatternConverter> {
    private static final int PANEL_W = 236;
    private static final int PANEL_H = 408;
    private static final int PAD = 12;
    /** 标题栏 28 + 内边距 12。 */
    private static final int CONTENT_Y = 40;
    private static final int COL_X = PAD;
    private static final int COL_W = 212;
    private static final int ROW_X = COL_X + 8;
    private static final int ROW_W = COL_W - 16;
    private static final int HEAD_CARD_H = 51;
    private static final int BIND_CARD_Y = CONTENT_Y + HEAD_CARD_H + 8;
    private static final int BIND_CARD_H = 31;
    private static final int PATTERN_CARD_Y = BIND_CARD_Y + BIND_CARD_H + 8;
    private static final int PATTERN_CARD_H = 133;
    private static final int INV_CARD_Y = PATTERN_CARD_Y + PATTERN_CARD_H + 8;
    private static final int INV_CARD_H = 117;
    private static final int BUTTON_W = 52, BUTTON_H = 20;
    /** 样板槽总数，与菜单保持一致。 */
    private static final int PATTERN_SLOTS = HostPatternConverter.PATTERN_SLOTS;

    public PatternConverterScreen(ContainerPatternConverter menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_W;
        imageHeight = PANEL_H;
    }

    @Override
    protected void initControls() {
        for (int i = 0; i < PATTERN_SLOTS && i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(i),
                    23 + i % 9 * 22, PATTERN_CARD_Y + 23 + i / 9 * 22);
        for (int i = 0; i < 36 && PATTERN_SLOTS + i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(PATTERN_SLOTS + i),
                    23 + i % 9 * 22, INV_CARD_Y + (i < 27 ? 23 + i / 9 * 22 : 91));
    }

    @Override
    protected void buildApple(AppleHost h) {
        AppleWindow window = new AppleWindow(imageWidth, imageHeight, () -> title);
        window.referenceIcon("omniversal_pattern_converter");
        h.add(window);

        // 说明卡
        h.add(new AppleCard(COL_X, CONTENT_Y, COL_W, HEAD_CARD_H));
        h.add(new AppleIconTile(ROW_X, CONTENT_Y + 8, 20, () -> AppleUi.Icon.CONVERT,
                () -> AppleStyle.PURPLE).filled());
        h.add(new AppleLabel(ROW_X + 28, CONTENT_Y + 8, ROW_W - 28, AppleLabel.Kind.HEADLINE,
                () -> Component.translatable("gui.useless_mod.apple.converter_head")));
        h.add(new AppleLabel(ROW_X + 28, CONTENT_Y + 21, ROW_W - 28, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.converter_desc")).wrap());

        // 绑定卡：直接展示转换器真实绑定的目标（已绑定 / 未绑定两态）
        h.add(new AppleCard(COL_X, BIND_CARD_Y, COL_W, BIND_CARD_H));
        h.add(new AppleCanvas(COL_X, BIND_CARD_Y, COL_W, BIND_CARD_H,
                (g, mx, my, x, y, w, hh) -> paintBinding(g, x, y, w)));

        // 样板卡：27 个样板槽 9×3 + 进度 + 转换按钮
        h.add(new AppleCard(COL_X, PATTERN_CARD_Y, COL_W, PATTERN_CARD_H));
        h.add(new AppleLabel(ROW_X, PATTERN_CARD_Y + 8, 120, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.pattern_converter.patterns")));
        h.add(new AppleLabel(ROW_X, PATTERN_CARD_Y + 8, ROW_W, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.apple.loaded").copy()
                        .append(Component.literal(" " + loadedPatterns() + " / " + PATTERN_SLOTS))).right()
                .id("loaded_count"));
        h.add(new AppleProgress(ROW_X, PATTERN_CARD_Y + 91, ROW_W, 5,
                () -> loadedPatterns() / (double) PATTERN_SLOTS, () -> AppleStyle.PURPLE));
        ApplePushButton convert = new ApplePushButton(ROW_X + ROW_W - BUTTON_W, PATTERN_CARD_Y + 102,
                BUTTON_W, BUTTON_H, ApplePushButton.Kind.PRIMARY,
                () -> Component.translatable("gui.useless_mod.pattern_converter.convert"), this::convert).icon(AppleUi.Icon.CONVERT);
        convert.enabledWhen(() -> loadedPatterns() > 0);
        convert.id("convert");
        h.add(convert);

        // 物品栏卡
        h.add(new AppleCard(COL_X, INV_CARD_Y, COL_W, INV_CARD_H));
        h.add(new AppleLabel(ROW_X, INV_CARD_Y + 8, ROW_W, AppleLabel.Kind.CAPTION,
                () -> playerInventoryTitle));
        AppleSlotGrid grid = slotGrid();
        grid.cellSize(20);
        h.add(grid);
    }

    /**
     * 绑定卡内容：图标 + 目标名称（或维度）+ 坐标。
     *
     * <p>目标方块实体只在它位于当前维度且已加载时才能解析出方块名，此时显示真实方块名；
     * 否则退回显示维度与坐标 —— 链接本身仍然有效，只是目标暂时不可读。</p>
     */
    private void paintBinding(GuiGraphics g, int x, int y, int w) {
        GlobalPos link = menu.getLinkTarget();
        BlockEntity target = link == null ? null : menu.getLinkedBlockEntity();
        boolean bound = link != null;
        boolean reachable = target != null;
        int accent = bound && reachable ? AppleStyle.GREEN : AppleStyle.ORANGE;
        AppleUi.fillRound(g, x, y, w, BIND_CARD_H, 5, AppleStyle.tintOf(accent));
        AppleUi2.round(g, x + 6, y + 7, 16, 16, AppleStyle.R_TILE, AppleStyle.tintOf(accent));
        AppleUi.icon(g, bound && reachable ? AppleUi.Icon.CHECK : AppleUi.Icon.WARNING,
                x + 10, y + 11, 8, accent);

        Component headline;
        Component detail;
        if (!bound) {
            headline = Component.translatable("tooltip.useless_mod.pattern_converter.unlinked");
            detail = Component.translatable("tooltip.useless_mod.pattern_converter.bind_hint");
        } else {
            headline = reachable
                    ? target.getBlockState().getBlock().getName()
                    : Component.literal(link.dimension().location().toString());
            BlockPos pos = link.pos();
            detail = Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
        }

        int tx = x + 32;
        int width = Math.max(1, x + w - 8 - tx);
        Component bold = Component.literal(font.plainSubstrByWidth(headline.getString(), width))
                .withStyle(ChatFormatting.BOLD);
        g.drawString(font, bold, tx, y + 8, accent, false);
        g.drawString(font, font.plainSubstrByWidth(detail.getString(), width), tx, y + 19,
                AppleStyle.TERTIARY, false);
    }

    private void convert() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ContainerPatternConverter.CONVERT_BUTTON);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        // The reference empty state has neutral slots, not a blue document in every cell.
        super.renderSlot(graphics, slot);
    }

    private int loadedPatterns() {
        int n = 0;
        for (var slot : menu.slots) if (slot instanceof PatternSlotItemHandler && slot.hasItem()) n++;
        return n;
    }
}
