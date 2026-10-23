package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.network.FurnaceMenuSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Supplements vanilla's byte-sized stack counts and short-sized menu values. */
public final class FurnaceMenuSynchronizer {
    private final List<ContainerData> dataGroups = new ArrayList<>();
    private List<Integer> lastData = List.of();
    private List<ItemStack> lastSlots = List.of();
    private ItemStack lastCarried = ItemStack.EMPTY;

    public void addData(ContainerData data) {
        dataGroups.add(data);
    }

    public void broadcast(AbstractContainerMenu menu, ServerPlayer player) {
        List<Integer> data = new ArrayList<>();
        for (ContainerData group : dataGroups) {
            for (int index = 0; index < group.getCount(); index++) data.add(group.get(index));
        }
        boolean itemsChanged = lastSlots.size() != menu.slots.size()
                || !ItemStack.matches(lastCarried, menu.getCarried());
        boolean wide = menu.getCarried().getCount() > Byte.MAX_VALUE;
        for (int index = 0; index < menu.slots.size(); index++) {
            ItemStack stack = menu.slots.get(index).getItem();
            wide |= stack.getCount() > Byte.MAX_VALUE;
            if (!itemsChanged && !ItemStack.matches(lastSlots.get(index), stack)) itemsChanged = true;
        }
        boolean dataChanged = !data.equals(lastData);
        if (!dataChanged && !itemsChanged && !wide) return;

        List<ItemStack> slots = itemsChanged || wide
                ? menu.slots.stream().map(slot -> slot.getItem().copy()).toList() : List.of();
        ItemStack carried = menu.getCarried().copy();
        // A vanilla click acknowledgement can overwrite the wide counts after our last packet.
        PacketDistributor.sendToPlayer(player, new FurnaceMenuSyncPacket(menu.containerId,
                menu.getStateId(), slots, carried, dataChanged ? List.copyOf(data) : List.of()));
        if (!slots.isEmpty()) lastSlots = slots;
        lastCarried = carried;
        lastData = List.copyOf(data);
    }

    public boolean applyData(List<Integer> values) {
        if (values.isEmpty()) return true;
        int count = dataGroups.stream().mapToInt(ContainerData::getCount).sum();
        if (values.size() != count) return false;
        int offset = 0;
        for (ContainerData group : dataGroups) {
            for (int index = 0; index < group.getCount(); index++) group.set(index, values.get(offset++));
        }
        return true;
    }
}
