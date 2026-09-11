package com.sorrowmist.useless.content.recipe.adapters.mekanism.mekanismextra;

import appeng.api.stacks.GenericStack;
import com.jerry.genextras.common.config.GeneratorsExtraConfig;
import com.jerry.genextras.common.registries.GenExtraBlocks;
import com.jerry.genextras.common.registries.GenExtraChemicals;
import com.jerry.mekextras.common.registries.ExtraChemicals;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismChemicalRecipeSupport;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.math.MathUtils;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.util.HeatUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Optional Mekanism Extras naquadah reactor recipe-viewer adapter. */
public final class NaquadahReactorRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final ResourceLocation SOURCE_ID =
            ResourceLocation.fromNamespaceAndPath("mekanism_extras", "naquadah_reactor");

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(GenExtraBlocks.NAQUADAH_REACTOR_CONTROLLER.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new java.util.ArrayList<>(3);

        long energyPerFuel = GeneratorsExtraConfig.extraGenerators.energyPerReactorFuel.get();
        long waterPerFuel = Math.round(energyPerFuel * HeatUtils.getSteamEnergyEfficiency()
                / HeatUtils.getWaterThermalEnthalpy());
        if (waterPerFuel <= 0L) {
            return result;
        }

        var singleFuelWater = IngredientCreatorAccess.fluid().from(
                FluidTags.WATER, MathUtils.clampToInt(waterPerFuel));
        var singleFuelWaterInputs = MekanismChemicalRecipeSupport.fluidIngredients(singleFuelWater);
        var pairedFuelWater = IngredientCreatorAccess.fluid().from(
                FluidTags.WATER, MathUtils.clampToInt(
                        MekanismChemicalRecipeSupport.saturatingMultiply(waterPerFuel, 2L)));
        var pairedFuelWaterInputs = MekanismChemicalRecipeSupport.fluidIngredients(pairedFuelWater);
        if (singleFuelWaterInputs.isEmpty() || pairedFuelWaterInputs.isEmpty()) {
            return result;
        }

        addFuelRecipe(result, pairedFuelWaterInputs,
                List.of(ExtraChemicals.RICH_NAQUADAH_FUEL.asStack(1),
                        ExtraChemicals.RICH_URANIUM_FUEL.asStack(1)),
                MekanismChemicalRecipeSupport.saturatingMultiply(waterPerFuel, 2L),
                "rich_fuels_water");
        addFuelRecipe(result, singleFuelWaterInputs,
                List.of(ExtraChemicals.NAQUADAH_URANIUM_FUEL.asStack(1)),
                waterPerFuel, "naquadah_uranium_fuel_water");
        addWaterOnlyRecipe(result);
        return result;
    }

    private void addFuelRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result,
                               List<?> waterInputs, List<ChemicalStack> fuels,
                               long steamAmount, String suffix) {
        List<GenericStack> fuelKeys = new java.util.ArrayList<>(fuels.size());
        for (ChemicalStack fuel : fuels) {
            GenericStack fuelKey = MekanismChemicalRecipeSupport.key(fuel);
            if (fuelKey == null) {
                return;
            }
            fuelKeys.add(fuelKey);
        }
        GenericStack steamKey = MekanismChemicalRecipeSupport.key(
                GenExtraChemicals.POLONIUM_CONTAINING_STEAM.asStack(steamAmount));
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
                GenExtraChemicals.POLONIUM_CONTAINING_STEAM.asStack(1));
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
