package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.attribute.GasAttributes;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.Mekanism;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.util.HeatUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import net.minecraft.tags.FluidTags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Dynamic Thermal Evaporative Boiler recipe-viewer adapter. */
public final class BoilerRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.BOILER_CASING.getBlock());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();
        List<SizedFluidIngredient> water = MekanismChemicalRecipeSupport.fluidIngredients(
                IngredientCreatorAccess.fluid().from(FluidTags.WATER, WATER_AMOUNT));
        if (water.isEmpty()) return result;

        // This is the common-side equivalent of BoilerRecipeViewerRecipe.getBoilerRecipes().
        double waterToSteamHeatNecessary = WATER_AMOUNT * HeatUtils.getWaterThermalEnthalpy()
                / HeatUtils.getSteamEnergyEfficiency();
        ChemicalStack<?> steam = MekanismGases.STEAM.getStack(WATER_AMOUNT);
        ResourceLocation waterId = Mekanism.rl("water");
        addRecipe(result, waterId, water.get(0), null, steam, mekanism.api.chemical.gas.GasStack.EMPTY);

        for (Gas gas : MekanismAPI.gasRegistry()) {
            GasAttributes.HeatedCoolant coolant=gas.get(GasAttributes.HeatedCoolant.class);
            if(coolant==null) continue;
            long amount=Math.round(waterToSteamHeatNecessary/coolant.getThermalEnthalpy());
            if(amount<=0) continue;
            GasStack heated=new GasStack(gas,amount);
            addRecipe(result,Mekanism.rl("boiler_"+id(heated)),water.get(0),heated,steam,
                    new GasStack(coolant.getCooledGas(),amount));
        }
        return result;
    }

    private static final int WATER_AMOUNT = 1;

    private void addRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result, ResourceLocation sourceId,
                           SizedFluidIngredient water, @Nullable ChemicalStack<?> coolant,
                           ChemicalStack<?> steam, ChemicalStack<?> cooledCoolant) {
        GenericStack steamKey = MekanismChemicalRecipeSupport.key(steam);
        GenericStack coolantKey = coolant == null ? null : MekanismChemicalRecipeSupport.key(coolant);
        GenericStack cooledKey = MekanismChemicalRecipeSupport.key(cooledCoolant);
        if (steamKey == null || (coolant != null && coolantKey == null)
                || (!cooledCoolant.isEmpty() && cooledKey == null)) return;

        List<GenericStack> chemicalInputs = coolantKey == null ? List.of() : List.of(coolantKey);
        List<GenericStack> chemicalOutputs = new ArrayList<>();
        chemicalOutputs.add(steamKey);
        if (cooledKey != null) chemicalOutputs.add(cooledKey);
        String suffix = "boiler_" + (coolant == null ? "water" : id(coolant));
        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(sourceId, suffix);
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), List.of(water), chemicalInputs, List.of(), List.of(), chemicalOutputs,
                0L,
                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }

    private static String id(ChemicalStack<?> stack) {
        var id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

}
