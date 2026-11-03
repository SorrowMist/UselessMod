package com.sorrowmist.useless.content.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;

/** Isolates failures from individual external-recipe conversions. */
public final class RecipeConversionUtils {
    // 本包内的 IRecipeAdapter 是旧桥接接口，与新 API 接口同名。新接口的引用只能写全限定名，
    // 否则简名会解析为旧接口，并与下方 deprecated 重载产生签名冲突。
    private static final Logger LOGGER = LogUtils.getLogger();

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
        // 单条配方转换失败只跳过该条，链接错误则停用整个适配器的转换动作。
        return AdapterGuards.callEach(adapter, "convertAll", () -> {
            List<AdvancedAlloyFurnaceRecipe> converted = useActualInputs
                    ? rawAdapter.convertAll((RecipeHolder) holder, level, actualInputs)
                    : rawAdapter.convertAll((RecipeHolder) holder, level);
            return converted == null ? List.of() : List.copyOf(converted);
        }, List.of());
    }
}
