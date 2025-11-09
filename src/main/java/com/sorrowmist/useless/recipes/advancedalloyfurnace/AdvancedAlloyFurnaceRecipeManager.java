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

    // 新增：优先查找使用催化剂或模具的配方
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipeWithCatalystOrMold(Level level, List<ItemStack> inputItems,
                                                                  FluidStack inputFluid, ItemStack catalyst,
                                                                  ItemStack mold) {
        if (level == null) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        // 优先查找需要催化剂或模具的配方
        List<AdvancedAlloyFurnaceRecipe> prioritizedRecipes = new ArrayList<>();
        List<AdvancedAlloyFurnaceRecipe> normalRecipes = new ArrayList<>();

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            if (recipe.requiresCatalyst() || recipe.requiresMold()) {
                prioritizedRecipes.add(recipe);
            } else {
                normalRecipes.add(recipe);
            }
        }

        // 先检查需要催化剂或模具的配方
        for (AdvancedAlloyFurnaceRecipe recipe : prioritizedRecipes) {
            if (recipe.matches(inputItems, inputFluid, catalyst, mold)) {
                com.sorrowmist.useless.UselessMod.LOGGER.debug("Found prioritized recipe with catalyst/mold: {}", recipe.getId());
                return recipe;
            }
        }

        // 如果没有找到优先配方，检查普通配方
        for (AdvancedAlloyFurnaceRecipe recipe : normalRecipes) {
            if (recipe.matches(inputItems, inputFluid)) {
                com.sorrowmist.useless.UselessMod.LOGGER.debug("Found normal recipe: {}", recipe.getId());
                return recipe;
            }
        }

        return null;
    }

    // 修改原有的getRecipe方法，使用新的优先级逻辑
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipe(Level level, List<ItemStack> inputItems, FluidStack inputFluid) {
        // 使用空的催化剂和模具槽位来调用新方法
        return getRecipeWithCatalystOrMold(level, inputItems, inputFluid, ItemStack.EMPTY, ItemStack.EMPTY);
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