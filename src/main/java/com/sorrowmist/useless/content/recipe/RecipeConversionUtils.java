package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Isolates failures from individual external-recipe conversions. */
public final class RecipeConversionUtils {

    private RecipeConversionUtils() {
    }

    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level) {
        return convert(adapter, holder, level, null, false);
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level) {
        return convertAll((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter, holder, level);
    }

    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs) {
        return convert(adapter, holder, level, actualInputs, true);
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs) {
        return convertAll((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter,
                holder, level, actualInputs);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<AdvancedAlloyFurnaceRecipe> convert(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs, boolean useActualInputs) {
        if (adapter == null || holder == null) {
            return List.of();
        }

        com.sorrowmist.useless.api.recipe.IRecipeAdapter rawAdapter = adapter;
        // 兼容模组的版本错配会抛 NoClassDefFoundError / NoSuchMethodError（LinkageError），
        // 它不是 RuntimeException，此前会一路冒泡终止服务器 tick。这里统一交给守卫降级：
        // 单条配方转换失败只跳过该条（守卫只打印一次摘要），链接错误则停用整个适配器的转换动作。
        return AdapterGuards.callEach(adapter, "convertAll", () -> describeHolder(holder), () -> {
            List<AdvancedAlloyFurnaceRecipe> converted = useActualInputs
                    ? rawAdapter.convertAll((RecipeHolder) holder, level, actualInputs)
                    : rawAdapter.convertAll((RecipeHolder) holder, level);
            return immutableNonNull(converted);
        }, List.of());
    }

    /** 摘要日志里使用的条目标识，避免为了打日志在每次转换前拼接字符串。 */
    private static String describeHolder(RecipeHolder<?> holder) {
        return holder == null ? "<null recipe>" : holder.id().toString();
    }

    /**
     * 把适配器返回值规整成不可变列表。
     *
     * <p>适配器可能返回 {@code null}、含 {@code null} 元素的列表或可变列表。含空元素的列表直接
     * 交给下游会在匹配阶段抛空指针，而 {@link List#copyOf} 也会拒绝空元素；这里统一过滤掉空
     * 元素后再冻结，让坏数据只损失那一条，不影响同一配方的其它转换结果。</p>
     */
    private static List<AdvancedAlloyFurnaceRecipe> immutableNonNull(
            List<AdvancedAlloyFurnaceRecipe> converted) {
        if (converted == null || converted.isEmpty()) return List.of();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>(converted.size());
        for (AdvancedAlloyFurnaceRecipe recipe : converted) {
            if (recipe != null) result.add(recipe);
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }
}
