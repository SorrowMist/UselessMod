package com.sorrowmist.useless.inventory.slot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 基础槽位实现，参考 Mekanism 的 BasicInventorySlot
 * 支持超过64的堆叠限制
 */
public class BasicInventorySlot implements IInventorySlot {

    // ========== 静态工厂方法 ==========

    public static BasicInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return at(stack -> true, listener, x, y);
    }

    public static BasicInventorySlot at(Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        return at(validator, listener, x, y, Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    public static BasicInventorySlot at(Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Slots with a custom limit must allow at least one item");
        }
        return new BasicInventorySlot(limit, (stack, type) -> true, (stack, type) -> true, validator, listener, x, y);
    }

    public static BasicInventorySlot at(BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canExtract,
                                        BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canInsert,
                                        Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        return new BasicInventorySlot(canExtract, canInsert, validator, listener, x, y);
    }

    // ========== 字段 ==========

    protected ItemStack current = ItemStack.EMPTY;
    private final BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canExtract;
    private final BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canInsert;
    private final Predicate<@NotNull ItemStack> validator;
    private final int limit;
    @Nullable
    private final IContentsListener listener;
    private final int x;
    private final int y;
    protected boolean obeyStackLimit = true;

    // ========== 构造函数 ==========

    protected BasicInventorySlot(Predicate<@NotNull ItemStack> canExtract, Predicate<@NotNull ItemStack> canInsert,
                                  Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        this((stack, automationType) -> automationType == AutomationType.MANUAL || canExtract.test(stack),
             (stack, automationType) -> canInsert.test(stack), validator, listener, x, y);
    }

    protected BasicInventorySlot(BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canExtract,
                                  BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canInsert,
                                  Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        this(Item.ABSOLUTE_MAX_STACK_SIZE, canExtract, canInsert, validator, listener, x, y);
    }

    protected BasicInventorySlot(int limit, BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canExtract,
                                  BiPredicate<@NotNull ItemStack, @NotNull AutomationType> canInsert,
                                  Predicate<@NotNull ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        this.limit = limit;
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.validator = validator;
        this.listener = listener;
        this.x = x;
        this.y = y;
    }

    // ========== 核心方法实现 ==========

    @Override
    public ItemStack getStack() {
        return current;
    }

    @Override
    public void setStack(ItemStack stack) {
        setStack(stack, true);
    }

    public void setStackUnchecked(ItemStack stack) {
        setStack(stack, false);
    }

    private void setStack(ItemStack stack, boolean validateStack) {
        if (stack.isEmpty()) {
            if (current.isEmpty()) {
                return;
            }
            current = ItemStack.EMPTY;
        } else if (!validateStack || isItemValid(stack)) {
            current = stack.copy();
        } else {
            throw new RuntimeException("Invalid stack for slot: " + stack);
        }
        onContentsChanged();
    }

    @Override
    public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int needed = getLimit(stack) - current.getCount();
        if (needed <= 0 || !isItemValidForInsertion(stack, automationType)) {
            return stack;
        }

        boolean sameType = false;
        if (current.isEmpty() || (sameType = ItemStack.isSameItemSameComponents(current, stack))) {
            int toAdd = Math.min(stack.getCount(), needed);
            if (action.execute()) {
                if (sameType) {
                    current.grow(toAdd);
                    onContentsChanged();
                } else {
                    setStackUnchecked(stack.copyWithCount(toAdd));
                }
            }
            return stack.copyWithCount(stack.getCount() - toAdd);
        }
        return stack;
    }

    @Override
    public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
        if (current.isEmpty() || amount < 1 || !canExtract.test(current, automationType)) {
            return ItemStack.EMPTY;
        }

        // 确保提取数量不超过物品的最大堆叠数
        int currentAmount = Math.min(current.getCount(), current.getMaxStackSize());
        if (currentAmount < amount) {
            amount = currentAmount;
        }

        ItemStack toReturn = current.copyWithCount(amount);
        if (action.execute()) {
            current.shrink(amount);
            onContentsChanged();
        }
        return toReturn;
    }

    @Override
    public int getLimit(ItemStack stack) {
        return obeyStackLimit && !stack.isEmpty() ? Math.min(limit, stack.getMaxStackSize()) : limit;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return validator.test(stack);
    }

    public boolean isItemValidForInsertion(ItemStack stack, AutomationType automationType) {
        return validator.test(stack) && canInsert.test(stack, automationType);
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    // ========== 便捷方法 ==========

    @Override
    public int setStackSize(int amount, Action action) {
        if (current.isEmpty()) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }
        int maxStackSize = getLimit(current);
        if (amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (current.getCount() == amount || action.simulate()) {
            return amount;
        }
        current.setCount(amount);
        onContentsChanged();
        return amount;
    }

    @Override
    public int growStack(int amount, Action action) {
        int current = this.current.getCount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(amount, getLimit(this.current) - current);
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    @Override
    public boolean isEmpty() {
        return current.isEmpty();
    }

    @Override
    public int getCount() {
        return current.getCount();
    }

    // ========== 序列化 ==========

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        if (!isEmpty()) {
            // NeoForge 1.21 的 ItemStack.save() 已经支持超过64的堆叠
            nbt.put("item", current.save(provider));
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("item")) {
            // NeoForge 1.21 的 ItemStack.parseOptional() 已经支持超过64的堆叠
            setStackUnchecked(ItemStack.parseOptional(provider, nbt.getCompound("item")));
        } else {
            setEmpty();
        }
    }

    // ========== Getters ==========

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
