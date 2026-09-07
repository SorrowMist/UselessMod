package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.ContainerPatternConverter;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Inventory screen for loading patterns and starting a batch conversion. */
public final class PatternConverterScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<ContainerPatternConverter> {
    private static final int CONVERT_BUTTON_WIDTH = 54;
    private PressableAE2Button convertButton;

    public PatternConverterScreen(ContainerPatternConverter menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 178;
        inventoryLabelY = 85;
    }

    @Override
    protected void init() {
        super.init();
        convertButton = addRenderableWidget(new PressableAE2Button(
                leftPos + imageWidth - CONVERT_BUTTON_WIDTH - 6,
                topPos + 4,
                CONVERT_BUTTON_WIDTH,
                20,
                Component.translatable("gui.useless_mod.pattern_converter.convert"),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, ContainerPatternConverter.CONVERT_BUTTON);
                    }
                }));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        convertButton.releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 30, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 97, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 155, 9, 1);
        for (var slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot instanceof PatternSlotItemHandler patternSlot) {
            var icon = patternSlot.getIcon();
            if ((patternSlot.renderIconWithItem() || patternSlot.getItem().isEmpty())
                    && patternSlot.isSlotEnabled() && icon != null) {
                icon.getBlitter()
                        .dest(patternSlot.x, patternSlot.y)
                        .opacity(patternSlot.getOpacityOfIcon())
                        .blit(graphics);
            }
        }
        super.renderSlot(graphics, slot);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 88) {
            titleText = font.plainSubstrByWidth(titleText, 85) + "...";
        }
        graphics.drawString(font, titleText, 8, 6, MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
