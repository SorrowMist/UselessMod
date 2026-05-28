package com.sorrowmist.useless.inventory.slot;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 大容量槽位 - 支持超过64堆叠的槽位实现
 * 这是按照 Mekanism Bin 的思路实现的
 */
public class LargeInventorySlot extends BasicInventorySlot {

    // ========== 静态工厂方法 ==========

    /**
     * 创建一个大容量槽位
     *
     * @param capacity 容量（可以超过64）
     * @param listener 内容变化监听器
     * @param x        GUI X坐标
     * @param y        GUI Y坐标
     */
    public static LargeInventorySlot create(int capacity, @Nullable IContentsListener listener, int x, int y) {
        return create(capacity, listener, stack -> true, x, y);
    }

    /**
     * 创建一个大容量槽位（带验证器）
     *
     * @param capacity  容量（可以超过64）
     * @param listener  内容变化监听器
     * @param validator 物品验证器
     * @param x         GUI X坐标
     * @param y         GUI Y坐标
     */
    public static LargeInventorySlot create(int capacity, @Nullable IContentsListener listener,
                                            Predicate<@NotNull ItemStack> validator, int x, int y) {
        Objects.requireNonNull(validator, "Validator cannot be null");
        return new LargeInventorySlot(capacity, listener, validator, x, y);
    }

    /**
     * 创建输入用大容量槽位
     *
     * @param capacity 容量
     * @param listener 监听器
     * @param x        X坐标
     * @param y        Y坐标
     */
    public static LargeInventorySlot createInput(int capacity, @Nullable IContentsListener listener, int x, int y) {
        return new LargeInventorySlot(capacity, listener, stack -> true, x, y) {
            @Override
            protected boolean canExtract(ItemStack stack, AutomationType type) {
                // 输入槽不允许外部自动化提取
                return type != AutomationType.EXTERNAL;
            }
        };
    }

    /**
     * 创建输出用大容量槽位
     *
     * @param capacity 容量
     * @param listener 监听器
     * @param x        X坐标
     * @param y        Y坐标
     */
    public static LargeInventorySlot createOutput(int capacity, @Nullable IContentsListener listener, int x, int y) {
        return new LargeInventorySlot(capacity, listener, stack -> true, x, y) {
            @Override
            protected boolean canInsert(ItemStack stack, AutomationType type) {
                // 输出槽不允许外部自动化插入
                return type != AutomationType.EXTERNAL;
            }
        };
    }

    // ========== 构造函数 ==========

    protected LargeInventorySlot(int capacity, @Nullable IContentsListener listener,
                                  Predicate<@NotNull ItemStack> validator, int x, int y) {
        super(capacity, (stack, type) -> true, (stack, type) -> true, validator, listener, x, y);
        // 关键：不遵守默认堆叠限制
        this.obeyStackLimit = false;
    }

    // 子类可以覆盖这些方法来控制插入/提取
    protected boolean canExtract(ItemStack stack, AutomationType type) {
        return true;
    }

    protected boolean canInsert(ItemStack stack, AutomationType type) {
        return true;
    }

    @Override
    public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
        if (!canInsert(stack, automationType)) {
            return stack;
        }
        return super.insertItem(stack, action, automationType);
    }

    @Override
    public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
        if (!canExtract(current, automationType)) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(amount, action, automationType);
    }
}
