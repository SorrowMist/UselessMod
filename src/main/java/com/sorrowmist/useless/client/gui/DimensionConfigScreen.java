package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class DimensionConfigScreen extends AbstractContainerScreen<DimensionConfigMenu> {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 346;

    private EditBox layersField;
    private EditBox startYField;
    private EditBox boundaryXField;
    private EditBox boundaryZField;
    private EditBox roadWidthField;
    private PressableAE2Button layersDown;
    private PressableAE2Button layersUp;
    private PressableAE2Button startYDown;
    private PressableAE2Button startYUp;
    private PressableAE2Button boundaryXDown;
    private PressableAE2Button boundaryXUp;
    private PressableAE2Button boundaryZDown;
    private PressableAE2Button boundaryZUp;
    private PressableAE2Button roadWidthDown;
    private PressableAE2Button roadWidthUp;
    private PressableAE2Button bedrockButton;
    private PressableAE2Button bottomButton;
    private PressableAE2Button roadPresetButton;
    private PressableAE2Button centerEnabledButton;
    private PressableAE2Button applyButton;
    private PressableAE2Button teleportButton;
    private PressableAE2Button cancelButton;
    private Slot pressedSlot;
    private boolean updatingFields;

    public DimensionConfigScreen(DimensionConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelX = 16;
        inventoryLabelY = 242;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        layersField = createNumberField(106, 36, 52,
                Component.translatable("gui.useless_mod.dimension_config.layers"),
                menu.getPlatformLayers(), menu::setPlatformLayers, false);
        startYField = createNumberField(178, 36, 52,
                Component.translatable("gui.useless_mod.dimension_config.start_y"),
                menu.getPlatformStartY(), menu::setPlatformStartY, true);
        boundaryXField = createNumberField(106, 82, 52,
                Component.translatable("gui.useless_mod.dimension_config.boundary_interval_x"),
                menu.getBoundaryIntervalX(), menu::setBoundaryIntervalX, false);
        boundaryZField = createNumberField(250, 82, 52,
                Component.translatable("gui.useless_mod.dimension_config.boundary_interval_z"),
                menu.getBoundaryIntervalZ(), menu::setBoundaryIntervalZ, false);
        roadWidthField = createNumberField(106, 128, 52,
                Component.translatable("gui.useless_mod.dimension_config.road_width"),
                menu.getRoadWidth(), menu::setRoadWidth, false);

        layersDown = addStepButton(106, 54, "-", layersField, -1, 1, 256,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        layersUp = addStepButton(134, 54, "+", layersField, 1, 1, 256,
                "gui.useless_mod.dimension_config.tooltip.increase");
        startYDown = addStepButton(178, 54, "-", startYField, -1, -64, 256,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        startYUp = addStepButton(206, 54, "+", startYField, 1, -64, 256,
                "gui.useless_mod.dimension_config.tooltip.increase");

        boundaryXDown = addStepButton(106, 100, "-", boundaryXField, -1, 0, 256,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        boundaryXUp = addStepButton(134, 100, "+", boundaryXField, 1, 0, 256,
                "gui.useless_mod.dimension_config.tooltip.increase");
        boundaryZDown = addStepButton(250, 100, "-", boundaryZField, -1, 0, 256,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        boundaryZUp = addStepButton(278, 100, "+", boundaryZField, 1, 0, 256,
                "gui.useless_mod.dimension_config.tooltip.increase");
        roadWidthDown = addStepButton(106, 146, "-", roadWidthField, -1, 0, 16,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        roadWidthUp = addStepButton(134, 146, "+", roadWidthField, 1, 0, 16,
                "gui.useless_mod.dimension_config.tooltip.increase");

        bedrockButton = addButton(252, 36, 78, 16, bedrockText(),
                "gui.useless_mod.dimension_config.tooltip.bedrock", button -> {
                    menu.toggleGenerateBedrock();
                    updateToggleButtons();
                });
        bottomButton = addButton(334, 36, 72, 16, bottomText(),
                "gui.useless_mod.dimension_config.tooltip.bottom", button -> {
                    menu.toggleBedrockAtBottom();
                    updateToggleButtons();
                });
        roadPresetButton = addButton(178, 128, 78, 16, roadPresetText(),
                "gui.useless_mod.dimension_config.tooltip.road_preset", button -> {
                    menu.cycleRoadPreset();
                    updateFeatureButtons();
                });
        centerEnabledButton = addButton(106, 176, 100, 16, centerEnabledText(),
                "gui.useless_mod.dimension_config.tooltip.center_marker", button -> {
                    menu.toggleCenterMarker();
                    updateFeatureButtons();
                });

        applyButton = addButton(224, 258, 88, 18,
                Component.translatable("gui.useless_mod.dimension_config.apply"),
                "gui.useless_mod.dimension_config.tooltip.apply", button -> submit(false));
        teleportButton = addButton(318, 258, 88, 18,
                Component.translatable("gui.useless_mod.dimension_config.apply_and_teleport"),
                "gui.useless_mod.dimension_config.tooltip.apply_and_teleport", button -> submit(true));
        cancelButton = addButton(224, 280, 182, 18,
                Component.translatable("gui.useless_mod.dimension_config.cancel"),
                "gui.useless_mod.dimension_config.tooltip.cancel", button -> onClose());
        teleportButton.visible = menu.canTeleport();
        updateToggleButtons();
        updateFeatureButtons();
        updateControls();
    }

    private PressableAE2Button addStepButton(int x, int y, String text, EditBox field,
                                             int delta, int min, int max, String tooltipKey) {
        return addButton(x, y, 24, 14, Component.literal(text), tooltipKey,
                button -> adjust(field, delta, min, max));
    }

    private PressableAE2Button addButton(int x, int y, int width, int height,
                                         Component message, String tooltipKey,
                                         Button.OnPress onPress) {
        PressableAE2Button button = new PressableAE2Button(
                leftPos + x, topPos + y, width, height, message, onPress);
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return addRenderableWidget(button);
    }

    private EditBox createNumberField(int x, int y, int width, Component message,
                                      int initialValue, java.util.function.IntConsumer setter,
                                      boolean signed) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, 14, message);
        field.setMaxLength(4);
        field.setFilter(value -> value.isEmpty()
                || (signed && value.equals("-"))
                || value.matches(signed ? "-?\\d+" : "\\d+"));
        field.setValue(Integer.toString(initialValue));
        field.setResponder(value -> {
            if (updatingFields || value.isEmpty() || value.equals("-")) return;
            try {
                setter.accept(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(field);
        return field;
    }

    private Component bedrockText() {
        return Component.translatable(menu.isGenerateBedrock()
                ? "gui.useless_mod.dimension_config.bedrock_on"
                : "gui.useless_mod.dimension_config.bedrock_off");
    }

    private Component bottomText() {
        return Component.translatable(menu.isBedrockAtBottom()
                ? "gui.useless_mod.dimension_config.bottom_on"
                : "gui.useless_mod.dimension_config.bottom_off");
    }

    private Component roadPresetText() {
        return Component.translatable("gui.useless_mod.dimension_config.road_preset",
                Component.translatable("gui.useless_mod.dimension_config.road_preset."
                        + menu.getRoadPreset().name().toLowerCase(Locale.ROOT)));
    }

    private Component centerEnabledText() {
        return Component.translatable(menu.isCenterMarkerEnabled()
                ? "gui.useless_mod.dimension_config.center_marker_on"
                : "gui.useless_mod.dimension_config.center_marker_off");
    }

    private void updateToggleButtons() {
        bedrockButton.setMessage(bedrockText());
        bottomButton.setMessage(bottomText());
    }

    private void updateFeatureButtons() {
        roadPresetButton.setMessage(roadPresetText());
        centerEnabledButton.setMessage(centerEnabledText());
    }

    private void updateControls() {
        boolean complete = menu.isCompleteConfiguration();
        applyButton.active = complete;
        teleportButton.active = complete && menu.canTeleport();
    }

    private void submit(boolean teleport) {
        if (!menu.isCompleteConfiguration()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("gui.useless_mod.dimension_config.invalid"), true);
            }
            return;
        }
        menu.createConfiguration().ifPresent(config -> PacketDistributor.sendToServer(
                new DimensionConfigSubmitPacket(menu.containerId, config, teleport)));
    }

    private void adjust(EditBox field, int delta, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(field.getValue());
        } catch (NumberFormatException exception) {
            value = min;
        }
        value = Math.max(min, Math.min(max, value + delta));
        updatingFields = true;
        field.setValue(Integer.toString(value));
        updatingFields = false;
        if (field == layersField) menu.setPlatformLayers(value);
        else if (field == startYField) menu.setPlatformStartY(value);
        else if (field == boundaryXField) menu.setBoundaryIntervalX(value);
        else if (field == boundaryZField) menu.setBoundaryIntervalZ(value);
        else if (field == roadWidthField) menu.setRoadWidth(value);
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics, leftPos + 4, topPos + 18, leftPos + 94, topPos + 230);
        MachineScreenStyle.drawInset(graphics, leftPos + 98, topPos + 18, leftPos + imageWidth - 4, topPos + 230);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 34, 1, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 100, 1, 2);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 148, 1, 3, 18, 17);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 212, 1, 1);
        MachineScreenStyle.drawInset(graphics, leftPos + 4, topPos + 236,
                leftPos + 198, topPos + 334);
        MachineScreenStyle.drawInset(graphics, leftPos + 220, topPos + 236,
                leftPos + imageWidth - 4, topPos + 334);
        for (Slot slot : menu.slots) {
            if (isRoadSlot(slot)) {
                MachineScreenStyle.drawRoadSlotBackground(graphics, leftPos, topPos, slot,
                        isRoadSlotActive(slot), slot == pressedSlot);
            } else {
                MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 84) titleText = font.plainSubstrByWidth(titleText, 81) + "...";
        graphics.drawString(font, titleText, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        String target = menu.getTargetDimension().location().toString();
        if (font.width(target) > 86) target = font.plainSubstrByWidth(target, 83) + "...";
        graphics.drawString(font, target, 8, 19, MachineScreenStyle.MUTED_TEXT_COLOR, false);

        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.border_block", 36);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.fill_block", 54);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.center_block", 72);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.boundary_alternate_1", 102);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.boundary_alternate_2", 120);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_surface", 150);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_edge", 167);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_center_marking", 184);
        drawLeftLabel(graphics, "gui.useless_mod.dimension_config.center_marker", 214);

        drawLabel(graphics, "gui.useless_mod.dimension_config.layers", 106, 26);
        drawLabel(graphics, "gui.useless_mod.dimension_config.start_y", 178, 26);
        drawLabel(graphics, "gui.useless_mod.dimension_config.boundary_interval_x", 106, 72);
        drawLabel(graphics, "gui.useless_mod.dimension_config.boundary_interval_z", 250, 72);
        drawLabel(graphics, "gui.useless_mod.dimension_config.road_width", 106, 118);
        drawLabel(graphics, "gui.useless_mod.dimension_config.road_options", 178, 118);
        drawLabel(graphics, "gui.useless_mod.dimension_config.center_options", 106, 166);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
    }

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private void drawLeftLabel(GuiGraphics graphics, String key, int y) {
        Component label = Component.translatable(key);
        String text = label.getString();
        if (font.width(text) > 58) text = font.plainSubstrByWidth(text, 58);
        graphics.drawString(font, text, 34, y, MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private boolean isRoadSlot(Slot slot) {
        return slot == menu.getGhostSlot(DimensionConfigMenu.BOUNDARY_A_SLOT)
                || slot == menu.getGhostSlot(DimensionConfigMenu.BOUNDARY_B_SLOT)
                || slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_A_SLOT)
                || slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_B_SLOT)
                || slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_C_SLOT);
    }

    private boolean isRoadSlotActive(Slot slot) {
        int index = roadSlotIndex(slot);
        return index < 0 || menu.isGhostSlotActive(index);
    }

    private int roadSlotIndex(Slot slot) {
        if (slot == menu.getGhostSlot(DimensionConfigMenu.BOUNDARY_A_SLOT)) {
            return DimensionConfigMenu.BOUNDARY_A_SLOT;
        }
        if (slot == menu.getGhostSlot(DimensionConfigMenu.BOUNDARY_B_SLOT)) {
            return DimensionConfigMenu.BOUNDARY_B_SLOT;
        }
        if (slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_A_SLOT)) {
            return DimensionConfigMenu.ROAD_A_SLOT;
        }
        if (slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_B_SLOT)) {
            return DimensionConfigMenu.ROAD_B_SLOT;
        }
        if (slot == menu.getGhostSlot(DimensionConfigMenu.ROAD_C_SLOT)) {
            return DimensionConfigMenu.ROAD_C_SLOT;
        }
        return -1;
    }

    private Slot slotAt(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        for (Slot slot : menu.slots) {
            if (slot.isActive() && localX >= slot.x && localX < slot.x + 16
                    && localY >= slot.y && localY < slot.y + 16) {
                return slot;
            }
        }
        return null;
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        if (isRoadSlot(slot) && !isRoadSlotActive(slot)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            EditBox[] fields = {layersField, startYField,
                    boundaryXField, boundaryZField, roadWidthField};
            for (EditBox field : fields) {
                if (field != null && (field.keyPressed(keyCode, scanCode, modifiers)
                        || field.canConsumeInput())) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot slot = slotAt(mouseX, mouseY);
        if (isRoadSlot(slot) && !isRoadSlotActive(slot)) return true;
        pressedSlot = isRoadSlot(slot) && isRoadSlotActive(slot) ? slot : null;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) pressedSlot = null;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedSlot = null;
        layersDown.releaseVisualState();
        layersUp.releaseVisualState();
        startYDown.releaseVisualState();
        startYUp.releaseVisualState();
        boundaryXDown.releaseVisualState();
        boundaryXUp.releaseVisualState();
        boundaryZDown.releaseVisualState();
        boundaryZUp.releaseVisualState();
        roadWidthDown.releaseVisualState();
        roadWidthUp.releaseVisualState();
        bedrockButton.releaseVisualState();
        bottomButton.releaseVisualState();
        roadPresetButton.releaseVisualState();
        centerEnabledButton.releaseVisualState();
        applyButton.releaseVisualState();
        teleportButton.releaseVisualState();
        cancelButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
