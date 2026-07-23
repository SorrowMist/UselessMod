package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.content.menus.OmniversalMoldHubMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MoldHubScreen extends PagedRecoverableScreen<OmniversalMoldHubMenu> {
    public MoldHubScreen(OmniversalMoldHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
