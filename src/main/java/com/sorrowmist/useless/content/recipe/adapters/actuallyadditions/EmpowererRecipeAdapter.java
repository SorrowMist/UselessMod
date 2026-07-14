package com.sorrowmist.useless.content.recipe.adapters.actuallyadditions;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.actuallyadditions.mod.blocks.ActuallyBlocks;
import de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes;
import de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actually Additions 充能台配方适配器
 * <p>
 * 将充能台配方转换为合金炉配方
 * 充能台使用1个基础物品 + 4个辅助物品来产生输出
 */
public class EmpowererRecipeAdapter implements IRecipeAdapter<EmpowererRecipe> {

    @Override
    public Class<EmpowererRecipe> getRecipeClass() {
        return EmpowererRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(ActuallyBlocks.EMPOWERER.get());
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<EmpowererRecipe> holder, Level level) {
        if (holder == null) return null;

        EmpowererRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        // 获取所有输入并合并相同物品
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();

        // 基础输入
        Ingredient baseInput = originalRecipe.getInput();
        if (baseInput != null && baseInput.getItems().length > 0) {
            AdapterUtils.mergeIngredient(ingredientCounts, baseInput, 1L);
        }

        // 4个辅助物品（modifiers）
        NonNullList<Ingredient> modifiers = NonNullList.create();
        modifiers.add(originalRecipe.getStandOne());
        modifiers.add(originalRecipe.getStandTwo());
        modifiers.add(originalRecipe.getStandThree());
        modifiers.add(originalRecipe.getStandFour());

        for (Ingredient modifier : modifiers) {
            if (modifier != null && modifier.getItems().length > 0) {
                AdapterUtils.mergeIngredient(ingredientCounts, modifier, 1L);
            }
        }

        if (ingredientCounts.isEmpty()) {
            return null;
        }

        // 转换为 CountedIngredient 列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        // 获取输出物品
        ItemStack output = originalRecipe.getOutput();
        if (output.isEmpty()) {
            return null;
        }

        // 获取配方能量和处理时间
        int energyPerStand = originalRecipe.getEnergyPerStand();
        int totalEnergy = energyPerStand * 4; // 4个充能台
        int time = originalRecipe.getTime();

        // 创建充能台模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(ActuallyBlocks.EMPOWERER.get()));

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),           // 无流体输入
                List.of(output),     // 输出物品
                List.of(),           // 无流体输出
                totalEnergy,
                time,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 充能台作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    @Nullable
    public List<RecipeHolder<EmpowererRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (mergedInputs.isEmpty() || level == null) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<EmpowererRecipe>> recipes = recipeManager.getAllRecipesFor(ActuallyRecipes.Types.EMPOWERING.get());

        return recipes.stream()
                .filter(holder -> matchesRecipeInputs(holder.value(), mergedInputs))
                .toList();
    }

    private boolean matchesRecipeInputs(EmpowererRecipe recipe, Map<Ingredient, Long> mergedInputs) {
        // 统计配方所需的物品（按 Ingredient 合并）
        Map<Ingredient, Long> recipeCounts = new LinkedHashMap<>();
        Ingredient baseInput = recipe.getInput();
        if (baseInput != null && baseInput.getItems().length > 0) {
            AdapterUtils.mergeIngredient(recipeCounts, baseInput, 1L);
        }
        AdapterUtils.mergeIngredient(recipeCounts, recipe.getStandOne(), 1L);
        AdapterUtils.mergeIngredient(recipeCounts, recipe.getStandTwo(), 1L);
        AdapterUtils.mergeIngredient(recipeCounts, recipe.getStandThree(), 1L);
        AdapterUtils.mergeIngredient(recipeCounts, recipe.getStandFour(), 1L);

        return AdapterUtils.matchesRequired(mergedInputs, recipeCounts);
    }
}
