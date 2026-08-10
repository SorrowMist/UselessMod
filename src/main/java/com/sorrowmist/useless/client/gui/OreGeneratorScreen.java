package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.OreGeneratorMenu;
import com.sorrowmist.useless.network.OreGeneratorOutputTogglePacket;
import com.sorrowmist.useless.network.OreGeneratorSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

public final class OreGeneratorScreen extends AbstractContainerScreen<OreGeneratorMenu> {
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 242;

    private EditBox rateField;
    private PressableAE2Button rateDown;
    private PressableAE2Button rateUp;
    private PressableAE2Button applyButton;
    private PressableAE2Button aeButton;
    private PressableAE2Button previousPageButton;
    private PressableAE2Button nextPageButton;
    private long lastSyncedRate = Long.MIN_VALUE;
    private String syncedRateText = "";
    private boolean updatingRateField;
    private boolean rateDirty;

    public OreGeneratorScreen(OreGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelX = 44;
        inventoryLabelY = 146;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        previousPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 128, topPos + 4, 18, 12,
                Component.literal("<"), button -> changePage(-1)));
        nextPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 148, topPos + 4, 18, 12,
                Component.literal(">"), button -> changePage(1)));

        rateField = new EditBox(font, leftPos + 177, topPos + 35, 62, 14,
                Component.translatable("gui.useless_mod.ore_generator.rate"));
        rateField.setMaxLength(24);
        rateField.setFilter(ScaledEnergyAmount::isValidInput);
        rateField.setResponder(value -> {
            if (!updatingRateField) rateDirty = !value.equals(syncedRateText);
        });
        addRenderableWidget(rateField);

        rateDown = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 52, 28, 14,
                Component.literal("-"), button -> adjustRate(-1)));
        rateUp = addRenderableWidget(new PressableAE2Button(
                leftPos + 211, topPos + 52, 28, 14,
                Component.literal("+"), button -> adjustRate(1)));
        applyButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 73, 62, 18,
                Component.translatable("gui.useless_mod.ore_generator.apply"),
                button -> sendRate()));
        aeButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 112, 62, 18,
                Component.translatable("gui.useless_mod.ore_generator.ae_toggle"),
                button -> toggleAeOutput()));
        syncRateField(true);
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncRateField(false);
        updateControls();
    }

    private void syncRateField(boolean force) {
        if (rateField == null) return;
        long rate = Math.max(1L, menu.getOutputRate());
        if ((force || (!rateField.isFocused() && !rateDirty)) && rate != lastSyncedRate) {
            setRateFieldValue(rate);
            lastSyncedRate = rate;
        }
    }

    private void setRateFieldValue(long rate) {
        syncedRateText = ScaledEnergyAmount.format(rate);
        lastSyncedRate = Math.max(0L, rate);
        updatingRateField = true;
        rateField.setValue(syncedRateText);
        updatingRateField = false;
        rateDirty = false;
    }

    private void sendRate() {
        long rate = readRateField();
        rate = Math.max(1L, rate);
        setRateFieldValue(rate);
        lastSyncedRate = rate;
        PacketDistributor.sendToServer(new OreGeneratorSettingsPacket(
                menu.containerId, menu.getBlockPos(), rate));
    }

    private void adjustRate(long delta) {
        long current = readRateField();
        long adjusted;
        if (delta > 0) {
            adjusted = current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
        } else {
            adjusted = Math.max(1L, current - 1L);
        }
        setRateFieldValue(Math.max(1L, adjusted));
        sendRate();
    }

    private long readRateField() {
        if (!rateDirty && rateField.getValue().equals(syncedRateText)
                && lastSyncedRate >= 1L) {
            return lastSyncedRate;
        }
        return ScaledEnergyAmount.parse(rateField.getValue(), Long.MAX_VALUE)
                .orElse(menu.getOutputRate());
    }

    private void toggleAeOutput() {
        PacketDistributor.sendToServer(new OreGeneratorOutputTogglePacket(
                menu.containerId, menu.getBlockPos()));
    }

    private void changePage(int delta) {
        if (minecraft == null || minecraft.gameMode == null) return;
        int pageCount = Math.max(1, menu.getPageCount());
        int target = Math.floorMod(menu.getPage() + delta, pageCount);
        if (target != menu.getPage()) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    delta > 0 ? OreGeneratorMenu.NEXT_PAGE : OreGeneratorMenu.PREVIOUS_PAGE);
        }
    }

    private void updateControls() {
        if (rateField == null) return;
        boolean editable = !menu.isRecoveryPage();
        rateField.setEditable(editable);
        rateDown.active = editable;
        rateUp.active = editable;
        applyButton.active = editable;
        aeButton.active = true;
        previousPageButton.visible = menu.getPageCount() > 1;
        nextPageButton.visible = menu.getPageCount() > 1;
        previousPageButton.active = menu.getPage() > 0;
        nextPageButton.active = menu.getPage() + 1 < menu.getPageCount();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && rateField != null && rateField.isFocused()) {
            sendRate();
            setFocused(null);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && rateField != null
                && (rateField.keyPressed(keyCode, scanCode, modifiers) || rateField.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (rateDirty) sendRate();
        super.onClose();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        previousPageButton.releaseVisualState();
        nextPageButton.releaseVisualState();
        rateDown.releaseVisualState();
        rateUp.releaseVisualState();
        applyButton.releaseVisualState();
        aeButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        int sampleSlot = menu.getSampleSlotIndex(slot);
        if (sampleSlot < 0 || sampleSlot < menu.getActiveSlots()) return;

        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
        graphics.fill(slot.x + 11, slot.y + 2, slot.x + 15, slot.y + 7, 0xFFF2F2F2);
        graphics.fill(slot.x + 12, slot.y + 1, slot.x + 14, slot.y + 3, 0xFFF2F2F2);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 172, topPos + 18, leftPos + 246, topPos + 142);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 22, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 158, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 218, 9, 1);
        for (Slot slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        String page = (menu.getPage() + 1) + "/" + menu.getPageCount();
        graphics.drawString(font, page, 126 - font.width(page), 6,
                menu.isRecoveryPage() ? MachineScreenStyle.ERROR_TEXT_COLOR
                        : MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);

        graphics.drawString(font, Component.translatable("gui.useless_mod.ore_generator.rate"),
                177, 22, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.ore_generator.rate_unit"),
                177, 94, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        Component aeState = Component.translatable(menu.isAeOnline()
                ? "gui.useless_mod.ore_generator.ae_online"
                : "gui.useless_mod.ore_generator.ae_offline");
        graphics.drawString(font, aeState, 177, 101,
                menu.isAeOnline() ? 0xFF2E7D32 : MachineScreenStyle.ERROR_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(
                        "gui.useless_mod.ore_generator.countdown", menu.getCountdownTicks()),
                8, 91, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(
                        "gui.useless_mod.ore_generator.active_slots",
                        menu.getActiveSlots(), menu.getConfiguredSlots()),
                8, 103, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(
                        menu.isOutputToAe() ? "gui.useless_mod.ore_generator.ae_enabled"
                                : "gui.useless_mod.ore_generator.ae_disabled"),
                177, 136, MachineScreenStyle.TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int sampleSlot = menu.getSampleSlotIndex(hoveredSlot);
        if (hoveredSlot != null && sampleSlot >= menu.getActiveSlots()
                && hoveredSlot.getItem().isEmpty()) {
            graphics.renderTooltip(font,
                    List.of(Component.translatable("gui.useless_mod.ore_generator.locked_slot")),
                    Optional.empty(), mouseX, mouseY);
        }
    }
}
