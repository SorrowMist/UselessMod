package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class OmniversalMoldHubMenu extends PagedRecoverableMenu {
    public OmniversalMoldHubMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public OmniversalMoldHubMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.OMNIVERSAL_MOLD_HUB_MENU.get(), containerId, inventory, handler(inventory, pos), pos);
    }

    private static RecoverableItemStackHandler handler(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof OmniversalMoldHubBlockEntity hub) {
            return hub.getMolds();
        }
        return new RecoverableItemStackHandler(ConfigManager::getOmniversalMoldSlots, stack -> false, () -> {});
    }
}
