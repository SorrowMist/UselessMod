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
import com.sorrowmist.useless.client.gui.apple.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
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
        extends AppleContainerScreen<PassiveCraftingHatchMenu> {
    /** Multiplier edits are optimistic on the client until the next server status snapshot. */
    private static final int PENDING_TICKS = 40;
    /**
     * Above {@code PatternSlotRenderer}'s 300 so a pattern's own display amount cannot cover it.
     */
    private static final float BADGE_Z = 400.0F;
    private static final int MULTIPLIER_CUSTOM_COLOR = 0xFFFFC65C;
    private static final int MULTIPLIER_INHERITED_COLOR = 0xFFFFFFFF;
    private static final int SELECTION_COLOR = AppleStyle.BLUE;
    /** Widest text the right-hand readout can hold without spilling out of its inset. */
    private static final int READOUT_WIDTH = 196;
    private static final int READOUT_X = 240;
    private static final int READOUT_TOP = 204;
    private static final int READOUT_ROW_HEIGHT = 12;

    private EditBox intervalField;
    private EditBox multiplierField;
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
    private boolean layoutSlotEdit;

    public PassiveCraftingHatchScreen(
            PassiveCraftingHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 456;
        imageHeight = 356;
        inventoryLabelX = 242;
        inventoryLabelY = 224;
        titleLabelX = 9;
        titleLabelY = 7;
    }

    @Override
    protected void preInit() { imageHeight = selectedPatternSlot >= 0 ? 374 : 356; }

    @Override
    protected void initControls() {
        moveReferenceSlots();
        intervalField = numericField(244, 135, 104,
                Component.translatable("gui.useless_mod.passive_crafting.interval"));
        multiplierField = scaledAmountField(244, 175, 144,
                Component.translatable("gui.useless_mod.passive_crafting.multiplier"));
        addRenderableWidget(intervalField);
        addRenderableWidget(multiplierField);
        syncFields(true);
        updatePageControls();
    }

    /** Design 03: paged patterns and legend left, live state/editor/inventory right. */
    @Override
    protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(imageWidth, imageHeight, () -> title).icon(AppleUi.Icon.GRID, AppleStyle.BLUE));
        h.add(new AppleBadge(364, 8, 80, () -> Component.literal(statValue(0)),
                () -> menu.isFormed() ? AppleStyle.GREEN : AppleStyle.ORANGE));
        h.add(new AppleCard(12, 40, 212, 304));
        h.add(new AppleLabel(20, 48, 100, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.patterns")));
        h.add(new ApplePager(150, 44, 66, 16,
                () -> Component.literal((menu.getPage() + 1) + " / " + menu.getPageCount()),
                () -> menu.getPage() > 0, () -> page(PagedRecoverableMenu.PREVIOUS_PAGE),
                () -> menu.getPage() + 1 < menu.getPageCount(), () -> page(PagedRecoverableMenu.NEXT_PAGE)).id("pager"));
        var legend = new PassiveCraftingHatchBlockEntity.SlotState[]{
                PassiveCraftingHatchBlockEntity.SlotState.RUNNING, PassiveCraftingHatchBlockEntity.SlotState.READY,
                PassiveCraftingHatchBlockEntity.SlotState.PAUSED, PassiveCraftingHatchBlockEntity.SlotState.MISSING_INPUT,
                PassiveCraftingHatchBlockEntity.SlotState.AE_OFFLINE, PassiveCraftingHatchBlockEntity.SlotState.INVALID_PATTERN,
                PassiveCraftingHatchBlockEntity.SlotState.WAITING_OUTPUT, PassiveCraftingHatchBlockEntity.SlotState.EMPTY};
        for (int i = 0; i < legend.length; i++) {
            var state = legend[i];
            final boolean locked = i == 7;
            int x = 20 + (i % 2) * 100, y = 290 + (i / 2) * 12;
            h.add(new AppleCanvas(x, y, 96, 10, (g, mx, my, cx, cy, w, hh) -> {
                if (locked) AppleUi.icon(g, AppleUi.Icon.LOCK, cx, cy, 8, AppleStyle.TERTIARY);
                else AppleUi.fillRound(g, cx, cy + 1, 7, 7, 2, statusColor(state));
                Component label = Component.translatable(locked ? "gui.useless_mod.passive_crafting.locked_slot"
                        : "gui.useless_mod.passive_crafting.status." + state.name().toLowerCase(Locale.ROOT));
                g.drawString(font, font.plainSubstrByWidth(label.getString(), 85), cx + 10, cy, AppleStyle.SECONDARY, false);
            }));
        }

        h.add(new AppleCard(232, 40, 212, 66));
        String[] statKeys = {"structure", "slots", "next_run"};
        AppleUi.Icon[] statIcons = {AppleUi.Icon.LINK, AppleUi.Icon.GRID, AppleUi.Icon.ROTATE};
        int[] accents = {AppleStyle.GREEN, AppleStyle.BLUE, AppleStyle.ORANGE};
        for (int i = 0; i < 3; i++) {
            int idx = i, y = 44 + i * 22;
            h.add(new AppleIconTile(240, y, 14, () -> statIcons[idx], () -> accents[idx]).filled());
            h.add(new AppleLabel(260, y + 2, 66, AppleLabel.Kind.BODY,
                    () -> Component.translatable("gui.useless_mod.apple." + statKeys[idx])));
            h.add(new AppleLabel(330, y + 2, 106, AppleLabel.Kind.BODY,
                    () -> Component.literal(statValue(idx))).right());
        }
        boolean editingSlot = selectedPatternSlot >= 0;
        intervalField.visible = !editingSlot;
        h.add(new AppleCard(232, 114, 212, editingSlot ? 122 : 94));
        if (!editingSlot) {
            h.add(new AppleLabel(242, 122, 194, AppleLabel.Kind.CAPTION,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.interval")));
            h.add(new AppleStepper(240, 135, 162, 16, intervalField, () -> adjustInterval(-20), () -> adjustInterval(20))
                    .trailingButtons().<AppleStepper>enabledWhen(menu::isFormed).id("interval"));
            h.add(new ApplePushButton(407, 135, 29, 16, ApplePushButton.Kind.PRIMARY,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.apply"), this::sendSettings)
                    .<ApplePushButton>enabledWhen(menu::isFormed).id("apply"));
            h.add(new AppleLabel(242, 158, 194, AppleLabel.Kind.CAPTION,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.multiplier")));
        } else {
            h.add(new AppleLabel(242, 123, 194, AppleLabel.Kind.HEADLINE,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.slot_label_selected", selectedPatternSlot)));
            h.add(new AppleLabel(242, 137, 194, AppleLabel.Kind.FOOTNOTE,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.slot_select_hint")));
            h.add(new AppleLabel(242, 155, 194, AppleLabel.Kind.CAPTION,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.multiplier")));
        }
        h.add(new AppleStepper(240, 171, 196, 16, multiplierField, () -> adjustMultiplier(-1), () -> adjustMultiplier(1))
                .trailingButtons().<AppleStepper>enabledWhen(menu::isFormed).id("mult"));
        h.add(new AppleLabel(240, editingSlot ? 219 : 191, 196, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable(editingSlot ? "gui.useless_mod.passive_crafting.slot_wheel_hint"
                                : "gui.useless_mod.passive_crafting.max_multiplier",
                        ScaledEnergyAmount.format(menu.getMaxMultiplier()))));
        if (editingSlot) {
            h.add(new ApplePushButton(240, 195, 94, 16, ApplePushButton.Kind.TINTED,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.apply_slot"),
                    () -> clearSlotMultiplier(selectedPatternSlot))
                    .<ApplePushButton>enabledWhen(menu::isFormed).id("slotglobal"));
            h.add(new ApplePushButton(342, 195, 94, 16, ApplePushButton.Kind.SECONDARY,
                    () -> Component.translatable("gui.useless_mod.passive_crafting.apply_all"), this::clearSlotMultipliers)
                    .<ApplePushButton>enabledWhen(menu::isFormed).id("all"));
        }
        int invY = editingSlot ? 244 : 216;
        h.add(new AppleCard(232, invY, 212, 117));
        h.add(new AppleLabel(242, invY + 8, 194, AppleLabel.Kind.CAPTION, () -> playerInventoryTitle));
        h.add(slotGrid().cellSize(20));
        layoutSlotEdit = editingSlot;
    }

    private void moveReferenceSlots() {
        int invY = selectedPatternSlot >= 0 ? 244 : 216;
        for (int i = 0; i < 90 && i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(i), 23 + i % 9 * 22, 68 + i / 9 * 22);
        for (int i = 0; i < 36 && 90 + i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(90 + i), 243 + i % 9 * 22,
                    invY + (i < 27 ? 23 + i / 9 * 22 : 91));
    }

    private void refreshReferenceLayout() {
        if (host == null || layoutSlotEdit == (selectedPatternSlot >= 0)) return;
        imageHeight = selectedPatternSlot >= 0 ? 374 : 356;
        topPos = Math.max(0, (height - imageHeight) / 2);
        moveReferenceSlots();
        host = new AppleHost(imageWidth, imageHeight);
        buildApple(host);
        host.layout(width, height, leftPos, topPos);
    }

    private String statValue(int i) {
        return switch (i) {
            case 0 -> Component.translatable(menu.isFormed() ? "gui.useless_mod.passive_crafting.connected"
                    : "gui.useless_mod.passive_crafting.unformed").getString();
            case 1 -> menu.getActivePatternSlots() + " / " + menu.getConfiguredPatternSlots();
            case 2 -> menu.getCountdownTicks() + " tick";
            default -> "\u00d7" + ScaledEnergyAmount.format(menu.getMaxMultiplier());
        };
    }

    private int statColor(int i) {
        if (i == 0) return menu.isFormed() ? AppleStyle.GREEN_TEXT : AppleStyle.ORANGE_TEXT;
        return AppleStyle.LABEL;
    }

    private EditBox numericField(int x, int y, int width, Component narration) {
        EditBox field = new AppleEditBox(font, leftPos + x, topPos + y, width, 14, narration);
        field.setMaxLength(19);
        field.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        return field;
    }

    private EditBox scaledAmountField(int x, int y, int width, Component narration) {
        EditBox field = new AppleEditBox(font, leftPos + x, topPos + y, width, 14, narration);
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
        updatePageControls();
        refreshReferenceLayout();
    }

    private void clearSelection() {
        if (selectedPatternSlot < 0) {
            return;
        }
        selectedPatternSlot = -1;
        syncFields(true);
        refreshReferenceLayout();
    }

    private void updatePageControls() {
        // 翻页胶囊由 ApplePager 自行根据页码启用/禁用。
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
        if (force || (!intervalField.isFocused() && interval != lastSyncedInterval)) {
            intervalField.setValue(Integer.toString(interval));
            lastSyncedInterval = interval;
        }
        if (force || (!multiplierField.isFocused() && multiplier != lastSyncedMultiplier)) {
            setMultiplierFieldValue(multiplier);
            lastSyncedMultiplier = multiplier;
        }
    }

    private void adjustInterval(int delta) {
        long current = readNumber(intervalField, menu.getIntervalTicks());
        int value = (int) com.sorrowmist.useless.compat.forge1201.LongMath.clamp(current + delta,
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
        int interval = (int) com.sorrowmist.useless.compat.forge1201.LongMath.clamp(readNumber(intervalField, menu.getIntervalTicks()),
                (long) PassiveCraftingHatchBlockEntity.MIN_INTERVAL_TICKS,
                (long) PassiveCraftingHatchBlockEntity.MAX_INTERVAL_TICKS);
        long maximum = menu.getMaxMultiplier();
        long multiplier = ScaledEnergyAmount.parse(multiplierField.getValue(), maximum)
                .orElse(fieldTargetMultiplier());
        multiplier = com.sorrowmist.useless.compat.forge1201.LongMath.clamp(multiplier, 1L, maximum);
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
        refreshReferenceLayout();
    }

    /** Sends one slot's new batch size and echoes it locally until the next status snapshot. */
    private void requestSlotMultiplier(int patternSlot, long value) {
        if (!menu.isFormed() || patternSlot < 0) {
            return;
        }
        long clamped = com.sorrowmist.useless.compat.forge1201.LongMath.clamp(value, 1L, menu.getMaxMultiplier());
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
        long value = com.sorrowmist.useless.compat.forge1201.LongMath.clamp(next, 1L, maximum);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY != 0.0D) {
            updateMultiplierHover(mouseX, mouseY);
            if (multiplierHoverPatternSlot >= 0) {
                adjustHoveredSlotMultiplier(scrollY > 0.0D ? 1 : -1, hasShiftDown());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

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
            DesignAssets.draw(graphics, "slot_locked_40", slot.x - 2, slot.y - 2, 20, 20);
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
     * Draws the batch-size badge of one slot. Slots that merely follow the global multiplier stay
     * quiet when empty, so a configured slot is recognisable at a glance.
     *
     * <p>The badge has no backdrop and is pushed above the pattern render. A pattern slot shows its
     * own display amount in the bottom-right corner (drawn at z 300 by {@code PatternSlotRenderer}),
     * so the multiplier sits in the top-right corner at z 400: transparent, floating over the icon,
     * and with a drop shadow for contrast against bright item sprites.
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
        // 正常字号（与原版物品数量一致），不缩小。
        float scale = 1.0F;
        int textWidth = font.width(text);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, BADGE_Z);
        pose.translate(slot.x + 16 - textWidth - 1, slot.y + 1, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0,
                configured ? MULTIPLIER_CUSTOM_COLOR : MULTIPLIER_INHERITED_COLOR, true);
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
        graphics.drawString(font, lines.get(0), READOUT_X,
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
            case READY -> AppleStyle.BLUE;
            case RUNNING -> AppleStyle.GREEN;
            case PAUSED -> AppleStyle.TERTIARY;
            case WAITING_OUTPUT -> AppleStyle.TEAL;
            case MISSING_INPUT, MISSING_MOLD -> AppleStyle.ORANGE;
            case AE_OFFLINE -> AppleStyle.RED;
            case INVALID_PATTERN -> AppleStyle.PURPLE;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateMultiplierHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderExtraTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
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
