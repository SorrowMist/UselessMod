package com.sorrowmist.useless.recipes.advancedalloyfurnace;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;

public class AdvancedAlloyFurnaceRecipeManager {
    private static final AdvancedAlloyFurnaceRecipeManager INSTANCE = new AdvancedAlloyFurnaceRecipeManager();

    private final Map<String, List<AdvancedAlloyFurnaceRecipe>> recipeCache = new HashMap<>();

    public static AdvancedAlloyFurnaceRecipeManager getInstance() {
        return INSTANCE;
    }

    private AdvancedAlloyFurnaceRecipeManager() {}

    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipe(Level level, List<ItemStack> inputItems, FluidStack inputFluid) {
        if (level == null) return null;

        String cacheKey = generateCacheKey(inputItems, inputFluid);

        // 检查缓存
        if (recipeCache.containsKey(cacheKey)) {
            for (AdvancedAlloyFurnaceRecipe recipe : recipeCache.get(cacheKey)) {
                if (recipe.matches(inputItems, inputFluid)) {
                    return recipe;
                }
            }
        }

        // 从原版配方系统查找
        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            if (recipe.matches(inputItems, inputFluid)) {
                // 添加到缓存
                recipeCache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(recipe);
                return recipe;
            }
        }

        return null;
    }

    private String generateCacheKey(List<ItemStack> inputItems, FluidStack inputFluid) {
        StringBuilder key = new StringBuilder();

        // 物品部分
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                key.append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                key.append(":").append(stack.getCount()).append(";");
            }
        }

        // 流体部分
        if (!inputFluid.isEmpty()) {
            key.append(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(inputFluid.getFluid()));
            key.append(":").append(inputFluid.getAmount());
        }

        return key.toString();
    }

    public void clearCache() {
        recipeCache.clear();
    }

    public boolean isValidInputItem(Level level, ItemStack stack) {
        if (level == null) return false;

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getInputItems()) {
                if (ingredient.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 修复流体验证方法
    public boolean isValidInputFluid(Level level, FluidStack fluid) {
        if (fluid.isEmpty() || level == null) return false;

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            FluidStack recipeFluid = recipe.getInputFluid();
            if (!recipeFluid.isEmpty() && recipeFluid.getFluid().isSame(fluid.getFluid())) {
                return true;
            }
        }
        return false;
    }
}