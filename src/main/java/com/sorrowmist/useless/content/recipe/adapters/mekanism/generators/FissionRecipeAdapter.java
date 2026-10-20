package com.sorrowmist.useless.content.recipe.adapters.mekanism.generators;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismChemicalRecipeSupport;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipe;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismSyntheticRecipeAdapter;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.attribute.GasAttributes;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismGases;
import mekanism.common.util.HeatUtils;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Optional Mekanism Generators fission recipe-viewer adapter. */
public final class FissionRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        double energyPerFuel = MekanismGeneratorsConfig.generators.energyPerFissionFuel.get().doubleValue();
        long waterAmount = Math.round(energyPerFuel * HeatUtils.getSteamEnergyEfficiency()
                / HeatUtils.getWaterThermalEnthalpy());
        return createGeneratedRecipes(energyPerFuel, waterAmount);
    }

    List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(double energyPerFuel, long waterAmount) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();

        ChemicalStack<?> fuel = MekanismGases.FISSILE_FUEL.getStack(1);
        ChemicalStack<?> steam = MekanismGases.STEAM.getStack(waterAmount);
        var water = IngredientCreatorAccess.fluid().from(FluidTags.WATER, Math.toIntExact(waterAmount));
        result.addAll(createWaterRecipes(water, fuel, steam, MekanismGases.NUCLEAR_WASTE.getStack(1)));

        for(Gas gas:MekanismAPI.gasRegistry()) {
            GasAttributes.CooledCoolant coolant=gas.get(GasAttributes.CooledCoolant.class);
            if(coolant==null) continue;
            long amount=Math.round(energyPerFuel/coolant.getThermalEnthalpy());
            if(amount<=0) continue;
            GasStack cooled=new GasStack(gas,amount);
            addRecipe(result,MekanismGenerators.rl("fission_"+id(cooled)),fuel,cooled,null,
                    new GasStack(coolant.getHeatedGas(),amount),MekanismGases.NUCLEAR_WASTE.getStack(1));
        }
        return result;
    }

    List<RecipeHolder<MekanismSyntheticRecipe>> createWaterRecipes(
            FluidStackIngredient water, ChemicalStack<?> fuel, ChemicalStack<?> steam, ChemicalStack<?> waste) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();
        List<SizedFluidIngredient> waterInputs = MekanismChemicalRecipeSupport.fluidIngredients(water);
        if (!waterInputs.isEmpty()) {
            addRecipe(result, MekanismGenerators.rl("fission_water"), fuel, null,
                    waterInputs.get(0), steam, waste);
        }
        return result;
    }

    private void addRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result, ResourceLocation sourceId,
                           ChemicalStack<?> fuel, @Nullable ChemicalStack<?> coolant,
                           @Nullable SizedFluidIngredient water,
                           ChemicalStack<?> outputCoolant, ChemicalStack<?> waste) {
        GenericStack fuelKey = MekanismChemicalRecipeSupport.key(fuel);
        GenericStack coolantKey = coolant == null ? null : MekanismChemicalRecipeSupport.key(coolant);
        GenericStack outputCoolantKey = MekanismChemicalRecipeSupport.key(outputCoolant);
        GenericStack wasteKey = MekanismChemicalRecipeSupport.key(waste);
        if (fuelKey == null || (coolant != null && coolantKey == null) || outputCoolantKey == null || wasteKey == null) return;

        List<GenericStack> inputs = coolantKey == null ? List.of(fuelKey) : List.of(fuelKey, coolantKey);
        if (water != null && (water.ingredient() == null || water.ingredient().isEmpty()
                || water.amount() <= 0)) return;

        String suffix = "fission_" + id(fuel);
        if (water != null) {
            suffix += "_water";
        } else if (coolant != null) {
            suffix += "_" + id(coolant);
        } else {
            return;
        }
        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(sourceId, suffix);
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), water == null ? List.of() : List.of(water), inputs, List.of(), List.of(),
                List.of(outputCoolantKey, wasteKey),
                0L,
                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }

    private static String id(ChemicalStack<?> stack) {
        var id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

}
