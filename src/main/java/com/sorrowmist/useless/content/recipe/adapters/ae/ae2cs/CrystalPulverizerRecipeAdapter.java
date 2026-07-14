package com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.recipe.crystal_pulverizer.CrystalPulverizerRecipe;
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
 * AE2CS 晶体粉碎机配方适配器
 */
public class CrystalPulverizerRecipeAdapter implements IRecipeAdapter<CrystalPulverizerRecipe> {

    @Override
    public Class<CrystalPulverizerRecipe> getRecipeClass() {
        return CrystalPulverizerRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_pulverizer")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CrystalPulverizerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        CrystalPulverizerRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        ItemStack output = recipe.result();
        if (output.isEmpty()) return result;

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        CircuitEtcherRecipeAdapter.addSizedIngredient(ingredientCounts, recipe.input());

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
                CircuitEtcherRecipeAdapter.makeMold("crystal_pulverizer"),
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CrystalPulverizerRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<CrystalPulverizerRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null) return List.of();
        if (!CircuitEtcherRecipeAdapter.checkMold(mold, "crystal_pulverizer")) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalPulverizerRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<CrystalPulverizerRecipe> holder : (List<RecipeHolder<CrystalPulverizerRecipe>>) (List<?>)
                recipeManager.getAllRecipesFor(AECSRecipeTypes.CRYSTAL_PULVERIZER.get())) {
            CrystalPulverizerRecipe recipe = holder.value();
            if (recipe.result().isEmpty()) continue;

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.input());
            if (required.isEmpty()) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, required)) matches.add(holder);
        }
        return matches;
    }
}
