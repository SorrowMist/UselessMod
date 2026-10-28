package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.AETaskProgressRequestPacket;
import com.sorrowmist.useless.network.MultiblockAlloyFurnaceEnergyLimitPacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import com.sorrowmist.useless.client.gui.apple.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MultiblockAlloyFurnaceScreen extends AppleContainerScreen<MultiblockAlloyFurnaceMenu> {
    private static final int W = 596;
    /** Content-driven height; editing help adds 33 GUI pixels without recreating the EditBox. */
    record Geometry(int height, int taskCardY, int taskCardHeight, int taskListY, int controlsY) {}
    static Geometry referenceGeometry(boolean hasTasks, boolean editing) {
        int extra = editing ? 33 : 0;
        return new Geometry((hasTasks ? 336 : 318) + extra, 188 + extra,
                hasTasks ? 112 : 94, 216 + extra, (hasTasks ? 308 : 290) + extra);
    }
    private Geometry geometry = referenceGeometry(false, false);
    /** 等轴测结构草图：方块半宽（像素），原点为最上层顶面后角。 */
    private static final int ISO_S = 12;
    private static final int ISO_OX = 94;
    private static final int ISO_OY = 70;
    private static final int ENERGY_BAR_X = 195;
    private static final int ENERGY_BAR_Y = 141;
    private static final int ENERGY_BAR_WIDTH = 160;
    private static final int ENERGY_BAR_HEIGHT = 6;
    private static final int TASK_ROWS_PER_PAGE = 3;
    private static final int TASK_ROW_H = 26;
    private static final int TASK_WIDTH = 177;
    private static final int ENERGY_LIMIT_FIELD_X = 281;
    private static final int ENERGY_LIMIT_FIELD_Y = 155;
    private static final int ENERGY_LIMIT_FIELD_WIDTH = 74;
    private static final int INV_X = 382;
    private static final int INV_Y = 64;

    @Nullable
    private OmniversalAlloyFurnaceStructure.ValidationResult liveValidation;
    @Nullable
    private EditBox energyLimitField;
    private long lastSyncedEnergyLimit = Long.MIN_VALUE;
    private String syncedEnergyLimitText = "";
    private boolean updatingEnergyLimitField;
    private boolean energyLimitDirty;
    private boolean energyLimitFieldWasFocused;
    private int taskPage;
    private Map<Long, OmniversalAlloyFurnaceStructure.Mismatch> liveMismatches = Map.of();
    private Direction liveFacing = Direction.NORTH;

    public MultiblockAlloyFurnaceScreen(MultiblockAlloyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = geometry.height();
        inventoryLabelY = INV_Y - 11;
    }

    @Override
    protected void preInit() {
        geometry = referenceGeometry(!menu.getTaskProgress().isEmpty(), false);
        imageHeight = geometry.height();
    }

    @Override
    protected void initControls() {
        for (int i = 0; i < 36 && i < menu.slots.size(); i++)
            com.sorrowmist.useless.client.gui.ldl.api.SlotsNode.moveSlot(menu.slots.get(i),
                    INV_X + i % 9 * 22, i < 27 ? INV_Y + i / 9 * 22 : 132);
        energyLimitField = new AppleEditBox(font,
                leftPos + ENERGY_LIMIT_FIELD_X + 4, topPos + ENERGY_LIMIT_FIELD_Y + 3,
                ENERGY_LIMIT_FIELD_WIDTH - 6, 10,
                Component.translatable("gui.useless_mod.multiblock_alloy_furnace.energy_limit"));
        energyLimitField.setMaxLength(24);
        energyLimitField.setFilter(ScaledEnergyAmount::isValidInput);
        energyLimitField.setResponder(value -> {
            if (!updatingEnergyLimitField) {
                energyLimitDirty = !value.equals(syncedEnergyLimitText);
            }
        });
        addRenderableWidget(energyLimitField);
        syncEnergyLimitField(true);
        refreshStructurePreview();
        PacketDistributor.sendToServer(new AETaskProgressRequestPacket(
                menu.containerId, menu.getBlockPos()));
    }

    @Override
    protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(W, imageHeight, () -> title).icon(AppleUi.Icon.CUBE, AppleStyle.ORANGE));
        h.add(new AppleBadge(W - 120, 7, 108,
                () -> Component.translatable(menu.getTaskProgress().stream().anyMatch(t -> t.progress > 0)
                        ? "gui.useless_mod.apple.running" : "gui.useless_mod.apple.idle"),
                () -> menu.getActiveTasks() > 0 ? AppleStyle.GREEN : AppleStyle.TERTIARY).right());
        h.add(new ApplePushButton(186, geometry.controlsY(), 52, 16, ApplePushButton.Kind.SECONDARY,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                () -> PacketDistributor.sendToServer(new RedstoneControlPacket(menu.getBlockPos())))
                .icon(AppleUi.Icon.GEAR).id("redstone"));
        h.add(new ApplePushButton(290, geometry.controlsY(), 74, 16, ApplePushButton.Kind.DANGER,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                () -> PacketDistributor.sendToServer(new AECancelPacket(menu.getBlockPos())))
                .icon(AppleUi.Icon.XMARK).id("cancel"));

        h.add(new AppleCard(186, 40, 178, 66));
        h.add(new AppleSettingRow(186, 40, 178, 22,
                () -> Component.translatable("gui.useless_mod.apple.structure"), null, null)
                .leading(AppleUi.Icon.CUBE, AppleStyle.ORANGE));
        h.add(new AppleBadge(250, 44, 106, this::stateText,
                () -> structureValid() ? AppleStyle.GREEN : AppleStyle.RED).right());
        h.add(new AppleSettingRow(186, 62, 178, 22,
                () -> Component.translatable("gui.useless_mod.apple.part.coil"),
                () -> Component.literal(structureValid() ? Integer.toString(detectedCoilTier()) : "—"), null)
                .leading(AppleUi.Icon.LAYERS, AppleStyle.PURPLE));
        h.add(new AppleSettingRow(186, 84, 178, 22,
                () -> Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                this::redstoneText, () -> PacketDistributor.sendToServer(new RedstoneControlPacket(menu.getBlockPos())))
                .leading(AppleUi.Icon.BOLT, AppleStyle.RED).noSeparator());

        h.add(new AppleCard(186, 114, 178, geometry.taskCardY() - 122));
        h.add(new AppleCanvas(195, 123, 160, 24, (g, mx, my, x, y, w, hh) -> {
            AppleUi.icon(g, AppleUi.Icon.BOLT, x, y + 2, 10, AppleStyle.RED);
            AppleTextLayout.draw(g, font, Component.translatable("gui.useless_mod.apple.energy"), x + 18, y + 2, 42, .9f, AppleStyle.LABEL, false);
            AppleTextLayout.draw(g, font, Component.literal(ScaledEnergyAmount.format(menu.getEnergy()) + " / "
                    + ScaledEnergyAmount.format(menu.getCapacity()) + " FE"), x + 60, y + 2, 100, .75f, AppleStyle.SECONDARY, true);
            AppleUi.progress(g, x, y + 18, w, 6, menu.getCapacity() <= 0 ? 0 : (float) ((double) menu.getEnergy() / menu.getCapacity()), AppleStyle.RED);
        }));
        h.add(new AppleLabel(195, 158, 82, AppleLabel.Kind.BODY,
                () -> Component.translatable("gui.useless_mod.multiblock_alloy_furnace.energy_limit")));
        h.add(new AppleTextField(ENERGY_LIMIT_FIELD_X, ENERGY_LIMIT_FIELD_Y, ENERGY_LIMIT_FIELD_WIDTH, 16, energyLimitField)
                .rightAlign().id("limit"));
        if (geometry.height() > referenceGeometry(!menu.getTaskProgress().isEmpty(), false).height()) {
            h.add(new AppleLabel(195, 175, 160, AppleLabel.Kind.FOOTNOTE,
                    () -> Component.translatable("gui.useless_mod.multiblock_alloy_furnace.energy_limit.desc")).lines(3));
            h.add(new AppleLabel(195, 199, 160, AppleLabel.Kind.FOOTNOTE,
                    () -> Component.translatable("gui.useless_mod.multiblock_alloy_furnace.energy_limit.units")).lines(2));
        }
        h.add(new AppleCard(12, 40, 166, 191));
        h.add(new AppleLabel(23, 49, 96, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.apple.structure")));
        h.add(new AppleLabel(119, 49, 51, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.apple.dims")).right());
        h.add(new AppleCanvas(21, 64, 148, 159, (g, mx, my, x, y, w, hh) -> {
            renderIsoStructure(g, leftPos, topPos);
            drawLegend(g, leftPos + 25, topPos + 213);
        }));

        h.add(new AppleCard(186, geometry.taskCardY(), 178, geometry.taskCardHeight()));
        h.add(new AppleLabel(197, geometry.taskCardY() + 9, 98, AppleLabel.Kind.CAPTION,
                () -> Component.translatable("gui.useless_mod.multiblock_alloy_furnace.tasks", menu.getActiveTasks(), menu.getMaxTasks())));
        h.add(new ApplePager(299, geometry.taskCardY() + 8, 56, 13,
                () -> Component.literal((taskPage + 1) + " / " + taskPages().size()),
                () -> taskPage > 0, () -> changeTaskPage(-1),
                () -> taskPage < taskPages().size() - 1, () -> changeTaskPage(1)).id("tpager"));
        h.add(new AppleCanvas(186, geometry.taskListY(), TASK_WIDTH, geometry.taskCardHeight() - 36,
                (g, mx, my, x, y, w, hh) -> {
                    g.pose().pushPose();
                    g.pose().translate(leftPos + 186, topPos, 0);
                    renderTaskList(g);
                    g.pose().popPose();
                }));
        h.add(new AppleCard(371, 40, 212, 117));
        h.add(new AppleLabel(INV_X, 49, 194, AppleLabel.Kind.CAPTION, () -> playerInventoryTitle));
        h.add(slotGrid().cellSize(20));
    }

    private boolean structureValid() {
        return liveValidation == null ? menu.isFormed() : liveValidation.valid();
    }

    private int detectedCoilTier() {
        return liveValidation == null ? menu.getCoilTier() : liveValidation.coilTier();
    }

    private Component stateText() {
        if (structureValid()) return Component.translatable("gui.useless_mod.multiblock_alloy_furnace.formed");
        if (liveValidation != null && !liveValidation.mismatches().isEmpty())
            return Component.translatable("gui.useless_mod.multiblock_alloy_furnace.invalid_count",
                    liveValidation.mismatches().size());
        return Component.translatable("gui.useless_mod.multiblock_alloy_furnace.invalid");
    }

    private Component redstoneText() {
        RedstoneControlMode redstoneMode = RedstoneControlMode.byIndex(menu.getRedstoneMode());
        return Component.translatable("gui.useless_mod.multiblock_alloy_furnace.redstone",
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                        + redstoneMode.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (energyLimitField != null && energyLimitField.isFocused()
                && !energyLimitField.isMouseOver(mouseX, mouseY)) {
            commitEnergyLimit();
            setFocused(null);
            energyLimitFieldWasFocused = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void tickControls() {
        boolean focused = energyLimitField != null && energyLimitField.isFocused();
        if (energyLimitFieldWasFocused && !focused) commitEnergyLimit();
        energyLimitFieldWasFocused = focused;
        syncEnergyLimitField(false);
        refreshStructurePreview();
        updateTaskPageControls();
        Geometry next = referenceGeometry(!menu.getTaskProgress().isEmpty(), focused);
        if (!next.equals(geometry)) {
            geometry = next;
            imageHeight = next.height();
            topPos = (height - imageHeight) / 2;
            host = new AppleHost(imageWidth, imageHeight);
            buildApple(host);
            host.layout(width, height, leftPos, topPos);
        }
    }

    private void syncEnergyLimitField(boolean force) {
        if (energyLimitField == null) return;
        long limit = menu.getAutomaticEnergyLimit();
        if (force || (!energyLimitField.isFocused() && !energyLimitDirty
                && limit != lastSyncedEnergyLimit)) {
            setEnergyLimitFieldValue(limit);
            lastSyncedEnergyLimit = limit;
        }
    }

    private void commitEnergyLimit() {
        if (energyLimitField == null || !energyLimitDirty) return;
        long limit = ScaledEnergyAmount.parse(
                energyLimitField.getValue(), menu.getCapacity())
                .orElse(menu.getAutomaticEnergyLimit());
        setEnergyLimitFieldValue(limit);
        PacketDistributor.sendToServer(new MultiblockAlloyFurnaceEnergyLimitPacket(
                menu.containerId, menu.getBlockPos(), limit));
    }

    private void setEnergyLimitFieldValue(long limit) {
        if (energyLimitField == null) return;
        syncedEnergyLimitText = ScaledEnergyAmount.format(limit);
        updatingEnergyLimitField = true;
        energyLimitField.setValue(syncedEnergyLimitText);
        updatingEnergyLimitField = false;
        energyLimitDirty = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (energyLimitField != null && energyLimitField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitEnergyLimit();
            setFocused(null);
            energyLimitFieldWasFocused = false;
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && energyLimitField != null
                && consumesTextInputKey(energyLimitField, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean consumesTextInputKey(EditBox field, int keyCode, int scanCode, int modifiers) {
        return field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput();
    }

    @Override
    public void onClose() {
        commitEnergyLimit();
        super.onClose();
    }

    private void refreshStructurePreview() {
        if (minecraft == null || minecraft.level == null) {
            liveValidation = null;
            liveMismatches = Map.of();
            return;
        }

        BlockPos corePos = menu.getBlockPos();
        BlockState coreState = minecraft.level.getBlockState(corePos);
        liveFacing = coreState.hasProperty(MultiblockAlloyFurnaceCoreBlock.FACING)
                ? coreState.getValue(MultiblockAlloyFurnaceCoreBlock.FACING)
                : Direction.NORTH;
        for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (!minecraft.level.isLoaded(entry.worldPos(corePos, liveFacing))) {
                liveValidation = null;
                liveMismatches = Map.of();
                return;
            }
        }

        liveValidation = OmniversalAlloyFurnaceStructure.validate(
                minecraft.level, corePos, liveFacing);
        Map<Long, OmniversalAlloyFurnaceStructure.Mismatch> mismatches = new HashMap<>();
        for (var mismatch : liveValidation.mismatches()) {
            mismatches.put(mismatch.worldPos().asLong(), mismatch);
        }
        liveMismatches = Map.copyOf(mismatches);
    }

    /** Compact live structure legend; no extra dimension row below the reference card. */
    private void drawLegend(GuiGraphics g, int x, int y) {
        String[] keys = {"core", "coil", "casing"};
        int[] colors = {AppleStyle.ORANGE, AppleStyle.PURPLE, AppleStyle.TERTIARY};
        for (int i = 0; i < 3; i++) {
            AppleUi.fillRound(g, x + i * 43, y + 1, 6, 6, 1.5f, colors[i]);
            AppleTextLayout.draw(g, font, Component.translatable("gui.useless_mod.apple.part." + keys[i]),
                    x + i * 43 + 8, y, 34, .75f, AppleStyle.SECONDARY, false);
        }
    }

    private Component truncate(Component text, int maxWidth) {
        String value = text.getString();
        return font.width(value) <= maxWidth
                ? text
                : Component.literal(font.plainSubstrByWidth(
                        value, maxWidth - font.width("...")) + "...");
    }

    /** 等轴测屏幕坐标：返回方块顶面中心。bx/bz ∈ 0..2，by ∈ 0..3。 */
    private static int isoX(int bx, int bz) { return ISO_OX + (bx - bz) * ISO_S; }
    private static int isoY(int bx, int by, int bz) { return ISO_OY + (bx + bz) * ISO_S / 2 + (3 - by) * 32; }

    private List<OmniversalAlloyFurnaceStructure.Entry> isoOrder() {
        List<OmniversalAlloyFurnaceStructure.Entry> list = new ArrayList<>(OmniversalAlloyFurnaceStructure.entries());
        list.removeIf(e -> e.part() == OmniversalAlloyFurnaceStructure.Part.AIR);
        list.sort(Comparator.comparingInt((OmniversalAlloyFurnaceStructure.Entry e) -> e.localPos().getY())
                .thenComparingInt(e -> e.localPos().getX() + 1 + e.localPos().getZ()));
        return list;
    }

    private void renderIsoStructure(GuiGraphics g, int l, int tp) {
        for (var entry : isoOrder()) {
            int bx = entry.localPos().getX() + 1, by = entry.localPos().getY(), bz = entry.localPos().getZ();
            BlockPos worldPos = entry.worldPos(menu.getBlockPos(), liveFacing);
            int c = liveValidation == null ? 0xFFC7C7CC
                    : liveMismatches.containsKey(worldPos.asLong()) ? AppleUi.RED : partColor(entry, worldPos);
            drawIsoBlock(g, l + isoX(bx, bz), tp + isoY(bx, by, bz), c);
        }
    }

    private static int shade(int c, float f) {
        int r = (int) (((c >> 16) & 0xFF) * f), gg = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
        return (c & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    /** 画一个等轴测方块（顶面菱形 + 左右两个侧面），(cx, cy) 为顶面中心。 */
    private static void drawIsoBlock(GuiGraphics g, int cx, int cy, int color) {
        int s = ISO_S, h = ISO_S / 2;
        int top = color, left = shade(color, 0.86f), right = shade(color, 0.72f);
        for (int dx = -s; dx < s; dx++) {
            int half = (int) Math.ceil((s - Math.abs(dx + 0.5f)) * h / (float) s);
            g.fill(cx + dx, cy - half, cx + dx + 1, cy + half, top);
            int edge = cy + half;
            g.fill(cx + dx, edge, cx + dx + 1, edge + s, dx < 0 ? left : right);
        }
        // 细白描边，让方块之间有接缝
        g.fill(cx - s, cy, cx - s + 1, cy + s, 0x66FFFFFF);
        g.fill(cx, cy + h, cx + 1, cy + h + s, 0x66FFFFFF);
    }

    private int partColor(OmniversalAlloyFurnaceStructure.Entry entry, BlockPos worldPos) {
        if (entry.part() == OmniversalAlloyFurnaceStructure.Part.CASING
                && minecraft != null && minecraft.level != null) {
            OmniversalAlloyFurnaceStructure.FunctionalPart functionalPart =
                    OmniversalAlloyFurnaceStructure.functionalPart(minecraft.level.getBlockState(worldPos));
            if (functionalPart != null) {
                return switch (functionalPart) {
                    case PATTERN_ASSEMBLY -> 0xFF30B0C7;
                    case MOLD_HUB -> 0xFFFFCC00;
                    case PASSIVE_HATCH -> 0xFF5AC8FA;
                };
            }
        }
        return switch (entry.part()) {
            case CORE -> 0xFFFF9500;
            case CASING -> 0xFFAEAEB2;
            case COIL -> 0xFFAF52DE;
            case AIR -> 0xFFE5E5EA;
        };
    }

    private void renderTaskList(GuiGraphics graphics) {
        List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> pages = taskPages();
        taskPage = net.minecraft.util.Mth.clamp(taskPage, 0, pages.size() - 1);
        var page = pages.get(taskPage);
        int taskListY = geometry.taskListY();
        if (page.isEmpty()) {
            AppleTextLayout.drawCentered(graphics, font, Component.translatable("gui.useless_mod.apple.no_tasks"),
                    8, taskListY, TASK_WIDTH - 16, geometry.taskCardHeight() - 36, 4, 4, null, AppleStyle.SECONDARY);
        }
        for (int i = 0; i < page.size(); i++) {
            var task = page.get(i);
            int y = taskListY + i * TASK_ROW_H;
            AppleIconTile.draw(graphics, 9, y, 20, AppleUi.Icon.PATTERN, AppleStyle.ORANGE, false);
            String product = task.productName + " \u00d7" + task.totalOutputCount;
            AppleTextLayout.draw(graphics, font, Component.literal(product), 33, y + 2, 96, .8f, AppleStyle.LABEL, false);
            if (task.progress > 0 && task.maxProgress > 0) {
                float r = Math.min(1f, task.progress / (float) task.maxProgress);
                String p = Math.round(r * 100) + "%";
                AppleUi.progress(graphics, 30, y + 17, 106, 3, r, AppleUi.ORANGE);
                graphics.drawString(font, p, TASK_WIDTH - 10 - font.width(p), y + 14, AppleUi.BLUE_TEXT, false);
            } else {
                String detail = task.statusDetail == null || task.statusDetail.isBlank() ? ""
                        : task.statusDetail.startsWith("gui.useless_mod.")
                        ? Component.translatable(task.statusDetail).getString() : task.statusDetail;
                String st = Component.translatable(task.statusKey).getString() + (detail.isEmpty() ? "" : ": " + detail);
                st = font.plainSubstrByWidth(st, 140);
                graphics.drawString(font, st, 30, y + 14, MachineScreenStyle.MUTED_TEXT_COLOR, false);
            }
        }
    }

    private List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> taskPages() {
        var tasks = new ArrayList<>(menu.getTaskProgress());
        tasks.sort(Comparator
                .comparing((com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData task)
                        -> task.productName)
                .thenComparingInt(task -> task.progress));
        List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> pages = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i += TASK_ROWS_PER_PAGE)
            pages.add(List.copyOf(tasks.subList(i, Math.min(tasks.size(), i + TASK_ROWS_PER_PAGE))));
        if (pages.isEmpty()) pages.add(List.of());
        return pages;
    }

    private void updateTaskPageControls() {
        int pageCount = taskPages().size();
        taskPage = net.minecraft.util.Mth.clamp(taskPage, 0, pageCount - 1);
    }

    private void changeTaskPage(int delta) {
        int lastPage = taskPages().size() - 1;
        taskPage = net.minecraft.util.Mth.clamp(taskPage + delta, 0, lastPage);
        updateTaskPageControls();
    }

    @Override
    protected void renderExtraTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.useless_mod.advanced_alloy_furnace.energy",
                    ScaledEnergyAmount.format(menu.getEnergy()),
                    ScaledEnergyAmount.format(menu.getCapacity())), mouseX, mouseY);
        }
        if (energyLimitField != null && energyLimitField.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(
                            "gui.useless_mod.multiblock_alloy_furnace.energy_limit"),
                    Component.translatable(
                            "gui.useless_mod.multiblock_alloy_furnace.energy_limit.desc"),
                    Component.translatable(
                            "gui.useless_mod.multiblock_alloy_furnace.energy_limit.units")),
                    Optional.empty(), mouseX, mouseY);
        }
        renderControlTooltips(graphics, mouseX, mouseY);
        renderStructureTooltip(graphics, mouseX, mouseY);
    }

    private void renderControlTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(186, geometry.controlsY(),
                52, 16,
                mouseX, mouseY)) {
            RedstoneControlMode mode = RedstoneControlMode.byIndex(menu.getRedstoneMode());
            graphics.renderTooltip(font, List.of(
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.redstone_control."
                                    + mode.name().toLowerCase(Locale.ROOT))),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(290, geometry.controlsY(),
                74, 16,
                mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks.desc")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderStructureTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        OmniversalAlloyFurnaceStructure.Entry entry = findHoveredPreviewEntry(mouseX, mouseY);
        if (entry == null || minecraft == null || minecraft.level == null) return;

        BlockPos worldPos = entry.worldPos(menu.getBlockPos(), liveFacing);
        BlockState actual = minecraft.level.getBlockState(worldPos);
        boolean known = liveValidation != null;
        boolean mismatch = known && liveMismatches.containsKey(worldPos.asLong());
        List<Component> lines = new ArrayList<>(4);
        lines.add(Component.translatable(known
                        ? mismatch
                                ? "gui.useless_mod.multiblock_alloy_furnace.preview.incorrect"
                                : "gui.useless_mod.multiblock_alloy_furnace.preview.correct"
                        : "gui.useless_mod.multiblock_alloy_furnace.preview.unavailable")
                .withStyle(!known ? ChatFormatting.GRAY
                        : mismatch ? ChatFormatting.RED : ChatFormatting.GREEN));
        Component expected = expectedPartName(entry, worldPos);
        lines.add(Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.expected", expected));
        lines.add(Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.actual",
                actual.getBlock().getName()));
        lines.add(Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.position",
                worldPos.getX(), worldPos.getY(), worldPos.getZ()));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private Component expectedPartName(
            OmniversalAlloyFurnaceStructure.Entry entry, BlockPos worldPos) {
        return switch (entry.part()) {
            case CORE -> ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get().getName();
            case CASING -> expectedCasingName(worldPos);
            case COIL -> expectedCoilName();
            case AIR -> Blocks.AIR.getName();
        };
    }

    private Component expectedCasingName(BlockPos worldPos) {
        Component casingName = ModBlocks.OMNIVERSAL_FURNACE_CASING.get().getName();
        return Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.casing_with_functional_parts",
                casingName, ModBlocks.ME_PATTERN_ASSEMBLY.get().getName(),
                ModBlocks.OMNIVERSAL_MOLD_HUB.get().getName(),
                ModBlocks.PASSIVE_CRAFTING_HATCH.get().getName());
    }

    private Component expectedCoilName() {
        int tier = liveValidation == null ? menu.getCoilTier() : liveValidation.coilTier();
        var coil = ModBlocks.USELESS_COILS.get(tier);
        if (coil != null) return coil.get().getName();
        return Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.coil_any",
                ModBlocks.USELESS_COILS.get(UselessCoilBlock.MIN_TIER).get().getName(),
                ModBlocks.USELESS_COILS.get(UselessCoilBlock.MAX_TIER).get().getName());
    }

    @Nullable
    private OmniversalAlloyFurnaceStructure.Entry findHoveredPreviewEntry(int mouseX, int mouseY) {
        int rx = mouseX - leftPos, ry = mouseY - topPos;
        List<OmniversalAlloyFurnaceStructure.Entry> order = isoOrder();
        // 逆绘制顺序：最上层/最前方的方块优先命中
        for (int i = order.size() - 1; i >= 0; i--) {
            var e = order.get(i);
            int bx = e.localPos().getX() + 1, by = e.localPos().getY(), bz = e.localPos().getZ();
            int cx = isoX(bx, bz), cy = isoY(bx, by, bz);
            int dx = Math.abs(rx - cx);
            if (dx >= ISO_S) continue;
            int topEdge = cy - (ISO_S - dx) / 2;
            int bottomEdge = cy + (ISO_S - dx) / 2 + ISO_S;
            if (ry >= topEdge && ry < bottomEdge) return e;
        }
        return null;
    }
}
