package com.sorrowmist.useless.compat.enderio;

import com.enderio.base.common.handler.TravelHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ender IO 6.2 native travel calls; enabled tool recognition lives in EnderIOTravelHandlerMixin. */
public final class EnderIOTravelCompat {
    public static final String MOD_ID = "enderio";

    private EnderIOTravelCompat() {
    }

    public static Item.Properties markAsTravelItem(Item.Properties properties) {
        return properties; // 1.20.1 has no travel-item component; recognition is type/setting based.
    }

    public static void setTravelItemEnabled(ItemStack stack, boolean enabled) {
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(
                stack, com.sorrowmist.useless.core.component.UComponents.BeefTeleportEnabledComponent, enabled);
    }

    public static InteractionResult tryAnchorTeleport(Level level, Player player) {
        boolean success = TravelHandler.blockTeleport(level, player);
        return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
