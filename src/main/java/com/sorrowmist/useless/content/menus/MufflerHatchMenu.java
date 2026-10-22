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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 消声仓的容器菜单：正中心只有一个只出不进的槽位。
 *
 * <p>该槽位用于取出消声仓内部积存的物品，任何形式的放入都会被拒绝，包括直接点击与快速移动。
 * 槽位坐标与 {@code MufflerHatchScreen} 中的绘制位置保持一致。</p>
 */
public final class MufflerHatchMenu extends AbstractContainerMenu {
    /** 消声仓槽位在界面内的横坐标。 */
    public static final int SLOT_X = 80;
    /** 消声仓槽位在界面内的纵坐标。 */
    public static final int SLOT_Y = 35;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final BlockPos blockPos;
    @Nullable
    private final MufflerHatchBlockEntity hatch;

    public MufflerHatchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public MufflerHatchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.MUFFLER_HATCH_MENU.get(), containerId);
        blockPos = pos.immutable();
        hatch = inventory.player.level().getBlockEntity(pos) instanceof MufflerHatchBlockEntity found
                ? found : null;
        IItemHandler output = hatch == null ? new ItemStackHandler(1) : hatch.getOutput();
        addSlot(new WithdrawOnlySlot(output, 0, SLOT_X, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18,
                        PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        // 槽位只出不进：快捷移动仅允许从消声仓槽位搬向玩家背包，反向搬运直接拒绝。
        if (index != 0 || !moveItemStackTo(source, 1, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return hatch != null && player.level().getBlockEntity(blockPos) == hatch
                && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }

    /** 只允许取出、不允许放入的槽位。 */
    private static final class WithdrawOnlySlot extends SlotItemHandler {
        WithdrawOnlySlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
