package com.sorrowmist.useless.inventory.slot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 槽位接口，参考 Mekanism 的 IInventorySlot
 */
public interface IInventorySlot extends INBTSerializable<CompoundTag> {

    /**
     * 获取槽位中的物品堆
     * 注意：返回的 ItemStack 不应该被修改！
     */
    ItemStack getStack();

    /**
     * 设置槽位中的物品堆
     */
    void setStack(ItemStack stack);

    /**
     * 向槽位中插入物品
     *
     * @param stack          要插入的物品
     * @param action         操作类型（执行或模拟）
     * @param automationType 自动化类型
     * @return 未能插入的剩余物品
     */
    default ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int needed = getLimit(stack) - getCount();
        if (needed <= 0 || !isItemValid(stack)) {
            return stack;
        }

        boolean sameType = false;
        if (isEmpty() || (sameType = ItemStack.isSameItemSameComponents(getStack(), stack))) {
            int toAdd = Math.min(stack.getCount(), needed);
            if (action.execute()) {
                if (sameType) {
                    growStack(toAdd, action);
                } else {
                    setStack(stack.copyWithCount(toAdd));
                }
            }
            return stack.copyWithCount(stack.getCount() - toAdd);
        }
        return stack;
    }

    /**
     * 从槽位中提取物品
     *
     * @param amount         要提取的数量
     * @param action         操作类型（执行或模拟）
     * @param automationType 自动化类型
     * @return 提取的物品堆
     */
    default ItemStack extractItem(int amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount < 1) {
            return ItemStack.EMPTY;
        }

        ItemStack current = getStack();
        // 确保提取数量不超过物品的最大堆叠数
        int currentAmount = Math.min(current.getCount(), current.getMaxStackSize());
        if (currentAmount < amount) {
            amount = currentAmount;
        }

        ItemStack toReturn = current.copyWithCount(amount);
        if (action.execute()) {
            shrinkStack(amount, action);
        }
        return toReturn;
    }

    /**
     * 获取槽位的容量限制
     *
     * @param stack 要检查的物品堆
     * @return 槽位可以容纳的最大数量
     */
    int getLimit(ItemStack stack);

    /**
     * 检查物品是否对槽位有效
     */
    boolean isItemValid(ItemStack stack);

    /**
     * 设置堆叠大小
     *
     * @param amount 目标大小
     * @param action 操作类型
     * @return 实际设置的大小
     */
    default int setStackSize(int amount, Action action) {
        if (isEmpty()) {
            return 0;
        }
        if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }

        int maxSize = getLimit(getStack());
        if (amount > maxSize) {
            amount = maxSize;
        }

        if (getStack().getCount() == amount || action.simulate()) {
            return amount;
        }

        setStack(getStack().copyWithCount(amount));
        return amount;
    }

    /**
     * 增加堆叠数量
     *
     * @param amount 要增加的数量
     * @param action 操作类型
     * @return 实际增加的数量
     */
    default int growStack(int amount, Action action) {
        int current = getCount();
        if (current == 0) {
            return 0;
        }
        if (amount > 0) {
            int limit = getLimit(getStack());
            amount = Math.min(amount, limit - current);
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    /**
     * 减少堆叠数量
     *
     * @param amount 要减少的数量
     * @param action 操作类型
     * @return 实际减少的数量
     */
    default int shrinkStack(int amount, Action action) {
        return -growStack(-amount, action);
    }

    /**
     * 检查槽位是否为空
     */
    default boolean isEmpty() {
        return getStack().isEmpty();
    }

    /**
     * 清空槽位
     */
    default void setEmpty() {
        setStack(ItemStack.EMPTY);
    }

    /**
     * 获取当前堆叠数量
     */
    default int getCount() {
        return getStack().getCount();
    }

    /**
     * 槽位内容变化时的回调
     */
    default void onContentsChanged() {
    }

    @Override
    default CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        if (!isEmpty()) {
            nbt.put("item", getStack().save(provider));
        }
        return nbt;
    }

    @Override
    default void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("item")) {
            setStack(ItemStack.parseOptional(provider, nbt.getCompound("item")));
        } else {
            setEmpty();
        }
    }
}
