package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.inventories.BaseInternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把紧凑 F9 内部所有影子样板总线的「终端样板库存」串成一个连续库存。
 *
 * <p>对外的槽位编号是各总线顺序拼接后的绝对下标，读写一律经底层总线自己的
 * {@code setPatternDirect}，从而保证每条总线的空槽索引、内容版本与目录通知正常更新。</p>
 */
public final class CombinedPatternInventory extends BaseInternalInventory {

    private final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
    private final List<Integer> offsets = new ArrayList<>();
    private int total;

    /** 绑定（或重新绑定）底层总线；对外的槽位编号是各总线顺序拼接后的绝对下标。 */
    public void bind(List<ECOCraftingPatternBusBlockEntity> source) {
        buses.clear();
        offsets.clear();
        total = 0;
        for (ECOCraftingPatternBusBlockEntity bus : source) {
            if (bus.getPatternSlotCount() <= 0) {
                continue;
            }
            offsets.add(total);
            buses.add(bus);
            total += bus.getPatternSlotCount();
        }
    }

    public boolean isEmpty() {
        return total <= 0;
    }

    @Override
    public int size() {
        syncOffsets();
        return total;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        syncOffsets();
        int part = partOf(slot);
        return part < 0 ? ItemStack.EMPTY : bus(part).getTerminalPatternInventory().getStackInSlot(localSlot(part, slot));
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        syncOffsets();
        int part = partOf(slot);
        if (part < 0) {
            return;
        }
        bus(part).setPatternDirect(localSlot(part, slot), stack);
        syncOffsets();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        syncOffsets();
        int part = partOf(slot);
        return part >= 0 && bus(part).getTerminalPatternInventory().isItemValid(localSlot(part, slot), stack);
    }

    /** 合并视图里该槽位所属的底层总线；越界返回 null。 */
    @Nullable
    public ECOCraftingPatternBusBlockEntity busOf(int slot) {
        syncOffsets();
        int part = partOf(slot);
        return part < 0 ? null : bus(part);
    }

    /** 合并视图槽位在所属底层总线内的下标；越界返回 -1。 */
    public int localSlotOf(int slot) {
        syncOffsets();
        int part = partOf(slot);
        return part < 0 ? -1 : localSlot(part, slot);
    }

    /** 某条底层总线在合并视图里的起始下标；不属于本视图时返回 -1。 */
    public int offsetOf(ECOCraftingPatternBusBlockEntity bus) {
        syncOffsets();
        int index = buses.indexOf(bus);
        return index < 0 ? -1 : offsets.get(index);
    }

    /**
     * 单条总线的页数会随占用槽位增长，合并视图的下标随之整体后移；
     * 对外读之前先对一次总长，避免拿旧下标去读新布局。
     */
    private void syncOffsets() {
        int running = 0;
        for (int index = 0; index < buses.size(); index++) {
            int next = running + bus(index).getPatternSlotCount();
            if (offsets.get(index) != running) {
                offsets.set(index, running);
            }
            running = next;
        }
        total = running;
    }

    private ECOCraftingPatternBusBlockEntity bus(int part) {
        return buses.get(part);
    }

    private int localSlot(int part, int slot) {
        return slot - offsets.get(part);
    }

    private int partOf(int slot) {
        if (slot < 0 || slot >= total) {
            return -1;
        }
        int low = 0;
        int high = offsets.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (offsets.get(middle) <= slot) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }
}
