package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

public class PagedRecoverableScreen<T extends PagedRecoverableMenu> extends AbstractContainerScreen<T> {
    @Nullable
    private PressableAE2Button previousPageButton;
    @Nullable
    private PressableAE2Button nextPageButton;

    public PagedRecoverableScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();
        previousPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 132, topPos + 4, 18, 12,
                Component.literal("<"), button -> page(PagedRecoverableMenu.PREVIOUS_PAGE)));
        nextPageButton = addRenderableWidget(new PressableAE2Button(
                leftPos + 152, topPos + 4, 18, 12,
                Component.literal(">"), button -> page(PagedRecoverableMenu.NEXT_PAGE)));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (previousPageButton != null) {
            previousPageButton.releaseVisualState();
        }
        if (nextPageButton != null) {
            nextPageButton.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void page(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MachineScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 18, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 85, 9, 3);
        MachineScreenStyle.drawSlotGroup(graphics, leftPos, topPos, 8, 143, 9, 1);
        for (var slot : menu.slots) {
            MachineScreenStyle.drawSlotBackground(graphics, leftPos, topPos, slot);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = title.getString();
        if (font.width(titleText) > 88) {
            titleText = font.plainSubstrByWidth(titleText, 85) + "...";
        }
        graphics.drawString(font, titleText, 8, 6, MachineScreenStyle.TEXT_COLOR, false);
        String page = (menu.getPage() + 1) + "/" + menu.getPageCount();
        graphics.drawString(font, page, 128 - font.width(page), 6,
                menu.isRecoveryPage()
                        ? MachineScreenStyle.ERROR_TEXT_COLOR
                        : MachineScreenStyle.MUTED_TEXT_COLOR,
                false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY,
                MachineScreenStyle.MUTED_TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
