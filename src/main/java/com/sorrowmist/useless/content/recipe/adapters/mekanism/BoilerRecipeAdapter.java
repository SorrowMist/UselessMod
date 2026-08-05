package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.attribute.ChemicalAttributes;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.datamaps.chemical.attribute.HeatedCoolant;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.util.HeatUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.tags.FluidTags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Dynamic Thermal Evaporative Boiler recipe-viewer adapter. */
public final class BoilerRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.BOILER_CASING.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>();
        List<FluidStack> water = MekanismChemicalRecipeSupport.fluidRepresentations(
                IngredientCreatorAccess.fluid().from(FluidTags.WATER, WATER_AMOUNT));
        if (water.isEmpty()) return result;

        // This is the common-side equivalent of BoilerRecipeViewerRecipe.getBoilerRecipes().
        double waterToSteamHeatNecessary = WATER_AMOUNT * HeatUtils.getWaterThermalEnthalpy()
                / HeatUtils.getSteamEnergyEfficiency();
        ChemicalStack steam = MekanismChemicals.STEAM.asStack(WATER_AMOUNT);
        ResourceLocation waterId = Mekanism.rl("water");
        for (FluidStack waterStack : water) {
            addRecipe(result, waterId, waterStack, null, steam, ChemicalStack.EMPTY);
        }

        for (Map.Entry<ResourceKey<Chemical>, HeatedCoolant> entry : MekanismAPI.CHEMICAL_REGISTRY
                .getDataMap(IMekanismDataMapTypes.INSTANCE.heatedChemicalCoolant()).entrySet()) {
            ResourceKey<Chemical> key = entry.getKey();
            HeatedCoolant coolant = entry.getValue();
            long amount = Math.round(waterToSteamHeatNecessary / coolant.thermalEnthalpy());
            ChemicalStackIngredient input = IngredientCreatorAccess.chemicalStack().fromHolder(
                    MekanismAPI.CHEMICAL_REGISTRY.getHolderOrThrow(key), amount);
            for (FluidStack waterStack : water) {
                for (ChemicalStack concrete : input.getRepresentations()) {
                    addRecipe(result, Mekanism.rl("boiler_" + id(concrete)), waterStack,
                            concrete, steam, coolant.cool(amount));
                }
            }
        }

        @SuppressWarnings("removal")
        Iterable<Chemical> chemicals = MekanismAPI.CHEMICAL_REGISTRY;
        for (Chemical chemical : chemicals) {
            @SuppressWarnings("removal")
            ChemicalAttributes.HeatedCoolant legacy = chemical.getLegacy(ChemicalAttributes.HeatedCoolant.class);
            if (legacy == null) continue;
            long amount = Math.round(waterToSteamHeatNecessary / legacy.getThermalEnthalpy());
            ChemicalStack coolant = chemical.getStack(amount);
            for (FluidStack waterStack : water) {
                addRecipe(result, Mekanism.rl("boiler_" + id(coolant)), waterStack,
                        coolant, steam, legacy.getCooledChemical().getStack(amount));
            }
        }
        return result;
    }

    private static final int WATER_AMOUNT = 1;

    private void addRecipe(List<RecipeHolder<MekanismSyntheticRecipe>> result, ResourceLocation sourceId,
                           FluidStack water, @Nullable ChemicalStack coolant,
                           ChemicalStack steam, ChemicalStack cooledCoolant) {
        GenericStack steamKey = MekanismChemicalRecipeSupport.key(steam);
        GenericStack coolantKey = coolant == null ? null : MekanismChemicalRecipeSupport.key(coolant);
        GenericStack cooledKey = MekanismChemicalRecipeSupport.key(cooledCoolant);
        if (steamKey == null || (coolant != null && coolantKey == null)
                || (!cooledCoolant.isEmpty() && cooledKey == null)) return;

        List<GenericStack> chemicalInputs = coolantKey == null ? List.of() : List.of(coolantKey);
        List<GenericStack> chemicalOutputs = new ArrayList<>();
        chemicalOutputs.add(steamKey);
        if (cooledKey != null) chemicalOutputs.add(cooledKey);
        String suffix = "boiler_" + fluidId(water)
                + (coolant == null ? "_water" : "_" + id(coolant));
        ResourceLocation id = MekanismChemicalRecipeSupport.variantId(sourceId, suffix);
        AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                id, List.of(), List.of(water.copy()), chemicalInputs, List.of(), List.of(), chemicalOutputs,
                0L,
                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem());
        result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
    }

    private static String id(ChemicalStack stack) {
        var id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static String fluidId(FluidStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
