package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class DimensionConfigScreen extends AbstractContainerScreen<DimensionConfigMenu> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 230;

    private EditBox layersField;
    private EditBox startYField;
    private PressableAE2Button layersDown;
    private PressableAE2Button layersUp;
    private PressableAE2Button startYDown;
    private PressableAE2Button startYUp;
    private PressableAE2Button bedrockButton;
    private PressableAE2Button bottomButton;
    private PressableAE2Button applyButton;
    private PressableAE2Button teleportButton;
    private PressableAE2Button cancelButton;
    private boolean updatingFields;

    public DimensionConfigScreen(DimensionConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelX = 16;
        inventoryLabelY = 116;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        layersField = createNumberField(106, 38, 48,
                Component.translatable("gui.useless_mod.dimension_config.layers"),
                menu.getPlatformLayers(), value -> menu.setPlatformLayers(value));
        startYField = createNumberField(202, 38, 48,
                Component.translatable("gui.useless_mod.dimension_config.start_y"),
                menu.getPlatformStartY(),
                value -> menu.setPlatformStartY(value));

        layersDown = addRenderableWidget(new PressableAE2Button(
                leftPos + 106, topPos + 54, 24, 14, Component.literal("-"),
                button -> adjust(layersField, -1, 1, 256)));
        layersUp = addRenderableWidget(new PressableAE2Button(
                leftPos + 132, topPos + 54, 24, 14, Component.literal("+"),
                button -> adjust(layersField, 1, 1, 256)));
        startYDown = addRenderableWidget(new PressableAE2Button(
                leftPos + 202, topPos + 54, 24, 14, Component.literal("-"),
                button -> adjust(startYField, -1, -64, 256)));
        startYUp = addRenderableWidget(new PressableAE2Button(
                leftPos + 228, topPos + 54, 24, 14, Component.literal("+"),
                button -> adjust(startYField, 1, -64, 256)));

        bedrockButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 106, topPos + 74, 92, 16, bedrockText(), button -> {
                    menu.toggleGenerateBedrock();
                    updateToggleButtons();
                }));
        bottomButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 202, topPos + 74, 88, 16, bottomText(), button -> {
                    menu.toggleBedrockAtBottom();
                    updateToggleButtons();
                }));
        applyButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 204, topPos + 116, 86, 18,
                Component.translatable("gui.useless_mod.dimension_config.apply"),
                button -> submit(false)));
        teleportButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 204, topPos + 138, 86, 18,
                Component.translatable("gui.useless_mod.dimension_config.apply_and_teleport"),
                button -> submit(true)));
        cancelButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 204, topPos + 160, 86, 18,
                Component.translatable("gui.useless_mod.dimension_config.cancel"),
                button -> onClose()));
        teleportButton.visible = menu.canTeleport();
        updateControls();
    }

    private EditBox createNumberField(int x, int y, int width, Component message,
                                      int initialValue, java.util.function.IntConsumer setter) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, 14, message);
        field.setMaxLength(4);
        field.setFilter(value -> value.isEmpty() || value.equals("-") || value.matches("-?\\d+"));
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

    private void updateToggleButtons() {
        bedrockButton.setMessage(bedrockText());
        bottomButton.setMessage(bottomText());
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
        else menu.setPlatformStartY(value);
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
        MachineScreenStyle.drawInset(graphics,
                leftPos + 4, topPos + 18, leftPos + 94, topPos + 105);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 98, topPos + 18, leftPos + imageWidth - 4, topPos + 105);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 34, 1, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 126, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 184, 9, 1);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 198, topPos + 110, leftPos + imageWidth - 4, topPos + 222);
        for (Slot slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 84) {
            titleText = font.plainSubstrByWidth(titleText, 81) + "...";
        }
        graphics.drawString(font, titleText, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        String target = menu.getTargetDimension().location().toString();
        if (font.width(target) > 86) target = font.plainSubstrByWidth(target, 83) + "...";
        graphics.drawString(font, target, 8, 19, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.border_block"),
                38, 36, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.fill_block"),
                38, 54, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.center_block"),
                38, 72, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.layers"),
                106, 26, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.start_y"),
                202, 26, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (layersField != null && (layersField.keyPressed(keyCode, scanCode, modifiers)
                    || layersField.canConsumeInput())) return true;
            if (startYField != null && (startYField.keyPressed(keyCode, scanCode, modifiers)
                    || startYField.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        layersDown.releaseVisualState();
        layersUp.releaseVisualState();
        startYDown.releaseVisualState();
        startYUp.releaseVisualState();
        bedrockButton.releaseVisualState();
        bottomButton.releaseVisualState();
        applyButton.releaseVisualState();
        teleportButton.releaseVisualState();
        cancelButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
