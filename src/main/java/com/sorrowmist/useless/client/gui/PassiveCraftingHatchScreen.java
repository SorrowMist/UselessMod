package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import com.sorrowmist.useless.network.PassiveCraftingSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PassiveCraftingHatchScreen
        extends AbstractContainerScreen<PassiveCraftingHatchMenu> {
    private EditBox intervalField;
    private EditBox multiplierField;
    private PressableAE2Button intervalDown;
    private PressableAE2Button intervalUp;
    private PressableAE2Button multiplierDown;
    private PressableAE2Button multiplierUp;
    private PressableAE2Button applyButton;
    private PressableAE2Button previousPageButton;
    private PressableAE2Button nextPageButton;
    private int lastSyncedInterval = Integer.MIN_VALUE;
    private long lastSyncedMultiplier = Long.MIN_VALUE;
    private String syncedMultiplierText = "";
    private boolean updatingMultiplierField;
    private boolean multiplierDirty;
    private boolean multiplierFieldWasFocused;

    public PassiveCraftingHatchScreen(
            PassiveCraftingHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 250;
        imageHeight = 242;
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
                Component.literal("<"), button -> page(PagedRecoverableMenu.PREVIOUS_PAGE)));
        nextPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 148, topPos + 4, 18, 12,
                Component.literal(">"), button -> page(PagedRecoverableMenu.NEXT_PAGE)));

        intervalField = numericField(177, 34, 62,
                Component.translatable("gui.useless_mod.passive_crafting.interval"));
        multiplierField = scaledAmountField(177, 78, 62,
                Component.translatable("gui.useless_mod.passive_crafting.multiplier"));
        addRenderableWidget(intervalField);
        addRenderableWidget(multiplierField);

        intervalDown = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 51, 28, 14,
                Component.literal("-"), button -> adjustInterval(-20)));
        intervalUp = addRenderableWidget(new PressableAE2Button(
                leftPos + 211, topPos + 51, 28, 14,
                Component.literal("+"), button -> adjustInterval(20)));
        multiplierDown = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 95, 28, 14,
                Component.literal("-"), button -> adjustMultiplier(-1)));
        multiplierUp = addRenderableWidget(new PressableAE2Button(
                leftPos + 211, topPos + 95, 28, 14,
                Component.literal("+"), button -> adjustMultiplier(1)));
        applyButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 177, topPos + 116, 62, 18,
                Component.translatable("gui.useless_mod.passive_crafting.apply"),
                button -> sendSettings()));
        syncFields(true);
        updatePageControls();
    }

    private EditBox numericField(int x, int y, int width, Component narration) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, 14, narration);
        field.setMaxLength(19);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        return field;
    }

    private EditBox scaledAmountField(int x, int y, int width, Component narration) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, 14, narration);
        field.setMaxLength(24);
        field.setFilter(ScaledEnergyAmount::isValidInput);
        field.setResponder(value -> {
            if (!updatingMultiplierField) {
                multiplierDirty = !value.equals(syncedMultiplierText);
            }
        });
        return field;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean multiplierFocused = multiplierField.isFocused();
        if (multiplierFieldWasFocused && !multiplierFocused && multiplierDirty) {
            sendSettings();
        }
        multiplierFieldWasFocused = multiplierFocused;
        syncFields(false);
        boolean editable = menu.isFormed();
        intervalField.setEditable(editable);
        multiplierField.setEditable(editable);
        intervalDown.active = editable;
        intervalUp.active = editable;
        multiplierDown.active = editable;
        multiplierUp.active = editable;
        applyButton.active = editable;
        updatePageControls();
    }

    private void updatePageControls() {
        int pageCount = menu.getPageCount();
        previousPageButton.visible = pageCount > 1;
        previousPageButton.active = menu.getPage() > 0;
        nextPageButton.visible = pageCount > 1;
        nextPageButton.active = menu.getPage() < pageCount - 1;
    }

    private void page(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void syncFields(boolean force) {
        int interval = Math.max(PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                menu.getIntervalTicks());
        long multiplier = Math.max(1L, menu.getMultiplier());
        if ((force || !intervalField.isFocused()) && interval != lastSyncedInterval) {
            intervalField.setValue(Integer.toString(interval));
            lastSyncedInterval = interval;
        }
        if ((force || !multiplierField.isFocused()) && multiplier != lastSyncedMultiplier) {
            setMultiplierFieldValue(multiplier);
            lastSyncedMultiplier = multiplier;
        }
    }

    private void adjustInterval(int delta) {
        long current = readNumber(intervalField, menu.getIntervalTicks());
        int value = (int) Mth.clamp(current + delta,
                PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                PassiveCraftingHatchBlockEntity.MAX_INTERVAL_TICKS);
        intervalField.setValue(Integer.toString(value));
        sendSettings();
    }

    private void adjustMultiplier(int delta) {
        long current = ScaledEnergyAmount.parse(multiplierField.getValue(), menu.getMaxMultiplier())
                .orElse(menu.getMultiplier());
        long adjusted = delta > 0
                ? current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L
                : current <= 1L ? 1L : current - 1L;
        long value = Math.max(1L, Math.min(menu.getMaxMultiplier(), adjusted));
        setMultiplierFieldValue(value);
        sendSettings();
    }

    private void sendSettings() {
        if (!menu.isFormed()) {
            syncFields(true);
            return;
        }
        int interval = (int) Mth.clamp(readNumber(intervalField, menu.getIntervalTicks()),
                (long) PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                (long) PassiveCraftingHatchBlockEntity.MAX_INTERVAL_TICKS);
        long multiplier = ScaledEnergyAmount.parse(multiplierField.getValue(), menu.getMaxMultiplier())
                .orElse(menu.getMultiplier());
        multiplier = Math.max(1L, Math.min(menu.getMaxMultiplier(), multiplier));
        intervalField.setValue(Integer.toString(interval));
        setMultiplierFieldValue(multiplier);
        lastSyncedInterval = interval;
        lastSyncedMultiplier = multiplier;
        PacketDistributor.sendToServer(new PassiveCraftingSettingsPacket(
                menu.containerId, menu.getBlockPos(), interval, multiplier));
    }

    private void setMultiplierFieldValue(long multiplier) {
        syncedMultiplierText = ScaledEnergyAmount.format(multiplier);
        updatingMultiplierField = true;
        multiplierField.setValue(syncedMultiplierText);
        updatingMultiplierField = false;
        multiplierDirty = false;
    }

    private static long readNumber(EditBox field, long fallback) {
        try {
            return Long.parseLong(field.getValue());
        } catch (NumberFormatException exception) {
            return Math.max(1, fallback);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (intervalField.isFocused() || multiplierField.isFocused())) {
            sendSettings();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (multiplierDirty) {
            sendSettings();
        }
        super.onClose();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        previousPageButton.releaseVisualState();
        nextPageButton.releaseVisualState();
        intervalDown.releaseVisualState();
        intervalUp.releaseVisualState();
        multiplierDown.releaseVisualState();
        multiplierUp.releaseVisualState();
        applyButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
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
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        int patternSlot = menu.getPatternSlotIndex(slot);
        if (patternSlot < 0 || !PatternSlotRenderer.renderPattern(
                graphics, font, slot.getItem(), slot.x, slot.y,
                slot.x + slot.y * imageWidth, minecraft == null ? null : minecraft.level)) {
            super.renderSlot(graphics, slot);
        }
        if (patternSlot < 0) return;

        var status = menu.getSlotStatus(patternSlot);
        int color = statusColor(status.state());
        int width = status.maxProgress() <= 0 ? 16
                : Mth.clamp((int) ((long) status.progress() * 16L / status.maxProgress()), 0, 16);
        if (width > 0 && status.state() != PassiveCraftingHatchBlockEntity.SlotState.EMPTY) {
            graphics.fill(slot.x, slot.y + 14, slot.x + width, slot.y + 16, color);
        }
        if (patternSlot >= menu.getActivePatternSlots()) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
            graphics.fill(slot.x + 11, slot.y + 2, slot.x + 15, slot.y + 7, 0xFFF2F2F2);
            graphics.fill(slot.x + 12, slot.y + 1, slot.x + 14, slot.y + 3, 0xFFF2F2F2);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        String page = (menu.getPage() + 1) + "/" + menu.getPageCount();
        graphics.drawString(font, page, 126 - font.width(page), 6,
                menu.isRecoveryPage() ? MachineScreenStyle.ERROR_TEXT_COLOR : MachineScreenStyle.MUTED_TEXT_COLOR,
                false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.interval"),
                177, 22, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.multiplier"),
                177, 66, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.max_multiplier",
                        ScaledEnergyAmount.format(menu.getMaxMultiplier())),
                8, 91, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.countdown",
                        menu.getCountdownTicks()),
                8, 102, MachineScreenStyle.MUTED_TEXT_COLOR, false);

        int patternSlot = menu.getPatternSlotIndex(hoveredSlot);
        if (patternSlot >= 0) {
            var status = menu.getSlotStatus(patternSlot);
            Component statusText = statusComponent(status);
            graphics.drawString(font, font.split(statusText, 156).getFirst(),
                    8, 116, statusColor(status.state()), false);
            if (status.maxProgress() > 0) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.passive_crafting.progress",
                                status.progress(), status.maxProgress()),
                        8, 128, MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
        } else {
            Component state = Component.translatable(menu.isFormed()
                    ? "gui.useless_mod.passive_crafting.connected"
                    : "gui.useless_mod.passive_crafting.unformed");
            graphics.drawString(font, state, 8, 116,
                    menu.isFormed() ? 0xFF2E7D32 : 0xFFA66A00, false);
            graphics.drawString(font,
                    Component.translatable("gui.useless_mod.passive_crafting.active_slots",
                            menu.getActivePatternSlots(), menu.getConfiguredPatternSlots()),
                    8, 128, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
    }

    private static Component statusComponent(PassiveCraftingHatchBlockEntity.SlotStatus status) {
        Component base = Component.translatable("gui.useless_mod.passive_crafting.status."
                + status.state().name().toLowerCase(Locale.ROOT));
        return status.detail().isEmpty() ? base : Component.translatable(
                "gui.useless_mod.passive_crafting.status_detail", base, status.detail());
    }

    private static int statusColor(PassiveCraftingHatchBlockEntity.SlotState state) {
        return switch (state) {
            case EMPTY -> MachineScreenStyle.MUTED_TEXT_COLOR;
            case READY -> 0xFF517497;
            case RUNNING -> 0xFF2E7D32;
            case PAUSED -> 0xFFA66A00;
            case WAITING_OUTPUT -> 0xFF7B4EA3;
            case MISSING_INPUT, MISSING_MOLD, AE_OFFLINE, INVALID_PATTERN ->
                    MachineScreenStyle.ERROR_TEXT_COLOR;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int patternSlot = menu.getPatternSlotIndex(hoveredSlot);
        if (hoveredSlot != null && patternSlot >= 0 && hoveredSlot.getItem().isEmpty()) {
            var status = menu.getSlotStatus(patternSlot);
            Component first = patternSlot >= menu.getActivePatternSlots()
                    ? Component.translatable("gui.useless_mod.passive_crafting.locked_slot")
                    : statusComponent(status);
            graphics.renderTooltip(font, List.of(first), Optional.empty(), mouseX, mouseY);
        }
    }
}
