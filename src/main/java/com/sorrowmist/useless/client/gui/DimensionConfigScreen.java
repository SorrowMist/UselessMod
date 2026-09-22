package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import com.sorrowmist.useless.world.dimension.PlatformStyle;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class DimensionConfigScreen extends AbstractContainerScreen<DimensionConfigMenu> {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 346;

    /* 生成预览弹窗：不常驻占位，点「预览」按钮后以覆盖层显示，避免撑宽配置面板。 */
    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 330;
    private static final int POPUP_PAD = 10;
    private static final int VIEW_W = POPUP_WIDTH - POPUP_PAD * 2;
    private static final int TOP_VIEW_H = 170;
    private static final int SIDE_VIEW_H = 52;
    private static final int INFO_LINE_HEIGHT = 11;
    private static final int CLOSE_BUTTON_W = 80;
    private static final int CLOSE_BUTTON_H = 18;
    private static final int DIALOG_BUTTON_W = 84;
    private static final int DIALOG_BUTTON_H = 18;

    private final PlatformPreview preview =
            new PlatformPreview(VIEW_W, TOP_VIEW_H, VIEW_W, SIDE_VIEW_H);

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
    private PressableAE2Button previewButton;
    /* 覆盖层按钮只用 addWidget 进 children，不放进 renderables：它们由覆盖层自己渲染，
       否则会在 super.render() 阶段被画到底层 UI 下面。 */
    private PressableAE2Button closePreviewButton;
    private PressableAE2Button confirmImportButton;
    private PressableAE2Button cancelImportButton;
    private Slot pressedSlot;
    private boolean updatingFields;
    /** 预览弹窗是否打开；打开时独占点击与 ESC。 */
    private boolean previewOpen;
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
        // 主配置区 y>=196 是空的，正好放预览入口，不必撑宽面板。
        previewButton = addButton(106, 200, 100, 18,
                Component.translatable("gui.useless_mod.dimension_config.preview"),
                "gui.useless_mod.dimension_config.tooltip.preview",
                button -> setPreviewOpen(true));

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
        // 覆盖层按钮只进 children（addWidget），由覆盖层自行渲染；若放进 renderables，会被
        // super.render() 绘制到底层 UI 之下，导致不可见。
        closePreviewButton = addWidget(new PressableAE2Button(
                closeButtonX(), closeButtonY(), CLOSE_BUTTON_W, CLOSE_BUTTON_H,
                Component.translatable("gui.useless_mod.dimension_config.preview.close"),
                button -> setPreviewOpen(false)));
        confirmImportButton = addWidget(new PressableAE2Button(
                confirmX(), confirmY(), DIALOG_BUTTON_W, DIALOG_BUTTON_H,
                Component.translatable("gui.useless_mod.dimension_config.confirm"),
                button -> confirmImport(true)));
        cancelImportButton = addWidget(new PressableAE2Button(
                cancelImportX(), cancelImportY(), DIALOG_BUTTON_W, DIALOG_BUTTON_H,
                Component.translatable("gui.useless_mod.dimension_config.cancel_import"),
                button -> confirmImport(false)));
        syncOverlayButtons();

        teleportButton.visible = menu.canTeleport();
        updateToggleButtons();
        updateFeatureButtons();
        updateControls();
        refreshPreview();
    }

    /** 覆盖层按钮的 visible 同时决定是否响应点击，所以每次开关覆盖层都要同步。 */
    private void syncOverlayButtons() {
        closePreviewButton.visible = previewOpen;
        boolean importing = pendingImport != null;
        confirmImportButton.visible = importing;
        cancelImportButton.visible = importing;
    }

    /**
     * 把界面当前的编辑状态喂给预览。样式按传送方块所属维度取，与配置写入的目标维度一致。
     * {@code createConfiguration()} 在方块槽位不全时返回空，此时预览显示提示而不是旧图。
     */
    private void refreshPreview() {
        PlatformStyle style = UselessDimensions.styleFor(menu.getTargetDimension());
        DimensionGenerationConfig config = menu.createConfiguration()
                .map(DimensionGenerationConfig::normalized)
                .orElse(null);
        preview.refresh(style, config);
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
        syncOverlayButtons();
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
        syncOverlayButtons();
    }

    /** 将菜单当前数值同步回输入框，避免导入后界面与配置不一致。 */
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
        refreshPreview();
    }

    /** 界面移除时释放预览占用的动态贴图，避免泄漏。 */
    @Override
    public void removed() {
        super.removed();
        preview.close();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pendingImport != null) {
            // 必须先 flush：底层 UI 里的方块物品走的是 RenderBuffers 的固定缓冲
            // （solid / cutout / translucent），不主动提交的话它们会在帧末才绘制，
            // 结果盖在覆盖层上面。
            graphics.flush();
            renderImportConfirm(graphics, mouseX, mouseY, partialTick);
        } else if (previewOpen) {
            graphics.flush();
            renderPreviewPopup(graphics, mouseX, mouseY, partialTick);
        }
    }

    /* ================= 生成预览弹窗 ================= */

    private int popupX() {
        return (width - POPUP_WIDTH) / 2;
    }

    private int popupY() {
        return Math.max(4, (height - POPUP_HEIGHT) / 2);
    }

    private int closeButtonX() {
        return popupX() + POPUP_WIDTH - POPUP_PAD - CLOSE_BUTTON_W;
    }

    private int closeButtonY() {
        return popupY() + POPUP_HEIGHT - 24;
    }

    private void setPreviewOpen(boolean open) {
        previewOpen = open;
        syncOverlayButtons();
        if (open) refreshPreview();
    }

    /** 遮住整个界面并居中显示俯视示意图与侧视剖面。 */
    private void renderPreviewPopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 遮罩用 fill（排队），而 drawPanel 走 AE2 BackgroundGenerator 的 blitSprite（立即），
        // 若不先提交遮罩，它会在帧末才绘制，从而覆盖弹窗。
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.flush();
        int x = popupX();
        int y = popupY();
        MachineScreenStyle.drawPanel(graphics, x, y, POPUP_WIDTH, POPUP_HEIGHT);
        graphics.drawString(font, Component.translatable("gui.useless_mod.dimension_config.preview"),
                x + POPUP_PAD, y + 7, MachineScreenStyle.TEXT_COLOR, false);

        int viewX = x + POPUP_PAD;
        int topY = y + 30;
        int sideY = topY + TOP_VIEW_H + 14;
        int infoY = sideY + SIDE_VIEW_H + 7;

        if (!preview.isValid()) {
            drawPopupLine(graphics, Component.translatable(
                            "gui.useless_mod.dimension_config.preview.empty"),
                    viewX, topY + 4, MachineScreenStyle.MUTED_TEXT_COLOR);
            graphics.flush();
            closePreviewButton.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawPopupLabel(graphics, "gui.useless_mod.dimension_config.preview.top", viewX, topY - 10);
        drawPopupLabel(graphics, "gui.useless_mod.dimension_config.preview.side", viewX, sideY - 10);
        graphics.renderOutline(viewX - 1, topY - 1, VIEW_W + 2, TOP_VIEW_H + 2,
                MachineScreenStyle.SLOT_SHADOW_COLOR);
        graphics.renderOutline(viewX - 1, sideY - 1, VIEW_W + 2, SIDE_VIEW_H + 2,
                MachineScreenStyle.SLOT_SHADOW_COLOR);
        // 预览贴图走的是立即绘制（GuiGraphics 的 blit 内部直接 drawWithShader），
        // 先把上面这些排队的内容提交掉，否则它们会在帧末提交时压住预览。
        graphics.flush();
        preview.renderTop(graphics, viewX, topY);
        preview.renderSide(graphics, viewX, sideY);

        int line = 0;
        drawPopupLine(graphics, Component.translatable(
                        "gui.useless_mod.dimension_config.preview.period",
                        preview.periodBlocksX(), preview.periodBlocksZ()),
                viewX, infoY + line++ * INFO_LINE_HEIGHT, MachineScreenStyle.SUBTLE_TEXT_COLOR);
        drawPopupLine(graphics, renderModeText(),
                viewX, infoY + line++ * INFO_LINE_HEIGHT, MachineScreenStyle.SUBTLE_TEXT_COLOR);
        drawPopupLine(graphics, Component.translatable(
                        "gui.useless_mod.dimension_config.preview.range",
                        preview.bottomY(), preview.topY(), preview.topY() - preview.bottomY()),
                viewX, infoY + line++ * INFO_LINE_HEIGHT, MachineScreenStyle.SUBTLE_TEXT_COLOR);
        if (preview.bedrockY() != Integer.MIN_VALUE) {
            drawPopupLine(graphics, Component.translatable(
                            "gui.useless_mod.dimension_config.preview.bedrock", preview.bedrockY()),
                    viewX, infoY + line * INFO_LINE_HEIGHT, MachineScreenStyle.SUBTLE_TEXT_COLOR);
        }
        graphics.flush();
        closePreviewButton.render(graphics, mouseX, mouseY, partialTick);
        renderPreviewTooltip(graphics, mouseX, mouseY, viewX, topY);
    }

    private void drawPopupLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private void drawPopupLine(GuiGraphics graphics, Component text, int x, int y, int color) {
        String value = text.getString();
        if (font.width(value) > VIEW_W) {
            value = font.plainSubstrByWidth(value, VIEW_W - 3) + "...";
        }
        graphics.drawString(font, value, x, y, color, false);
    }

    /** 说明当前是真实材质还是降级成了色块；色块时顺便给出一个像素代表多少格。 */
    private Component renderModeText() {
        if (preview.isTextured()) {
            return Component.translatable("gui.useless_mod.dimension_config.preview.textured");
        }
        int blocksPerPixel = preview.blocksPerPixel();
        if (blocksPerPixel <= 1) {
            return Component.translatable("gui.useless_mod.dimension_config.preview.color");
        }
        return Component.translatable("gui.useless_mod.dimension_config.preview.blocks",
                blocksPerPixel);
    }

    /** 悬停俯视图时提示所指格子的坐标、方块与角色。 */
    private void renderPreviewTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                      int viewX, int viewY) {
        if (!preview.isValid()) return;
        int localX = mouseX - viewX;
        int localY = mouseY - viewY;
        if (localX < 0 || localY < 0 || localX >= VIEW_W || localY >= TOP_VIEW_H) return;
        int[] block = preview.blockAt(localX, localY);
        if (block == null) return;
        BlockState state = preview.stateAtBlock(block[0], block[1]);
        if (state == null) return;

        Component tooltip = Component.literal("X=" + block[0] + ", Z=" + block[1])
                .append(Component.literal("\n"))
                .append(state.getBlock().getName());
        String role = preview.roleKey(state);
        if (role != null) {
            tooltip = tooltip.copy().append(Component.literal("\n"))
                    .append(Component.translatable(
                            "gui.useless_mod.dimension_config.preview.role." + role));
        }
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    /** 底部状态提示，导入导出后短暂显示结果；画在标签层以免盖住物品提示。 */
    private void renderStatus(GuiGraphics graphics) {
        if (statusTicks <= 0 || statusKey == null) return;
        String text = Component.translatable(statusKey).getString();
        if (font.width(text) > 404) text = font.plainSubstrByWidth(text, 401) + "...";
        graphics.drawString(font, text, 8, 336, MachineScreenStyle.TEXT_COLOR, false);
    }

    /** 导入二次确认层：遮住整个界面，底板与按钮都用本模组统一的面板与按钮样式。 */
    private void renderImportConfirm(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.flush();
        int boxLeft = leftPos + 90;
        int boxTop = topPos + 140;
        MachineScreenStyle.drawPanel(graphics, boxLeft, boxTop, imageWidth - 180, 60);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.useless_mod.dimension_config.import_confirm"),
                leftPos + imageWidth / 2, boxTop + 12, MachineScreenStyle.TEXT_COLOR);
        graphics.flush();
        confirmImportButton.render(graphics, mouseX, mouseY, partialTick);
        cancelImportButton.render(graphics, mouseX, mouseY, partialTick);
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
        // 预览弹窗同理：ESC 只关弹窗。
        if (previewOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setPreviewOpen(false);
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
        // 覆盖层独占点击：只转发给覆盖层自己的按钮，避免穿透到底下的槽位与控件。
        if (pendingImport != null) {
            confirmImportButton.mouseClicked(mouseX, mouseY, button);
            cancelImportButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (previewOpen) {
            closePreviewButton.mouseClicked(mouseX, mouseY, button);
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
        // 覆盖层按钮不在 renderables 里，事件也要手动转发，否则按下后松手不会触发。
        if (pendingImport != null) {
            confirmImportButton.mouseReleased(mouseX, mouseY, button);
            confirmImportButton.releaseVisualState();
            cancelImportButton.mouseReleased(mouseX, mouseY, button);
            cancelImportButton.releaseVisualState();
            return true;
        }
        if (previewOpen) {
            closePreviewButton.mouseReleased(mouseX, mouseY, button);
            closePreviewButton.releaseVisualState();
            return true;
        }
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
        previewButton.releaseVisualState();
        applyButton.releaseVisualState();
        teleportButton.releaseVisualState();
        cancelButton.releaseVisualState();
        exportButton.releaseVisualState();
        importButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
