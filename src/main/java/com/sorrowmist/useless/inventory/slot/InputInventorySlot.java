package com.sorrowmist.useless.inventory.slot;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 输入槽位，参考 Mekanism 的 InputInventorySlot
 * 默认不允许外部自动化提取
 */
public class InputInventorySlot extends BasicInventorySlot {

    // ========== 静态工厂方法 ==========

    public static InputInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return at(stack -> true, listener, x, y);
    }

    public static InputInventorySlot at(Predicate<@NotNull ItemStack> isItemValid, @Nullable IContentsListener listener, int x, int y) {
        return at(stack -> true, isItemValid, listener, x, y);
    }

    public static InputInventorySlot at(Predicate<@NotNull ItemStack> insertPredicate,
                                        Predicate<@NotNull ItemStack> isItemValid,
                                        @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(insertPredicate, "Insertion check cannot be null");
        Objects.requireNonNull(isItemValid, "Item validity check cannot be null");
        return new InputInventorySlot(insertPredicate, isItemValid, listener, x, y);
    }

    // ========== 构造函数 ==========

    protected InputInventorySlot(Predicate<@NotNull ItemStack> insertPredicate,
                                  Predicate<@NotNull ItemStack> isItemValid,
                                  @Nullable IContentsListener listener, int x, int y) {
        // 输入槽默认不允许外部自动化提取
        super((stack, automationType) -> automationType == AutomationType.MANUAL || automationType == AutomationType.INTERNAL,
              (stack, automationType) -> insertPredicate.test(stack),
              isItemValid, listener, x, y);
    }
}
