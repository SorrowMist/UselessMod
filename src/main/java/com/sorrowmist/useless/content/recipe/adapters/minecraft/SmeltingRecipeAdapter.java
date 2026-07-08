package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 熔炉配方适配器
 * <p>
 * 支持原版熔炉配方
 */
public class SmeltingRecipeAdapter implements IRecipeAdapter<AbstractCookingRecipe> {

    // 处理时间基础值（ticks）
    private static final int BASE_PROCESS_TIME = 200;

    @Override
    public Class<AbstractCookingRecipe> getRecipeClass() {
        return AbstractCookingRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<AbstractCookingRecipe> holder, Level level) {
        if (holder == null) return null;

        AbstractCookingRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取输入材料
        NonNullList<Ingredient> ingredients = originalRecipe.getIngredients();
        List<CountedIngredient> countedIngredients = AdapterUtils.mergeIngredients(ingredients);

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品
        ItemStack result = originalRecipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        List<ItemStack> outputs = List.of(result.copy());

        // 计算能量和处理时间
        int cookingTime = originalRecipe.getCookingTime();
        int processTime = cookingTime > 0 ? cookingTime : BASE_PROCESS_TIME;

        // 能量消耗根据处理时间比例调整
        int energy = AdapterUtils.safeInt((long) processTime * AdapterUtils.DEFAULT_ENERGY / BASE_PROCESS_TIME);

        // 熔炉配方需要熔炉作为模具/标志物
        Ingredient furnaceMold = AdapterUtils.toMoldIngredient(getMoldItem());

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                outputs,
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                furnaceMold,
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(Items.FURNACE);
    }

    @Override
    @Nullable
    public RecipeHolder<AbstractCookingRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();

        // 只查找原版熔炉配方
        return castHolder(findSmeltingRecipe(recipeManager, RecipeType.SMELTING, mergedInputs));
    }

    /**
     * 安全地转换配方持有者类型
     */
    @SuppressWarnings("unchecked")
    private <T extends AbstractCookingRecipe> RecipeHolder<AbstractCookingRecipe> castHolder(RecipeHolder<T> holder) {
        return (RecipeHolder<AbstractCookingRecipe>) holder;
    }

    /**
     * 查找匹配的熔炉配方
     */
    @Nullable
    private <T extends AbstractCookingRecipe> RecipeHolder<T> findSmeltingRecipe(
            RecipeManager recipeManager,
            RecipeType<T> recipeType,
            Map<Ingredient, Long> mergedInputs
    ) {
        List<RecipeHolder<T>> recipes = recipeManager.getAllRecipesFor(recipeType);

        for (RecipeHolder<T> holder : recipes) {
            T recipe = holder.value();
            NonNullList<Ingredient> ingredients = recipe.getIngredients();

            if (ingredients.isEmpty()) continue;

            Ingredient mainIngredient = ingredients.getFirst();

            if (AdapterUtils.hasMatchingIngredient(mergedInputs, mainIngredient, 1L)) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(Items.FURNACE);
    }

}
