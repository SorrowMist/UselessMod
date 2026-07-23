package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;

public final class MultiblockAlloyFurnaceScreen extends AbstractContainerScreen<MultiblockAlloyFurnaceMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public MultiblockAlloyFurnaceScreen(MultiblockAlloyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("R"), button ->
                        PacketDistributor.sendToServer(new RedstoneControlPacket(menu.getBlockPos())))
                .bounds(leftPos + 132, topPos + 4, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("X"), button ->
                        PacketDistributor.sendToServer(new AECancelPacket(menu.getBlockPos())))
                .bounds(leftPos + 152, topPos + 4, 18, 14).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        long capacity = menu.getCapacity();
        int width = capacity <= 0 ? 0 : (int) Math.round(
                Math.min(1.0D, (double) menu.getEnergy() / (double) capacity) * 154.0D);
        graphics.fill(leftPos + 11, topPos + 60, leftPos + 165, topPos + 66, 0xFF272727);
        graphics.fill(leftPos + 11, topPos + 60, leftPos + 11 + width, topPos + 66, 0xFF4CAF50);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 116) titleText = font.plainSubstrByWidth(titleText, 113) + "...";
        graphics.drawString(font, titleText, 8, 6, 0x404040, false);
        int stateColor = menu.isFormed() ? 0x2E7D32 : 0xB71C1C;
        graphics.drawString(font,
                Component.translatable(menu.isFormed()
                        ? "gui.useless_mod.multiblock_alloy_furnace.formed"
                        : "gui.useless_mod.multiblock_alloy_furnace.invalid"), 8, 22, stateColor, false);
        graphics.drawString(font, Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.coil", menu.getCoilTier()), 8, 34, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                "gui.useless_mod.multiblock_alloy_furnace.tasks",
                menu.getActiveTasks(), menu.getMaxTasks()), 8, 46, 0x404040, false);
        renderStructurePreview(graphics);
        renderTaskList(graphics);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    private void renderStructurePreview(GuiGraphics graphics) {
        int originX = 88;
        int originY = 32;
        int cellSize = 6;
        int layerStride = 21;
        for (int layer = 0; layer < 4; layer++) {
            int layerX = originX + layer * layerStride;
            for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
                if (entry.localPos().getY() != layer) continue;
                int x = layerX + (entry.localPos().getX() + 1) * cellSize;
                int y = originY + entry.localPos().getZ() * cellSize;
                graphics.fill(x, y, x + cellSize - 1, y + cellSize - 1, partColor(entry.part()));
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
        int rows = Math.min(4, visibleTasks.size());
        for (int index = 0; index < rows; index++) {
            var task = visibleTasks.get(index);
            String state = task.getProgress() > 0 && task.getMaxProgress() > 0
                    ? task.getProgress() + "/" + task.getMaxProgress()
                    : Component.translatable(task.getStatusKey()).getString();
            String line = task.getProductName() + " x" + task.getTotalOutputCount() + "  " + state;
            if (font.width(line) > 154) line = font.plainSubstrByWidth(line, 151) + "...";
            graphics.drawString(font, line, 11, 73 + index * 12, 0x404040, false);
        }
        if (visibleTasks.size() > rows) {
            graphics.drawString(font, "+" + (visibleTasks.size() - rows), 11, 120, 0x606060, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(11, 60, 154, 6, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.useless_mod.advanced_alloy_furnace.energy",
                    menu.getEnergy(), menu.getCapacity()), mouseX, mouseY);
        }
    }
}
