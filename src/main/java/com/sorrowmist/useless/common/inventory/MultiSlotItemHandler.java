package com.sorrowmist.useless.common.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * 多槽位物品处理器，支持高堆叠，实现 IItemHandlerModifiable 接口
 */
public class MultiSlotItemHandler implements IItemHandlerModifiable {
    private final CustomItemStorage[] slots;
    private final int slotCount;
    private final Predicate<ItemStack> globalValidator;

    public MultiSlotItemHandler(int slotCount) {
        this(slotCount, CustomItemStorage.DEFAULT_VALIDATOR);
    }

    public MultiSlotItemHandler(int slotCount, Predicate<ItemStack> globalValidator) {
        this.slotCount = slotCount;
        this.globalValidator = globalValidator;
        this.slots = new CustomItemStorage[slotCount];

        for (int i = 0; i < slotCount; i++) {
            this.slots[i] = new CustomItemStorage(0, globalValidator);
        }
    }

    public MultiSlotItemHandler setSlotCapacity(int slot, int capacity) {
        if (slot >= 0 && slot < slotCount) {
            this.slots[slot].setCapacity(capacity);
        }
        return this;
    }

    public MultiSlotItemHandler setAllSlotCapacity(int capacity) {
        for (CustomItemStorage slot : slots) {
            slot.setCapacity(capacity);
        }
        return this;
    }

    public MultiSlotItemHandler setSlotValidator(int slot, Predicate<ItemStack> validator) {
        if (slot >= 0 && slot < slotCount) {
            this.slots[slot].setValidator(validator);
        }
        return this;
    }

    @Override
    public int getSlots() {
        return slotCount;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= slotCount) {
            return ItemStack.EMPTY;
        }
        return slots[slot].getStackInSlot(0);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= slotCount || stack.isEmpty()) {
            return stack;
        }
        return slots[slot].insertItem(0, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= slotCount || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return slots[slot].extractItem(0, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= slotCount) {
            return 0;
        }
        return slots[slot].getSlotLimit(0);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= slotCount) {
            return false;
        }
        return slots[slot].isItemValid(0, stack);
    }

    // 实现 IItemHandlerModifiable 接口的方法
    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (slot >= 0 && slot < slotCount) {
            slots[slot].setItemStack(stack);
        }
    }

    // NBT 序列化
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag slotsTag = new ListTag();

        for (int i = 0; i < slotCount; i++) {
            CompoundTag slotTag = new CompoundTag();
            slots[i].write(slotTag);
            slotsTag.add(slotTag);
        }

        nbt.put("Slots", slotsTag);
        nbt.putInt("Size", slotCount);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("Slots")) {
            ListTag slotsTag = nbt.getList("Slots", 10); // 10 = CompoundTag
            for (int i = 0; i < Math.min(slotsTag.size(), slotCount); i++) {
                CompoundTag slotTag = slotsTag.getCompound(i);
                slots[i].read(slotTag);
            }
        }
    }




    // 便捷方法
    public ItemStack getItemInSlot(int slot) {
        return getStackInSlot(slot);
    }

    public void setItemInSlot(int slot, ItemStack stack) {
        setStackInSlot(slot, stack);
    }

    public boolean isSlotEmpty(int slot) {
        return getStackInSlot(slot).isEmpty();
    }

    public int getSlotStored(int slot) {
        return slots[slot].getStored();
    }

    public int getSlotCapacity(int slot) {
        return slots[slot].getCapacity();
    }

    public int getSlotSpace(int slot) {
        return slots[slot].getSpace();
    }
}