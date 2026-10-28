package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import com.sorrowmist.useless.world.dimension.PlatformStyle;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 维度配置（09）：LDLib1 Apple 控件版。槽位（9 个 Ghost 槽 + 背包）坐标、JEI 幽灵拖放、
 * 道路/边界槽禁用逻辑与原版一致；数值、开关、模式、预设、提交全部沿用原菜单方法与网络包。
 * 预览弹窗与导入确认用独立的 {@link AppleHost} 覆盖层渲染（底层界面在预览期间不渲染）。
 */
public final class DimensionConfigScreen extends AppleContainerScreen<DimensionConfigMenu> {
    private static final int PANEL_WIDTH = 476;
    private static final int PANEL_HEIGHT = 359;

    public record ActionGeometry(int cancelX, int applyX, int teleportX, int y) {}

    /** Keep every available action right-aligned, without an empty teleport-sized gap. */
    public static ActionGeometry actionGeometry(boolean canTeleport) {
        int right = PANEL_WIDTH - 24;
        int teleportX = right - 96;
        int applyX = canTeleport ? teleportX - 8 - 52 : right - 52;
        return new ActionGeometry(applyX - 8 - 52, applyX, teleportX, PANEL_HEIGHT - 12 - 22);
    }

    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 330;
    private static final int POPUP_PAD = 10;
    private static final int VIEW_W = POPUP_WIDTH - POPUP_PAD * 2;
    private static final int TOP_VIEW_H = 170;
    private static final int SIDE_VIEW_H = 52;
    private static final int INFO_LINE_HEIGHT = 11;
    private static final int CONFIRM_W = 240, CONFIRM_H = 64;
    /** 预览全屏底板：不透明，底层槽位物品走固定缓冲无法被覆盖，只能跳过底层绘制。 */
    private static final int PREVIEW_BACKDROP_COLOR = 0xFF1C1C1E;

    private final PlatformPreview preview =
            new PlatformPreview(VIEW_W, TOP_VIEW_H, VIEW_W, SIDE_VIEW_H);

    private EditBox layersField, startYField, boundaryXField, boundaryZField, roadWidthField;
    private AppleHost popupHost, confirmHost;
    private Slot pressedSlot;
    private boolean updatingFields;
    private boolean previewOpen;
    private DimensionGenerationConfig pendingImport;
    private String statusKey;
    private int statusTicks;
    /** 悬停提示：屏内坐标矩形 + 语言键 + 可见条件。 */
    private record Tip(int x, int y, int w, int h, String key, BooleanSupplier shown) {}
    private final List<Tip> tips = new ArrayList<>();

    public DimensionConfigScreen(DimensionConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelX = 20;
        inventoryLabelY = 236;
    }

    /** 自动缩放也为全尺寸预览留出高度，不在打开预览时重建正在编辑的控件。 */
    public int fitHeight() { return Math.max(imageHeight, POPUP_HEIGHT); }

    @Override
    protected void initControls() {
        layersField = createNumberField(Component.translatable("gui.useless_mod.dimension_config.layers"),
                menu.getPlatformLayers(), menu::setPlatformLayers, false);
        startYField = createNumberField(Component.translatable("gui.useless_mod.dimension_config.start_y"),
                menu.getPlatformStartY(), menu::setPlatformStartY, true);
        boundaryXField = createNumberField(Component.translatable("gui.useless_mod.dimension_config.boundary_interval_x"),
                menu.getBoundaryIntervalX(), menu::setBoundaryIntervalX, false);
        boundaryZField = createNumberField(Component.translatable("gui.useless_mod.dimension_config.boundary_interval_z"),
                menu.getBoundaryIntervalZ(), menu::setBoundaryIntervalZ, false);
        roadWidthField = createNumberField(Component.translatable("gui.useless_mod.dimension_config.road_width"),
                menu.getRoadWidth(), value -> {
                    menu.setRoadWidth(value);
                    if (menu.getRoadWidth() != value) syncFieldsFromMenu();
                }, false);
        for (int i = 0; i < 9; i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(i), 44 + i % 3 * 68, 68 + i / 3 * 49);
        for (int i = 0; i < 36 && 9 + i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(9 + i),
                    23 + i % 9 * 22, i < 27 ? 239 + i / 9 * 22 : 307);
        tips.clear();
        refreshPreview();
    }

    private static Component tr(String k) { return Component.translatable(k); }

