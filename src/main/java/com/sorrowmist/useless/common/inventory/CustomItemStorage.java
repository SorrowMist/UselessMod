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

    protected final long baseCapacity;
    protected Predicate<ItemStack> validator;
    protected ItemStack item;
    protected long count;
    protected long capacity;

    public CustomItemStorage() {
        this(DEFAULT_VALIDATOR);
    }

    public CustomItemStorage(long capacity) {
        this(capacity, DEFAULT_VALIDATOR);
    }

    public CustomItemStorage(Predicate<ItemStack> validator) {
        this(0L, validator);
    }

    public CustomItemStorage(long capacity, Predicate<ItemStack> validator) {
        this.item = ItemStack.EMPTY;
        this.count = 0;
        this.baseCapacity = capacity;
        this.capacity = capacity;
        this.validator = validator;
    }

    public CustomItemStorage setCapacity(long capacity) {
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
        this.count = item.isEmpty() ? 0 : (long) item.getCount();
    }

    public CustomItemStorage read(CompoundTag nbt) {
        this.item = loadItemStack(nbt);
        // 从NBT中读取实际的long计数
        if (nbt.contains("LongCount")) {
            this.count = nbt.getLong("LongCount");
        } else if (nbt.contains("IntCount")) {
            this.count = nbt.getInt("IntCount");
        } else {
            this.count = this.item.isEmpty() ? 0 : (long) this.item.getCount();
        }
        return this;
    }

    public CompoundTag write(CompoundTag nbt) {
        // 保存ItemStack，但忽略其count字段
        ItemStack stack = this.item.copy();
        stack.setCount(1); // 设置为1，因为实际count存储在LongCount中
        stack.save(nbt);
        // 保存实际的long计数
        nbt.putLong("LongCount", this.count);
        return nbt;
    }

    public static ItemStack loadItemStack(CompoundTag nbt) {
        ItemStack retStack = ItemStack.of(nbt);
        if (nbt.contains("LongCount")) {
            long storedCount = nbt.getLong("LongCount");
            // 由于ItemStack的count是int类型，我们只能设置为Math.min(storedCount, Integer.MAX_VALUE)
            // 实际的long计数会在CustomItemStorage中管理
            retStack.setCount((int) Math.min(storedCount, Integer.MAX_VALUE));
        } else if (nbt.contains("IntCount")) {
            // 兼容旧的IntCount标签
            int storedCount = nbt.getInt("IntCount");
            retStack.setCount(storedCount);
        }
        return retStack;
    }

    protected static CompoundTag saveItemStack(ItemStack stack, CompoundTag nbt) {
        // 保存ItemStack的基本信息
        stack.save(nbt);
        // 总是保存为LongCount，支持更大的数值
        nbt.putLong("LongCount", stack.getCount());
        return nbt;
    }

    public ItemStack getItemStack() {
        return this.item;
    }

    public long getCount() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.count <= 0 || this.item.isEmpty();
    }

    public boolean isFull() {
        return this.count >= this.getCapacity();
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // 创建一个副本并设置正确的count（转换为int）
        ItemStack stack = this.item.copy();
        stack.setCount((int) Math.min(this.count, Integer.MAX_VALUE));
        return stack;
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

        if (this.isEmpty()) {
            // 计算可插入的数量
            long maxInsert = Math.min((long) stack.getCount(), this.capacity);
            if (!simulate) {
                this.item = stack.copy();
                this.item.setCount(1); // 实际数量存储在count字段中
                this.count = maxInsert;
            }
            
            // 计算剩余物品
            if (maxInsert >= (long) stack.getCount()) {
                return ItemStack.EMPTY;
            } else {
                return copyStackWithSize(stack, stack.getCount() - (int) maxInsert);
            }
        }
        else if (ItemStack.isSameItemSameTags(this.item, stack)) {
            // 计算剩余空间
            long availableSpace = this.capacity - this.count;
            if (availableSpace <= 0) {
                // 没有空间，返回所有物品
                return stack;
            }
            
            // 计算可添加的数量
            long addAmount = Math.min((long) stack.getCount(), availableSpace);
            if (addAmount <= 0) {
                return stack;
            }
            
            // 更新物品数量
            if (!simulate) {
                this.count += addAmount;
            }
            
            // 计算剩余物品
            if (addAmount >= (long) stack.getCount()) {
                return ItemStack.EMPTY;
            } else {
                return copyStackWithSize(stack, stack.getCount() - (int) addAmount);
            }
        }
        else {
            return stack;
        }
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || this.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 计算可提取的数量
        long retCount = Math.min(this.count, (long) amount);
        ItemStack ret = copyStackWithSize(this.item, (int) retCount);

        if (!simulate) {
            this.count -= retCount;
            if (this.count <= 0) {
                this.item = ItemStack.EMPTY;
                this.count = 0;
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

    public void modify(long quantity) {
        this.count = Math.min(this.count + quantity, this.capacity);
        if (this.count <= 0) {
            this.item = ItemStack.EMPTY;
            this.count = 0;
        }
    }

    public long getCapacity() {
        return this.capacity;
    }

    public long getStored() {
        return this.count;
    }

    public long getSpace() {
        return this.capacity - this.count;
    }

    // 辅助方法
    private static ItemStack copyStackWithSize(@Nonnull ItemStack stack, int size) {
        if (size <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }
}