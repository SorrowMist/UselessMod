package com.sorrowmist.useless.recipes.advancedalloyfurnace;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
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

    // 优化后的配方查找方法
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipeWithCatalystOrMold(Level level, List<ItemStack> inputItems, 
                                                                  FluidStack inputFluid, ItemStack catalyst, 
                                                                  ItemStack mold) {
        if (level == null) {
            return null;
        }

        // 提前检查输入是否为空
        boolean hasItems = false;
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }
        
        if (!hasItems && inputFluid.isEmpty()) {
            return null;
        }
        
        // 检查缓存
        String cacheKey = generateCacheKey(inputItems, inputFluid, catalyst, mold);
        if (recipeCache.containsKey(cacheKey)) {
            return recipeCache.get(cacheKey).isEmpty() ? null : recipeCache.get(cacheKey).get(0);
        }

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        boolean hasCatalyst = !catalyst.isEmpty();
        boolean hasMold = !mold.isEmpty();
        
        AdvancedAlloyFurnaceRecipe catalystRecipe = null;
        AdvancedAlloyFurnaceRecipe moldRecipe = null;
        AdvancedAlloyFurnaceRecipe normalRecipe = null;

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            // 首先检查输入物品和流体是否匹配
            if (!recipe.matches(inputItems, inputFluid)) {
                continue;
            }

            // 检查模具匹配（如果配方需要模具）- 模具是必须的
            if (recipe.requiresMold()) {
                if (!hasMold || !recipe.getMold().test(mold)) {
                    continue;
                }
            }

            // 根据优先级找到第一个匹配的配方
            if (recipe.requiresCatalyst() && catalystRecipe == null) {
                catalystRecipe = recipe;
            } else if (recipe.requiresMold() && moldRecipe == null) {
                moldRecipe = recipe;
            } else if (!recipe.requiresCatalyst() && !recipe.requiresMold() && normalRecipe == null) {
                normalRecipe = recipe;
            }
            
            // 如果所有类型都找到了，提前退出循环
            if (catalystRecipe != null && moldRecipe != null && normalRecipe != null) {
                break;
            }
        }

        // 选择要返回的配方
        AdvancedAlloyFurnaceRecipe selectedRecipe = null;
        if (catalystRecipe != null) {
            selectedRecipe = catalystRecipe;
        } else if (moldRecipe != null) {
            selectedRecipe = moldRecipe;
        } else if (normalRecipe != null) {
            selectedRecipe = normalRecipe;
        }

        // 更新缓存
        List<AdvancedAlloyFurnaceRecipe> resultList = new ArrayList<>();
        if (selectedRecipe != null) {
            resultList.add(selectedRecipe);
        }
        recipeCache.put(cacheKey, resultList);

        return selectedRecipe;
    }


    // 修改原有的getRecipe方法，使用新的优先级逻辑
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipe(Level level, List<ItemStack> inputItems, FluidStack inputFluid) {
        // 使用空的催化剂和模具槽位来调用新方法
        return getRecipeWithCatalystOrMold(level, inputItems, inputFluid, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private String generateCacheKey(List<ItemStack> inputItems, FluidStack inputFluid, ItemStack catalyst, ItemStack mold) {
        StringBuilder key = new StringBuilder();

        // 物品部分（排序以确保顺序不影响缓存键）
        List<ItemStack> sortedItems = new ArrayList<>();
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                sortedItems.add(stack);
            }
        }
        sortedItems.sort(Comparator.comparing(stack -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())));
        
        for (ItemStack stack : sortedItems) {
            key.append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
            key.append(":").append(stack.getCount()).append(";");
        }

        // 流体部分
        if (!inputFluid.isEmpty()) {
            key.append(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(inputFluid.getFluid()));
            key.append(":").append(inputFluid.getAmount()).append(";");
        }
        
        // 催化剂部分
        if (!catalyst.isEmpty()) {
            key.append("CAT:").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(catalyst.getItem()));
            key.append(":").append(catalyst.getCount()).append(";");
        }
        
        // 模具部分
        if (!mold.isEmpty()) {
            key.append("MOLD:").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mold.getItem()));
            key.append(":").append(mold.getCount()).append(";");
        }

        return key.toString();
    }

    public void clearCache() {
        recipeCache.clear();
    }

    public boolean isValidInputItem(Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) return false;

        // 检查缓存
        String cacheKey = "ITEM_VALID:" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (recipeCache.containsKey(cacheKey)) {
            return !recipeCache.get(cacheKey).isEmpty();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getInputItems()) {
                if (ingredient.test(stack)) {
                    // 缓存结果
                    recipeCache.put(cacheKey, Collections.singletonList(recipe));
                    return true;
                }
            }
        }
        
        // 缓存结果
        recipeCache.put(cacheKey, Collections.emptyList());
        return false;
    }

    // 优化后的流体验证方法
    public boolean isValidInputFluid(Level level, FluidStack fluid) {
        if (fluid.isEmpty() || level == null) return false;

        // 检查缓存
        String cacheKey = "FLUID_VALID:" + net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (recipeCache.containsKey(cacheKey)) {
            return !recipeCache.get(cacheKey).isEmpty();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            FluidStack recipeFluid = recipe.getInputFluid();
            if (!recipeFluid.isEmpty() && recipeFluid.getFluid().isSame(fluid.getFluid())) {
                // 缓存结果
                recipeCache.put(cacheKey, Collections.singletonList(recipe));
                return true;
            }
        }
        
        // 缓存结果
        recipeCache.put(cacheKey, Collections.emptyList());
        return false;
    }
}