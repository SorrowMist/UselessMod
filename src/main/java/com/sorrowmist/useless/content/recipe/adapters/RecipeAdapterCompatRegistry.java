package com.sorrowmist.useless.content.recipe.adapters;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.EmpowererRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.LaserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.advancedae.ReactionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2.InscriberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CircuitEtcherRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalAggregatorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalGrowthRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalPulverizerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.CrystalCatalyzerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.LightningAssemblyRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.LightningSimulationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.OverloadProcessingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.SteakLightningRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.dataenergistics.DataReassemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CircuitCutterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CrystalAssemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.EnchantingApparatusRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.ImbuementRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.DissolutionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.EnrichmentChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MetallurgicInfuserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.SmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.AwakeningRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.InfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RecipeAdapterCompatRegistry {
    private static final Logger LOGGER = LogManager.getLogger(RecipeAdapterCompatRegistry.class);

    public static final String EXTENDED_AE = "extendedae";
    public static final String ADVANCED_AE = "advanced_ae";
    public static final String MEKANISM = "mekanism";
    public static final String AE2 = "ae2";
    public static final String AE2CS = "ae2cs";
    public static final String INDUSTRIAL_FOREGOING = "industrialforegoing";
    public static final String ACTUALLY_ADDITIONS = "actuallyadditions";
    public static final String ARS_NOUVEAU = "ars_nouveau";
    public static final String MYSTICAL_AGRICULTURE = "mysticalagriculture";
    public static final String AE2LT = "ae2lt";
    public static final String DATA_ENERGISTICS = "data_energistics";

    private static final List<CompatEntry> ENTRIES = List.of(
            new CompatEntry(null, RecipeAdapterCompatRegistry::registerMinecraft),
            new CompatEntry(EXTENDED_AE, RecipeAdapterCompatRegistry::registerExtendedAE),
            new CompatEntry(ADVANCED_AE, RecipeAdapterCompatRegistry::registerAdvancedAE),
            new CompatEntry(MEKANISM, RecipeAdapterCompatRegistry::registerMekanism),
            new CompatEntry(AE2, RecipeAdapterCompatRegistry::registerAE2),
            new CompatEntry(AE2CS, RecipeAdapterCompatRegistry::registerAECrystalScience),
            new CompatEntry(INDUSTRIAL_FOREGOING, RecipeAdapterCompatRegistry::registerIndustrialForegoing),
            new CompatEntry(ACTUALLY_ADDITIONS, RecipeAdapterCompatRegistry::registerActuallyAdditions),
            new CompatEntry(ARS_NOUVEAU, RecipeAdapterCompatRegistry::registerArsNouveau),
            new CompatEntry(MYSTICAL_AGRICULTURE, RecipeAdapterCompatRegistry::registerMysticalAgriculture),
            new CompatEntry(AE2LT, RecipeAdapterCompatRegistry::registerAELightningTech),
            new CompatEntry(DATA_ENERGISTICS, RecipeAdapterCompatRegistry::registerDataEnergistics)
    );

    private RecipeAdapterCompatRegistry() {}

    public static void init(FMLCommonSetupEvent event) {
        for (CompatEntry entry : ENTRIES) {
            initCompat(event, entry.modId(), entry.registerAction());
        }
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static void initCompat(FMLCommonSetupEvent event, @Nullable String modId, Runnable registerAction) {
        if (modId != null && !isLoaded(modId)) return;

        event.enqueueWork(() -> {
            try {
                registerAction.run();
            } catch (Exception e) {
                LOGGER.error("Failed to register recipe adapters for mod: {}", modId, e);
            }
        });
    }

    private static void register(IRecipeAdapter<?> adapter) {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(adapter);
    }

    private static void registerMinecraft() {
        register(new SmeltingRecipeAdapter());
    }

    private static void registerExtendedAE() {
        register(new CircuitCutterRecipeAdapter());
        register(new CrystalAssemblerRecipeAdapter());
    }

    private static void registerAdvancedAE() {
        register(new ReactionChamberRecipeAdapter());
    }

    private static void registerMekanism() {
        register(new MetallurgicInfuserRecipeAdapter());
        register(new EnrichmentChamberRecipeAdapter());
    }

    private static void registerAE2() {
        register(new InscriberRecipeAdapter());
    }

    private static void registerAECrystalScience() {
        register(new CircuitEtcherRecipeAdapter());
        register(new CrystalAggregatorRecipeAdapter());
        register(new CrystalPulverizerRecipeAdapter());
        register(new CrystalGrowthRecipeAdapter());
    }

    private static void registerIndustrialForegoing() {
        register(new DissolutionChamberRecipeAdapter());
    }

    private static void registerActuallyAdditions() {
        register(new LaserRecipeAdapter());
        register(new EmpowererRecipeAdapter());
    }

    private static void registerArsNouveau() {
        register(new EnchantingApparatusRecipeAdapter());
        register(new ImbuementRecipeAdapter());
    }

    private static void registerMysticalAgriculture() {
        register(new InfusionRecipeAdapter());
        register(new AwakeningRecipeAdapter());
        register(new SeedEssenceRecipeAdapter());
    }

    private static void registerAELightningTech() {
        register(new LightningSimulationRecipeAdapter());
        register(new LightningAssemblyRecipeAdapter());
        register(new OverloadProcessingRecipeAdapter());
        register(new CrystalCatalyzerRecipeAdapter());
        register(new SteakLightningRecipeAdapter());
    }

    private static void registerDataEnergistics() {
        register(new DataReassemblerRecipeAdapter());
    }

    private record CompatEntry(@Nullable String modId, Runnable registerAction) {}
}
