package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.network.PassiveCraftingSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int PANEL_COLOR = 0xFF20252B;
    private static final int PANEL_EDGE = 0xFF59636E;
    private static final int SLOT_COLOR = 0xFF101418;
    private static final int SLOT_EDGE = 0xFF737D87;

    private EditBox intervalField;
    private EditBox multiplierField;
    private Button intervalDown;
    private Button intervalUp;
    private Button multiplierDown;
    private Button multiplierUp;
    private Button applyButton;
    private int lastSyncedInterval = Integer.MIN_VALUE;
    private int lastSyncedMultiplier = Integer.MIN_VALUE;

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
        intervalField = numericField(148, 34, 70,
                Component.translatable("gui.useless_mod.passive_crafting.interval"));
        multiplierField = numericField(148, 70, 70,
                Component.translatable("gui.useless_mod.passive_crafting.multiplier"));
        addRenderableWidget(intervalField);
        addRenderableWidget(multiplierField);

        intervalDown = addRenderableWidget(Button.builder(
                        Component.literal("-"), button -> adjustInterval(-20))
                .bounds(leftPos + 128, topPos + 34, 18, 14).build());
        intervalUp = addRenderableWidget(Button.builder(
                        Component.literal("+"), button -> adjustInterval(20))
                .bounds(leftPos + 220, topPos + 34, 18, 14).build());
        multiplierDown = addRenderableWidget(Button.builder(
                        Component.literal("-"), button -> adjustMultiplier(-1))
                .bounds(leftPos + 128, topPos + 70, 18, 14).build());
        multiplierUp = addRenderableWidget(Button.builder(
                        Component.literal("+"), button -> adjustMultiplier(1))
                .bounds(leftPos + 220, topPos + 70, 18, 14).build());
        applyButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.useless_mod.passive_crafting.apply"),
                        button -> sendSettings())
                .bounds(leftPos + 128, topPos + 92, 110, 18).build());
        syncFields(true);
    }

    private EditBox numericField(int x, int y, int width, Component narration) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, 14, narration);
        field.setMaxLength(10);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        return field;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncFields(false);
        boolean editable = menu.isFormed();
        intervalField.setEditable(editable);
        multiplierField.setEditable(editable);
        intervalDown.active = editable;
        intervalUp.active = editable;
        multiplierDown.active = editable;
        multiplierUp.active = editable;
        applyButton.active = editable;
    }

    private void syncFields(boolean force) {
        int interval = Math.max(PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                menu.getIntervalTicks());
        int multiplier = Math.max(1, menu.getMultiplier());
        if ((force || !intervalField.isFocused()) && interval != lastSyncedInterval) {
            intervalField.setValue(Integer.toString(interval));
            lastSyncedInterval = interval;
        }
        if ((force || !multiplierField.isFocused()) && multiplier != lastSyncedMultiplier) {
            multiplierField.setValue(Integer.toString(multiplier));
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
        long current = readNumber(multiplierField, menu.getMultiplier());
        int value = (int) Mth.clamp(current + delta, 1L, (long) menu.getMaxMultiplier());
        multiplierField.setValue(Integer.toString(value));
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
        int multiplier = (int) Mth.clamp(readNumber(multiplierField, menu.getMultiplier()),
                1L, (long) menu.getMaxMultiplier());
        intervalField.setValue(Integer.toString(interval));
        multiplierField.setValue(Integer.toString(multiplier));
        PacketDistributor.sendToServer(new PassiveCraftingSettingsPacket(
                menu.containerId, menu.getBlockPos(), interval, multiplier));
    }

    private static long readNumber(EditBox field, int fallback) {
        try {
            return Long.parseLong(field.getValue());
        } catch (NumberFormatException exception) {
            return Math.max(1, fallback);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER
                && (intervalField.isFocused() || multiplierField.isFocused())) {
            sendSettings();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_EDGE);
        graphics.fill(leftPos + 1, topPos + 1,
                leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL_COLOR);
        graphics.fill(leftPos + 120, topPos + 18, leftPos + 246, topPos + 142, 0xFF171B20);

        for (int slot = 0; slot < menu.slots.size(); slot++) {
            Slot menuSlot = menu.slots.get(slot);
            drawSlotBackground(graphics, menuSlot.x, menuSlot.y);
        }
    }

    private void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        int left = leftPos + x - 1;
        int top = topPos + y - 1;
        graphics.fill(left, top, left + 18, top + 18, SLOT_EDGE);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT_COLOR);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        boolean pattern = slot.index < PassiveCraftingHatchMenu.PATTERN_SLOTS;
        if (!pattern || !PatternSlotRenderer.renderPattern(
                graphics, font, slot.getItem(), slot.x, slot.y,
                slot.x + slot.y * imageWidth, minecraft == null ? null : minecraft.level)) {
            super.renderSlot(graphics, slot);
        }
        if (!pattern) return;

        int index = slot.index;
        var status = menu.getSlotStatus(index);
        int color = statusColor(status.state());
        int width = status.maxProgress() <= 0 ? 16
                : Mth.clamp((int) ((long) status.progress() * 16L / status.maxProgress()), 0, 16);
        if (width > 0 && status.state() != PassiveCraftingHatchBlockEntity.SlotState.EMPTY) {
            graphics.fill(slot.x, slot.y + 14, slot.x + width, slot.y + 16, color);
        }
        if (index >= menu.getActivePatternSlots()) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
            // Compact padlock glyph for withdraw-only recovery slots.
            graphics.fill(slot.x + 11, slot.y + 2, slot.x + 15, slot.y + 7, 0xFF8E969E);
            graphics.fill(slot.x + 12, slot.y + 1, slot.x + 14, slot.y + 3, 0xFF8E969E);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE6EBEF, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0xFFBEC6CE, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.interval"),
                128, 22, 0xFFBEC6CE, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.multiplier"),
                128, 58, 0xFFBEC6CE, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.max_multiplier",
                        menu.getMaxMultiplier()),
                128, 114, 0xFFAEB8C1, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.countdown",
                        menu.getCountdownTicks()),
                128, 125, 0xFFAEB8C1, false);

        Slot hovered = hoveredSlot;
        if (hovered != null && hovered.index < PassiveCraftingHatchMenu.PATTERN_SLOTS) {
            var status = menu.getSlotStatus(hovered.index);
            Component statusText = statusComponent(status);
            graphics.drawString(font, font.split(statusText, 108).getFirst(),
                    8, 116, statusColor(status.state()), false);
            if (status.maxProgress() > 0) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.passive_crafting.progress",
                                status.progress(), status.maxProgress()),
                        8, 128, 0xFFAEB8C1, false);
            }
        } else {
            Component state = Component.translatable(menu.isFormed()
                    ? "gui.useless_mod.passive_crafting.connected"
                    : "gui.useless_mod.passive_crafting.unformed");
            graphics.drawString(font, state, 8, 116,
                    menu.isFormed() ? 0xFF72C28B : 0xFFE2A45E, false);
            graphics.drawString(font,
                    Component.translatable("gui.useless_mod.passive_crafting.active_slots",
                            menu.getActivePatternSlots(), PassiveCraftingHatchMenu.PATTERN_SLOTS),
                    8, 128, 0xFFAEB8C1, false);
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
            case EMPTY -> 0xFF6F7780;
            case READY -> 0xFF65AEDD;
            case RUNNING -> 0xFF67C587;
            case PAUSED -> 0xFFE1AA52;
            case WAITING_OUTPUT -> 0xFFC08AE1;
            case MISSING_INPUT, MISSING_MOLD, AE_OFFLINE, INVALID_PATTERN -> 0xFFE16F69;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null
                && hoveredSlot.index < PassiveCraftingHatchMenu.PATTERN_SLOTS
                && hoveredSlot.getItem().isEmpty()) {
            var status = menu.getSlotStatus(hoveredSlot.index);
            Component first = hoveredSlot.index >= menu.getActivePatternSlots()
                    ? Component.translatable("gui.useless_mod.passive_crafting.locked_slot")
                    : statusComponent(status);
            graphics.renderTooltip(font, List.of(first), Optional.empty(), mouseX, mouseY);
        }
    }
}
