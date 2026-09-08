package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.jerry.mekmm.common.config.MoreMachineConfig;
import com.jerry.mekmm.common.registries.MoreMachineBlocks;
import com.jerry.mekmm.common.registries.MoreMachineChemicals;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Exposes Mekanism More Machine's ambient gas collector as a water-fed recipe. */
public final class AmbientGasCollectorRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final int PROCESS_TICKS = 19;
    private static final ResourceLocation RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("mekmm", "ambient_gas_collector_water");

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MoreMachineBlocks.AMBIENT_GAS_COLLECTOR.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        int gasAmount = MoreMachineConfig.general.gasCollectAmount.get();
        if (gasAmount <= 0) {
            return List.of();
        }

        GenericStack output = MekanismChemicalRecipeSupport.key(
                MoreMachineChemicals.UNSTABLE_DIMENSIONAL_GAS.asStack(gasAmount));
        if (output == null) {
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                RECIPE_ID,
                List.of(),
                List.of(new FluidStack(Fluids.WATER, gasAmount)),
                List.of(),
                List.of(),
                List.of(),
                List.of(output),
                MekanismChemicalRecipeSupport.saturatingMultiply(
                        MoreMachineConfig.usage.ambientGasCollector.get(), PROCESS_TICKS),
                PROCESS_TICKS,
                getMoldItem());
        return List.of(MekanismChemicalRecipeSupport.syntheticHolder(RECIPE_ID, converted));
    }
}
