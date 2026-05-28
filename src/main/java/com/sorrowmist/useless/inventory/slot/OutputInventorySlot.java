package com.sorrowmist.useless.inventory.slot;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 输出槽位，参考 Mekanism 的 OutputInventorySlot
 * 默认不允许外部自动化插入
 */
public class OutputInventorySlot extends BasicInventorySlot {

    // ========== 静态工厂方法 ==========

    public static OutputInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return at(stack -> true, listener, x, y);
    }

    public static OutputInventorySlot at(Predicate<@NotNull ItemStack> isItemValid, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(isItemValid, "Item validity check cannot be null");
        return new OutputInventorySlot(isItemValid, listener, x, y);
    }

    // ========== 构造函数 ==========

    protected OutputInventorySlot(Predicate<@NotNull ItemStack> isItemValid,
                                   @Nullable IContentsListener listener, int x, int y) {
        // 输出槽默认不允许外部自动化插入
        super((stack, automationType) -> true,
              (stack, automationType) -> automationType != AutomationType.EXTERNAL,
              isItemValid, listener, x, y);
    }
}
