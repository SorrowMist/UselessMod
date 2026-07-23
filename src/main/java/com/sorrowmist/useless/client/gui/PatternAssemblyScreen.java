package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class PatternAssemblyScreen extends PagedRecoverableScreen<MePatternAssemblyMenu> {
    public PatternAssemblyScreen(MePatternAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < MePatternAssemblyMenu.SLOTS_PER_PAGE
                && PatternSlotRenderer.renderPattern(graphics, font, slot.getItem(), slot.x, slot.y,
                slot.x + slot.y * imageWidth, minecraft == null ? null : minecraft.level)) {
            return;
        }
        super.renderSlot(graphics, slot);
    }
}
