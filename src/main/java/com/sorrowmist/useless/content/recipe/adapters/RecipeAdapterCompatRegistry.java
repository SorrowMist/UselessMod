package com.sorrowmist.useless.content.recipe.adapters;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.BrewingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.CraftingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.FishingLootRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.SmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.VillagerTradeRecipeAdapter;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RecipeAdapterCompatRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String EXTENDED_AE = RecipeSourceIds.EXTENDED_AE;
    public static final String ADVANCED_AE = RecipeSourceIds.ADVANCED_AE;
    public static final String MEKANISM = RecipeSourceIds.MEKANISM;
    public static final String MEKANISM_GENERATORS = RecipeSourceIds.MEKANISM_GENERATORS;
    public static final String APP_MEK = RecipeSourceIds.APP_MEK;
    public static final String AE2 = RecipeSourceIds.AE2;
    public static final String AE2CS = RecipeSourceIds.AE2CS;
    public static final String FARMERS_DELIGHT = RecipeSourceIds.FARMERS_DELIGHT;
    public static final String EXTRA_DELIGHT = RecipeSourceIds.EXTRA_DELIGHT;
    public static final String CRABBERS_DELIGHT = RecipeSourceIds.CRABBERS_DELIGHT;
    public static final String CASUALNESS_DELIGHT = RecipeSourceIds.CASUALNESS_DELIGHT;
    public static final String EXPANDED_DELIGHT = RecipeSourceIds.EXPANDED_DELIGHT;
    public static final String BREWIN_AND_CHEWIN = RecipeSourceIds.BREWIN_AND_CHEWIN;
    public static final String NOMADS_DELIGHT = RecipeSourceIds.NOMADS_DELIGHT;
    public static final String UBES_DELIGHT = RecipeSourceIds.UBES_DELIGHT;
    public static final String BARBEQUES_DELIGHT = RecipeSourceIds.BARBEQUES_DELIGHT;
    public static final String YOUKAIS_FEASTS = RecipeSourceIds.YOUKAIS_FEASTS;
    public static final String YOUKAI_HOMECOMING = RecipeSourceIds.YOUKAI_HOMECOMING;
    public static final String KALEIDOSCOPE_COOKERY = RecipeSourceIds.KALEIDOSCOPE_COOKERY;
    public static final String KALEIDOSCOPE_GRILLING = RecipeSourceIds.KALEIDOSCOPE_GRILLING;
    public static final String KALEIDOSCOPE_TAVERN = RecipeSourceIds.KALEIDOSCOPE_TAVERN;
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
    public static final String AVARITIA = RecipeSourceIds.AVARITIA;

    /**
     * Re-Avaritia 与 AvaritiaNeo 均声明 modId {@code avaritia}，但包结构互不兼容：前者位于
     * {@code committee.nova.mods.avaritia}，后者位于 {@code net.byAqua3.avaritia}。二者的显示名
     * 分别为 {@code Re-Avaritia} 与 {@code Avaritia}，该差异不足以作为可靠判据，故改以 Re-Avaritia
     * 独有的注册类作为判定依据：仅当该类可加载时才注册针对 Re-Avaritia 编写的适配器，否则适配器会在
     * 取模具物品时抛出 {@link NoClassDefFoundError}。
     */
    private static final String RE_AVARITIA_REGISTRY_CLASS =
            "committee.nova.mods.avaritia.init.registry.ModBlocks";

    public static final String NEO_ECO_AE = RecipeSourceIds.NEO_ECO_AE;
    public static final String NATURES_AURA = RecipeSourceIds.NATURES_AURA;
    public static final String FORBIDDEN_ARCANUS = RecipeSourceIds.FORBIDDEN_ARCANUS;
    public static final String OCCULTISM = RecipeSourceIds.OCCULTISM;
    public static final String SUMMONING_RITUALS = RecipeSourceIds.SUMMONING_RITUALS;
    public static final String MALUM = RecipeSourceIds.MALUM;
    public static final String ENDER_IO = RecipeSourceIds.ENDER_IO;
    public static final String CREATE = RecipeSourceIds.CREATE;
    public static final String ORITECH = RecipeSourceIds.ORITECH;
    public static final String NEOVITAE = RecipeSourceIds.NEOVITAE;
    public static final String UFO = RecipeSourceIds.UFO;
    public static final String MODERN_INDUSTRIALIZATION = RecipeSourceIds.MODERN_INDUSTRIALIZATION;
    public static final String IMMERSIVE_ENGINEERING = RecipeSourceIds.IMMERSIVE_ENGINEERING;
    public static final String PNEUMATICCRAFT = RecipeSourceIds.PNEUMATICCRAFT;
    public static final String BIG_REACTORS = RecipeSourceIds.BIG_REACTORS;
    public static final String JUSTDIRETHINGS = RecipeSourceIds.JUSTDIRETHINGS;
    public static final String JDTE = RecipeSourceIds.JDTE;
    public static final String IRONS_SPELLBOOKS = RecipeSourceIds.IRONS_SPELLBOOKS;
    public static final String HOSTILE_NETWORKS = RecipeSourceIds.HOSTILE_NETWORKS;
    public static final String APOTHIC_FLUX = RecipeSourceIds.APOTHIC_FLUX;
    public static final String FLUX_NETWORKS = RecipeSourceIds.FLUX_NETWORKS;
    public static final String APOTHEOSIS = RecipeSourceIds.APOTHEOSIS;
    public static final String LYCHEE = RecipeSourceIds.LYCHEE;
    public static final String ASTRAL_SORCERY = RecipeSourceIds.ASTRAL_SORCERY;
    public static final String BOTANY_POTS = RecipeSourceIds.BOTANY_POTS;
    public static final String EX_DEORUM = RecipeSourceIds.EX_DEORUM;

    private static final List<CompatEntry> ENTRIES = List.of(
            new CompatEntry(null, RecipeAdapterCompatRegistry::registerMinecraft),
            new CompatEntry(RecipeSourceIds.THERMAL, () -> invokeOptionalLoader(
                    "com.sorrowmist.useless.compat.thermal.ThermalRecipeCompatLoader")),
            new CompatEntry(RecipeSourceIds.BLOOD_MAGIC, () -> invokeOptionalLoader(
                    "com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeCompatLoader")),
            new CompatEntry(RecipeSourceIds.EMBERS, () -> invokeOptionalLoader(
                    "com.sorrowmist.useless.compat.embers.EmbersRecipeCompatLoader")),
            new CompatEntry(FARMERS_DELIGHT, RecipeAdapterCompatRegistry::registerFarmersDelight),
            new CompatEntry(EXTRA_DELIGHT, RecipeAdapterCompatRegistry::registerExtraDelight),
            new CompatEntry(CRABBERS_DELIGHT, RecipeAdapterCompatRegistry::registerCrabbersDelight),
            new CompatEntry(CASUALNESS_DELIGHT, RecipeAdapterCompatRegistry::registerCasualnessDelight),
            new CompatEntry(EXPANDED_DELIGHT, RecipeAdapterCompatRegistry::registerExpandedDelight),
            new CompatEntry(BREWIN_AND_CHEWIN, RecipeAdapterCompatRegistry::registerBrewinAndChewin),
            new CompatEntry(NOMADS_DELIGHT, RecipeAdapterCompatRegistry::registerNomadsDelight),
            new CompatEntry(UBES_DELIGHT, RecipeAdapterCompatRegistry::registerUbesDelight),
            new CompatEntry(BARBEQUES_DELIGHT, RecipeAdapterCompatRegistry::registerBarbequesDelight),
            new CompatEntry(YOUKAIS_FEASTS, RecipeAdapterCompatRegistry::registerYoukaisHomecoming),
            new CompatEntry(KALEIDOSCOPE_COOKERY, RecipeAdapterCompatRegistry::registerKaleidoscopeCookery),
            new CompatEntry(KALEIDOSCOPE_GRILLING, RecipeAdapterCompatRegistry::registerKaleidoscopeGrilling),
            new CompatEntry(KALEIDOSCOPE_TAVERN, RecipeAdapterCompatRegistry::registerKaleidoscopeTavern),
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
            new CompatEntry(AVARITIA, RecipeAdapterCompatRegistry::registerAvaritia),
            new CompatEntry(NEO_ECO_AE, RecipeAdapterCompatRegistry::registerNeoECOAE),
            new CompatEntry(NATURES_AURA, RecipeAdapterCompatRegistry::registerNaturesAura),
            new CompatEntry(FORBIDDEN_ARCANUS, RecipeAdapterCompatRegistry::registerForbiddenArcanus),
            new CompatEntry(OCCULTISM, RecipeAdapterCompatRegistry::registerOccultism),
            new CompatEntry(SUMMONING_RITUALS, RecipeAdapterCompatRegistry::registerSummoningRituals),
            new CompatEntry(MALUM, RecipeAdapterCompatRegistry::registerMalum),
            new CompatEntry(ENDER_IO, RecipeAdapterCompatRegistry::registerEnderIO),
            new CompatEntry(CREATE, RecipeAdapterCompatRegistry::registerCreate),
            new CompatEntry(ORITECH, RecipeAdapterCompatRegistry::registerOritech),
            new CompatEntry(NEOVITAE, RecipeAdapterCompatRegistry::registerNeoVitae),
            new CompatEntry(UFO, RecipeAdapterCompatRegistry::registerUfo),
            new CompatEntry(MODERN_INDUSTRIALIZATION, RecipeAdapterCompatRegistry::registerModernIndustrialization),
            new CompatEntry(IMMERSIVE_ENGINEERING, RecipeAdapterCompatRegistry::registerImmersiveEngineering),
            new CompatEntry(PNEUMATICCRAFT, RecipeAdapterCompatRegistry::registerPneumaticCraft),
            new CompatEntry(BIG_REACTORS, RecipeAdapterCompatRegistry::registerBigReactors),
            new CompatEntry(JUSTDIRETHINGS, RecipeAdapterCompatRegistry::registerJustDireThings),
            new CompatEntry(IRONS_SPELLBOOKS, RecipeAdapterCompatRegistry::registerIronsSpellbooks),
            new CompatEntry(HOSTILE_NETWORKS, RecipeAdapterCompatRegistry::registerHostileNetworks),
            new CompatEntry(APOTHIC_FLUX, RecipeAdapterCompatRegistry::registerApothicFlux),
            new CompatEntry(FLUX_NETWORKS, RecipeAdapterCompatRegistry::registerFluxNetworks),
            new CompatEntry(APOTHEOSIS, RecipeAdapterCompatRegistry::registerApotheosis),
            new CompatEntry(LYCHEE, RecipeAdapterCompatRegistry::registerLychee),
            new CompatEntry(ASTRAL_SORCERY, RecipeAdapterCompatRegistry::registerAstralSorcery),
            new CompatEntry(BOTANY_POTS, RecipeAdapterCompatRegistry::registerBotanyPots),
            new CompatEntry(EX_DEORUM, RecipeAdapterCompatRegistry::registerExDeorum)
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

    /**
     * 探测指定类能否被当前类加载器解析。传入 {@code initialize=false} 以避免触发目标类的静态初始化，
     * 因此该探测本身不产生副作用；类缺失或其依赖不满足时返回 {@code false}，不向调用方抛出异常。
     */
    private static boolean isClassAvailable(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        try {
            Class.forName(className, false, RecipeAdapterCompatRegistry.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    private static void initCompat(FMLCommonSetupEvent event, @Nullable String modId, Runnable registerAction) {
        if (modId != null && (!isLoaded(modId) || !ConfigManager.isRecipeConversionEnabled(modId))) return;

        event.enqueueWork(() -> {
            try {
                registerAction.run();
            } catch (Exception e) {
                RateLimitedLogger.error(LOGGER, "Failed to register recipe adapters for mod: {}", modId, e);
            }
        });
    }

    private static void registerByName(String className) {
        try {
            Class<?> type=Class.forName(className,true,RecipeAdapterCompatRegistry.class.getClassLoader());
            register((IRecipeAdapter<?>)type.getConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Installed mod recipe adapter unavailable: "+className,exception);
        }
    }

    private static void registerFactory(String className) {
        try {
            Class<?> type=Class.forName(className,true,RecipeAdapterCompatRegistry.class.getClassLoader());
            var adapters=(java.util.List<?>)type.getMethod("createAdapters").invoke(null);
            for(Object adapter:adapters) register((IRecipeAdapter<?>)adapter);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Installed mod adapter factory unavailable: "+className,exception);
        }
    }

    private static void register(IRecipeAdapter<?> adapter) {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(adapter);
    }

    private static void registerMinecraft() {
        if (ConfigManager.isCraftingRecipeConversionEnabled()) {
            register(new CraftingRecipeAdapter());
        }
        if (ConfigManager.isSmeltingRecipeConversionEnabled()) {
            register(new SmeltingRecipeAdapter(RecipeType.SMELTING, Items.FURNACE));
            register(new SmeltingRecipeAdapter(RecipeType.BLASTING, Items.BLAST_FURNACE));
            register(new SmeltingRecipeAdapter(RecipeType.SMOKING, Items.SMOKER));
            register(new SmeltingRecipeAdapter(RecipeType.CAMPFIRE_COOKING, Items.CAMPFIRE));
        }
        if (ConfigManager.isBrewingRecipeConversionEnabled()) {
            register(new BrewingRecipeAdapter());
        }
        if (ConfigManager.isVillagerTradeRecipeConversionEnabled()) {
            register(new VillagerTradeRecipeAdapter());
        }
        if (ConfigManager.isFishingRecipeConversionEnabled()) {
            register(new FishingLootRecipeAdapter());
        }
    }

    private static void registerFarmersDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.farmersdelight.FarmersDelightRecipeCompatLoader");
    }

    private static void registerExtraDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.extradelight.ExtraDelightRecipeCompatLoader");
    }

    private static void registerCrabbersDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.crabbersdelight.CrabbersDelightRecipeCompatLoader");
    }

    private static void registerCasualnessDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.casualnessdelight.CasualnessDelightRecipeCompatLoader");
    }

    private static void registerExpandedDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.expandeddelight.ExpandedDelightRecipeCompatLoader");
    }

    private static void registerBrewinAndChewin() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.brewinandchewin.BrewinAndChewinRecipeCompatLoader");
    }

    private static void registerNomadsDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.nomadsdelight.NomadsDelightRecipeCompatLoader");
    }

    private static void registerUbesDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.ubesdelight.UbesDelightRecipeCompatLoader");
    }

    private static void registerBarbequesDelight() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.barbequesdelight.BarbequesDelightRecipeCompatLoader");
    }

    private static void registerYoukaisHomecoming() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.youkaishomecoming.YoukaisHomecomingRecipeCompatLoader");
    }

    private static void registerKaleidoscopeCookery() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.kaleidoscope.cookery.KaleidoscopeCookeryRecipeCompatLoader");
    }

    private static void registerKaleidoscopeGrilling() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.kaleidoscope.grilling.KaleidoscopeGrillingRecipeCompatLoader");
    }

    private static void registerKaleidoscopeTavern() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.kaleidoscope.tavern.KaleidoscopeTavernRecipeCompatLoader");
    }

    private static void registerExtendedAE() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CircuitCutterRecipeAdapter");
        // Crystal assembler is a later-version machine, absent from ExtendedAE 1.4.6.
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.extendedae.CrystalFixerRecipeAdapter");
    }

    private static void registerAdvancedAE() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.advancedae.ReactionChamberRecipeAdapter");
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
            RateLimitedLogger.error(LOGGER, "Failed to register optional recipe adapters from {}", className, exception);
        }
    }

    private static void registerAE2() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2.ChargerRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2.InscriberRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2.WorldInteractionRecipeAdapter");
    }

    private static void registerAECrystalScience() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CircuitEtcherRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalAggregatorRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalPulverizerRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalGrowthRecipeAdapter");
    }

    private static void registerIndustrialForegoing() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.BioReactorRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.DissolutionChamberRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.FermentationStationRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.FluidSievingMachineRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.WashingFactoryRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.LatexProcessingUnitRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.FluidExtractorRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.SlaughterFactoryRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.LaserDrillOreRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.LaserDrillFluidRecipeAdapter");
        if (isLoaded("ifeu")) {
            registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.InfuserRecipeAdapter");
            registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.PrecisionShapedRecipeAdapter");
            registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.PrecisionShapelessRecipeAdapter");
            registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.FluidShapedRecipeAdapter");
            registerByName("com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.FluidShapelessRecipeAdapter");
        }
    }

    private static void registerActuallyAdditions() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.LaserRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.EmpowererRecipeAdapter");
    }

    private static void registerArsNouveau() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.arsnouveau.EnchantingApparatusRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.arsnouveau.ImbuementRecipeAdapter");
    }

    private static void registerMysticalAgriculture() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.InfusionRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.AwakeningRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SoulJarRecipeAdapter");
        if (isLoaded("omnia")) {
            registerByName("com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.OmniaUniversalSeedRecipeAdapter");
        }
    }

    private static void registerAELightningTech() {
        registerFactory("com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatLoader");
    }

    private static void registerDataEnergistics() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.ae.dataenergistics.DataReassemblerRecipeAdapter");
    }

    private static void registerProductiveBees() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.productivebees.BeeProduceRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.productivebees.CentrifugeRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.productivebees.BeeBreedingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.productivebees.BeeConversionRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.productivebees.BeeFishingRecipeAdapter");
    }

    private static void registerDraconicEvolution() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.draconicevolution.DraconicFusionRecipeAdapter");
    }

    private static void registerPowah() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.powah.EnergizingRecipeAdapter");
    }

    private static void registerExtendedCrafting() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingTableRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCompressorRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCombinationRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingEnderCrafterRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingFluxCrafterRecipeAdapter");
    }

    private static void registerAvaritia() {
        // 二级门禁：外层 CompatEntry 已确认 modId "avaritia" 存在，但 AvaritiaNeo 同样使用该 modId
        // 且包结构完全不兼容。缺失该判定时，下列适配器会在取模具物品时抛出 NoClassDefFoundError，
        // 该错误属于 Error 而非 Exception，会穿透 initCompat 的捕获并导致整局加载失败。
        if (!isClassAvailable(RE_AVARITIA_REGISTRY_CLASS)) {
            RateLimitedLogger.warn(LOGGER, "Mod id {} is present but {} is not loadable; skipping Re-Avaritia recipe adapters. "
                    + "A non Re-Avaritia implementation of this mod id is likely installed.",
                    AVARITIA, RE_AVARITIA_REGISTRY_CLASS);
            return;
        }
        registerByName("com.sorrowmist.useless.content.recipe.adapters.avaritia.ReAvaritiaTableRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.avaritia.ReAvaritiaExtremeSmithingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.avaritia.ReAvaritiaCompressorRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.avaritia.ReAvaritiaCollectorRecipeAdapter");
    }

    private static void registerNeoECOAE() {
        String adapterClass = "com.sorrowmist.useless.content.recipe.adapters.eco.IntegratedWorkingStationRecipeAdapter";
        if (isClassAvailable(adapterClass)) {
            registerByName(adapterClass);
        }
    }

    private static void registerNaturesAura() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.naturesaura.TreeRitualRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.naturesaura.NatureAltarRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.naturesaura.AnimalSpawnerRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.naturesaura.OfferingRecipeAdapter");
    }

    private static void registerForbiddenArcanus() {
        invokeOptionalLoader("com.sorrowmist.useless.compat.forbiddenarcanus.ForbiddenArcanusRecipeCompatLoader");
    }

    private static void registerOccultism() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.occultism.OccultismRitualRecipeAdapter");
    }

    private static void registerSummoningRituals() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.summoningrituals.SummoningRitualsAltarRecipeAdapter");
    }

    private static void registerMalum() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.malum.SpiritFocusingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.malum.SpiritInfusionRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.malum.VoidFavorRecipeAdapter");
    }

    private static void registerEnderIO() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.EnchanterRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.FireCraftingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.AlloySmeltingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.SlicingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.SagMillingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.SoulVialRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.SoulBindingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.enderio.TankRecipeAdapter");
        // EnderIO 6.2.13 has no Vat machine or fermenting recipe type.
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

    private static void registerModernIndustrialization() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.modernindustrialization.ModernIndustrializationRecipeCompatLoader");
    }

    private static void registerImmersiveEngineering() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.immersiveengineering.ImmersiveEngineeringRecipeCompatLoader");
    }

    private static void registerPneumaticCraft() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.pneumaticcraft.PneumaticCraftRecipeCompatLoader");
    }

    private static void registerBigReactors() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.extremereactors.ExtremeReactorsRecipeCompatLoader");
    }

    private static void registerJustDireThings() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.justdirethings.GooSpreadRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.justdirethings.GooSpreadRecipeTagAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.justdirethings.FluidDropRecipeAdapter");
        // 附属开关需在此二次判定：外层 CompatEntry 只看本体（justdirethings）的开关，
        // 本体开启时附属仍可能被玩家单独关闭，所以这里必须再查一次 JDTE 自己的开关。
        if (isLoaded(JDTE) && ConfigManager.isRecipeConversionEnabled(JDTE)) {
            invokeOptionalLoader(
                    "com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte.JDTERecipeCompatLoader");
        }
    }

    private static void registerIronsSpellbooks() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.ironsspellbooks.IronsSpellbooksRecipeCompatLoader");
    }

    private static void registerHostileNetworks() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.hostilenetworks.HostileNetworksRecipeAdapter");
    }

    private static void registerApothicFlux() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.apothicflux.ApothicFluxRecipeCompatLoader");
    }

    private static void registerFluxNetworks() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.fluxnetworks.FluxNetworksRecipeAdapter");
    }

    private static void registerApotheosis() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.apotheosis.ApotheosisRecipeCompatLoader");
    }

    private static void registerLychee() {
        invokeOptionalLoader(
                "com.sorrowmist.useless.compat.lychee.LycheeRecipeCompatLoader");
    }

    private static void registerAstralSorcery() {
        // 植物魔法（Mystical Agriculture）存在同名适配器，此处必须使用全限定名以避免简名冲突。
        registerByName("com.sorrowmist.useless.content.recipe.adapters.astralsorcery.InfusionRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.astralsorcery.LightwellRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.astralsorcery.AltarRecipeAdapter");
    }

    private static void registerBotanyPots() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.botanypots.BotanyPotsRecipeAdapter");
    }

    /**
     * 注册 Ex Deorum 的桶、坩埚、筛子与锤子配方适配器。
     *
     * <p>桶的四类配方（堆肥、物品混合、流体混合、流体转化）共用 {@code exdeorum:barrels}
     * 物品标签作为模具，因此无需按石桶与木桶分别登记；坩埚与筛子的模具由材料注册表在运行时枚举。
     * 筛子适配同时覆盖普通筛子与重型筛子，其模具由配方限定的筛网决定。</p>
     *
     * <p>锤子适配覆盖普通锤与压缩锤两类配方，模具按配方实际类型在转换阶段选定，固定为钻石锤与
     * 钻石压缩锤。该适配器以生成配方方式提供结果，必须在此登记，否则其配方不会进入合金炉配方目录。</p>
     */
    private static void registerExDeorum() {
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.BarrelCompostRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.BarrelMixingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.BarrelFluidMixingRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.FluidTransformationRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.CrucibleRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.SieveRecipeAdapter");
        registerByName("com.sorrowmist.useless.content.recipe.adapters.exdeorum.HammerRecipeAdapter");
    }

    private record CompatEntry(@Nullable String modId, Runnable registerAction) {}
}
