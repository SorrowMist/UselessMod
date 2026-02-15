package com.sorrowmist.useless.content.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 配方适配器接口
 * <p>
 * 用于将不同类型的配方转换为高级熔炉可用的格式
 *
 * @param <T> 原始配方类型
 */
public interface IRecipeAdapter<T extends Recipe<?>> {

    /**
     * 获取适配器支持的配方类型
     *
     * @return 配方类型Class
     */
    Class<T> getRecipeClass();

    /**
     * 将原始配方转换为高级熔炉配方
     *
     * @param holder  配方持有者（包含ID和配方）
     * @param level   世界
     * @return 转换后的高级熔炉配方，如果无法转换则返回 null
     */
    @Nullable
    default AdvancedAlloyFurnaceRecipe convert(RecipeHolder<T> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /**
     * 将原始配方转换为多个高级熔炉配方（支持批量版本）
     *
     * @param holder  配方持有者（包含ID和配方）
     * @param level   世界
     * @return 转换后的高级熔炉配方列表，如果无法转换则返回空列表
     */
    default List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<T> holder, Level level) {
        AdvancedAlloyFurnaceRecipe recipe = convert(holder, level);
        return recipe == null ? List.of() : List.of(recipe);
    }

    /**
     * 检查给定的输入物品是否可能匹配此适配器处理的配方类型
     *
     * @param level      世界
     * @param inputs     输入物品列表
     * @return 是否可能匹配
     */
    boolean canHandle(Level level, List<ItemStack> inputs);

    /**
     * 查找并返回匹配的配方持有者
     *
     * @param level   世界
     * @param inputs  输入物品列表
     * @return 匹配的配方持有者，如果没有则返回 null
     */
    @Nullable
    RecipeHolder<T> findMatchingRecipe(Level level, List<ItemStack> inputs);

    /**
     * 获取此适配器的优先级
     * <p>
     * 数字越大优先级越高，优先尝试匹配
     *
     * @return 优先级数值
     */
    default int getPriority() {
        return 0;
    }
}
