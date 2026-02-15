package com.sorrowmist.useless.content.recipe.adapters;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 熔炉配方适配器
 * <p>
 * 支持原版熔炉配方
 */
public class SmeltingRecipeAdapter implements IRecipeAdapter<AbstractCookingRecipe> {

    // 能量消耗基础值
    private static final int BASE_ENERGY = 2000;
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
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料
        NonNullList<Ingredient> ingredients = originalRecipe.getIngredients();
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                countedIngredients.add(new CountedIngredient(ingredient, 1));
            }
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品
        ItemStack result = originalRecipe.getResultItem(level.registryAccess());
        List<ItemStack> outputs = List.of(result.copy());

        // 计算能量和处理时间
        int cookingTime = originalRecipe.getCookingTime();
        int processTime = cookingTime > 0 ? cookingTime : BASE_PROCESS_TIME;

        // 能量消耗根据处理时间比例调整
        int energy = (int) ((double) processTime / BASE_PROCESS_TIME * BASE_ENERGY);

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                outputs,
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<AbstractCookingRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();

        // 只查找原版熔炉配方
        return castHolder(findSmeltingRecipe(recipeManager, RecipeType.SMELTING, inputs));
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
            List<ItemStack> inputStacks
    ) {
        List<RecipeHolder<T>> recipes = recipeManager.getAllRecipesFor(recipeType);

        for (RecipeHolder<T> holder : recipes) {
            T recipe = holder.value();
            NonNullList<Ingredient> ingredients = recipe.getIngredients();

            if (ingredients.isEmpty()) continue;

            Ingredient mainIngredient = ingredients.getFirst();

            for (ItemStack stack : inputStacks) {
                if (!stack.isEmpty() && mainIngredient.test(stack)) {
                    return holder;
                }
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 100; // 高优先级
    }
}
