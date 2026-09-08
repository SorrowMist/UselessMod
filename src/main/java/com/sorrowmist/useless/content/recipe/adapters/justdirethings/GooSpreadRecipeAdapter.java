package com.sorrowmist.useless.content.recipe.adapters.justdirethings;

import com.direwolf20.justdirethings.datagen.recipes.GooSpreadRecipe;
import com.direwolf20.justdirethings.setup.Registration;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GooSpreadRecipeAdapter implements IRecipeAdapter<GooSpreadRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<GooSpreadRecipe> getRecipeClass() {
        return GooSpreadRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<GooSpreadRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        GooSpreadRecipe source = holder.value();
        Ingredient input = JustDireThingsRecipeAdapterUtils.blockInput(source.getInput());
        Fluid inputFluid = input == null
                ? JustDireThingsRecipeAdapterUtils.fluid(source.getInput()) : null;
        ItemStack output = JustDireThingsRecipeAdapterUtils.blockOutput(source.getOutput());
        Fluid outputFluid = output.isEmpty()
                ? JustDireThingsRecipeAdapterUtils.fluid(source.getOutput()) : null;
        Ingredient mold = JustDireThingsRecipeAdapterUtils.gooMold(source.getTierRequirement());
        if ((input == null && inputFluid == null)
                || (output.isEmpty() && outputFluid == null)
                || mold.isEmpty()) return null;

        List<CountedIngredient> itemInputs = input == null
                ? List.of()
                : List.of(new CountedIngredient(input, 1L));
        List<LongSizedFluidIngredient> inputFluids = inputFluid == null
                ? List.of()
                : JustDireThingsRecipeAdapterUtils.fluidInput(inputFluid);
        List<ItemStack> itemOutputs = output.isEmpty() ? List.of() : List.of(output);
        List<FluidStack> fluidOutputs = outputFluid == null
                ? List.of()
                : List.of(new FluidStack(outputFluid, JustDireThingsRecipeAdapterUtils.FLUID_AMOUNT));

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                itemInputs,
                inputFluids,
                List.of(),
                itemOutputs,
                fluidOutputs,
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, source.getCraftingDuration()),
                Ingredient.EMPTY,
                0,
                List.of(mold),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<GooSpreadRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<GooSpreadRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<GooSpreadRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(Registration.GOO_SPREAD_RECIPE_TYPE.get())) {
            GooSpreadRecipe source = holder.value();
            ItemStack output = JustDireThingsRecipeAdapterUtils.blockOutput(source.getOutput());
            if (JustDireThingsRecipeAdapterUtils.hasConvertibleInput(source.getInput())
                    && (!output.isEmpty()
                    || JustDireThingsRecipeAdapterUtils.fluid(source.getOutput()) != null)
                    && JustDireThingsRecipeAdapterUtils.matchesMold(source.getTierRequirement(), mold)
                    && JustDireThingsRecipeAdapterUtils.matchesInput(
                    source.getInput(), mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
