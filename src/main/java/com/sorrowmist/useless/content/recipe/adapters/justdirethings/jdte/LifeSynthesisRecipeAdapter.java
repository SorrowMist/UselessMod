package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import com.jdte.common.recipes.LifeSynthesisRecipe;
import com.jdte.setup.JDTEBlocks;
import com.jdte.setup.JDTERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts JDTE Life Synthesis Vat recipes into alloy-furnace recipes. */
public final class LifeSynthesisRecipeAdapter implements IRecipeAdapter<LifeSynthesisRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<LifeSynthesisRecipe> getRecipeClass() {
        return LifeSynthesisRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(JDTEBlocks.LIFE_SYNTHESIS_VAT.get());
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(
            RecipeHolder<LifeSynthesisRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        LifeSynthesisRecipe source = holder.value();
        List<CountedIngredient> inputs = inputs(source);
        FluidStack nutrient = source.nutrient();
        FluidStack output = source.output();
        if (nutrient == null || output == null || output.isEmpty()
                || output.getAmount() <= 0 || source.processTicks() <= 0 || source.energy() <= 0) {
            return null;
        }

        List<LongSizedFluidIngredient> inputFluids = nutrient.isEmpty()
                ? List.of() : List.of(LongSizedFluidIngredient.from(nutrient));
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                source.energy(),
                source.processTicks(),
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<LifeSynthesisRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        Map<Ingredient, Long> availableInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        List<RecipeHolder<LifeSynthesisRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<LifeSynthesisRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(JDTERecipes.LIFE_SYNTHESIS_RECIPE_TYPE.get())) {
            LifeSynthesisRecipe source = holder.value();
            if (convert(holder, level) == null || !matchesInputs(source, availableInputs)
                    || !matchesNutrient(source, availableFluids)) {
                continue;
            }
            matches.add(holder);
        }
        return List.copyOf(matches);
    }

    private static List<CountedIngredient> inputs(LifeSynthesisRecipe source) {
        if (source.inputs() == null || source.inputs().isEmpty()) return List.of();

        List<CountedIngredient> result = new ArrayList<>();
        for (LifeSynthesisRecipe.InputSlot slot : source.inputs()) {
            if (slot == null || slot.ingredient() == null || slot.ingredient().isEmpty()
                    || slot.count() <= 0) {
                continue;
            }
            result.add(new CountedIngredient(slot.ingredient(), slot.count()));
        }
        return List.copyOf(result);
    }

    private static boolean matchesInputs(
            LifeSynthesisRecipe source, Map<Ingredient, Long> availableInputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        if (source.inputs() != null) {
            for (LifeSynthesisRecipe.InputSlot slot : source.inputs()) {
                if (slot == null || slot.ingredient() == null || slot.ingredient().isEmpty()
                        || slot.count() <= 0) {
                    continue;
                }
                AdapterUtils.mergeIngredient(required, slot.ingredient(), slot.count());
            }
        }
        return AdapterUtils.matchesRequired(availableInputs, required);
    }

    private static boolean matchesNutrient(
            LifeSynthesisRecipe source, Map<FluidStack, Long> availableFluids) {
        FluidStack nutrient = source.nutrient();
        if (nutrient == null || nutrient.isEmpty()) return true;
        return FluidIngredientAllocator.matchesLong(
                List.of(LongSizedFluidIngredient.from(nutrient)), availableFluids, 1L);
    }
}
