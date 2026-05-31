package com.sorrowmist.useless.content.recipe.adapters.ae2cs;

import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2CS 晶体聚合器配方适配器
 */
public class CrystalAggregatorRecipeAdapter implements IRecipeAdapter<CrystalAggregatorRecipe> {

    @Override
    public Class<CrystalAggregatorRecipe> getRecipeClass() {
        return CrystalAggregatorRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalAggregatorRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        CrystalAggregatorRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        ItemStack output = recipe.result();
        if (output.isEmpty()) return result;

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        CircuitEtcherRecipeAdapter.addSizedIngredient(ingredientCounts, recipe.inputA());
        CircuitEtcherRecipeAdapter.addSizedIngredient(ingredientCounts, recipe.inputB());
        CircuitEtcherRecipeAdapter.addSizedIngredient(ingredientCounts, recipe.inputC());

        if (ingredientCounts.isEmpty()) return result;

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(), originalId.getPath() + "_converted");

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients, List.of(),
                List.of(output.copy()), List.of(),
                Math.max(recipe.energyCost(), 1000), 100,
                Ingredient.EMPTY, 0,
                CircuitEtcherRecipeAdapter.makeMold("crystal_aggregator"),
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalAggregatorRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalAggregatorRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalAggregatorRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) return null;
        if (!CircuitEtcherRecipeAdapter.checkMold(mold, "crystal_aggregator")) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalAggregatorRecipe>> recipes = (List<RecipeHolder<CrystalAggregatorRecipe>>) (List<?>)
                recipeManager.getAllRecipesFor(AECSRecipeTypes.CRYSTAL_AGGREGATOR.get());

        for (RecipeHolder<CrystalAggregatorRecipe> holder : recipes) {
            CrystalAggregatorRecipe recipe = holder.value();
            if (recipe.result().isEmpty()) continue;

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputA());
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputB());
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputC());
            if (required.isEmpty()) continue;

            if (CircuitEtcherRecipeAdapter.matchesCounted(inputs, required)) return holder;
        }
        return null;
    }

    @Override
    public int getPriority() { return 66; }
}
