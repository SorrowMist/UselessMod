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
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatLoader;
import com.sorrowmist.useless.content.recipe.adapters.ae.dataenergistics.DataReassemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CircuitCutterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CrystalAssemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.EnchantingApparatusRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.ImbuementRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.DissolutionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.CrusherRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.EnrichmentChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MetallurgicInfuserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.OsmiumCompressorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PrecisionSawmillRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.SmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.AwakeningRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.InfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.productivebees.BeeProduceRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.productivebees.CentrifugeRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.draconicevolution.DraconicFusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.eco.IntegratedWorkingStationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.powah.EnergizingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.naturesaura.AnimalSpawnerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.naturesaura.NatureAltarRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.naturesaura.OfferingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.naturesaura.TreeRitualRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.occultism.OccultismRitualRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.malum.SpiritFocusingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.malum.SpiritInfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCombinationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCompressorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingEnderCrafterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingFluxCrafterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingTableRecipeAdapter;
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
    public static final String PRODUCTIVE_BEES = "productivebees";
    public static final String DRACONIC_EVOLUTION = "draconicevolution";
    public static final String POWAH = "powah";
    public static final String EXTENDED_CRAFTING = "extendedcrafting";
    public static final String NEO_ECO_AE = "neoecoae";
    public static final String NATURES_AURA = "naturesaura";
    public static final String OCCULTISM = "occultism";
    public static final String MALUM = "malum";

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
            new CompatEntry(DATA_ENERGISTICS, RecipeAdapterCompatRegistry::registerDataEnergistics),
            new CompatEntry(PRODUCTIVE_BEES, RecipeAdapterCompatRegistry::registerProductiveBees),
            new CompatEntry(DRACONIC_EVOLUTION, RecipeAdapterCompatRegistry::registerDraconicEvolution),
            new CompatEntry(POWAH, RecipeAdapterCompatRegistry::registerPowah),
            new CompatEntry(EXTENDED_CRAFTING, RecipeAdapterCompatRegistry::registerExtendedCrafting),
            new CompatEntry(NEO_ECO_AE, RecipeAdapterCompatRegistry::registerNeoECOAE),
            new CompatEntry(NATURES_AURA, RecipeAdapterCompatRegistry::registerNaturesAura),
            new CompatEntry(OCCULTISM, RecipeAdapterCompatRegistry::registerOccultism),
            new CompatEntry(MALUM, RecipeAdapterCompatRegistry::registerMalum)
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
        register(new CrusherRecipeAdapter());
        register(new PrecisionSawmillRecipeAdapter());
        register(new OsmiumCompressorRecipeAdapter());
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
        AELightningTechCompatLoader.createAdapters().forEach(RecipeAdapterCompatRegistry::register);
    }

    private static void registerDataEnergistics() {
        register(new DataReassemblerRecipeAdapter());
    }

    private static void registerProductiveBees() {
        register(new BeeProduceRecipeAdapter());
        register(new CentrifugeRecipeAdapter());
    }

    private static void registerDraconicEvolution() {
        register(new DraconicFusionRecipeAdapter());
    }

    private static void registerPowah() {
        register(new EnergizingRecipeAdapter());
    }

    private static void registerExtendedCrafting() {
        register(new ExtendedCraftingTableRecipeAdapter());
        register(new ExtendedCraftingCompressorRecipeAdapter());
        register(new ExtendedCraftingCombinationRecipeAdapter());
        register(new ExtendedCraftingEnderCrafterRecipeAdapter());
        register(new ExtendedCraftingFluxCrafterRecipeAdapter());
    }

    private static void registerNeoECOAE() {
        register(new IntegratedWorkingStationRecipeAdapter());
    }

    private static void registerNaturesAura() {
        register(new TreeRitualRecipeAdapter());
        register(new NatureAltarRecipeAdapter());
        register(new AnimalSpawnerRecipeAdapter());
        register(new OfferingRecipeAdapter());
    }

    private static void registerOccultism() {
        register(new OccultismRitualRecipeAdapter());
    }

    private static void registerMalum() {
        register(new SpiritFocusingRecipeAdapter());
        register(new SpiritInfusionRecipeAdapter());
    }

    private record CompatEntry(@Nullable String modId, Runnable registerAction) {}
}
