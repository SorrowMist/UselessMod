package com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * AE2CS 晶体聚合器配方适配器
 */
public class CrystalAggregatorRecipeAdapter implements IRecipeAdapter<CrystalAggregatorRecipe> {

    @Override
    public Class<CrystalAggregatorRecipe> getRecipeClass() {
        return CrystalAggregatorRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_aggregator")));
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

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients, List.of(),
                List.of(output.copy()), List.of(),
                AdapterUtils.ae2csEnergyCost(recipe.energyCost()), 100,
                Ingredient.EMPTY, 0,
                CircuitEtcherRecipeAdapter.makeMold("crystal_aggregator"),
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalAggregatorRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null) return null;
        if (!CircuitEtcherRecipeAdapter.checkMold(mold, "crystal_aggregator")) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<CrystalAggregatorRecipe> holder : (List<RecipeHolder<CrystalAggregatorRecipe>>) (List<?>)
                recipeManager.getAllRecipesFor(AECSRecipeTypes.CRYSTAL_AGGREGATOR.get())) {
            CrystalAggregatorRecipe recipe = holder.value();
            if (recipe.result().isEmpty()) continue;

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputA());
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputB());
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.inputC());
            if (required.isEmpty()) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, required)) return holder;
        }
        return null;
    }
}
