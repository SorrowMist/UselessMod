package com.sorrowmist.useless.content.recipe.adapters.actuallyadditions;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.actuallyadditions.mod.blocks.ActuallyBlocks;
import de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Actually Additions 原子再构机配方适配器
 * <p>
 * 将原子再构机配方转换为高级熔炉配方
 * 原子再构机使用能量将输入物品转换为输出物品
 */
public class LaserRecipeAdapter implements IRecipeAdapter<LaserRecipe> {

    // 基础能量消耗（与原子再构机配方保持一致）
    private static final int BASE_PROCESS_TIME = 20;

    @Override
    public Class<LaserRecipe> getRecipeClass() {
        return LaserRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<LaserRecipe> holder, Level level) {
        if (holder == null) return null;

        LaserRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        Ingredient input = originalRecipe.getInput();
        if (input != null && input.getItems().length > 0) {
            countedIngredients.add(new CountedIngredient(input, 1L));
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品
        ItemStack result = originalRecipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        // 获取配方能量消耗
        int energy = originalRecipe.getEnergy();

        // 创建原子再构机模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(ActuallyBlocks.ATOMIC_RECONSTRUCTOR.get()));

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),           // 无流体输入
                List.of(result),     // 输出物品
                List.of(),           // 无流体输出
                energy,              // 使用配方的能量消耗
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 原子再构机作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<LaserRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (inputs.isEmpty() || level == null) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<LaserRecipe>> recipes = recipeManager.getAllRecipesFor(de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes.Types.LASER.get());

        for (RecipeHolder<LaserRecipe> holder : recipes) {
            LaserRecipe recipe = holder.value();
            // 检查输入是否匹配配方
            for (ItemStack input : inputs) {
                if (!input.isEmpty() && recipe.matches(input)) {
                    return holder;
                }
            }
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 10; // 较高优先级
    }
}
