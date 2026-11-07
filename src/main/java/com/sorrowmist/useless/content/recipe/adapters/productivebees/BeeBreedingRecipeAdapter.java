package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.recipe.BeeBreedingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 12.6.0 breeding consumes food for live/caged parents and creates an entity with inherited
 * attributes, age and cooldowns. A pair of permanent species-egg molds cannot represent it.
 */
public final class BeeBreedingRecipeAdapter implements IRecipeAdapter<BeeBreedingRecipe> {
    @Override
    public Class<BeeBreedingRecipe> getRecipeClass() {
        return BeeBreedingRecipe.class;
    }

    @Override
    public List<RecipeHolder<BeeBreedingRecipe>> getGeneratedRecipes(Level level) {
        // In particular, do not synthesize self-breeding recipes from the JEI bee list.
        return List.of();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BeeBreedingRecipe> holder, Level level) {
        return ProductiveBeesAdapterUtils.unsupported(holder,
                "Breeding needs native parent entities/cages, genetics, breeding eligibility, food and cooldown state");
    }

    @Override
    public List<RecipeHolder<BeeBreedingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return List.of();
    }
}
