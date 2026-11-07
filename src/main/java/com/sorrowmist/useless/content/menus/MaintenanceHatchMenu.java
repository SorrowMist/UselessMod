package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.network.MaintenanceCalibrationSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A slotless menu also provides the server-side authority/distance boundary for calibration packets. */
public final class MaintenanceHatchMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;
    private final MaintenanceHatchBlockEntity hatch;
    private MaintenanceCalibrationSyncPacket calibration;
    private long calibrationLocalTick;

    public MaintenanceCalibrationSyncPacket getCalibration() { return calibration; }
    public long getCalibrationLocalTick() { return calibrationLocalTick; }
    public void receiveCalibration(MaintenanceCalibrationSyncPacket packet, long localTick) {
        if (packet.containerId() != containerId || !packet.pos().equals(pos)) return;
        calibration = packet;
        calibrationLocalTick = localTick;
    }

    public MaintenanceHatchMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos());
    }

    public MaintenanceHatchMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenuType.MAINTENANCE_HATCH_MENU.get(), id);
        this.pos = pos.immutable();
        level = inventory.player.level();
        hatch = level.getBlockEntity(pos) instanceof MaintenanceHatchBlockEntity found ? found : null;
    }

    public BlockPos getBlockPos() { return pos; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return player.level() == level && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof MaintenanceHatchBlockEntity found
                && (hatch == null || hatch == found)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!level.isClientSide && hatch != null) hatch.abortCalibration(player.getUUID());
    }
}
