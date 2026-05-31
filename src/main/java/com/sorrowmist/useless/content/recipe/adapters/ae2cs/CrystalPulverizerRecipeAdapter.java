package com.sorrowmist.useless.content.recipe.adapters.ae2cs;

import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.recipe.crystal_pulverizer.CrystalPulverizerRecipe;
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
 * AE2CS 晶体粉碎机配方适配器
 */
public class CrystalPulverizerRecipeAdapter implements IRecipeAdapter<CrystalPulverizerRecipe> {

    @Override
    public Class<CrystalPulverizerRecipe> getRecipeClass() {
        return CrystalPulverizerRecipe.class;
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

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(), originalId.getPath() + "_converted");

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients, List.of(),
                List.of(output.copy()), List.of(),
                Math.max(recipe.energyCost(), 1000), 100,
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
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalPulverizerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CrystalPulverizerRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) return null;
        if (!CircuitEtcherRecipeAdapter.checkMold(mold, "crystal_pulverizer")) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CrystalPulverizerRecipe>> recipes = (List<RecipeHolder<CrystalPulverizerRecipe>>) (List<?>)
                recipeManager.getAllRecipesFor(AECSRecipeTypes.CRYSTAL_PULVERIZER.get());

        for (RecipeHolder<CrystalPulverizerRecipe> holder : recipes) {
            CrystalPulverizerRecipe recipe = holder.value();
            if (recipe.result().isEmpty()) continue;

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            CircuitEtcherRecipeAdapter.addSizedIngredient(required, recipe.input());
            if (required.isEmpty()) continue;

            if (CircuitEtcherRecipeAdapter.matchesCounted(inputs, required)) return holder;
        }
        return null;
    }

    @Override
    public int getPriority() { return 64; }
}
