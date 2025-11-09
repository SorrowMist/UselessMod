package com.sorrowmist.useless.common.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * 自定义物品存储类，支持高堆叠上限
 * 参考热力膨胀的设计思路，但不依赖其类
 */
public class CustomItemStorage implements IItemHandler {
    protected static final Predicate<ItemStack> DEFAULT_VALIDATOR = (stack) -> true;

    protected final int baseCapacity;
    protected Predicate<ItemStack> validator;
    protected ItemStack item;
    protected int capacity;

    public CustomItemStorage() {
        this(DEFAULT_VALIDATOR);
    }

    public CustomItemStorage(int capacity) {
        this(capacity, DEFAULT_VALIDATOR);
    }

    public CustomItemStorage(Predicate<ItemStack> validator) {
        this(0, validator);
    }

    public CustomItemStorage(int capacity, Predicate<ItemStack> validator) {
        this.item = ItemStack.EMPTY;
        this.baseCapacity = capacity;
        this.capacity = capacity;
        this.validator = validator;
    }

    public CustomItemStorage setCapacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public CustomItemStorage setValidator(Predicate<ItemStack> validator) {
        if (validator != null) {
            this.validator = validator;
        }
        return this;
    }

    public boolean isItemValid(@Nonnull ItemStack stack) {
        return this.validator.test(stack);
    }

    public void setItemStack(ItemStack item) {
        this.item = item.isEmpty() ? ItemStack.EMPTY : item;
    }

    public CustomItemStorage read(CompoundTag nbt) {
        this.item = loadItemStack(nbt);
        return this;
    }

    public CompoundTag write(CompoundTag nbt) {
        return saveItemStack(this.item, nbt);
    }

    public static ItemStack loadItemStack(CompoundTag nbt) {
        ItemStack retStack = ItemStack.of(nbt);
        if (nbt.contains("IntCount")) {
            int storedCount = nbt.getInt("IntCount");
            if (retStack.getCount() < storedCount) {
                retStack.setCount(storedCount);
            }
        }
        return retStack;
    }

    protected static CompoundTag saveItemStack(ItemStack stack, CompoundTag nbt) {
        stack.save(nbt);
        if (stack.getCount() > 127) {
            nbt.putInt("IntCount", stack.getCount());
        }
        return nbt;
    }

    public ItemStack getItemStack() {
        return this.item;
    }

    public int getCount() {
        return this.item.getCount();
    }

    public boolean isEmpty() {
        return this.item.isEmpty();
    }

    public boolean isFull() {
        return this.item.getCount() >= this.getCapacity();
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.item;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!this.isItemValid(stack)) {
            return stack;
        }

        if (this.item.isEmpty()) {
            int maxStack = this.getSlotLimit(slot);
            int count = Math.min(stack.getCount(), maxStack);
            if (!simulate) {
                this.setItemStack(copyStackWithSize(stack, count));
            }
            return count >= stack.getCount() ? ItemStack.EMPTY : copyStackWithSize(stack, stack.getCount() - count);
        }
        else if (ItemStack.isSameItemSameTags(this.item, stack)) {
            int totalCount = this.item.getCount() + stack.getCount();
            int limit = this.getSlotLimit(0);

            if (totalCount <= limit) {
                if (!simulate) {
                    this.item.setCount(totalCount);
                }
                return ItemStack.EMPTY;
            }
            else {
                if (!simulate) {
                    this.item.setCount(limit);
                }
                return copyStackWithSize(stack, totalCount - limit);
            }
        }
        else {
            return stack;
        }
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || this.item.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int retCount = Math.min(this.item.getCount(), amount);
        ItemStack ret = copyStackWithSize(this.item, retCount);

        if (!simulate) {
            this.item.shrink(retCount);
            if (this.item.isEmpty()) {
                this.setItemStack(ItemStack.EMPTY);
            }
        }

        return ret;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return this.isItemValid(stack);
    }

    public boolean clear() {
        if (this.isEmpty()) {
            return false;
        }
        this.item = ItemStack.EMPTY;
        return true;
    }

    public void modify(int quantity) {
        this.item.setCount(Math.min(this.item.getCount() + quantity, this.getCapacity()));
        if (this.item.isEmpty()) {
            this.item = ItemStack.EMPTY;
        }
    }

    public int getCapacity() {
        return this.getSlotLimit(0);
    }

    public int getStored() {
        return this.item.getCount();
    }

    public int getSpace() {
        return this.getCapacity() - this.getStored();
    }

    // 辅助方法
    private static ItemStack copyStackWithSize(@Nonnull ItemStack stack, int size) {
        if (size <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }
}