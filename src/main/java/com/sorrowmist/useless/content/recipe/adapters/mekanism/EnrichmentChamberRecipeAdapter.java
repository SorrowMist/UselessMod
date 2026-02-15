package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.common.registries.MekanismBlocks;
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
 * Mekanism 富集仓配方适配器
 */
public class EnrichmentChamberRecipeAdapter implements IRecipeAdapter<ItemStackToItemStackRecipe> {

    // 基础能量消耗
    private static final int BASE_ENERGY = 2000;
    // 处理时间基础值（ticks）
    private static final int BASE_PROCESS_TIME = 20;

    @Override
    public Class<ItemStackToItemStackRecipe> getRecipeClass() {
        return ItemStackToItemStackRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<ItemStackToItemStackRecipe> holder, Level level) {
        if (holder == null) return null;

        ItemStackToItemStackRecipe originalRecipe = holder.value();
        
        // 只处理富集仓配方（ENRICHING类型）
        if (!originalRecipe.getType().equals(
                mekanism.api.recipes.MekanismRecipeTypes.TYPE_ENRICHING.value())) {
            return null;
        }

        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取输入材料
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        var input = originalRecipe.getInput();
        if (input != null && !input.hasNoMatchingInstances()) {
            var inputStacks = input.getRepresentations();
            if (!inputStacks.isEmpty()) {
                Ingredient ingredient = Ingredient.of(inputStacks.stream());
                countedIngredients.add(new CountedIngredient(ingredient, 1));
            }
        }

        if (countedIngredients.isEmpty()) {
            return null;
        }

        // 获取输出物品
        List<ItemStack> outputs = originalRecipe.getOutputDefinition();
        if (outputs.isEmpty()) {
            return null;
        }

        // 创建富集仓模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(MekanismBlocks.ENRICHMENT_CHAMBER.get()));

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),           // 无流体输入
                outputs,
                List.of(),           // 无流体输出
                BASE_ENERGY,
                BASE_PROCESS_TIME,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 富集仓作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<ItemStackToItemStackRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackToItemStackRecipe>> recipes = recipeManager.getAllRecipesFor(
                mekanism.api.recipes.MekanismRecipeTypes.TYPE_ENRICHING.value()
        );

        for (RecipeHolder<ItemStackToItemStackRecipe> holder : recipes) {
            ItemStackToItemStackRecipe recipe = holder.value();
            
            // 确保是富集仓配方
            if (!recipe.getType().equals(
                    mekanism.api.recipes.MekanismRecipeTypes.TYPE_ENRICHING.value())) {
                continue;
            }

            var input = recipe.getInput();
            if (input == null || input.hasNoMatchingInstances()) continue;

            for (ItemStack stack : inputs) {
                if (!stack.isEmpty() && input.test(stack)) {
                    return holder;
                }
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 70; // 优先级低于冶金灌注机
    }
}
