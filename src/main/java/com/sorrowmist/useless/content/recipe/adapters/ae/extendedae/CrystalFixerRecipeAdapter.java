package com.sorrowmist.useless.content.recipe.adapters.ae.extendedae;

import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.recipe.CrystalFixerRecipe;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
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

/** Converts ExtendedAE crystal fixer recipes into alloy-furnace recipes. */
public final class CrystalFixerRecipeAdapter implements IRecipeAdapter<CrystalFixerRecipe> {
    private static final int PROCESS_TIME = 100;
    private static final long ENERGY = 50L * PROCESS_TIME * AdapterUtils.AE_TO_FE_CONVERSION;

    @Override
    public Class<CrystalFixerRecipe> getRecipeClass() {
        return CrystalFixerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EAESingletons.CRYSTAL_FIXER);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CrystalFixerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        CrystalFixerRecipe source = holder.value();
        IngredientStack.Item fuel = source.getFuel();
        if (fuel == null || fuel.isEmpty() || source.getInput() == null || source.getOutput() == null) {
            return List.of();
        }

        Ingredient fuelIngredient = fuel.getIngredient();
        long fuelAmount = fuel.getAmount();
        long expectedFuelAmount = expectedFuelAmount(fuelAmount, source.getChance());
        if (AdapterUtils.isIngredientEmpty(fuelIngredient) || expectedFuelAmount <= 0L) {
            return List.of();
        }

        List<CountedIngredient> inputs = List.of(
                new CountedIngredient(Ingredient.of(new ItemStack(source.getInput())), 1L),
                new CountedIngredient(fuelIngredient, expectedFuelAmount));

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(new ItemStack(source.getOutput())),
                List.of(),
                List.of(),
                ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<CrystalFixerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<CrystalFixerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrystalFixerRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(CrystalFixerRecipe.TYPE)) {
            CrystalFixerRecipe source = holder.value();
            IngredientStack.Item fuel = source.getFuel();
            if (fuel == null || fuel.isEmpty() || source.getInput() == null || source.getOutput() == null) {
                continue;
            }

            Ingredient fuelIngredient = fuel.getIngredient();
            long fuelAmount = fuel.getAmount();
            long expectedFuelAmount = expectedFuelAmount(fuelAmount, source.getChance());
            if (AdapterUtils.isIngredientEmpty(fuelIngredient) || expectedFuelAmount <= 0L) {
                continue;
            }

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            AdapterUtils.mergeIngredient(required,
                    Ingredient.of(new ItemStack(source.getInput())), 1L);
            AdapterUtils.mergeIngredient(required, fuelIngredient, expectedFuelAmount);
            if (AdapterUtils.matchesRequired(mergedInputs, required)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** Calculates the integer fuel cost expected for one successful repair. */
    private static long expectedFuelAmount(long fuelAmount, double chance) {
        if (fuelAmount <= 0L || !Double.isFinite(chance) || chance <= 0.0) {
            return -1L;
        }
        double expected = fuelAmount / Math.min(1.0, chance);
        if (!Double.isFinite(expected) || expected > Long.MAX_VALUE) {
            return -1L;
        }
        long roundedUp = (long) Math.ceil(expected);
        return roundedUp > 0L ? roundedUp : -1L;
    }
}