    @Override
    protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(imageWidth, imageHeight, () -> title).icon(AppleUi.Icon.GLOBE, AppleStyle.TEAL));
        h.add(new AppleBadge(imageWidth - 248, 5, 240, () -> {
            if (statusTicks > 0 && statusKey != null)
                return Component.literal(font.plainSubstrByWidth(tr(statusKey).getString(), 228));
            return Component.literal(font.plainSubstrByWidth(menu.getTargetDimension().location().toString(), 200));
        }, () -> statusTicks > 0 && statusKey != null ? AppleStyle.BLUE : AppleStyle.SECONDARY).right().id("status"));

        h.add(new AppleCard(12, 40, 212, 168));
        h.add(new AppleCard(12, 216, 212, 117));
        h.add(new AppleCard(232, 77, 220, 132));
        h.add(new AppleCard(232, 230, 220, 22));
        h.add(new AppleCard(232, 272, 220, 22));
        h.add(new AppleLabel(22, 48, 194, AppleLabel.Kind.CAPTION, () -> tr("gui.useless_mod.apple.blocks")));
        h.add(new AppleLabel(234, 65, 218, AppleLabel.Kind.CAPTION, () -> tr("gui.useless_mod.apple.platform")));
        h.add(new AppleLabel(234, 217, 218, AppleLabel.Kind.CAPTION, () -> tr("gui.useless_mod.dimension_config.road_options")));
        h.add(new AppleLabel(234, 260, 218, AppleLabel.Kind.CAPTION, () -> tr("gui.useless_mod.dimension_config.center_options")));
        h.add(new AppleLabel(22, 224, 194, AppleLabel.Kind.CAPTION, () -> playerInventoryTitle));
        String[] names = {"border_block", "fill_block", "preview.role.center", "boundary_alternate_1",
                "boundary_alternate_2", "road_surface", "road_edge", "road_center_marking", "center_marker"};
        for (int i = 0; i < names.length; i++) {
            int index = i, x = 22 + (i % 3) * 68, y = 90 + (i / 3) * 49;
            h.add(new AppleLabel(x, y, 60, AppleLabel.Kind.FOOTNOTE,
                    () -> Component.literal(font.plainSubstrByWidth(tr("gui.useless_mod.dimension_config." + names[index]).getString(), 60))).center());
            h.add(new AppleCanvas(x + 20, y - 28, 20, 20, (g, mx, my, cx, cy, w, hh) -> {
                if (!menu.isGhostSlotActive(index)) {
                    AppleUi.fillRound(g, cx, cy, w, hh, 4, AppleStyle.SLOT_LOCKED);
                    AppleUi.icon(g, AppleUi.Icon.LOCK, cx + 6, cy + 6, 8, AppleStyle.TERTIARY);
                }
            }));
        }
        h.add(slotGrid().cellSize(20).disabledWhen(s -> isRoadSlot(s) && !isRoadSlotActive(s)));

        // 平台卡
        label(h, 240, 84, 104, "gui.useless_mod.dimension_config.layers", () -> true);
        label(h, 240, 106, 104, "gui.useless_mod.dimension_config.start_y", () -> true);
        stepper(h, 356, 80, layersField, 1, 256, "layers", () -> true);
        stepper(h, 356, 102, startYField, -64, 256, "start_y", () -> true);
        h.add(new AppleLabel(240, 172, 170, AppleLabel.Kind.BODY, () -> Component.literal(switchLabel(bedrockText()))));
        h.add(new AppleLabel(240, 194, 170, AppleLabel.Kind.BODY, () -> Component.literal(switchLabel(bottomText()))));
        h.add(new AppleSwitch(422, 169, 22, 13, menu::isGenerateBedrock, menu::toggleGenerateBedrock).id("bedrock"));
        h.add(new AppleSwitch(422, 191, 22, 13, menu::isBedrockAtBottom, menu::toggleBedrockAtBottom).id("bottom"));
        tip(422, 169, 22, 13, "gui.useless_mod.dimension_config.tooltip.bedrock", () -> true);
        tip(422, 191, 22, 13, "gui.useless_mod.dimension_config.tooltip.bottom", () -> true);

        // 布局卡：模式分段 + 边界间隔/道路宽度
        h.add(new AppleSegmented(398, 40, 54, 18,
                () -> List.of(tr("gui.useless_mod.dimension_config.mode.road"), tr("gui.useless_mod.dimension_config.mode.multi")),
                () -> menu.getMode() == DimensionGenerationConfig.Mode.MULTI ? 1 : 0,
                index -> {
                    int current = menu.getMode() == DimensionGenerationConfig.Mode.MULTI ? 1 : 0;
                    if (index == current) return;
                    menu.cycleMode();
                    syncFieldsFromMenu();
                }).id("mode"));
        tip(398, 40, 54, 18, "gui.useless_mod.dimension_config.tooltip.mode", () -> true);
        label(h, 240, 128, 104, "gui.useless_mod.dimension_config.boundary_interval_x", menu::isBoundaryIntervalVisible);
        label(h, 240, 150, 104, "gui.useless_mod.dimension_config.boundary_interval_z", menu::isBoundaryIntervalVisible);
        label(h, 240, 237, 104, "gui.useless_mod.dimension_config.road_width", menu::isRoadWidthVisible);
        stepper(h, 356, 124, boundaryXField, 0, 256, "boundary_x", menu::isBoundaryIntervalVisible);
        stepper(h, 356, 146, boundaryZField, 0, 256, "boundary_z", menu::isBoundaryIntervalVisible);
        stepper(h, 356, 233, roadWidthField, DimensionConfigMenu.MIN_ROAD_WIDTH, DimensionConfigMenu.MAX_ROAD_WIDTH,
                "road_width", menu::isRoadWidthVisible);

        // 中心标记卡
        h.add(new AppleLabel(240, 279, 170, AppleLabel.Kind.BODY, () -> menu.isCenterMarkerVisible()
                ? Component.literal(switchLabel(centerEnabledText()))
                : tr("gui.useless_mod.apple.multi_unavailable"))
                .color(AppleStyle.LABEL));
        h.add(new AppleSwitch(422, 277, 22, 13, menu::isCenterMarkerEnabled, menu::toggleCenterMarker)
                .<AppleSwitch>shownWhen(menu::isCenterMarkerVisible).id("center_marker"));
        tip(422, 277, 22, 13, "gui.useless_mod.dimension_config.tooltip.center_marker", menu::isCenterMarkerVisible);

        // 预设卡
        button(h, 232, 302, 44, 16, ApplePushButton.Kind.TINTED, "gui.useless_mod.dimension_config.preview",
                "preview", () -> setPreviewOpen(true), () -> true);
        button(h, 282, 302, 44, 16, ApplePushButton.Kind.SECONDARY, "gui.useless_mod.dimension_config.export",
                "export", this::exportPreset, () -> true);
        button(h, 332, 302, 44, 16, ApplePushButton.Kind.SECONDARY, "gui.useless_mod.dimension_config.import",
                "import", this::importPreset, () -> true);

        // Bottom-right actions; reserve teleport space only when that action exists.
        ActionGeometry actions = actionGeometry(menu.canTeleport());
        button(h, actions.applyX(), actions.y(), 52, 22, ApplePushButton.Kind.PRIMARY, "gui.useless_mod.dimension_config.apply",
                "apply", () -> submit(false), menu::isCompleteConfiguration);
        ApplePushButton tp = button(h, actions.teleportX(), actions.y(), 96, 22, ApplePushButton.Kind.TINTED,
                "gui.useless_mod.dimension_config.apply_and_teleport", "apply_and_teleport", () -> submit(true),
                () -> menu.isCompleteConfiguration() && menu.canTeleport());
        tp.shownWhen(menu::canTeleport);
        button(h, actions.cancelX(), actions.y(), 52, 22, ApplePushButton.Kind.SECONDARY, "gui.useless_mod.dimension_config.cancel",
                "cancel", this::onClose, () -> true);
    }

    @Override
    protected void afterBuild() {
        // 预览弹窗
        popupHost = new AppleHost(POPUP_WIDTH, POPUP_HEIGHT);
        popupHost.add(new AppleWindow(POPUP_WIDTH, POPUP_HEIGHT, () -> tr("gui.useless_mod.dimension_config.preview")));
        popupHost.add(new AppleCanvas(0, 28, POPUP_WIDTH, POPUP_HEIGHT - 56, (g, mx, my, x, y, w, hh) -> renderPreviewBody(g, x, y)));
        popupHost.add(new ApplePushButton(POPUP_WIDTH - POPUP_PAD - 80, POPUP_HEIGHT - 24, 80, 18,
                ApplePushButton.Kind.PRIMARY, () -> tr("gui.useless_mod.dimension_config.preview.close"),
                () -> setPreviewOpen(false)).id("preview_close"));
        // 导入确认
        confirmHost = new AppleHost(CONFIRM_W, CONFIRM_H);
        confirmHost.add(new AppleCard(0, 0, CONFIRM_W, CONFIRM_H));
        confirmHost.add(new AppleLabel(8, 12, CONFIRM_W - 16, AppleLabel.Kind.HEADLINE,
                () -> tr("gui.useless_mod.dimension_config.import_confirm")).center());
        confirmHost.add(new ApplePushButton(CONFIRM_W / 2 - 90, 32, 84, 18, ApplePushButton.Kind.PRIMARY,
                () -> tr("gui.useless_mod.dimension_config.confirm"), () -> confirmImport(true)).id("import_confirm"));
        confirmHost.add(new ApplePushButton(CONFIRM_W / 2 + 6, 32, 84, 18, ApplePushButton.Kind.SECONDARY,
                () -> tr("gui.useless_mod.dimension_config.cancel_import"), () -> confirmImport(false)).id("import_cancel"));
    }

    /** Covered ghost slots are not editable, including through JEI's independent event path. */
    public boolean acceptsGhostIngredients() {
        return !previewOpen && pendingImport == null && host != null && !host.isPopoverOpen();
    }

    /** 当前覆盖层宿主（预览/导入确认），无覆盖层时为 null。 */
    public AppleHost appleOverlayHost() {
        return previewOpen ? popupHost : pendingImport != null ? confirmHost : null;
    }

    private void head(AppleHost h, int x, int y, AppleUi.Icon icon, int color, String key) {
        h.add(new AppleIconTile(x, y, 12, () -> icon, () -> color).filled());
        h.add(new AppleLabel(x + 16, y + 2, 100, AppleLabel.Kind.HEADLINE, () -> tr(key)));
    }

    private void group(AppleHost h, int y, String key, BooleanSupplier shown) {
        h.add(new AppleLabel(12, y, 132, AppleLabel.Kind.FOOTNOTE, () -> tr(key))
                .color(AppleStyle.TERTIARY).<AppleLabel>shownWhen(shown));
    }

    private void left(AppleHost h, int y, String key, BooleanSupplier shown) {
        h.add(new AppleLabel(33, y, 112, AppleLabel.Kind.BODY,
                () -> Component.literal(font.plainSubstrByWidth(tr(key).getString(), 112)))
                .color(AppleStyle.SECONDARY).<AppleLabel>shownWhen(shown));
    }

    private void label(AppleHost h, int x, int y, int w, String key, BooleanSupplier shown) {
        h.add(new AppleLabel(x, y, w, AppleLabel.Kind.BODY,
                () -> Component.literal(font.plainSubstrByWidth(tr(key).getString(), w)))
                .color(AppleStyle.SECONDARY).<AppleLabel>shownWhen(shown));
    }

    private void stepper(AppleHost h, int x, int y, EditBox field, int min, int max, String id, BooleanSupplier shown) {
        h.add(new AppleStepper(x, y, 88, 16, field, () -> adjust(field, -1, min, max), () -> adjust(field, 1, min, max))
                .trailingButtons().<AppleStepper>shownWhen(shown).id(id));
        tip(x + 52, y, 18, 16, "gui.useless_mod.dimension_config.tooltip.decrease", shown);
        tip(x + 70, y, 18, 16, "gui.useless_mod.dimension_config.tooltip.increase", shown);
    }

    private ApplePushButton button(AppleHost h, int x, int y, int w, int hh, ApplePushButton.Kind kind, String key,
                                   String id, Runnable action, BooleanSupplier enabled) {
        ApplePushButton b = h.add(new ApplePushButton(x, y, w, hh, kind, () -> tr(key), action)
                .<ApplePushButton>enabledWhen(enabled).id(id));
        tip(x, y, w, hh, "gui.useless_mod.dimension_config.tooltip." + id, () -> b.isVisible());
        return b;
    }

    private void tip(int x, int y, int w, int h, String key, BooleanSupplier shown) {
        tips.add(new Tip(x, y, w, h, key, shown));
    }

    @Override
    protected void renderExtraTooltips(GuiGraphics g, int mx, int my) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) return;
        int lx = mx - leftPos, ly = my - topPos;
        for (Tip t : tips) {
            if (!t.shown().getAsBoolean()) continue;
            if (lx >= t.x() && lx < t.x() + t.w() && ly >= t.y() && ly < t.y() + t.h()) {
                g.renderTooltip(font, font.split(tr(t.key()), 200), mx, my);
                return;
            }
        }
    }

    private void refreshPreview() {
        PlatformStyle style = UselessDimensions.styleFor(menu.getTargetDimension());
        DimensionGenerationConfig config = menu.createConfiguration()
                .map(DimensionGenerationConfig::normalized)
                .orElse(null);
        preview.refresh(style, config);
    }

    /** "生成基岩层：开" → "生成基岩层"：开关本身已表达开/关。 */
    private static String switchLabel(Component message) {
        String s = message.getString();
        int i = s.indexOf('：');
        if (i < 0) i = s.indexOf(':');
        return i > 0 ? s.substring(0, i).trim() : s;
    }

    private EditBox createNumberField(Component message, int initialValue,
                                      java.util.function.IntConsumer setter, boolean signed) {
        EditBox field = new AppleEditBox(font, leftPos, topPos, 30, 10, message);
        field.setMaxLength(4);
        field.setTextColor(AppleStyle.LABEL);
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
        return tr(menu.isGenerateBedrock() ? "gui.useless_mod.dimension_config.bedrock_on"
                : "gui.useless_mod.dimension_config.bedrock_off");
    }

    private Component bottomText() {
        return tr(menu.isBedrockAtBottom() ? "gui.useless_mod.dimension_config.bottom_on"
                : "gui.useless_mod.dimension_config.bottom_off");
    }

    private Component centerEnabledText() {
        return tr(menu.isCenterMarkerEnabled() ? "gui.useless_mod.dimension_config.center_marker_on"
                : "gui.useless_mod.dimension_config.center_marker_off");
    }

    private void submit(boolean teleport) {
        if (!menu.isCompleteConfiguration()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(tr("gui.useless_mod.dimension_config.invalid"), true);
            }
            return;
        }
        menu.createConfiguration().ifPresent(config -> PacketDistributor.sendToServer(
                new DimensionConfigSubmitPacket(menu.containerId, config, teleport)));
    }

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

    private void confirmImport(boolean accepted) {
        if (accepted && pendingImport != null) {
            menu.applyPreset(pendingImport);
            syncFieldsFromMenu();
            setStatus("gui.useless_mod.dimension_config.imported");
        }
        pendingImport = null;
    }

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
    }

    @Override
    protected void tickControls() {
        if (statusTicks > 0) statusTicks--;
        refreshPreview();
        if (popupHost != null) popupHost.tick();
        if (confirmHost != null) confirmHost.tick();
    }

    @Override
    public void removed() {
        super.removed();
        preview.close();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (previewOpen) {
            graphics.fill(0, 0, width, height, PREVIEW_BACKDROP_COLOR);
            graphics.flush();
            int x = popupX(), y = popupY();
            popupHost.layout(width, height, x, y);
            popupHost.render(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
            renderPreviewTooltip(graphics, mouseX, mouseY, x + POPUP_PAD, y + 36);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pendingImport != null) {
            graphics.flush();
            graphics.fill(0, 0, width, height, 0x66000000);
            graphics.flush();
            confirmHost.layout(width, height, leftPos + (imageWidth - CONFIRM_W) / 2, topPos + 138);
            confirmHost.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /* ================= 生成预览弹窗 ================= */

    private int popupX() { return (width - POPUP_WIDTH) / 2; }

    private int popupY() { return Math.max(4, (height - POPUP_HEIGHT) / 2); }

    private void setPreviewOpen(boolean open) {
        previewOpen = open;
        if (open) refreshPreview();
    }

    /** 弹窗主体（屏幕坐标，(x,y) 为标题栏下沿）：俯视图、侧视剖面与说明行。 */
    private void renderPreviewBody(GuiGraphics graphics, int x, int y0) {
        int viewX = x + POPUP_PAD;
        int topY = y0 + 14;
        int sideY = topY + TOP_VIEW_H + 14;
        int infoY = sideY + SIDE_VIEW_H + 7;
        if (!preview.isValid()) {
            drawPopupLine(graphics, tr("gui.useless_mod.dimension_config.preview.empty"), viewX, topY + 4, AppleStyle.SECONDARY);
            return;
        }
        drawPopupLine(graphics, tr("gui.useless_mod.dimension_config.preview.top"), viewX, topY - 10, AppleStyle.TERTIARY);
        drawPopupLine(graphics, tr("gui.useless_mod.dimension_config.preview.side"), viewX, sideY - 10, AppleStyle.TERTIARY);
        graphics.renderOutline(viewX - 1, topY - 1, VIEW_W + 2, TOP_VIEW_H + 2, AppleStyle.SLOT_EDGE);
        graphics.renderOutline(viewX - 1, sideY - 1, VIEW_W + 2, SIDE_VIEW_H + 2, AppleStyle.SLOT_EDGE);
        graphics.flush();
        preview.renderTop(graphics, viewX, topY);
        preview.renderSide(graphics, viewX, sideY);
        int line = 0;
        drawPopupLine(graphics, Component.translatable("gui.useless_mod.dimension_config.preview.period",
                preview.periodBlocksX(), preview.periodBlocksZ()), viewX, infoY + line++ * INFO_LINE_HEIGHT, AppleStyle.SECONDARY);
        drawPopupLine(graphics, renderModeText(), viewX, infoY + line++ * INFO_LINE_HEIGHT, AppleStyle.SECONDARY);
        drawPopupLine(graphics, Component.translatable("gui.useless_mod.dimension_config.preview.range",
                        preview.bottomY(), preview.topY(), preview.topY() - preview.bottomY()),
                viewX, infoY + line++ * INFO_LINE_HEIGHT, AppleStyle.SECONDARY);
        if (preview.bedrockY() != Integer.MIN_VALUE) {
            drawPopupLine(graphics, Component.translatable("gui.useless_mod.dimension_config.preview.bedrock",
                    preview.bedrockY()), viewX, infoY + line * INFO_LINE_HEIGHT, AppleStyle.SECONDARY);
        }
    }

    private void drawPopupLine(GuiGraphics graphics, Component text, int x, int y, int color) {
        String value = text.getString();
        if (font.width(value) > VIEW_W) value = font.plainSubstrByWidth(value, VIEW_W - 3) + "...";
        graphics.drawString(font, value, x, y, color, false);
    }

    private Component renderModeText() {
        if (preview.isTextured()) return tr("gui.useless_mod.dimension_config.preview.textured");
        int blocksPerPixel = preview.blocksPerPixel();
        if (blocksPerPixel <= 1) return tr("gui.useless_mod.dimension_config.preview.color");
        return Component.translatable("gui.useless_mod.dimension_config.preview.blocks", blocksPerPixel);
    }

    private void renderPreviewTooltip(GuiGraphics graphics, int mouseX, int mouseY, int viewX, int viewY) {
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
                    .append(tr("gui.useless_mod.dimension_config.preview.role." + role));
        }
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    /* ================= 槽位 ================= */

    private boolean isRoadSlot(Slot slot) {
        return roadSlotIndex(slot) >= 0;
    }

    private boolean isRoadSlotActive(Slot slot) {
        int index = roadSlotIndex(slot);
        return index < 0 || menu.isGhostSlotActive(index);
    }

    private int roadSlotIndex(Slot slot) {
        int[] idx = {DimensionConfigMenu.BOUNDARY_A_SLOT, DimensionConfigMenu.BOUNDARY_B_SLOT,
                DimensionConfigMenu.ROAD_A_SLOT, DimensionConfigMenu.ROAD_B_SLOT, DimensionConfigMenu.ROAD_C_SLOT};
        for (int i : idx) if (slot == menu.getGhostSlot(i)) return i;
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
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66FFFFFF);
        } else if (slot == pressedSlot) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x22007AFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pendingImport != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            confirmImport(false);
            return true;
        }
        if (previewOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) setPreviewOpen(false);
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            EditBox[] fields = {layersField, startYField, boundaryXField, boundaryZField, roadWidthField};
            for (EditBox field : fields) {
                if (field != null && (field.keyPressed(keyCode, scanCode, modifiers)
                        || field.canConsumeInput())) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingImport != null) {
            confirmHost.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (previewOpen) {
            popupHost.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null || !slot.isActive()) return super.mouseClicked(mouseX, mouseY, button);
        if (isRoadSlot(slot) && !isRoadSlotActive(slot)) return true;
        pressedSlot = isRoadSlot(slot) ? slot : null;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) pressedSlot = null;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pendingImport != null) { confirmHost.mouseReleased(mouseX, mouseY, button); return true; }
        if (previewOpen) { popupHost.mouseReleased(mouseX, mouseY, button); return true; }
        pressedSlot = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
