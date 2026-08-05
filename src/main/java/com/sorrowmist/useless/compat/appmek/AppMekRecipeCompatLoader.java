package com.sorrowmist.useless.compat.appmek;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
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

        manager.registerAdapter(new MetallurgicInfuserRecipeAdapter());
        manager.registerAdapter(new OsmiumCompressorRecipeAdapter());
        manager.registerAdapter(new PurificationChamberRecipeAdapter());
        manager.registerAdapter(new ChemicalInjectionChamberRecipeAdapter());
        manager.registerAdapter(new PaintingRecipeAdapter());
        manager.registerAdapter(ChemicalChemicalRecipeAdapter.chemicalInfuser());
        manager.registerAdapter(ChemicalChemicalRecipeAdapter.pigmentMixer());
        manager.registerAdapter(ChemicalToChemicalRecipeAdapter.isotopicCentrifuge());
        manager.registerAdapter(ChemicalToChemicalRecipeAdapter.solarNeutronActivator());
        manager.registerAdapter(new ElectrolysisRecipeAdapter());
        manager.registerAdapter(new ChemicalDissolutionRecipeAdapter());
        manager.registerAdapter(new ChemicalCrystallizerRecipeAdapter());
        manager.registerAdapter(new FluidChemicalRecipeAdapter());
        manager.registerAdapter(new PressurizedReactionRecipeAdapter());
        manager.registerAdapter(new RotaryRecipeAdapter());
        manager.registerAdapter(ItemToChemicalRecipeAdapter.chemicalConversion());
        manager.registerAdapter(ItemToChemicalRecipeAdapter.oxidizing());
        manager.registerAdapter(ItemToChemicalRecipeAdapter.pigmentExtracting());
        manager.registerAdapter(new NucleosynthesizingRecipeAdapter());
        manager.registerAdapter(new SpsRecipeAdapter());
        manager.registerAdapter(new BoilerRecipeAdapter());
    }
}
