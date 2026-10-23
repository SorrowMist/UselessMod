package com.sorrowmist.useless.content.menus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Checks the open machine menu before accepting a furnace control packet. */
public final class FurnaceMenuAccess {
    private FurnaceMenuAccess() {
    }

    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || player.isSpectator()
                || !player.level().hasChunkAt(pos) || !player.containerMenu.stillValid(player)
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        if (player.containerMenu instanceof AdvancedAlloyFurnaceMenu menu) {
            return menu.getBlockEntity() != null && pos.equals(menu.getBlockEntity().getBlockPos());
        }
        if (player.containerMenu instanceof MultiblockAlloyFurnaceMenu menu) return pos.equals(menu.getBlockPos());
        if (player.containerMenu instanceof PagedRecoverableMenu menu) return pos.equals(menu.getBlockPos());
        return false;
    }
}
