// HighStackSlot.java
package com.sorrowmist.useless.menu.slot;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

public class HighStackSlot extends SlotItemHandler {
    public HighStackSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public int getMaxStackSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxStackSize(@Nonnull ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    /**
     * 获取用于显示的堆叠，可以修改显示的数量等
     */
    public ItemStack getDisplayStack() {
        ItemStack stack = getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 如果数量大于1，创建一个显示用的堆叠（数量为1，实际数量通过工具提示显示）
        if (stack.getCount() > 1) {
            ItemStack displayStack = stack.copy();
            displayStack.setCount(1);
            return displayStack;
        }

        return stack;
    }
}