package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Native bee interaction/conversion is not a static item-to-species-egg recipe. */
public final class BeeConversionRecipeAdapter implements IRecipeAdapter<BeeConversionRecipe> {
    @Override
    public Class<BeeConversionRecipe> getRecipeClass() {
        return BeeConversionRecipe.class;
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
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<BeeConversionRecipe> holder, Level level) {
        // Native chance is an integer, not the old float probability. Even a guaranteed result
        // requires the interaction's actual entity/state and cannot be replaced with a new egg.
        return ProductiveBeesAdapterUtils.unsupported(holder,
                "Conversion needs the native bee interaction, entity NBT/state and integer chance semantics");
    }

    @Override
    public List<RecipeHolder<BeeConversionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return List.of();
    }
}
