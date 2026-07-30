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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
    private static final int TASK_LIST_X = 11;
    private static final int TASK_LIST_Y = 84;
    private static final int TASK_LIST_WIDTH = 154;
    private static final int TASK_LIST_MAX_LINES = 5;
    private static final int TASK_LIST_LINE_HEIGHT = 9;
    private static final float TASK_LIST_TEXT_SCALE = 0.75F;
    private static final int TASK_PAGE_PREVIOUS_X = 124;
    private static final int TASK_PAGE_NEXT_X = 154;
    private static final int TASK_PAGE_BUTTON_Y = 118;
    private static final int TASK_PAGE_BUTTON_WIDTH = 12;
    private static final int TASK_PAGE_BUTTON_HEIGHT = 10;
    private static final int ENERGY_LIMIT_FIELD_X = 88;
    private static final int ENERGY_LIMIT_FIELD_Y = 60;
    private static final int ENERGY_LIMIT_FIELD_WIDTH = 80;

    @Nullable
    private OmniversalAlloyFurnaceStructure.ValidationResult liveValidation;
    @Nullable
    private AlloyFurnaceControlButton cancelButton;
    @Nullable
    private PressableAE2Button previousTaskPageButton;
    @Nullable
    private PressableAE2Button nextTaskPageButton;
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
        energyLimitField = new EditBox(font,
                leftPos + ENERGY_LIMIT_FIELD_X, topPos + ENERGY_LIMIT_FIELD_Y,
                ENERGY_LIMIT_FIELD_WIDTH, 14,
                Component.translatable("gui.useless_mod.multiblock_alloy_furnace.energy_limit"));
        energyLimitField.setMaxLength(24);
        energyLimitField.setFilter(ScaledEnergyAmount::isValidInput);
        energyLimitField.setResponder(value -> {
            if (!updatingEnergyLimitField) {
                energyLimitDirty = !value.equals(syncedEnergyLimitText);
            }
        });
        addRenderableWidget(energyLimitField);
        previousTaskPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + TASK_PAGE_PREVIOUS_X, topPos + TASK_PAGE_BUTTON_Y,
                TASK_PAGE_BUTTON_WIDTH, TASK_PAGE_BUTTON_HEIGHT,
                Component.literal("<"), button -> changeTaskPage(-1)));
        nextTaskPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + TASK_PAGE_NEXT_X, topPos + TASK_PAGE_BUTTON_Y,
                TASK_PAGE_BUTTON_WIDTH, TASK_PAGE_BUTTON_HEIGHT,
                Component.literal(">"), button -> changeTaskPage(1)));
        syncEnergyLimitField(true);
        refreshStructurePreview();
        PacketDistributor.sendToServer(new AETaskProgressRequestPacket(
                menu.containerId, menu.getBlockPos()));
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cancelButton != null) {
            cancelButton.releaseVisualState();
        }
        if (previousTaskPageButton != null) {
            previousTaskPageButton.releaseVisualState();
        }
        if (nextTaskPageButton != null) {
            nextTaskPageButton.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean focused = energyLimitField != null && energyLimitField.isFocused();
        if (energyLimitFieldWasFocused && !focused) commitEnergyLimit();
        energyLimitFieldWasFocused = focused;
        syncEnergyLimitField(false);
        refreshStructurePreview();
        updateTaskPageControls();
    }

    private void syncEnergyLimitField(boolean force) {
        if (energyLimitField == null) return;
        long limit = menu.getAutomaticEnergyLimit();
        if ((force || (!energyLimitField.isFocused() && !energyLimitDirty))
                && limit != lastSyncedEnergyLimit) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        updateTaskPageControls();
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
        Component redstoneText = Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.redstone",
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control."
                        + redstoneMode.name().toLowerCase(Locale.ROOT)));
        graphics.drawString(font, truncate(redstoneText, 76),
                8, 52, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, truncate(Component.translatable(
                        "gui.useless_mod.multiblock_alloy_furnace.energy_limit"), 80),
                ENERGY_LIMIT_FIELD_X, 50, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        Component tasksText = Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.tasks",
                menu.getActiveTasks(), menu.getMaxTasks());
        graphics.drawString(font, truncate(tasksText, 76),
                8, 63, MachineScreenStyle.MUTED_TEXT_COLOR, false);
        renderStructurePreview(graphics);
        renderTaskList(graphics);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    private Component truncate(Component text, int maxWidth) {
        String value = text.getString();
        return font.width(value) <= maxWidth
                ? text
                : Component.literal(font.plainSubstrByWidth(
                        value, maxWidth - font.width("...")) + "...");
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
                            y + PREVIEW_CELL_SIZE - 2, partColor(entry, worldPos));
                } else {
                    graphics.fill(x, y, x + PREVIEW_CELL_SIZE - 1,
                            y + PREVIEW_CELL_SIZE - 1, partColor(entry, worldPos));
                }
            }
        }
    }

    private int partColor(OmniversalAlloyFurnaceStructure.Entry entry, BlockPos worldPos) {
        if (entry.part() == OmniversalAlloyFurnaceStructure.Part.CASING
                && minecraft != null && minecraft.level != null) {
            OmniversalAlloyFurnaceStructure.FunctionalPart functionalPart =
                    OmniversalAlloyFurnaceStructure.functionalPart(minecraft.level.getBlockState(worldPos));
            if (functionalPart != null) {
                return switch (functionalPart) {
                    case PATTERN_ASSEMBLY -> 0xFF00838F;
                    case MOLD_HUB -> 0xFFF9A825;
                    case PASSIVE_HATCH -> 0xFF517497;
                };
            }
        }
        return switch (entry.part()) {
            case CORE -> 0xFFD84315;
            case CASING -> 0xFF757575;
            case COIL -> 0xFF6A1B9A;
            case AIR -> 0xFF212121;
        };
    }

    private void renderTaskList(GuiGraphics graphics) {
        List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> pages = taskPages();
        taskPage = Math.clamp(taskPage, 0, pages.size() - 1);
        List<String> lines = new ArrayList<>();
        for (var task : pages.get(taskPage)) {
            lines.addAll(taskLines(task));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(TASK_LIST_X, TASK_LIST_Y, 0);
        graphics.pose().scale(TASK_LIST_TEXT_SCALE, TASK_LIST_TEXT_SCALE, 1.0F);
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(font, lines.get(line), 0, line * TASK_LIST_LINE_HEIGHT,
                    MachineScreenStyle.MUTED_TEXT_COLOR, false);
        }
        graphics.pose().popPose();

        if (pages.size() > 1) {
            String pageText = (taskPage + 1) + "/" + pages.size();
            graphics.drawString(font, pageText,
                    TASK_PAGE_NEXT_X - 2 - font.width(pageText), TASK_PAGE_BUTTON_Y + 1,
                    MachineScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
    }

    private String fitTaskLine(String text) {
        int scaledWidth = (int) (TASK_LIST_WIDTH / TASK_LIST_TEXT_SCALE);
        if (font.width(text) <= scaledWidth) return text;
        return font.plainSubstrByWidth(text, scaledWidth - font.width("...")) + "...";
    }

    private List<String> taskLines(
            com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData task) {
        String status = task.progress > 0 && task.maxProgress > 0
                ? task.progress + "/" + task.maxProgress
                : Component.translatable(task.statusKey).getString();
        String detail = task.statusDetail == null || task.statusDetail.isBlank() ? ""
                : task.statusDetail.startsWith("gui.useless_mod.")
                ? Component.translatable(task.statusDetail).getString()
                : task.statusDetail;
        String state = detail.isEmpty() ? status : status + ": " + detail;
        String product = task.productName + " x" + task.totalOutputCount;
        String combined = product + "  " + state;
        if (font.width(combined) <= (int) (TASK_LIST_WIDTH / TASK_LIST_TEXT_SCALE)) {
            return List.of(combined);
        }
        if (detail.isEmpty() || font.width(state) <= (int) (TASK_LIST_WIDTH / TASK_LIST_TEXT_SCALE)) {
            return List.of(fitTaskLine(product), fitTaskLine(state));
        }
        return List.of(fitTaskLine(product), fitTaskLine(status), fitTaskLine(detail));
    }

    private List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> taskPages() {
        var tasks = new ArrayList<>(menu.getTaskProgress());
        tasks.sort(Comparator
                .comparing((com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData task)
                        -> task.productName)
                .thenComparingInt(task -> task.progress));
        List<List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData>> pages = new ArrayList<>();
        List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData> page = new ArrayList<>();
        int usedLines = 0;
        for (var task : tasks) {
            int requiredLines = taskLines(task).size();
            if (!page.isEmpty() && usedLines + requiredLines > TASK_LIST_MAX_LINES) {
                pages.add(List.copyOf(page));
                page.clear();
                usedLines = 0;
            }
            page.add(task);
            usedLines += requiredLines;
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(List.copyOf(page));
        }
        return pages;
    }

    private void updateTaskPageControls() {
        int pageCount = taskPages().size();
        taskPage = Math.clamp(taskPage, 0, pageCount - 1);
        if (previousTaskPageButton != null) {
            previousTaskPageButton.visible = pageCount > 1;
            previousTaskPageButton.active = taskPage > 0;
        }
        if (nextTaskPageButton != null) {
            nextTaskPageButton.visible = pageCount > 1;
            nextTaskPageButton.active = taskPage < pageCount - 1;
        }
    }

    private void changeTaskPage(int delta) {
        int lastPage = taskPages().size() - 1;
        taskPage = Math.clamp(taskPage + delta, 0, lastPage);
        updateTaskPageControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
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
