package com.sorrowmist.useless.content.recipe.adapters.justdirethings;

import com.direwolf20.justdirethings.datagen.recipes.GooSpreadRecipeTag;
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

public final class GooSpreadRecipeTagAdapter implements IRecipeAdapter<GooSpreadRecipeTag> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.JUSTDIRETHINGS;
    }

    @Override
    public Class<GooSpreadRecipeTag> getRecipeClass() {
        return GooSpreadRecipeTag.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<GooSpreadRecipeTag> holder, Level level) {
        if (holder == null || holder.value() == null) return null;

        GooSpreadRecipeTag source = holder.value();
        Ingredient input = JustDireThingsRecipeAdapterUtils.blockTagInput(source.getInput());
        ItemStack output = JustDireThingsRecipeAdapterUtils.rawOreDrop(source.getOutput());
        Ingredient mold = JustDireThingsRecipeAdapterUtils.gooMold(source.getTierRequirement());
        if (input == null || input.isEmpty() || output.isEmpty() || mold.isEmpty()) return null;

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
    public List<RecipeHolder<GooSpreadRecipeTag>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) return List.of();

        List<RecipeHolder<GooSpreadRecipeTag>> matches = new ArrayList<>();
        for (RecipeHolder<GooSpreadRecipeTag> holder : level.getRecipeManager()
                .getAllRecipesFor(Registration.GOO_SPREAD_RECIPE_TYPE_TAG.get())) {
            GooSpreadRecipeTag source = holder.value();
            ItemStack output = JustDireThingsRecipeAdapterUtils.rawOreDrop(source.getOutput());
            Ingredient input = JustDireThingsRecipeAdapterUtils.blockTagInput(source.getInput());
            if (!input.isEmpty()
                    && JustDireThingsRecipeAdapterUtils.matchesMold(source.getTierRequirement(), mold)
                    && JustDireThingsRecipeAdapterUtils.matchesItem(input, mergedInputs)
                    && !output.isEmpty()) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }
}
