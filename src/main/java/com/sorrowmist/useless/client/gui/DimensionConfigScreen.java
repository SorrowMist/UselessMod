package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
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
    private PressableAE2Button modeButton;
    private PressableAE2Button centerEnabledButton;
    private PressableAE2Button applyButton;
    private PressableAE2Button teleportButton;
    private PressableAE2Button cancelButton;
    private PressableAE2Button exportButton;
    private PressableAE2Button importButton;
    private Slot pressedSlot;
    private boolean updatingFields;
    /** 待确认导入的预设；非空时界面显示二次确认层。 */
    private DimensionGenerationConfig pendingImport;
    /** 状态提示的语言键与剩余显示时间（tick）。 */
    private String statusKey;
    private int statusTicks;

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
        roadWidthField = createNumberField(250, 128, 52,
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
        roadWidthDown = addStepButton(250, 146, "-", roadWidthField, -1, 0, 16,
                "gui.useless_mod.dimension_config.tooltip.decrease");
        roadWidthUp = addStepButton(278, 146, "+", roadWidthField, 1, 0, 16,
                "gui.useless_mod.dimension_config.tooltip.increase");

        bedrockButton = addButton(236, 36, 84, 16, bedrockText(),
                "gui.useless_mod.dimension_config.tooltip.bedrock", button -> {
                    menu.toggleGenerateBedrock();
                    updateToggleButtons();
                });
        bottomButton = addButton(326, 36, 84, 16, bottomText(),
                "gui.useless_mod.dimension_config.tooltip.bottom", button -> {
                    menu.toggleBedrockAtBottom();
                    updateToggleButtons();
                });
        
        modeButton = addButton(106, 128, 100, 16, modeText(),
                "gui.useless_mod.dimension_config.tooltip.mode", button -> {
                    menu.cycleMode();
                    updateFeatureButtons();
                    updateControls();
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
        // 右下角：把当前配置导出到剪贴板，或从剪贴板导入一份预设。
        exportButton = addButton(224, 302, 88, 18,
                Component.translatable("gui.useless_mod.dimension_config.export"),
                "gui.useless_mod.dimension_config.tooltip.export", button -> exportPreset());
        importButton = addButton(318, 302, 88, 18,
                Component.translatable("gui.useless_mod.dimension_config.import"),
                "gui.useless_mod.dimension_config.tooltip.import", button -> importPreset());
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

    /** 模式按钮文本：马路 / 多联。标签 "模式" 已单独绘制，按钮内不再重复。 */
    private Component modeText() {
        String key = menu.getMode() == DimensionGenerationConfig.Mode.MULTI
                ? "multi" : "road";
        return Component.translatable("gui.useless_mod.dimension_config.mode." + key);
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
        modeButton.setMessage(modeText());
        centerEnabledButton.setMessage(centerEnabledText());
        // 边界间隔在多联模式下表示合并尺寸，因此始终保留；道路宽度只在马路模式出现。
        boolean boundary = menu.isBoundaryIntervalVisible();
        boolean roadWidthVisible = menu.isRoadWidthVisible();
        boundaryXField.visible = boundary;
        boundaryZField.visible = boundary;
        boundaryXDown.visible = boundary;
        boundaryXUp.visible = boundary;
        boundaryZDown.visible = boundary;
        boundaryZUp.visible = boundary;
        roadWidthField.visible = roadWidthVisible;
        roadWidthDown.visible = roadWidthVisible;
        roadWidthUp.visible = roadWidthVisible;
        centerEnabledButton.visible = menu.isCenterMarkerVisible();
        centerEnabledButton.active = menu.isCenterMarkerVisible();
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

    /** 把当前界面上的配置序列化后写入系统剪贴板。 */
    private void exportPreset() {
        DimensionGenerationConfig config = menu.createConfiguration().orElse(null);
        if (config == null) {
            setStatus("gui.useless_mod.dimension_config.export_invalid");
            return;
        }
        String text = config.toPresetJson();
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > DimensionGenerationConfig.MAX_PRESET_BYTES) {
            setStatus("gui.useless_mod.dimension_config.error.limit");
            return;
        }
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(text);
        setStatus("gui.useless_mod.dimension_config.exported");
    }

    /** 读取剪贴板并解析预设；成功时先进入二次确认，避免误覆盖当前配置。 */
    private void importPreset() {
        if (minecraft == null) return;
        String text = minecraft.keyboardHandler.getClipboard();
        try {
            pendingImport = DimensionGenerationConfig.fromPresetJson(text);
            setStatus("gui.useless_mod.dimension_config.import_confirm");
        } catch (DimensionGenerationConfig.PresetException exception) {
            setStatus(presetErrorKey(exception.error()));
        }
    }

    /** 确认导入：套用到界面编辑状态，仍需点「应用」才会保存到世界。 */
    private void confirmImport(boolean accepted) {
        if (accepted && pendingImport != null) {
            menu.applyPreset(pendingImport);
            syncFieldsFromMenu();
            updateToggleButtons();
            updateFeatureButtons();
            updateControls();
            setStatus("gui.useless_mod.dimension_config.imported");
        }
        pendingImport = null;
    }

    /** 把菜单当前的数值同步回输入框，导入后界面才不会与配置脱节。 */
    private void syncFieldsFromMenu() {
        updatingFields = true;
        layersField.setValue(Integer.toString(menu.getPlatformLayers()));
        startYField.setValue(Integer.toString(menu.getPlatformStartY()));
        boundaryXField.setValue(Integer.toString(menu.getBoundaryIntervalX()));
        boundaryZField.setValue(Integer.toString(menu.getBoundaryIntervalZ()));
        roadWidthField.setValue(Integer.toString(menu.getRoadWidth()));
        updatingFields = false;
    }

    private static String presetErrorKey(DimensionGenerationConfig.PresetError error) {
        return switch (error) {
            case INVALID_TEXT -> "gui.useless_mod.dimension_config.error.invalid_text";
            case UNSUPPORTED_VERSION -> "gui.useless_mod.dimension_config.error.unsupported_version";
            case INVALID_STRUCTURE -> "gui.useless_mod.dimension_config.error.invalid_structure";
            case BLOCKED_BLOCK -> "gui.useless_mod.dimension_config.error.blocked_block";
            case LIMIT -> "gui.useless_mod.dimension_config.error.limit";
        };
    }

    private void setStatus(String key) {
        statusKey = key;
        statusTicks = 100;
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
        if (statusTicks > 0) statusTicks--;
        updateControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pendingImport != null) renderImportConfirm(graphics, mouseX, mouseY);
    }

    /** 底部状态提示，导入导出后短暂显示结果；画在标签层以免盖住物品提示。 */
    private void renderStatus(GuiGraphics graphics) {
        if (statusTicks <= 0 || statusKey == null) return;
        String text = Component.translatable(statusKey).getString();
        if (font.width(text) > 404) text = font.plainSubstrByWidth(text, 401) + "...";
        graphics.drawString(font, text, 8, 336, MachineScreenStyle.TEXT_COLOR, false);
    }

    /** 导入二次确认层：遮住面板并给出确认/取消两个按钮。 */
    private void renderImportConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xAA000000);
        int boxLeft = leftPos + 90;
        int boxTop = topPos + 140;
        int boxRight = leftPos + imageWidth - 90;
        int boxBottom = topPos + 200;
        graphics.fill(boxLeft, boxTop, boxRight, boxBottom, 0xFF1B1B1B);
        graphics.renderOutline(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop,
                MachineScreenStyle.TEXT_COLOR);
        String prompt = Component.translatable(
                "gui.useless_mod.dimension_config.import_confirm").getString();
        graphics.drawCenteredString(font, prompt, leftPos + imageWidth / 2,
                boxTop + 14, MachineScreenStyle.TEXT_COLOR);
        boolean overConfirm = isInside(mouseX, mouseY, confirmX(), confirmY());
        boolean overCancel = isInside(mouseX, mouseY, cancelImportX(), cancelImportY());
        drawConfirmButton(graphics, confirmX(), confirmY(),
                "gui.useless_mod.dimension_config.confirm", overConfirm);
        drawConfirmButton(graphics, cancelImportX(), cancelImportY(),
                "gui.useless_mod.dimension_config.cancel_import", overCancel);
    }

    private void drawConfirmButton(GuiGraphics graphics, int x, int y, String key, boolean hovered) {
        graphics.fill(x, y, x + 84, y + 18, hovered ? 0xFF3A3A3A : 0xFF262626);
        graphics.renderOutline(x, y, 84, 18, MachineScreenStyle.TEXT_COLOR);
        graphics.drawCenteredString(font, Component.translatable(key).getString(),
                x + 42, y + 5, MachineScreenStyle.TEXT_COLOR);
    }

    private int confirmX() {
        return leftPos + imageWidth / 2 - 90;
    }

    private int confirmY() {
        return topPos + 168;
    }

    private int cancelImportX() {
        return leftPos + imageWidth / 2 + 6;
    }

    private int cancelImportY() {
        return topPos + 168;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 84 && mouseY >= y && mouseY < y + 18;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics, leftPos + 4, topPos + 18, leftPos + 94, topPos + 230);
        MachineScreenStyle.drawInset(graphics, leftPos + 98, topPos + 18, leftPos + imageWidth - 4, topPos + 230);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 34, 1, 3);
        // 多联模式隐藏边界交替、道路主体与中心标记槽位，连同其槽位底板一起隐藏。
        if (menu.isBoundarySlotVisible()) {
            MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 100, 1, 2);
        }
        if (menu.isRoadPatternSelected()) {
            MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 148, 1, 3, 18, 17);
        }
        if (menu.isCenterMarkerVisible()) {
            MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 16, 212, 1, 1);
        }
        MachineScreenStyle.drawInset(graphics, leftPos + 4, topPos + 236,
                leftPos + 198, topPos + 334);
        MachineScreenStyle.drawInset(graphics, leftPos + 220, topPos + 236,
                leftPos + imageWidth - 4, topPos + 334);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
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
        boolean boundarySlot = menu.isBoundarySlotVisible();
        boolean centerMarkerVisible = menu.isCenterMarkerVisible();
        boolean roadPattern = menu.isRoadPatternSelected();
        if (boundarySlot) {
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.boundary_alternate_1", 102);
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.boundary_alternate_2", 120);
        }
        if (roadPattern) {
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_surface", 150);
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_edge", 167);
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.road_center_marking", 184);
        }
        if (centerMarkerVisible) {
            drawLeftLabel(graphics, "gui.useless_mod.dimension_config.center_marker", 214);
        }

        drawLabel(graphics, "gui.useless_mod.dimension_config.layers", 106, 26);
        drawLabel(graphics, "gui.useless_mod.dimension_config.start_y", 178, 26);
        drawLabel(graphics, "gui.useless_mod.dimension_config.boundary_interval_x", 106, 72);
        drawLabel(graphics, "gui.useless_mod.dimension_config.boundary_interval_z", 250, 72);
        drawLabel(graphics, "gui.useless_mod.dimension_config.mode", 106, 118);
        if (roadPattern) {
            drawLabel(graphics, "gui.useless_mod.dimension_config.road_width", 250, 118);
        }
        if (centerMarkerVisible) {
            drawLabel(graphics, "gui.useless_mod.dimension_config.center_options", 106, 166);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        renderStatus(graphics);
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
            if (slot instanceof DimensionConfigMenu.GhostSlot ghost && ghost.isHidden()) continue;
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
        // 确认层存在时，ESC 只取消导入，不关闭整个界面。
        if (pendingImport != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            confirmImport(false);
            return true;
        }
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
        // 二次确认层存在时独占点击，避免误触底下的槽位与按钮。
        if (pendingImport != null) {
            if (isInside(mouseX, mouseY, confirmX(), confirmY())) {
                confirmImport(true);
            } else if (isInside(mouseX, mouseY, cancelImportX(), cancelImportY())) {
                confirmImport(false);
            }
            return true;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null || !slot.isActive()) return super.mouseClicked(mouseX, mouseY, button);
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
        modeButton.releaseVisualState();
        centerEnabledButton.releaseVisualState();
        applyButton.releaseVisualState();
        teleportButton.releaseVisualState();
        cancelButton.releaseVisualState();
        exportButton.releaseVisualState();
        importButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
