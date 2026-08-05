package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Synthetic water -> heavy water recipe using Mekanism's filter upgrade as a mold. */
public final class HeavyWaterRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            "useless_mod", "mekanism/heavy_water_filter_upgrade");
    private static final int PROCESS_TICKS = 200;
    private static final long ENERGY = 10_000L;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismItems.FILTER_UPGRADE.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                RECIPE_ID,
                List.of(),
                List.of(new FluidStack(Fluids.WATER, 1)),
                List.of(),
                List.of(),
                List.of(new FluidStack(MekanismFluids.HEAVY_WATER.get(), 1)),
                List.of(),
                ENERGY,
                PROCESS_TICKS,
                getMoldItem());
        return List.of(MekanismChemicalRecipeSupport.syntheticHolder(RECIPE_ID, converted));
    }
}
