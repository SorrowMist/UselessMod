package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.tabulavitae.TabulaVitaeRecipe;
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

import java.util.List;
import java.util.Map;

/** Converts Tabula Vitae recipes while preserving their item inputs, output and ticks. */
public final class TabulaVitaeRecipeAdapter implements IRecipeAdapter<TabulaVitaeRecipe> {
    private static final ItemStack MOLD = new ItemStack(NVBlocks.TABULA_VITAE.asItem());

    @Override
    public Class<TabulaVitaeRecipe> getRecipeClass() {
        return TabulaVitaeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MOLD.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<TabulaVitaeRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        TabulaVitaeRecipe source = holder.value();
        ItemStack output = source.getOutput();
        if (output == null || output.isEmpty() || source.getTicks() <= 0) return List.of();

        return List.of(new AdvancedAlloyFurnaceRecipe(
                holder.id(),
                NeoVitaeAdapterUtils.counted(source.getInput()),
                List.of(),
                List.of(),
                List.of(output),
                List.of(),
                List.of(),
                NeoVitaeAdapterUtils.energyFor(source.getSyphon()),
                source.getTicks(),
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(MOLD)),
                AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<TabulaVitaeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<TabulaVitaeRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<TabulaVitaeRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(NVRecipes.TABULA_VITAE_TYPE.get())) {
            TabulaVitaeRecipe source = holder.value();
            if (source != null && source.getTicks() > 0
                    && NeoVitaeAdapterUtils.matchesItems(mergedInputs,
                    NeoVitaeAdapterUtils.counted(source.getInput()))) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
