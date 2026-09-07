package com.sorrowmist.useless.content.recipe.adapters.justdirethings;

import com.direwolf20.justdirethings.datagen.recipes.FluidDropRecipe;
import com.direwolf20.justdirethings.setup.Registration;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
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

public final class FluidDropRecipeAdapter implements IRecipeAdapter<FluidDropRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<FluidDropRecipe> getRecipeClass() {
        return FluidDropRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<FluidDropRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        FluidDropRecipe source = holder.value();
        Fluid input = JustDireThingsRecipeAdapterUtils.fluid(source.getInput());
        Fluid output = JustDireThingsRecipeAdapterUtils.fluid(source.getOutput());
        ItemStack catalyst = source.getCatalyst() == null
                ? ItemStack.EMPTY : new ItemStack(source.getCatalyst());
        if (input == null || output == null || catalyst.isEmpty()) return null;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(Ingredient.of(catalyst), 1L)),
                JustDireThingsRecipeAdapterUtils.fluidInput(input),
                List.of(),
                List.of(),
                List.of(new FluidStack(output, JustDireThingsRecipeAdapterUtils.FLUID_AMOUNT)),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(catalyst)),
                AlloyFurnaceMode.NORMAL);
    }

    @Override
    public List<RecipeHolder<FluidDropRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        List<RecipeHolder<FluidDropRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<FluidDropRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(Registration.FLUID_DROP_RECIPE_TYPE.get())) {
            FluidDropRecipe source = holder.value();
            Fluid input = JustDireThingsRecipeAdapterUtils.fluid(source.getInput());
            Fluid output = JustDireThingsRecipeAdapterUtils.fluid(source.getOutput());
            Ingredient catalyst = source.getCatalyst() == null
                    ? Ingredient.EMPTY : Ingredient.of(source.getCatalyst());
            if (input != null && output != null
                    && source.getCatalyst() != null
                    && mold.is(source.getCatalyst())
                    && JustDireThingsRecipeAdapterUtils.matchesItem(catalyst, mergedInputs)
                    && JustDireThingsRecipeAdapterUtils.matchesFluid(input, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
