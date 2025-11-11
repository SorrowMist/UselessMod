package com.sorrowmist.useless.recipes.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
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

    // 在 AdvancedAlloyFurnaceRecipeManager.java 中修改 getRecipeWithCatalystOrMold 方法
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipeWithCatalystOrMold(Level level, List<ItemStack> inputItems,
                                                                  FluidStack inputFluid, ItemStack catalyst,
                                                                  ItemStack mold) {
        if (level == null) {
            UselessMod.LOGGER.debug("Level is null, returning null");
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        );

        UselessMod.LOGGER.debug("=== Recipe Search Started ===");
        UselessMod.LOGGER.debug("Input items count: {}, Fluid: {}mb, Catalyst: {}, Mold: {}",
                inputItems.size(), inputFluid.getAmount(),
                catalyst.isEmpty() ? "none" : catalyst.getItem().toString(),
                mold.isEmpty() ? "none" : mold.getItem().toString());

        // 记录输入物品详情
        for (int i = 0; i < inputItems.size(); i++) {
            ItemStack stack = inputItems.get(i);
            if (!stack.isEmpty()) {
                UselessMod.LOGGER.debug("Input slot {}: {} x {}", i, stack.getItem(), stack.getCount());
            }
        }

        // 创建三个优先级的列表
        List<AdvancedAlloyFurnaceRecipe> catalystRecipes = new ArrayList<>();
        List<AdvancedAlloyFurnaceRecipe> moldRecipes = new ArrayList<>();
        List<AdvancedAlloyFurnaceRecipe> normalRecipes = new ArrayList<>();

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            UselessMod.LOGGER.debug("Checking recipe: {}", recipe.getId());

            // 首先检查输入物品和流体是否匹配
            boolean inputsMatch = recipe.matches(inputItems, inputFluid);

            UselessMod.LOGGER.debug("  Inputs match: {}", inputsMatch);

            if (!inputsMatch) {
                continue;
            }

            boolean moldMatch = true;
            boolean catalystCompatible = true;

            // 检查模具匹配（如果配方需要模具）- 模具是必须的
            if (recipe.requiresMold()) {
                UselessMod.LOGGER.debug("  Recipe requires mold");
                if (mold.isEmpty() || !recipe.getMold().test(mold)) {
                    moldMatch = false;
                    UselessMod.LOGGER.debug("  Mold not present or not matching");
                } else {
                    UselessMod.LOGGER.debug("  Mold matches");
                }
            }

            // 修改：催化剂现在是完全可选的，即使配方需要催化剂但没有放入，也允许匹配
            // 只是并行数会不同（为1）
            if (recipe.requiresCatalyst()) {
                // 检查配方是否允许催化剂
                if (recipe.isCatalystAllowed()) {
                    // 配方允许催化剂，正常处理
                    if (!catalyst.isEmpty() && recipe.getCatalyst().test(catalyst) && catalyst.getCount() >= recipe.getCatalystCount()) {
                        // 催化剂匹配，使用催化剂提供的并行数
                    } else {
                        // 没有放入催化剂或催化剂不匹配，但仍然允许配方运行（并行数为1）
                    }
                } else {
                    // 配方输出在黑名单中，强制并行数为1，忽略催化剂
                    // 不进行任何催化剂相关处理
                }
            }

            // 根据匹配情况分类
            if (moldMatch) {
                if (recipe.requiresCatalyst()) {
                    catalystRecipes.add(recipe);
                    UselessMod.LOGGER.debug("  -> Added to catalyst recipes");
                } else if (recipe.requiresMold()) {
                    moldRecipes.add(recipe);
                    UselessMod.LOGGER.debug("  -> Added to mold recipes");
                } else {
                    normalRecipes.add(recipe);
                    UselessMod.LOGGER.debug("  -> Added to normal recipes");
                }
            }
        }

        // 选择要返回的配方
        AdvancedAlloyFurnaceRecipe selectedRecipe = null;

        if (!catalystRecipes.isEmpty()) {
            selectedRecipe = catalystRecipes.get(0);
            UselessMod.LOGGER.debug("Selected catalyst recipe: {}", selectedRecipe.getId());
        } else if (!moldRecipes.isEmpty()) {
            selectedRecipe = moldRecipes.get(0);
            UselessMod.LOGGER.debug("Selected mold recipe: {}", selectedRecipe.getId());
        } else if (!normalRecipes.isEmpty()) {
            selectedRecipe = normalRecipes.get(0);
            UselessMod.LOGGER.debug("Selected normal recipe: {}", selectedRecipe.getId());
        } else {
            UselessMod.LOGGER.debug("No matching recipe found");
        }

        UselessMod.LOGGER.debug("=== Recipe Search Finished ===");
        return selectedRecipe;
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