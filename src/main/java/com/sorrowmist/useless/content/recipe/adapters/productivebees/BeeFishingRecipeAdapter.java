package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.recipe.BeeFishingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * ItemFishedEvent uses the hook biome, real-player eligibility, held-tool enchantments, random
 * selection and entity spawning. Water plus an undamaged rod mold preserves none of that.
 */
public final class BeeFishingRecipeAdapter implements IRecipeAdapter<BeeFishingRecipe> {
    @Override
    public Class<BeeFishingRecipe> getRecipeClass() {
        return BeeFishingRecipe.class;
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
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BeeFishingRecipe> holder, Level level) {
        return ProductiveBeesAdapterUtils.unsupported(holder,
                "Fishing needs the real player/hook biome, native probability/selection, tool state and world spawn");
    }

    @Override
    public List<RecipeHolder<BeeFishingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return List.of();
    }
}
