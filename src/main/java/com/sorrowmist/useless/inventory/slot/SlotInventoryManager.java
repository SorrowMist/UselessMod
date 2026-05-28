package com.sorrowmist.useless.inventory.slot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 槽位库存管理器
 * 简化多个槽位的创建、管理和序列化
 */
public class SlotInventoryManager {

    private final List<IInventorySlot> slots = new ArrayList<>();
    private final IContentsListener listener;

    public SlotInventoryManager(@Nullable IContentsListener listener) {
        this.listener = listener;
    }

    // ========== 槽位创建方法 ==========

    /**
     * 添加输入槽
     *
     * @param count    槽位数量
     * @param capacity 每个槽位的容量
     * @param xStart   起始X坐标
     * @param yStart   起始Y坐标
     * @param columns  每行槽位数
     */
    public List<LargeInventorySlot> addInputSlots(int count, int capacity,
                                                   int xStart, int yStart, int columns) {
        List<LargeInventorySlot> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = xStart + (i % columns) * 18;
            int y = yStart + (i / columns) * 18;
            LargeInventorySlot slot = LargeInventorySlot.createInput(capacity, listener, x, y);
            slots.add(slot);
            created.add(slot);
        }
        return created;
    }

    /**
     * 添加输出槽
     */
    public List<LargeInventorySlot> addOutputSlots(int count, int capacity,
                                                    int xStart, int yStart, int columns) {
        List<LargeInventorySlot> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = xStart + (i % columns) * 18;
            int y = yStart + (i / columns) * 18;
            LargeInventorySlot slot = LargeInventorySlot.createOutput(capacity, listener, x, y);
            slots.add(slot);
            created.add(slot);
        }
        return created;
    }

    /**
     * 添加通用槽位
     */
    public List<LargeInventorySlot> addSlots(int count, int capacity,
                                              int xStart, int yStart, int columns,
                                              Function<LargeInventorySlot, LargeInventorySlot> configurator) {
        List<LargeInventorySlot> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = xStart + (i % columns) * 18;
            int y = yStart + (i / columns) * 18;
            LargeInventorySlot slot = LargeInventorySlot.create(capacity, listener, x, y);
            if (configurator != null) {
                slot = configurator.apply(slot);
            }
            slots.add(slot);
            created.add(slot);
        }
        return created;
    }

    /**
     * 添加单个槽位
     */
    public void addSlot(IInventorySlot slot) {
        slots.add(slot);
    }

    // ========== 槽位访问方法 ==========

    /**
     * 获取所有槽位
     */
    public List<IInventorySlot> getAllSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * 获取槽位数量
     */
    public int getSlotCount() {
        return slots.size();
    }

    /**
     * 获取指定索引的槽位
     */
    public IInventorySlot getSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IndexOutOfBoundsException("Slot index: " + index);
        }
        return slots.get(index);
    }

    /**
     * 获取指定范围的槽位
     */
    public List<IInventorySlot> getSlots(int start, int count) {
        if (start < 0 || start + count > slots.size()) {
            throw new IndexOutOfBoundsException("Range: [" + start + ", " + (start + count) + ")");
        }
        return Collections.unmodifiableList(slots.subList(start, start + count));
    }

    // ========== 便捷操作 ==========

    /**
     * 检查所有槽位是否为空
     */
    public boolean isEmpty() {
        for (IInventorySlot slot : slots) {
            if (!slot.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 清空所有槽位
     */
    public void clear() {
        for (IInventorySlot slot : slots) {
            slot.setEmpty();
        }
    }

    /**
     * 获取所有非空槽位中的物品
     */
    public List<ItemStack> getNonEmptyStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (IInventorySlot slot : slots) {
            if (!slot.isEmpty()) {
                result.add(slot.getStack());
            }
        }
        return result;
    }

    /**
     * 查找第一个可以接收指定物品的槽位
     */
    public IInventorySlot findSlotForInsertion(ItemStack stack) {
        for (IInventorySlot slot : slots) {
            if (slot.isItemValid(stack)) {
                ItemStack remainder = slot.insertItem(stack, Action.SIMULATE, AutomationType.INTERNAL);
                if (remainder.getCount() < stack.getCount()) {
                    return slot;
                }
            }
        }
        return null;
    }

    // ========== 序列化 ==========

    /**
     * 序列化所有槽位
     */
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("index", i);
            slotTag.put("data", slots.get(i).serializeNBT(provider));
            list.add(slotTag);
        }

        tag.put("slots", list);
        tag.putInt("count", slots.size());
        return tag;
    }

    /**
     * 反序列化所有槽位
     */
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (!tag.contains("slots")) {
            return;
        }

        ListTag list = tag.getList("slots", 10); // 10 = CompoundTag
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slotTag = list.getCompound(i);
            int index = slotTag.getInt("index");
            if (index >= 0 && index < slots.size()) {
                slots.get(index).deserializeNBT(provider, slotTag.getCompound("data"));
            }
        }
    }

    /**
     * 序列化指定范围的槽位
     */
    public CompoundTag serializeRangeNBT(HolderLookup.Provider provider, int start, int count) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (int i = start; i < start + count && i < slots.size(); i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("index", i - start);
            slotTag.put("data", slots.get(i).serializeNBT(provider));
            list.add(slotTag);
        }

        tag.put("slots", list);
        tag.putInt("count", count);
        return tag;
    }
}
