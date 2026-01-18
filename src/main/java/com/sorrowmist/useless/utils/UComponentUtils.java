package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class UComponentUtils {
    /**
     * 获取物品的功能模式集合
     * @param stack 物品栈
     * @return 功能模式集合，如果不存在则返回空集合
     */
    public static EnumSet<FunctionMode> getFunctionModes(ItemStack stack) {
        return stack.getOrDefault(UComponents.FunctionModesComponent.get(), EnumSet.noneOf(FunctionMode.class));
    }

    /**
     * 检查物品是否具有特定的功能模式
     * @param stack 物品栈
     * @param mode 要检查的功能模式
     * @return 如果物品具有该功能模式则返回true，否则返回false
     */
    public static boolean hasFunctionMode(ItemStack stack, FunctionMode mode) {
        return getFunctionModes(stack).contains(mode);
    }
}