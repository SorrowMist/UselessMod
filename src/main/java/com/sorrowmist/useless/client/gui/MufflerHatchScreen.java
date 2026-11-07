package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.*;
import com.sorrowmist.useless.content.menus.MufflerHatchMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Output-only ash access using the existing Apple inventory widgets. */
public final class MufflerHatchScreen extends AppleContainerScreen<MufflerHatchMenu> {
    public MufflerHatchScreen(MufflerHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 204;
    }

    @Override protected void buildApple(AppleHost h) {
        h.add(new AppleWindow(imageWidth, imageHeight, () -> title).icon(AppleUi.Icon.CUBE, AppleStyle.TEAL));
        h.add(new AppleCard(16, 36, 192, 48));
        h.add(new AppleLabel(27, 41, 170, AppleLabel.Kind.FOOTNOTE,
                () -> Component.translatable("gui.useless_mod.muffler_hatch.output")));
        inventoryCard(h, 32, MufflerHatchMenu.PLAYER_INVENTORY_Y);
        h.add(slotGrid());
    }
}
