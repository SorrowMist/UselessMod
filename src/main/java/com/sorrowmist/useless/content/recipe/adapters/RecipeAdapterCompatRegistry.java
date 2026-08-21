package com.sorrowmist.useless.content.recipe.adapters;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
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
import com.sorrowmist.useless.content.recipe.adapters.minecraft.CraftingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.BrewingRecipeAdapter;
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
import com.sorrowmist.useless.content.recipe.adapters.enderio.AlloySmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.enderio.EnchanterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SlicingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SagMillingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulBindingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.enderio.VatFermentingRecipeAdapter;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RecipeAdapterCompatRegistry {
    private static final Logger LOGGER = LogManager.getLogger(RecipeAdapterCompatRegistry.class);

    public static final String EXTENDED_AE = RecipeSourceIds.EXTENDED_AE;
    public static final String ADVANCED_AE = RecipeSourceIds.ADVANCED_AE;
    public static final String MEKANISM = RecipeSourceIds.MEKANISM;
    public static final String MEKANISM_GENERATORS = RecipeSourceIds.MEKANISM_GENERATORS;
    public static final String APP_MEK = RecipeSourceIds.APP_MEK;
    public static final String AE2 = RecipeSourceIds.AE2;
    public static final String AE2CS = RecipeSourceIds.AE2CS;
    public static final String INDUSTRIAL_FOREGOING = RecipeSourceIds.INDUSTRIAL_FOREGOING;
    public static final String ACTUALLY_ADDITIONS = RecipeSourceIds.ACTUALLY_ADDITIONS;
    public static final String ARS_NOUVEAU = RecipeSourceIds.ARS_NOUVEAU;
    public static final String MYSTICAL_AGRICULTURE = RecipeSourceIds.MYSTICAL_AGRICULTURE;
    public static final String AE2LT = RecipeSourceIds.AE2LT;
    public static final String DATA_ENERGISTICS = RecipeSourceIds.DATA_ENERGISTICS;
    public static final String PRODUCTIVE_BEES = RecipeSourceIds.PRODUCTIVE_BEES;
    public static final String DRACONIC_EVOLUTION = RecipeSourceIds.DRACONIC_EVOLUTION;
    public static final String POWAH = RecipeSourceIds.POWAH;
    public static final String EXTENDED_CRAFTING = RecipeSourceIds.EXTENDED_CRAFTING;
    public static final String NEO_ECO_AE = RecipeSourceIds.NEO_ECO_AE;
    public static final String NATURES_AURA = RecipeSourceIds.NATURES_AURA;
    public static final String FORBIDDEN_ARCANUS = RecipeSourceIds.FORBIDDEN_ARCANUS;
    public static final String OCCULTISM = RecipeSourceIds.OCCULTISM;
    public static final String MALUM = RecipeSourceIds.MALUM;
    public static final String ENDER_IO = RecipeSourceIds.ENDER_IO;
    public static final String CREATE = RecipeSourceIds.CREATE;
    public static final String ORITECH = RecipeSourceIds.ORITECH;
    public static final String NEOVITAE = RecipeSourceIds.NEOVITAE;
    public static final String UFO = RecipeSourceIds.UFO;

    private static final List<CompatEntry> ENTRIES = List.of(
            new CompatEntry(null, RecipeAdapterCompatRegistry::registerMinecraft),
            new CompatEntry(EXTENDED_AE, RecipeAdapterCompatRegistry::registerExtendedAE),
            new CompatEntry(ADVANCED_AE, RecipeAdapterCompatRegistry::registerAdvancedAE),
            new CompatEntry(MEKANISM, RecipeAdapterCompatRegistry::registerMekanism),
            new CompatEntry(MEKANISM_GENERATORS, RecipeAdapterCompatRegistry::registerMekanismGenerators),
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
            new CompatEntry(FORBIDDEN_ARCANUS, RecipeAdapterCompatRegistry::registerForbiddenArcanus),
            new CompatEntry(OCCULTISM, RecipeAdapterCompatRegistry::registerOccultism),
            new CompatEntry(MALUM, RecipeAdapterCompatRegistry::registerMalum),
            new CompatEntry(ENDER_IO, RecipeAdapterCompatRegistry::registerEnderIO),
            new CompatEntry(CREATE, RecipeAdapterCompatRegistry::registerCreate),
            new CompatEntry(ORITECH, RecipeAdapterCompatRegistry::registerOritech),
            new CompatEntry(NEOVITAE, RecipeAdapterCompatRegistry::registerNeoVitae),
            new CompatEntry(UFO, RecipeAdapterCompatRegistry::registerUfo)
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
        if (modId != null && (!isLoaded(modId) || !ConfigManager.isRecipeConversionEnabled(modId))) return;

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
        if (ConfigManager.isCraftingRecipeConversionEnabled()) {
            register(new CraftingRecipeAdapter());
        }
        if (ConfigManager.isSmeltingRecipeConversionEnabled()) {
            register(new SmeltingRecipeAdapter());
        }
        if (ConfigManager.isBrewingRecipeConversionEnabled()) {
            register(new BrewingRecipeAdapter());
        }
    }

    private static void registerExtendedAE() {
        register(new CircuitCutterRecipeAdapter());
        register(new CrystalAssemblerRecipeAdapter());
    }

    private static void registerAdvancedAE() {
        register(new ReactionChamberRecipeAdapter());
    }

    private static void registerMekanism() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.mekanism.MekanismRecipeCompatLoader");
    }

    private static void registerMekanismGenerators() {
        if (isLoaded(MEKANISM) && isLoaded(APP_MEK)) {
            invokeOptionalLoader("com.sorrowmist.useless.compat.mekanismgenerators.MekanismGeneratorsCompatLoader");
        }
    }

    private static void invokeOptionalLoader(String className) {
        try {
            Class<?> loader = Class.forName(className, true, RecipeAdapterCompatRegistry.class.getClassLoader());
            loader.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("Failed to register optional recipe adapters from {}", className, exception);
        }
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

    private static void registerForbiddenArcanus() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.forbiddenarcanus.ForbiddenArcanusRecipeCompatLoader");
    }

    private static void registerOccultism() {
        register(new OccultismRitualRecipeAdapter());
    }

    private static void registerMalum() {
        register(new SpiritFocusingRecipeAdapter());
        register(new SpiritInfusionRecipeAdapter());
    }

    private static void registerEnderIO() {
        register(new EnchanterRecipeAdapter());
        register(new AlloySmeltingRecipeAdapter());
        register(new SlicingRecipeAdapter());
        register(new SagMillingRecipeAdapter());
        register(new SoulBindingRecipeAdapter());
        register(new VatFermentingRecipeAdapter());
    }

    private static void registerCreate() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.create.CreateRecipeCompatLoader");
    }

    private static void registerOritech() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.oritech.OritechRecipeCompatLoader");
    }

    private static void registerNeoVitae() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.neovitae.NeoVitaeRecipeCompatLoader");
    }

    private static void registerUfo() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.ufo.UfoRecipeCompatLoader");
    }

    private record CompatEntry(@Nullable String modId, Runnable registerAction) {}
}
