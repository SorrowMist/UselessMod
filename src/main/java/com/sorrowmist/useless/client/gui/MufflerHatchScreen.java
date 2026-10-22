package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.MufflerHatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 消声仓的容器界面。
 *
 * <p>界面正中心只有一个只出不进的槽位，其右侧不设任何控制区。</p>
 */
public final class MufflerHatchScreen extends AbstractContainerScreen<MufflerHatchMenu> {

    public MufflerHatchScreen(MufflerHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos,
                MufflerHatchMenu.SLOT_X, MufflerHatchMenu.SLOT_Y, 1, 1);
        for (Slot slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MachineScreenStyle.TEXT_COLOR, false);
    }
}