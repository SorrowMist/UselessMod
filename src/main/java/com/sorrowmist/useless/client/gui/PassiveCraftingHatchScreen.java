package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import com.sorrowmist.useless.network.PassiveCraftingSettingsPacket;
import com.sorrowmist.useless.network.PassiveCraftingSlotMultiplierPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Passive hatch screen: a paginated pattern inventory, the global interval/global multiplier
 * controls, and per-slot batch sizes.
 *
 * <p>The right-hand multiplier field is context sensitive. With no slot selected it edits the
 * global default exactly as before; after ctrl-clicking a pattern slot it edits that slot's own
 * override instead. Ctrl-clicking the same slot again clears the selection. The scroll wheel
 * adjusts whatever active slot is under the cursor, and a compact badge inside every slot shows
 * the value it will actually run at (amber while pinned, grey while following the global value).
 */
public final class PassiveCraftingHatchScreen
        extends AbstractContainerScreen<PassiveCraftingHatchMenu> {
    /** Multiplier edits are optimistic on the client until the next server status snapshot. */
    private static final int PENDING_TICKS = 40;
    private static final int BADGE_BACKDROP = 0x99000000;
    private static final int MULTIPLIER_CUSTOM_COLOR = 0xFFFFC65C;
    private static final int MULTIPLIER_INHERITED_COLOR = 0xFFE6E9F2;
    private static final int SELECTION_COLOR = 0xFFCC7A00;
    /** Widest text the right-hand readout can hold without spilling out of its inset. */
    private static final int READOUT_WIDTH = 70;
    private static final int READOUT_X = 174;
    private static final int READOUT_TOP = 151;
    private static final int READOUT_ROW_HEIGHT = 12;

    private EditBox intervalField;
    private EditBox multiplierField;
    private PressableAE2Button intervalDown;
    private PressableAE2Button intervalUp;
    private PressableAE2Button multiplierDown;
    private PressableAE2Button multiplierUp;
    private PressableAE2Button applyButton;
    private PressableAE2Button applySlotGlobalButton;
    private PressableAE2Button applyAllButton;
    private PressableAE2Button previousPageButton;
    private PressableAE2Button nextPageButton;
    private int lastSyncedInterval = Integer.MIN_VALUE;
    private long lastSyncedMultiplier = Long.MIN_VALUE;
    private String syncedMultiplierText = "";
    private boolean updatingMultiplierField;
    private boolean multiplierDirty;
    private boolean multiplierFieldWasFocused;

    /** Slot currently under the cursor with an active pattern slot, or {@code null}. */
    @Nullable
    private Slot multiplierHoverSlot;
    private int multiplierHoverPatternSlot = -1;
    /** Ctrl-clicked slot the multiplier field is bound to; {@code -1} means "the global default". */
    private int selectedPatternSlot = -1;
    private int lastObservedPage = -1;
    /** Pattern slot -> {pending value, remaining ticks}; a local echo of an in-flight edit. */
    private final Map<Integer, long[]> pendingSlotMultipliers = new HashMap<>();
    private int pendingGlobalResetTicks;

    public PassiveCraftingHatchScreen(
            PassiveCraftingHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 250;
        imageHeight = 336;
        inventoryLabelX = 44;
        inventoryLabelY = 242;
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
        applySlotGlobalButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 175, topPos + 186, 68, 16,
                Component.translatable("gui.useless_mod.passive_crafting.apply_slot"),
                button -> clearSlotMultiplier(selectedPatternSlot)));
        applyAllButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 175, topPos + 204, 68, 16,
                Component.translatable("gui.useless_mod.passive_crafting.apply_all"),
                button -> clearSlotMultipliers()));
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
        if (!pendingSlotMultipliers.isEmpty()) {
            pendingSlotMultipliers.entrySet().removeIf(entry -> --entry.getValue()[1] <= 0);
        }
        if (pendingGlobalResetTicks > 0) {
            pendingGlobalResetTicks--;
        }
        // The selection is an absolute slot index, but its value only arrives for the visible page.
        int page = menu.getPage();
        if (page != lastObservedPage) {
            lastObservedPage = page;
            clearSelection();
        } else if (!menu.isFormed() && selectedPatternSlot >= 0) {
            clearSelection();
        }
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
        // Only meaningful while a slot is bound to the multiplier field.
        applySlotGlobalButton.visible = selectedPatternSlot >= 0;
        applySlotGlobalButton.active = editable;
        applyAllButton.active = editable;
        updatePageControls();
    }

    private void clearSelection() {
        if (selectedPatternSlot < 0) {
            return;
        }
        selectedPatternSlot = -1;
        syncFields(true);
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

    /** Current value of the field's target: the selected slot's override, or the global default. */
    private long fieldTargetMultiplier() {
        if (selectedPatternSlot >= 0) {
            return Math.max(1L, displayedSlotMultiplier(selectedPatternSlot));
        }
        return Math.max(1L, menu.getMultiplier());
    }

    private void syncFields(boolean force) {
        int interval = Math.max(PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                menu.getIntervalTicks());
        long multiplier = fieldTargetMultiplier();
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
                .orElse(fieldTargetMultiplier());
        long adjusted = delta > 0
                ? current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L
                : current <= 1L ? 1L : current - 1L;
        long value = Math.max(1L, Math.min(menu.getMaxMultiplier(), adjusted));
        setMultiplierFieldValue(value);
        sendSettings();
    }

    /**
     * Applies the right panel. The interval is always global; the multiplier goes to the selected
     * slot when one is selected, and to the global default otherwise.
     */
    private void sendSettings() {
        if (!menu.isFormed()) {
            syncFields(true);
            return;
        }
        int interval = (int) Mth.clamp(readNumber(intervalField, menu.getIntervalTicks()),
                (long) PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                (long) PassiveCraftingHatchBlockEntity.MAX_INTERVAL_TICKS);
        long maximum = menu.getMaxMultiplier();
        long multiplier = ScaledEnergyAmount.parse(multiplierField.getValue(), maximum)
                .orElse(fieldTargetMultiplier());
        multiplier = Mth.clamp(multiplier, 1L, maximum);
        intervalField.setValue(Integer.toString(interval));
        setMultiplierFieldValue(multiplier);
        lastSyncedInterval = interval;
        lastSyncedMultiplier = multiplier;
        PacketDistributor.sendToServer(new PassiveCraftingSettingsPacket(
                menu.containerId, menu.getBlockPos(), interval,
                selectedPatternSlot >= 0 ? Math.max(1L, menu.getMultiplier()) : multiplier));
        if (selectedPatternSlot >= 0) {
            requestSlotMultiplier(selectedPatternSlot, multiplier);
        }
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

    // ------------------------------------------------------------------
    // Per-slot multiplier editing
    // ------------------------------------------------------------------

    /** Ctrl-click toggles which slot the right-hand multiplier field edits. */
    private void toggleSlotSelection(int patternSlot) {
        if (patternSlot < 0 || !menu.isFormed()) {
            return;
        }
        selectedPatternSlot = selectedPatternSlot == patternSlot ? -1 : patternSlot;
        syncFields(true);
    }

    /** Sends one slot's new batch size and echoes it locally until the next status snapshot. */
    private void requestSlotMultiplier(int patternSlot, long value) {
        if (!menu.isFormed() || patternSlot < 0) {
            return;
        }
        long clamped = Mth.clamp(value, 1L, menu.getMaxMultiplier());
        pendingSlotMultipliers.put(patternSlot, new long[]{clamped, PENDING_TICKS});
        PacketDistributor.sendToServer(new PassiveCraftingSlotMultiplierPacket(
                menu.containerId, menu.getBlockPos(), patternSlot, clamped));
    }

    /** Scroll-wheel stepper for the slot under the cursor. */
    private void adjustHoveredSlotMultiplier(int direction, boolean doubleStep) {
        int patternSlot = multiplierHoverPatternSlot;
        if (patternSlot < 0) {
            return;
        }
        long maximum = menu.getMaxMultiplier();
        long current = displayedSlotMultiplier(patternSlot);
        long next;
        if (doubleStep) {
            next = direction > 0
                    ? current >= maximum ? maximum : Math.min(maximum, current * 2L)
                    : Math.max(1L, current / 2L);
        } else {
            next = direction > 0
                    ? current >= maximum ? maximum : current + 1L
                    : current <= 1L ? 1L : current - 1L;
        }
        long value = Mth.clamp(next, 1L, maximum);
        if (value != current) {
            requestSlotMultiplier(patternSlot, value);
        }
    }

    private void clearSlotMultipliers() {
        if (!menu.isFormed()) {
            return;
        }
        pendingSlotMultipliers.clear();
        pendingGlobalResetTicks = PENDING_TICKS;
        PacketDistributor.sendToServer(new PassiveCraftingSlotMultiplierPacket(
                menu.containerId, menu.getBlockPos(), -1, 0L));
    }

    /** Drops one slot's override so it follows the global multiplier again. */
    private void clearSlotMultiplier(int patternSlot) {
        if (!menu.isFormed() || patternSlot < 0) {
            return;
        }
        // A pending value of 0 reads as "back to the global default" until the snapshot catches up.
        pendingSlotMultipliers.put(patternSlot, new long[]{0L, PENDING_TICKS});
        PacketDistributor.sendToServer(new PassiveCraftingSlotMultiplierPacket(
                menu.containerId, menu.getBlockPos(), patternSlot, 0L));
    }

    /** Value shown for a slot: the local echo of an in-flight edit wins over the snapshot. */
    private long displayedSlotMultiplier(int patternSlot) {
        long[] pending = pendingSlotMultipliers.get(patternSlot);
        if (pending != null && pending[1] > 0) {
            return pending[0] > 0L ? pending[0] : Math.max(1L, menu.getMultiplier());
        }
        if (pendingGlobalResetTicks > 0) {
            return Math.max(1L, menu.getMultiplier());
        }
        return menu.getSlotMultiplier(patternSlot);
    }

    private boolean displayedSlotMultiplierIsCustom(int patternSlot) {
        long[] pending = pendingSlotMultipliers.get(patternSlot);
        if (pending != null && pending[1] > 0) {
            return pending[0] > 0L;
        }
        return pendingGlobalResetTicks <= 0 && menu.hasOwnSlotMultiplier(patternSlot);
    }

    /** Slot the right-hand readout describes: the selection wins over the hover preview. */
    private int readoutPatternSlot() {
        return selectedPatternSlot >= 0 ? selectedPatternSlot : multiplierHoverPatternSlot;
    }

    /** Recomputes which active pattern slot the cursor is over. */
    private void updateMultiplierHover(double mouseX, double mouseY) {
        multiplierHoverSlot = null;
        multiplierHoverPatternSlot = -1;
        if (!menu.isFormed()) {
            return;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null) {
            return;
        }
        int patternSlot = menu.getPatternSlotIndex(slot);
        if (patternSlot < 0 || patternSlot >= menu.getActivePatternSlots()) {
            return;
        }
        multiplierHoverSlot = slot;
        multiplierHoverPatternSlot = patternSlot;
    }

    @Nullable
    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private Component slotMultiplierTooltip(int patternSlot) {
        long value = displayedSlotMultiplier(patternSlot);
        if (displayedSlotMultiplierIsCustom(patternSlot)) {
            return Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_custom",
                    ScaledEnergyAmount.format(value), ScaledEnergyAmount.format(menu.getMultiplier()));
        }
        return Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_global",
                ScaledEnergyAmount.format(menu.getMultiplier()));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateMultiplierHover(mouseX, mouseY);
        if (button == 0 && hasControlDown() && multiplierHoverPatternSlot >= 0) {
            toggleSlotSelection(multiplierHoverPatternSlot);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            updateMultiplierHover(mouseX, mouseY);
            if (multiplierHoverPatternSlot >= 0) {
                adjustHoveredSlotMultiplier(scrollY > 0.0D ? 1 : -1, hasShiftDown());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (intervalField.isFocused() || multiplierField.isFocused())) {
            sendSettings();
            setFocused(null);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE
                && (consumesTextInputKey(intervalField, keyCode, scanCode, modifiers)
                || consumesTextInputKey(multiplierField, keyCode, scanCode, modifiers))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean consumesTextInputKey(EditBox field, int keyCode, int scanCode, int modifiers) {
        return field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput();
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
        applySlotGlobalButton.releaseVisualState();
        applyAllButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 172, topPos + 18, leftPos + 246, topPos + 142);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 172, topPos + 146, leftPos + 246, topPos + 226);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 22, 9, 10);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 254, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 44, 312, 9, 1);

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
        boolean active = patternSlot < menu.getActivePatternSlots();
        if (active) {
            renderSlotMultiplier(graphics, slot, patternSlot);
            // Drawn after the badge so a running slot keeps its progress underline readable.
            renderSlotProgress(graphics, slot, status);
            if (patternSlot == selectedPatternSlot) {
                renderSelectionFrame(graphics, slot);
            }
        } else {
            renderSlotProgress(graphics, slot, status);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
            graphics.fill(slot.x + 11, slot.y + 2, slot.x + 15, slot.y + 7, 0xFFF2F2F2);
            graphics.fill(slot.x + 12, slot.y + 1, slot.x + 14, slot.y + 3, 0xFFF2F2F2);
        }
    }

    private void renderSlotProgress(GuiGraphics graphics, Slot slot,
                                    PassiveCraftingHatchBlockEntity.SlotStatus status) {
        if (status.state() == PassiveCraftingHatchBlockEntity.SlotState.EMPTY) {
            return;
        }
        int width = status.maxProgress() <= 0 ? 16
                : Mth.clamp((int) ((long) status.progress() * 16L / status.maxProgress()), 0, 16);
        if (width > 0) {
            graphics.fill(slot.x, slot.y + 14, slot.x + width, slot.y + 16, statusColor(status.state()));
        }
    }

    /**
     * Draws the compact batch-size badge of one slot. Slots that merely follow the global
     * multiplier stay quiet when empty, so a configured slot is recognisable at a glance.
     */
    private void renderSlotMultiplier(GuiGraphics graphics, Slot slot, int patternSlot) {
        boolean configured = displayedSlotMultiplierIsCustom(patternSlot);
        if (slot.getItem().isEmpty() && !configured) {
            return;
        }
        String text = ScaledEnergyAmount.format(displayedSlotMultiplier(patternSlot));
        if (text.isEmpty()) {
            return;
        }
        float scale = Math.min(0.75F, 14.0F / Math.max(1, font.width(text)));
        int textWidth = Math.max(1, Math.round(font.width(text) * scale));
        int textHeight = Math.max(1, Math.round(font.lineHeight * scale));
        // Stop above the two-pixel progress underline, which is drawn after the badge.
        int bottom = slot.y + 14;
        int top = bottom - textHeight;
        graphics.fill(slot.x + 1, top - 1, slot.x + 16, bottom, BADGE_BACKDROP);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(slot.x + 16 - textWidth - 1, top, 100.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0,
                configured ? MULTIPLIER_CUSTOM_COLOR : MULTIPLIER_INHERITED_COLOR, false);
        pose.popPose();
    }

    /** One-pixel frame around the ctrl-selected slot, drawn over the badge. */
    private void renderSelectionFrame(GuiGraphics graphics, Slot slot) {
        int left = slot.x - 1;
        int top = slot.y - 1;
        int right = slot.x + 17;
        int bottom = slot.y + 17;
        graphics.fill(left, top, right, top + 1, SELECTION_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, SELECTION_COLOR);
        graphics.fill(left, top, left + 1, bottom, SELECTION_COLOR);
        graphics.fill(right - 1, top, right, bottom, SELECTION_COLOR);
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
                8, 204, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_mod.passive_crafting.countdown",
                        menu.getCountdownTicks()),
                8, 215, MachineScreenStyle.MUTED_TEXT_COLOR, false);

        renderSlotMultiplierReadout(graphics);

        int patternSlot = menu.getPatternSlotIndex(hoveredSlot);
        if (patternSlot >= 0) {
            var status = menu.getSlotStatus(patternSlot);
            Component statusText = statusComponent(status);
            graphics.drawString(font, font.split(statusText, 156).getFirst(),
                    8, 227, statusColor(status.state()), false);
            if (status.maxProgress() > 0) {
                graphics.drawString(font,
                        Component.translatable("gui.useless_mod.passive_crafting.progress",
                                status.progress(), status.maxProgress()),
                        8, 239, MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
        } else {
            Component state = Component.translatable(menu.isFormed()
                    ? "gui.useless_mod.passive_crafting.connected"
                    : "gui.useless_mod.passive_crafting.unformed");
            graphics.drawString(font, state, 8, 227,
                    menu.isFormed() ? 0xFF2E7D32 : 0xFFA66A00, false);
            graphics.drawString(font,
                    Component.translatable("gui.useless_mod.passive_crafting.active_slots",
                            menu.getActivePatternSlots(), menu.getConfiguredPatternSlots()),
                    8, 239, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
    }

    /**
     * Right-hand readout telling the player what the multiplier field currently edits. Every row
     * goes through {@link Font#split} so a long translation wraps instead of spilling out of the
     * inset.
     */
    private void renderSlotMultiplierReadout(GuiGraphics graphics) {
        int target = readoutPatternSlot();
        if (target < 0) {
            drawReadoutRow(graphics, 0,
                    Component.translatable("gui.useless_mod.passive_crafting.slot_global_target"),
                    MachineScreenStyle.TEXT_COLOR);
            drawReadoutRow(graphics, 1,
                    Component.translatable("gui.useless_mod.passive_crafting.slot_select_hint"),
                    MachineScreenStyle.MUTED_TEXT_COLOR);
            drawReadoutRow(graphics, 2,
                    Component.translatable("gui.useless_mod.passive_crafting.slot_wheel_hint"),
                    MachineScreenStyle.MUTED_TEXT_COLOR);
            return;
        }
        boolean selected = selectedPatternSlot >= 0;
        boolean custom = displayedSlotMultiplierIsCustom(target);
        drawReadoutRow(graphics, 0,
                Component.translatable(selected
                        ? "gui.useless_mod.passive_crafting.slot_label_selected"
                        : "gui.useless_mod.passive_crafting.slot_label", target),
                selected ? SELECTION_COLOR : MachineScreenStyle.TEXT_COLOR);
        drawReadoutRow(graphics, 1,
                Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_value",
                        ScaledEnergyAmount.format(displayedSlotMultiplier(target))),
                MachineScreenStyle.TEXT_COLOR);
        drawReadoutRow(graphics, 2,
                custom
                        ? Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_state_custom")
                        : Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_state_global",
                        ScaledEnergyAmount.format(menu.getMultiplier())),
                custom ? MULTIPLIER_CUSTOM_COLOR : MachineScreenStyle.MUTED_TEXT_COLOR);
    }

    private void drawReadoutRow(GuiGraphics graphics, int row, Component text, int color) {
        var lines = font.split(text, READOUT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        graphics.drawString(font, lines.getFirst(), READOUT_X,
                READOUT_TOP + row * READOUT_ROW_HEIGHT, color, false);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = super.getTooltipFromContainerItem(stack);
        if (multiplierHoverPatternSlot < 0) {
            return tooltip;
        }
        List<Component> extended = new ArrayList<>(tooltip);
        if (multiplierHoverPatternSlot == selectedPatternSlot) {
            extended.add(Component.translatable(
                    "gui.useless_mod.passive_crafting.slot_selected_hint"));
        }
        extended.add(slotMultiplierTooltip(multiplierHoverPatternSlot));
        extended.add(Component.translatable("gui.useless_mod.passive_crafting.slot_multiplier_edit_hint"));
        return extended;
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
        updateMultiplierHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int patternSlot = menu.getPatternSlotIndex(hoveredSlot);
        if (hoveredSlot != null && patternSlot >= 0 && hoveredSlot.getItem().isEmpty()) {
            var status = menu.getSlotStatus(patternSlot);
            List<Component> lines = new ArrayList<>();
            lines.add(patternSlot >= menu.getActivePatternSlots()
                    ? Component.translatable("gui.useless_mod.passive_crafting.locked_slot")
                    : statusComponent(status));
            if (patternSlot < menu.getActivePatternSlots()) {
                if (patternSlot == selectedPatternSlot) {
                    lines.add(Component.translatable(
                            "gui.useless_mod.passive_crafting.slot_selected_hint"));
                }
                lines.add(slotMultiplierTooltip(patternSlot));
                lines.add(Component.translatable(
                        "gui.useless_mod.passive_crafting.slot_multiplier_edit_hint"));
            }
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }
}
