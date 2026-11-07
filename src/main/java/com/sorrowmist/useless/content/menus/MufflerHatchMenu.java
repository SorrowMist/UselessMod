package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** One output-only ash slot, followed by the player's inventory. */
public final class MufflerHatchMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 104;
    public static final int SLOT_Y = 53;
    public static final int PLAYER_INVENTORY_Y = 110;
    public static final int HOTBAR_Y = 168;
    private final BlockPos blockPos;
    private final Level level;
    private final MufflerHatchBlockEntity hatch;

    public MufflerHatchMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos());
    }

    public MufflerHatchMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenuType.MUFFLER_HATCH_MENU.get(), id);
        blockPos = pos.immutable();
        level = inventory.player.level();
        hatch = level.getBlockEntity(pos) instanceof MufflerHatchBlockEntity found ? found : null;
        // Never deserialize menu slot packets into a real client-side BE inventory.
        IItemHandler output = !level.isClientSide && hatch != null ? hatch.getOutput() : new ItemStackHandler(1);
        addSlot(new WithdrawOnlySlot(output, 0, SLOT_X, SLOT_Y));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 32 + col * 18, PLAYER_INVENTORY_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 32 + col * 18, HOTBAR_Y));
    }

    public BlockPos getBlockPos() { return blockPos; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index != 0 || !slots.get(0).hasItem()) return ItemStack.EMPTY;
        Slot slot = slots.get(0);
        ItemStack source = slot.getItem(), original = source.copy();
        if (!moveItemStackTo(source, 1, slots.size(), true)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return player.level() == level && level.hasChunkAt(blockPos)
                && level.getBlockEntity(blockPos) instanceof MufflerHatchBlockEntity found
                && (hatch == null || found == hatch)
                && player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    static final class WithdrawOnlySlot extends SlotItemHandler {
        WithdrawOnlySlot(IItemHandler handler, int index, int x, int y) { super(handler, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
