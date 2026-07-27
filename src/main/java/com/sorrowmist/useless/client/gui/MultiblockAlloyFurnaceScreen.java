package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MultiblockAlloyFurnaceScreen extends AbstractContainerScreen<MultiblockAlloyFurnaceMenu> {
    private static final int PREVIEW_ORIGIN_X = 88;
    private static final int PREVIEW_ORIGIN_Y = 32;
    private static final int PREVIEW_CELL_SIZE = 6;
    private static final int PREVIEW_LAYER_STRIDE = 21;
    private static final int REDSTONE_BUTTON_X = 142;
    private static final int CANCEL_BUTTON_X = 158;
    private static final int CONTROL_BUTTON_Y = 3;
    private static final int ENERGY_BAR_X = 11;
    private static final int ENERGY_BAR_Y = 74;
    private static final int ENERGY_BAR_WIDTH = 154;
    private static final int ENERGY_BAR_HEIGHT = 6;
    private static final int TASK_LIST_Y = 84;

    @Nullable
    private OmniversalAlloyFurnaceStructure.ValidationResult liveValidation;
    @Nullable
    private AlloyFurnaceControlButton cancelButton;
    private Map<Long, OmniversalAlloyFurnaceStructure.Mismatch> liveMismatches = Map.of();
    private Direction liveFacing = Direction.NORTH;

    public MultiblockAlloyFurnaceScreen(MultiblockAlloyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(AlloyFurnaceControlButton.redstone(
                leftPos + REDSTONE_BUTTON_X, topPos + CONTROL_BUTTON_Y,
                () -> RedstoneControlMode.byIndex(menu.getRedstoneMode()),
                button -> PacketDistributor.sendToServer(
                        new RedstoneControlPacket(menu.getBlockPos()))));
        cancelButton = addRenderableWidget(AlloyFurnaceControlButton.cancel(
                leftPos + CANCEL_BUTTON_X, topPos + CONTROL_BUTTON_Y,
                button -> PacketDistributor.sendToServer(
                        new AECancelPacket(menu.getBlockPos()))));
        refreshStructurePreview();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cancelButton != null) {
            cancelButton.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshStructurePreview();
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawInset(graphics,
                leftPos + 4, topPos + 20, leftPos + imageWidth - 4, topPos + 128);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 140, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 198, 9, 1);
        for (var slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
        long capacity = menu.getCapacity();
        int width = capacity <= 0 ? 0 : (int) Math.round(
                Math.min(1.0D, (double) menu.getEnergy() / (double) capacity) * ENERGY_BAR_WIDTH);
        graphics.fill(leftPos + ENERGY_BAR_X, topPos + ENERGY_BAR_Y,
                leftPos + ENERGY_BAR_X + ENERGY_BAR_WIDTH,
                topPos + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, 0xFF272727);
        graphics.fill(leftPos + ENERGY_BAR_X, topPos + ENERGY_BAR_Y,
                leftPos + ENERGY_BAR_X + width,
                topPos + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT, 0xFF4CAF50);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 116) titleText = font.plainSubstrByWidth(titleText, 113) + "...";
        graphics.drawString(font, titleText, 8, 6, MachineScreenStyle.TEXT_COLOR, false);
        boolean structureValid = liveValidation == null ? menu.isFormed() : liveValidation.valid();
        int stateColor = structureValid ? 0xFF2E7D32 : MachineScreenStyle.ERROR_TEXT_COLOR;
        Component stateText;
        if (structureValid) {
            stateText = Component.translatable("gui.useless_mod.multiblock_alloy_furnace.formed");
        } else if (liveValidation != null && !liveValidation.mismatches().isEmpty()) {
            stateText = Component.translatable(
                    "gui.useless_mod.multiblock_alloy_furnace.invalid_count",
                    liveValidation.mismatches().size());
        } else {
            stateText = Component.translatable("gui.useless_mod.multiblock_alloy_furnace.invalid");
        }
        graphics.drawString(font, stateText, 8, 22, stateColor, false);
        int detectedCoilTier = liveValidation == null ? menu.getCoilTier() : liveValidation.coilTier();
        graphics.drawString(font, Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.coil", detectedCoilTier),
                8, 34, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        RedstoneControlMode redstoneMode = RedstoneControlMode.byIndex(menu.getRedstoneMode());
        graphics.drawString(font, Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.redstone",
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                        + redstoneMode.name().toLowerCase(Locale.ROOT))),
                8, 52, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.tasks",
                menu.getActiveTasks(), menu.getMaxTasks()),
                8, 63, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        renderStructurePreview(graphics);
        renderTaskList(graphics);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private void renderStructurePreview(GuiGraphics graphics) {
        for (int layer = 0; layer < 4; layer++) {
            int layerX = PREVIEW_ORIGIN_X + layer * PREVIEW_LAYER_STRIDE;
            for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
                if (entry.localPos().getY() != layer) continue;
                int x = layerX + (entry.localPos().getX() + 1) * PREVIEW_CELL_SIZE;
                int y = PREVIEW_ORIGIN_Y + entry.localPos().getZ() * PREVIEW_CELL_SIZE;
                BlockPos worldPos = entry.worldPos(menu.getBlockPos(), liveFacing);
                boolean mismatch = liveMismatches.containsKey(worldPos.asLong());
                if (liveValidation == null) {
                    graphics.fill(x, y, x + PREVIEW_CELL_SIZE - 1,
                            y + PREVIEW_CELL_SIZE - 1, MachineScreenStyle.MUTED_TEXT_COLOR);
                } else if (mismatch) {
                    graphics.fill(x, y, x + PREVIEW_CELL_SIZE - 1,
                            y + PREVIEW_CELL_SIZE - 1, MachineScreenStyle.ERROR_TEXT_COLOR);
                    graphics.fill(x + 1, y + 1, x + PREVIEW_CELL_SIZE - 2,
                            y + PREVIEW_CELL_SIZE - 2, partColor(entry.part()));
                } else {
                    graphics.fill(x, y, x + PREVIEW_CELL_SIZE - 1,
                            y + PREVIEW_CELL_SIZE - 1, partColor(entry.part()));
                }
            }
        }
    }

    private static int partColor(OmniversalAlloyFurnaceStructure.Part part) {
        return switch (part) {
            case CORE -> 0xFFD84315;
            case PATTERN_ASSEMBLY -> 0xFF00838F;
            case MOLD_HUB -> 0xFFF9A825;
            case CASING -> 0xFF757575;
            case COIL -> 0xFF6A1B9A;
            case AIR -> 0xFF212121;
        };
    }

    private void renderTaskList(GuiGraphics graphics) {
        if (menu.getCore() == null) return;
        var visibleTasks = new ArrayList<>(menu.getCore().getAETaskProgressList());
        visibleTasks.sort(Comparator
                .comparing(com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae
                        .AdvancedAlloyFurnaceAeManager.AETaskProgress::getProductName)
                .thenComparingInt(task -> task.getProgress()));
        int rows = Math.min(3, visibleTasks.size());
        for (int index = 0; index < rows; index++) {
            var task = visibleTasks.get(index);
            String state = task.getProgress() > 0 && task.getMaxProgress() > 0
                    ? task.getProgress() + "/" + task.getMaxProgress()
                    : Component.translatable(task.getStatusKey()).getString();
            String line = task.getProductName() + " x" + task.getTotalOutputCount() + "  " + state;
            if (font.width(line) > 154) line = font.plainSubstrByWidth(line, 151) + "...";
            graphics.drawString(font, line, 11, TASK_LIST_Y + index * 12,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
        if (visibleTasks.size() > rows) {
            graphics.drawString(font, "+" + (visibleTasks.size() - rows), 11, 119,
                    MachineScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.useless_mod.advanced_alloy_furnace.energy",
                    menu.getEnergy(), menu.getCapacity()), mouseX, mouseY);
        }
        renderControlTooltips(graphics, mouseX, mouseY);
        renderStructureTooltip(graphics, mouseX, mouseY);
    }

    private void renderControlTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(REDSTONE_BUTTON_X, CONTROL_BUTTON_Y,
                AlloyFurnaceControlIcons.WIDTH, AlloyFurnaceControlIcons.HEIGHT,
                mouseX, mouseY)) {
            RedstoneControlMode mode = RedstoneControlMode.byIndex(menu.getRedstoneMode());
            graphics.renderTooltip(font, List.of(
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                    Component.translatable(
                            "gui.useless_mod.advanced_alloy_furnace.redstone_control."
                                    + mode.name().toLowerCase(Locale.ROOT))),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(CANCEL_BUTTON_X, CONTROL_BUTTON_Y,
                AlloyFurnaceControlIcons.WIDTH, AlloyFurnaceControlIcons.HEIGHT,
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
        Component expected = Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.preview.part."
                        + entry.part().name().toLowerCase(Locale.ROOT));
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

    @Nullable
    private OmniversalAlloyFurnaceStructure.Entry findHoveredPreviewEntry(int mouseX, int mouseY) {
        int relativeX = mouseX - leftPos;
        int relativeY = mouseY - topPos;
        for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
            int layerX = PREVIEW_ORIGIN_X
                    + entry.localPos().getY() * PREVIEW_LAYER_STRIDE;
            int x = layerX + (entry.localPos().getX() + 1) * PREVIEW_CELL_SIZE;
            int y = PREVIEW_ORIGIN_Y + entry.localPos().getZ() * PREVIEW_CELL_SIZE;
            if (relativeX >= x && relativeX < x + PREVIEW_CELL_SIZE - 1
                    && relativeY >= y && relativeY < y + PREVIEW_CELL_SIZE - 1) {
                return entry;
            }
        }
        return null;
    }
}
