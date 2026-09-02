package com.sorrowmist.useless.compat.enderio;

import com.enderio.enderio.content.travel.TravelHandler;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ender IO travel integration. This class is only called after the mod presence check.
 *
 * <p>The travel-item component and the calls into {@link TravelHandler} follow Ender IO's
 * travel implementation. Reference sources:</p>
 *
 * <ul>
 *     <li><a href="https://github.com/Team-EnderIO/EnderIO/blob/dev/1.21.1/enderio/src/main/java/com/enderio/enderio/content/travel/TravelHandler.java">Ender IO TravelHandler</a></li>
 *     <li><a href="https://github.com/Team-EnderIO/EnderIO/blob/dev/1.21.1/enderio/src/main/java/com/enderio/enderio/content/travel/TravelStaffItem.java">Ender IO TravelStaffItem</a></li>
 * </ul>
 */
public final class EnderIOTravelCompat {
    public static final String MOD_ID = "enderio";

    private EnderIOTravelCompat() {
    }

    public static Item.Properties markAsTravelItem(Item.Properties properties) {
        return properties.component(EIODataComponents.TRAVEL_ITEM, false);
    }

    public static void setTravelItemEnabled(ItemStack stack, boolean enabled) {
        stack.set(EIODataComponents.TRAVEL_ITEM, enabled);
    }

    public static InteractionResult tryShortTeleport(Level level, Player player) {
        if (level.isClientSide()) {
            return TravelHandler.teleportPosition(level, player).isPresent()
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        return TravelHandler.shortTeleport(level, player)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    public static InteractionResult tryAnchorTeleport(Level level, Player player) {
        boolean success = TravelHandler.blockTeleport(level, player)
                || TravelHandler.interact(level, player);
        return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
