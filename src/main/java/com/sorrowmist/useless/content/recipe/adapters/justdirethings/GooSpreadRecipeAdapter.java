package com.sorrowmist.useless.content.recipe.adapters.justdirethings;

import com.direwolf20.justdirethings.datagen.recipes.GooSpreadRecipe;
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
        ItemStack output = JustDireThingsRecipeAdapterUtils.rawOreDrop(source.getOutput());
        Ingredient mold = JustDireThingsRecipeAdapterUtils.gooMold(source.getTierRequirement());
        if (input == null || output.isEmpty() || mold.isEmpty()) return null;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
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
            Ingredient input = JustDireThingsRecipeAdapterUtils.blockInput(source.getInput());
            if (input != null
                    && JustDireThingsRecipeAdapterUtils.matchesMold(source.getTierRequirement(), mold)
                    && JustDireThingsRecipeAdapterUtils.matchesItem(input, mergedInputs)
                    && JustDireThingsRecipeAdapterUtils.rawOreDrop(source.getOutput())
                    .getCount() == JustDireThingsRecipeAdapterUtils.RAW_ORE_DROP_COUNT) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
