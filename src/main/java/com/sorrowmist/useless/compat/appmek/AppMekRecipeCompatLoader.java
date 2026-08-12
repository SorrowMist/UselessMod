package com.sorrowmist.useless.compat.appmek;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.BoilerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ChemicalChemicalRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ChemicalCrystallizerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ChemicalDissolutionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ChemicalInjectionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ChemicalToChemicalRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ElectrolysisRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.FluidChemicalRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.ItemToChemicalRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MetallurgicInfuserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.NucleosynthesizingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PaintingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PressurizedReactionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PurificationChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.RotaryRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.OsmiumCompressorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.SpsRecipeAdapter;

/** Registers adapters whose output or input is represented by AppMek's MekanismKey. */
public final class AppMekRecipeCompatLoader {
    private AppMekRecipeCompatLoader() {
    }

    public static void register() {
        AppMekChemicalCompat.registerKeyProvider();
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();

        manager.registerAdapter(new MetallurgicInfuserRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new OsmiumCompressorRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new PurificationChamberRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new ChemicalInjectionChamberRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new PaintingRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ChemicalChemicalRecipeAdapter.chemicalInfuser(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ChemicalChemicalRecipeAdapter.pigmentMixer(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ChemicalToChemicalRecipeAdapter.isotopicCentrifuge(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ChemicalToChemicalRecipeAdapter.solarNeutronActivator(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new ElectrolysisRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new ChemicalDissolutionRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new ChemicalCrystallizerRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new FluidChemicalRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new PressurizedReactionRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new RotaryRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ItemToChemicalRecipeAdapter.chemicalConversion(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ItemToChemicalRecipeAdapter.oxidizing(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(ItemToChemicalRecipeAdapter.pigmentExtracting(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new NucleosynthesizingRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new SpsRecipeAdapter(), RecipeSourceIds.APP_MEK);
        manager.registerAdapter(new BoilerRecipeAdapter(), RecipeSourceIds.APP_MEK);
    }
}
