package com.sorrowmist.useless.content.recipe.adapters.mekanism.mekanismextra;

import appeng.api.stacks.GenericStack;
import com.jerry.generator_extras.common.config.GenLoadConfig;
import com.jerry.generator_extras.common.genregistries.ExtraGenBlocks;
import com.jerry.generator_extras.common.genregistries.ExtraGenGases;
import com.jerry.mekanism_extras.common.registries.ExtraGases;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismChemicalRecipeSupport;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.util.HeatUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Optional Mekanism Extras 1.5+ naquadah reactor recipe-viewer adapter. */
public final class NaquadahReactorRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final ResourceLocation SOURCE_ID =
            new ResourceLocation("mekanism_extras", "naquadah_reactor");

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(ExtraGenBlocks.NAQUADAH_REACTOR_CONTROLLER.getBlock());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new java.util.ArrayList<>(3);

        double energyPerFuel = GenLoadConfig.generatorConfig.energyPerReactorFuel.get().doubleValue();
        long waterPerFuel = Math.round(energyPerFuel * HeatUtils.getSteamEnergyEfficiency()
                / HeatUtils.getWaterThermalEnthalpy());
        if (waterPerFuel <= 0L) {
            return result;
        }

        var singleFuelWater = IngredientCreatorAccess.fluid().from(
                FluidTags.WATER, Math.toIntExact(waterPerFuel));
        var singleFuelWaterInputs = MekanismChemicalRecipeSupport.fluidIngredients(singleFuelWater);
        var pairedFuelWater = IngredientCreatorAccess.fluid().from(
                FluidTags.WATER, Math.toIntExact(
                        MekanismChemicalRecipeSupport.saturatingMultiply(waterPerFuel, 2L)));
        var pairedFuelWaterInputs = MekanismChemicalRecipeSupport.fluidIngredients(pairedFuelWater);
        if (singleFuelWaterInputs.isEmpty() || pairedFuelWaterInputs.isEmpty()) {
            return result;
        }

        addFuelRecipe(result, pairedFuelWaterInputs,
                List.of(ExtraGases.RICH_NAQUADAH_FUEL.getStack(1),
                        ExtraGases.RICH_URANIUM_FUEL.getStack(1)),
                MekanismChemicalRecipeSupport.saturatingMultiply(waterPerFuel, 2L),
                "rich_fuels_water");
        addFuelRecipe(result, singleFuelWaterInputs,
                List.of(ExtraGases.NAQUADAH_URANIUM_FUEL.getStack(1)),
                waterPerFuel, "naquadah_uranium_fuel_water");
        addWaterOnlyRecipe(result);
        return result;
    }

    private void addFuelRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result,
                               List<?> waterInputs, List<ChemicalStack<?>> fuels,
                               long steamAmount, String suffix) {
        List<GenericStack> fuelKeys = new java.util.ArrayList<>(fuels.size());
        for (ChemicalStack<?> fuel : fuels) {
            GenericStack fuelKey = MekanismChemicalRecipeSupport.key(fuel);
            if (fuelKey == null) {
                return;
            }
            fuelKeys.add(fuelKey);
        }
        GenericStack steamKey = MekanismChemicalRecipeSupport.key(
                ExtraGenGases.POLONIUM_CONTAINING_STEAM.getStack(steamAmount));
        if (steamKey == null) {
            return;
        }

        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(SOURCE_ID, suffix);
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), waterInputs, fuelKeys, List.of(), List.of(), List.of(steamKey),
                0L, AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }

    private void addWaterOnlyRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result) {
        var water = IngredientCreatorAccess.fluid().from(FluidTags.WATER, 1);
        var waterInputs = MekanismChemicalRecipeSupport.fluidIngredients(water);
        GenericStack steamKey = MekanismChemicalRecipeSupport.key(
                ExtraGenGases.POLONIUM_CONTAINING_STEAM.getStack(1));
        if (waterInputs.isEmpty() || steamKey == null) {
            return;
        }

        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(SOURCE_ID, "water");
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), waterInputs, List.of(), List.of(), List.of(), List.of(steamKey),
                0L, AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }
}
