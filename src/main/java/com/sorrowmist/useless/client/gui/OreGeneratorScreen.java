package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.client.gui.ldl.api.*;
import com.sorrowmist.useless.content.menus.OreGeneratorMenu;
import com.sorrowmist.useless.network.OreGeneratorOutputTogglePacket;
import com.sorrowmist.useless.network.OreGeneratorSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

import static com.sorrowmist.useless.client.gui.ldl.api.Ui.*;

/**
 * 矿物生成器的声明式 API 界面，按 HTML 的 456×323 几何排布。
 * 只移动客户端显示坐标；数字同步、步进、回车和关闭提交由共享 API 处理。
 */
public final class OreGeneratorScreen extends AppleScreen<OreGeneratorMenu> {
    private final NumberField rate = numberField(() -> menu.getOutputRate(),
            value -> AppleNet.send(new OreGeneratorSettingsPacket(menu.containerId, menu.getBlockPos(), value)))
            .min(1).boxHeight(12).editableWhen(this::editable);

    public OreGeneratorScreen(OreGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 456, 323);
    }

    private boolean layoutRecovery;

    private boolean editable() { return !menu.isRecoveryPage(); }
    @Override protected void preInit() {
        super.preInit();
        imageHeight = menu.isRecoveryPage() ? 347 : 323;
    }
    @Override protected void tickControls() {
        super.tickControls();
        if (layoutRecovery != menu.isRecoveryPage()) rebuildLayout();
    }
    @Override protected int bodyX() { return 12; }
    @Override protected int bodyY() { return 40; }
    @Override protected AppleWindow createWindow() { return super.createWindow().referenceIcon("ore_generator_block"); }
    @Override protected AppleSlotGrid slotGrid() { return super.slotGrid().cellSize(20); }

    @Override
    protected UiNode titleAccessory() {
        return statusPill(() -> Component.translatable(menu.isRecoveryPage() ? "gui.useless_mod.apple.recovery"
                        : menu.getActiveSlots() > 0 ? "gui.useless_mod.apple.running" : "gui.useless_mod.apple.idle"),
                () -> menu.isRecoveryPage() ? AppleStyle.RED : menu.getActiveSlots() > 0 ? AppleStyle.GREEN : AppleStyle.SECONDARY)
                .size(68, 12).nudge(-6, 0);
    }

    @Override
    protected UiNode body() {
        int extra = menu.isRecoveryPage() ? 24 : 0;
        Absolute content = absolute(432, 270 + extra)
                .at(0, 0, 212, 270 + extra, card())
                .at(10, 12, 114, 9, caption(either(menu::isRecoveryPage,
                        "gui.useless_mod.apple.recovery", "gui.useless_mod.apple.samples")))
                .at(130, 8, 74, 17, new Leaf(74, 17, (x, y, w, h) ->
                        new ApplePageTabs(x, y, w, h, menu::getPage, menu::getPageCount,
                                menu::getActivePageCount, this::selectPage)).id("pager"))
                .at(220, 0, 212, 66, card())
                .at(362, 5, 38, 12, statusPill(either(menu::isAeOnline,
                                "gui.useless_mod.ore_generator.ae_online", "gui.useless_mod.ore_generator.ae_offline"),
                        () -> menu.isAeOnline() ? AppleStyle.GREEN : AppleStyle.RED).id("ae_status"))
                .at(402, 5, 22, 13, toggle(menu::isOutputToAe, () -> AppleNet.send(
                        new OreGeneratorOutputTogglePacket(menu.containerId, menu.getBlockPos())))
                        .bindEnabled(this::editable).id("ae"))
                .at(314, 28, 110, 9, Ui.body(() -> Component.literal(menu.getCountdownTicks() + " tick")).right())
                .at(314, 50, 110, 9, Ui.body(() -> Component.literal(menu.getActiveSlots() + " / " + menu.getConfiguredSlots())).right())
                .at(220, 74, 212, 57, card())
                .at(230, 82, 194, 9, caption(tr("gui.useless_mod.ore_generator.rate")))
                .at(228, 95, 162, 16, new Leaf(162, 16, (x, y, w, h) ->
                        new AppleStepper(x, y, w, h, rate.box(), () -> rate.add(-1), () -> rate.add(1)).trailingButtons())
                        .bindEnabled(this::editable).id("rate"))
                .at(395, 95, 29, 16, button(tr("gui.useless_mod.ore_generator.apply"), rate::submit)
                        .primary().bindEnabled(this::editable).id("apply"))
                .at(228, 119, 196, 4, progress(
                        () -> menu.getActiveSlots() / (double) Math.max(1, menu.getConfiguredSlots())).color(AppleStyle.GREEN))
                .at(220, 139, 212, 117, card())
                .at(230, 147, 194, 9, caption(() -> playerInventoryTitle));
        String[] labels = {"gui.useless_mod.ore_generator.ae_toggle", "gui.useless_mod.apple.next_output", "gui.useless_mod.apple.slots"};
        AppleUi.Icon[] icons = {AppleUi.Icon.AE, AppleUi.Icon.ROTATE, AppleUi.Icon.GRID};
        int[] colors = {AppleStyle.TEAL, AppleStyle.ORANGE, AppleStyle.BLUE};
        for (int i = 0; i < 3; i++) {
            final int row = i;
            content.at(228, 4 + i * 22, 14, 14, new Leaf(14, 14, (x, y, w, h) ->
                    new AppleIconTile(x, y, 14, () -> icons[row], () -> colors[row]).filled()));
            content.at(248, 6 + i * 22, 70, 9, Ui.body(tr(labels[i])));
            if (i > 0) content.at(248, i * 22, 184, 1, new Leaf(184, 1, (x, y, w, h) ->
                    new AppleCanvas(x, y, w, h, (g, mx, my, xx, yy, ww, hh) -> AppleUi.separator(g, xx, yy, ww))));
        }
        if (extra > 0) content.at(8, 28, 196, 16, new Leaf(196, 16, (x, y, w, h) ->
                new AppleCanvas(x, y, w, h, (g, mx, my, xx, yy, ww, hh) -> {
                    AppleUi.fillRound(g, xx, yy, ww, hh, 4, AppleStyle.ORANGE_TINT);
                    AppleUi.icon(g, AppleUi.Icon.WARNING, xx + 4, yy + 4, 8, AppleStyle.ORANGE_TEXT);
                    AppleTextLayout.draw(g, font, Component.translatable("gui.useless_mod.apple.recovery_page"), xx + 17, yy + 4,
                            ww - 21, .8f, AppleStyle.ORANGE_TEXT, false);
                })));
        String[] legends = {"legend_sample", "legend_locked", "legend_recovery"};
        for (int i = 0; i < 3; i++) {
            final int n = i;
            content.at(8 + i * 64, 254 + extra, 8, 8, new Leaf(8, 8, (x, y, w, h) ->
                    new AppleCanvas(x, y, w, h, (g, mx, my, xx, yy, ww, hh) ->
                            DesignAssets.draw(g, n == 1 ? "slot_locked_40" : "slot_empty_40", xx, yy, ww, hh))));
            content.at(19 + i * 64, 254 + extra, 49, 9, footnote(tr("gui.useless_mod.apple." + legends[i])));
        }
        return content;
    }

    @Override
    protected void afterLayout(AppleHost h) {
        layoutRecovery = menu.isRecoveryPage();
        int extra = layoutRecovery ? 24 : 0;
        for (int i = 0; i < 90 && i < menu.slots.size(); i++)
            SlotsNode.moveSlot(menu.slots.get(i), 23 + i % 9 * 22, 72 + extra + i / 9 * 22);
        for (int i = 0; i < 36 && 90 + i < menu.slots.size(); i++)
            SlotsNode.moveSlot(menu.slots.get(90 + i), 243 + i % 9 * 22, i < 27 ? 202 + i / 9 * 22 : 270);
    }

    private void selectPage(int target) {
        int delta = target - menu.getPage();
        for (int i = 0; i < Math.abs(delta); i++) net.button(delta > 0 ? OreGeneratorMenu.NEXT_PAGE : OreGeneratorMenu.PREVIOUS_PAGE);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        int sampleSlot = menu.getSampleSlotIndex(slot);
        if (sampleSlot >= 0 && sampleSlot >= menu.getActiveSlots())
            DesignAssets.draw(graphics, "slot_locked_40", slot.x - 2, slot.y - 2, 20, 20);
    }

    @Override
    protected void renderExtraTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int sampleSlot = menu.getSampleSlotIndex(hoveredSlot);
        if (hoveredSlot != null && sampleSlot >= menu.getActiveSlots() && hoveredSlot.getItem().isEmpty())
            graphics.renderTooltip(font, List.of(Component.translatable("gui.useless_mod.ore_generator.locked_slot")),
                    Optional.empty(), mouseX, mouseY);
    }
}
