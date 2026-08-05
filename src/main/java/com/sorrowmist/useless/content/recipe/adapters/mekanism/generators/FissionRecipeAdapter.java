package com.sorrowmist.useless.content.recipe.adapters.mekanism.generators;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismChemicalRecipeSupport;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipeAdapter;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.attribute.ChemicalAttributes;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.api.math.MathUtils;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.util.HeatUtils;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Optional Mekanism Generators fission recipe-viewer adapter. */
public final class FissionRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(GeneratorsBlocks.FISSION_REACTOR_CASING.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        long energyPerFuel = MekanismGeneratorsConfig.generators.energyPerFissionFuel.get();
        long waterAmount = Math.round(energyPerFuel * HeatUtils.getSteamEnergyEfficiency()
                / HeatUtils.getWaterThermalEnthalpy());
        return createGeneratedRecipes(energyPerFuel, waterAmount);
    }

    List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(long energyPerFuel, long waterAmount) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();

        ChemicalStack fuel = MekanismChemicals.FISSILE_FUEL.asStack(1);
        ChemicalStack steam = MekanismChemicals.STEAM.asStack(waterAmount);
        var water = IngredientCreatorAccess.fluid().from(FluidTags.WATER, MathUtils.clampToInt(waterAmount));
        result.addAll(createWaterRecipes(water, fuel, steam, MekanismChemicals.NUCLEAR_WASTE.asStack(1)));

        for (var entry : MekanismAPI.CHEMICAL_REGISTRY
                .getDataMap(IMekanismDataMapTypes.INSTANCE.cooledChemicalCoolant()).entrySet()) {
            ResourceKey<Chemical> key = entry.getKey();
            CooledCoolant coolant = entry.getValue();
            long amount = Math.round(energyPerFuel / coolant.thermalEnthalpy());
            ChemicalStackIngredient input = IngredientCreatorAccess.chemicalStack().fromHolder(
                    MekanismAPI.CHEMICAL_REGISTRY.getHolderOrThrow(key), amount);
            for (ChemicalStack concrete : input.getRepresentations()) {
                addRecipe(result, MekanismGenerators.rl("fission_" + id(concrete)), fuel, concrete,
                        null, coolant.heat(amount), MekanismChemicals.NUCLEAR_WASTE.asStack(1));
            }
        }

        @SuppressWarnings("removal")
        Iterable<Chemical> chemicals = MekanismAPI.CHEMICAL_REGISTRY;
        for (Chemical chemical : chemicals) {
            @SuppressWarnings("removal")
            ChemicalAttributes.CooledCoolant legacy = chemical.getLegacy(ChemicalAttributes.CooledCoolant.class);
            if (legacy == null) continue;
            long amount = Math.round(energyPerFuel / legacy.getThermalEnthalpy());
            ChemicalStack heated = legacy.getHeatedChemical().getStack(amount);
            addRecipe(result, MekanismGenerators.rl("fission_" + id(chemical.getStack(amount))), fuel,
                    chemical.getStack(amount), null, heated, MekanismChemicals.NUCLEAR_WASTE.asStack(1));
        }
        return result;
    }

    List<RecipeHolder<MekanismSyntheticRecipe>> createWaterRecipes(
            FluidStackIngredient water, ChemicalStack fuel, ChemicalStack steam, ChemicalStack waste) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();
        for (FluidStack waterStack : MekanismChemicalRecipeSupport.fluidRepresentations(water)) {
            addRecipe(result, MekanismGenerators.rl("fission_water"), fuel, null, waterStack, steam, waste);
        }
        return result;
    }

    private void addRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result, ResourceLocation sourceId,
                           ChemicalStack fuel, @Nullable ChemicalStack coolant,
                           @Nullable FluidStack water,
                           ChemicalStack outputCoolant, ChemicalStack waste) {
        GenericStack fuelKey = MekanismChemicalRecipeSupport.key(fuel);
        GenericStack coolantKey = coolant == null ? null : MekanismChemicalRecipeSupport.key(coolant);
        GenericStack outputCoolantKey = MekanismChemicalRecipeSupport.key(outputCoolant);
        GenericStack wasteKey = MekanismChemicalRecipeSupport.key(waste);
        if ((coolant != null && coolantKey == null) || outputCoolantKey == null || wasteKey == null) return;

        List<GenericStack> inputs = coolantKey == null ? List.of(fuelKey) : List.of(fuelKey, coolantKey);
        if (water != null && water.isEmpty()) return;
        List<FluidStack> fluids = water == null ? List.of() : List.of(water.copy());

        String suffix = "fission_" + id(fuel);
        if (water != null) {
            suffix += "_water_" + fluidId(water);
        } else if (coolant != null) {
            suffix += "_" + id(coolant);
        } else {
            return;
        }
        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(sourceId, suffix);
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), fluids, inputs, List.of(), List.of(),
                List.of(outputCoolantKey, wasteKey),
                0L,
                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }

    private static String id(ChemicalStack stack) {
        var id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static String fluidId(FluidStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
