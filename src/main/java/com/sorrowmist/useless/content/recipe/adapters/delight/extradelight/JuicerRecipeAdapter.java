package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.lance5057.extradelight.workstations.juicer.JuicerRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Extra Delight juicer recipes into deterministic input batches. */
public final class JuicerRecipeAdapter implements IRecipeAdapter<JuicerRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("extradelight", "juicer");

    @Override
    public String sourceId() {
        return RecipeSourceIds.EXTRA_DELIGHT;
    }

    @Override
    public Class<JuicerRecipe> getRecipeClass() {
        return JuicerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        ItemStack mold = ExtraDelightRecipeAdapterUtils.mold(MOLD_ID);
        return mold == null ? ItemStack.EMPTY : mold;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<JuicerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        JuicerRecipe source = holder.value();
        Ingredient input = source.getInput();
        FluidStack fluidOutput = source.getFluid();
        ItemStack itemOutput = source.getResultItem(level == null ? null : level.registryAccess());
        if (input == null || input.isEmpty()
                || (fluidOutput == null || fluidOutput.isEmpty())
                && (itemOutput == null || itemOutput.isEmpty())) {
            return List.of();
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = itemOutput == null
                || itemOutput.isEmpty() ? List.of() : List.of(new ExpectedOutputScaler.WeightedItemOutput(
                itemOutput.copy(), itemOutput.getCount(), itemOutput.getCount(),
                Math.max(0, Math.min(100, source.getChance())) / 100.0));
        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty()) {
            return List.of();
        }

        int operations = scaled.get().operations();
        List<CountedIngredient> inputs = ExtraDelightRecipeAdapterUtils.scaleInputs(
                List.of(new CountedIngredient(input, 1L)), operations);
        FluidStack scaledFluid;
        try {
            scaledFluid = ExtraDelightRecipeAdapterUtils.scaledFluid(fluidOutput, operations);
        } catch (ArithmeticException exception) {
            return List.of();
        }
        var energy = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_ENERGY, operations);
        var processTime = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
        if (inputs.isEmpty() || energy.isEmpty() || processTime.isEmpty()
                || scaled.get().outputs().isEmpty() && scaledFluid == null) {
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(),
                scaled.get().outputs(), scaledFluid == null ? List.of() : List.of(scaledFluid), List.of(),
                energy.getAsInt(), processTime.getAsInt(), Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<JuicerRecipe>> findMatchingRecipes(Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<JuicerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<JuicerRecipe> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), JuicerRecipe.class)) {
            JuicerRecipe source = holder.value();
            if (source.getInput() != null && !source.getInput().isEmpty()
                    && DelightRecipeAdapterUtils.matchesItems(
                    List.of(new CountedIngredient(source.getInput(), 1L)), mergedInputs, List.of())
                    && (mergedFluids == null || mergedFluids.isEmpty())) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
