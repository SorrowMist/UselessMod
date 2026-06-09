package com.sorrowmist.useless.compat.jei;

import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.SmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.ActuallyAdditionsCompat;
import com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.EmpowererRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.actuallyadditions.LaserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.advancedae.AdvancedAECompat;
import com.sorrowmist.useless.content.recipe.adapters.advancedae.ReactionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2.AE2Compat;
import com.sorrowmist.useless.content.recipe.adapters.ae2.InscriberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2cs.AECrystalScienceCompat;
import com.sorrowmist.useless.content.recipe.adapters.ae2cs.CircuitEtcherRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2cs.CrystalAggregatorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2cs.CrystalGrowthRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2cs.CrystalPulverizerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.AELightningTechCompat;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.CrystalCatalyzerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.LightningAssemblyRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.LightningSimulationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae2lt.OverloadProcessingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.ArsNouveauCompat;
import com.sorrowmist.useless.content.recipe.adapters.dataenergistics.DataEnergisticsCompat;
import com.sorrowmist.useless.content.recipe.adapters.dataenergistics.DataReassemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.EnchantingApparatusRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.arsnouveau.ImbuementRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedae.CircuitCutterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedae.CrystalAssemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedae.ExtendedAECompat;
import com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.DissolutionChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.IndustrialForegoingCompat;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.EnrichmentChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismCompat;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MetallurgicInfuserRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.AwakeningRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.InfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.MysticalAgricultureCompat;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.sorrowmist.useless.init.ModTags;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "jei_plugin");
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(guiHelper));
        registration.addRecipeCategories(new CatalystInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
        Level level = Minecraft.getInstance().level;

        // 获取所有高级合金炉配方
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>(recipeManager.getAllRecipesFor(
                ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get())
                .stream()
                .map(RecipeHolder::value)
                .toList());

        // 添加转换后的原版熔炉配方
        recipes.addAll(convertFurnaceRecipes(recipeManager, level));

        // 添加转换后的 ExtendedAE 配方（如果EAE已加载）
        if (ExtendedAECompat.isExtendedAELoaded()) {
            recipes.addAll(convertExtendedAERecipes(recipeManager, level));
        }

        // 添加转换后的 AdvancedAE 配方（如果AAE已加载）
        if (AdvancedAECompat.isAdvancedAELoaded()) {
            recipes.addAll(convertAdvancedAERecipes(recipeManager, level));
        }

        // 添加转换后的 Mekanism 配方（如果Mek已加载）
        if (MekanismCompat.isMekanismLoaded()) {
            recipes.addAll(convertMekanismRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 配方（如果AE2已加载）
        if (AE2Compat.isAE2Loaded()) {
            recipes.addAll(convertAE2Recipes(recipeManager, level));
        }

        // 添加转换后的 Industrial Foregoing 配方（如果IF已加载）
        if (IndustrialForegoingCompat.isIndustrialForegoingLoaded()) {
            recipes.addAll(convertIndustrialForegoingRecipes(recipeManager, level));
        }

        // 添加转换后的 Actually Additions 配方（如果AA已加载）
        if (ActuallyAdditionsCompat.isActuallyAdditionsLoaded()) {
            recipes.addAll(convertActuallyAdditionsRecipes(recipeManager, level));
        }

        // 添加转换后的 Ars Nouveau 配方（如果AN已加载）
        if (ArsNouveauCompat.isArsNouveauLoaded()) {
            recipes.addAll(convertArsNouveauRecipes(recipeManager, level));
        }

        // 添加转换后的 Mystical Agriculture 配方（如果MA已加载）
        if (MysticalAgricultureCompat.isMysticalAgricultureLoaded()) {
            recipes.addAll(convertMysticalAgricultureRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 Crystal Science 配方（如果AECS已加载）
        if (AECrystalScienceCompat.isAECSLoaded()) {
            recipes.addAll(convertAECSRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 Lightning Tech 配方（如果AE2LT已加载）
        if (AELightningTechCompat.isAELightningTechLoaded()) {
            recipes.addAll(convertAELightningTechRecipes(recipeManager, level));
        }

        // 添加转换后的 DataEnergistics 配方（如果DataEnergistics已加载）
        if (DataEnergisticsCompat.isDataEnergisticsLoaded()) {
            recipes.addAll(convertDataEnergisticsRecipes(recipeManager, level));
        }

        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);

        // 添加催化剂信息
        List<CatalystInfoCategory.CatalystInfo> catalystInfos = new ArrayList<>();
        catalystInfos.add(new CatalystInfoCategory.CatalystInfo());
        registration.addRecipes(CatalystInfoCategory.TYPE, catalystInfos);
    }

    /**
     * 转换原版熔炉配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertFurnaceRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();
        SmeltingRecipeAdapter adapter = new SmeltingRecipeAdapter();

        // 转换熔炉配方
        for (RecipeHolder<SmeltingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
            AdvancedAlloyFurnaceRecipe converted = adapter.convert(
                    castHolder(holder), level
            );
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 ExtendedAE 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertExtendedAERecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换电路切片器配方
        CircuitCutterRecipeAdapter cutterAdapter = new CircuitCutterRecipeAdapter();
        for (RecipeHolder<CircuitCutterRecipe> holder : recipeManager.getAllRecipesFor(CircuitCutterRecipe.TYPE)) {
            AdvancedAlloyFurnaceRecipe converted = cutterAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        // 转换水晶装配器配方
        CrystalAssemblerRecipeAdapter assemblerAdapter = new CrystalAssemblerRecipeAdapter();
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE)) {
            AdvancedAlloyFurnaceRecipe converted = assemblerAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 AdvancedAE 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAdvancedAERecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换反应仓配方
        ReactionChamberRecipeAdapter chamberAdapter = new ReactionChamberRecipeAdapter();
        for (RecipeHolder<ReactionChamberRecipe> holder : recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            AdvancedAlloyFurnaceRecipe converted = chamberAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 Mekanism 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertMekanismRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换冶金灌注机配方（支持批量版本）
        MetallurgicInfuserRecipeAdapter infuserAdapter = new MetallurgicInfuserRecipeAdapter();
        for (RecipeHolder<mekanism.api.recipes.ItemStackChemicalToItemStackRecipe> holder : recipeManager.getAllRecipesFor(
                mekanism.api.recipes.MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value())) {
            // 使用 convertAll 获取所有批量版本（普通物品和富集物品版本）
            List<AdvancedAlloyFurnaceRecipe> converted = infuserAdapter.convertAll(holder, level);
            convertedRecipes.addAll(converted);
        }

        // 转换富集仓配方
        EnrichmentChamberRecipeAdapter enrichmentAdapter = new EnrichmentChamberRecipeAdapter();
        for (RecipeHolder<mekanism.api.recipes.ItemStackToItemStackRecipe> holder : recipeManager.getAllRecipesFor(
                mekanism.api.recipes.MekanismRecipeTypes.TYPE_ENRICHING.value())) {
            AdvancedAlloyFurnaceRecipe converted = enrichmentAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 AE2 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAE2Recipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换压印器配方
        InscriberRecipeAdapter inscriberAdapter = new InscriberRecipeAdapter();
        for (RecipeHolder<appeng.recipes.handlers.InscriberRecipe> holder : recipeManager.getAllRecipesFor(
                appeng.recipes.AERecipeTypes.INSCRIBER)) {
            AdvancedAlloyFurnaceRecipe converted = inscriberAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 Industrial Foregoing 配方为高级熔炉配方用于JEI显示
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<AdvancedAlloyFurnaceRecipe> convertIndustrialForegoingRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换溶解成型机配方
        DissolutionChamberRecipeAdapter dissolutionAdapter = new DissolutionChamberRecipeAdapter();
        RecipeType<com.buuz135.industrial.recipe.DissolutionChamberRecipe> recipeType =
                (RecipeType<com.buuz135.industrial.recipe.DissolutionChamberRecipe>) com.buuz135.industrial.module.ModuleCore.DISSOLUTION_TYPE.get();
        for (RecipeHolder<com.buuz135.industrial.recipe.DissolutionChamberRecipe> holder : recipeManager.getAllRecipesFor(recipeType)) {
            AdvancedAlloyFurnaceRecipe converted = dissolutionAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 Actually Additions 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertActuallyAdditionsRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换原子再构机配方
        LaserRecipeAdapter laserAdapter = new LaserRecipeAdapter();
        for (RecipeHolder<de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes.Types.LASER.get())) {
            AdvancedAlloyFurnaceRecipe converted = laserAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        // 转换充能台配方
        EmpowererRecipeAdapter empowererAdapter = new EmpowererRecipeAdapter();
        for (RecipeHolder<de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes.Types.EMPOWERING.get())) {
            AdvancedAlloyFurnaceRecipe converted = empowererAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 Ars Nouveau 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertArsNouveauRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换附魔装置配方
        EnchantingApparatusRecipeAdapter apparatusAdapter = new EnchantingApparatusRecipeAdapter();
        for (RecipeHolder<com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe> holder :
                recipeManager.getAllRecipesFor(com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry.APPARATUS_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = apparatusAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        // 转换灌魔室配方
        ImbuementRecipeAdapter imbuementAdapter = new ImbuementRecipeAdapter();
        for (RecipeHolder<com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe> holder :
                recipeManager.getAllRecipesFor(com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry.IMBUEMENT_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = imbuementAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        return convertedRecipes;
    }

    /**
     * 转换 Mystical Agriculture 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertMysticalAgricultureRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换注魔祭坛配方
        InfusionRecipeAdapter infusionAdapter = new InfusionRecipeAdapter();
        for (RecipeHolder<com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe> holder :
                recipeManager.getAllRecipesFor(com.blakebr0.mysticalagriculture.init.ModRecipeTypes.INFUSION.get())) {
            AdvancedAlloyFurnaceRecipe converted = infusionAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        // 转换觉醒祭坛配方
        AwakeningRecipeAdapter awakeningAdapter = new AwakeningRecipeAdapter();
        for (RecipeHolder<com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe> holder :
                recipeManager.getAllRecipesFor(com.blakebr0.mysticalagriculture.init.ModRecipeTypes.AWAKENING.get())) {
            AdvancedAlloyFurnaceRecipe converted = awakeningAdapter.convert(holder, level);
            if (converted != null) {
                convertedRecipes.add(converted);
            }
        }

        // 添加种子→精华转换配方
        SeedEssenceRecipeAdapter seedEssenceAdapter = new SeedEssenceRecipeAdapter();
        convertedRecipes.addAll(seedEssenceAdapter.getAllRecipes());

        return convertedRecipes;
    }

    // TODO: AE2 Crystal Science adapters not yet implemented
    // convertAECSRecipes() method removed until adapters are created

    /**
     * 转换 AE2 Crystal Science 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAECSRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        CircuitEtcherRecipeAdapter etcherAdapter = new CircuitEtcherRecipeAdapter();
        for (RecipeHolder<io.github.lounode.ae2cs.common.recipe.circuit_etcher.CircuitEtcherRecipe> holder :
                recipeManager.getAllRecipesFor(io.github.lounode.ae2cs.common.init.AECSRecipeTypes.CIRCUIT_ETCHER.get())) {
            AdvancedAlloyFurnaceRecipe converted = etcherAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        CrystalAggregatorRecipeAdapter aggregatorAdapter = new CrystalAggregatorRecipeAdapter();
        for (RecipeHolder<io.github.lounode.ae2cs.common.recipe.crystal_aggregator.CrystalAggregatorRecipe> holder :
                recipeManager.getAllRecipesFor(io.github.lounode.ae2cs.common.init.AECSRecipeTypes.CRYSTAL_AGGREGATOR.get())) {
            AdvancedAlloyFurnaceRecipe converted = aggregatorAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        CrystalPulverizerRecipeAdapter pulverizerAdapter = new CrystalPulverizerRecipeAdapter();
        for (RecipeHolder<io.github.lounode.ae2cs.common.recipe.crystal_pulverizer.CrystalPulverizerRecipe> holder :
                recipeManager.getAllRecipesFor(io.github.lounode.ae2cs.common.init.AECSRecipeTypes.CRYSTAL_PULVERIZER.get())) {
            AdvancedAlloyFurnaceRecipe converted = pulverizerAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        CrystalGrowthRecipeAdapter growthAdapter = new CrystalGrowthRecipeAdapter();
        convertedRecipes.addAll(growthAdapter.getAllRecipes());

        return convertedRecipes;
    }

    /**
     * 转换 AE2 Lightning Tech 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAELightningTechRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        LightningSimulationRecipeAdapter simulationAdapter = new LightningSimulationRecipeAdapter();
        for (RecipeHolder<com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe> holder :
                recipeManager.getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = simulationAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        LightningAssemblyRecipeAdapter assemblyAdapter = new LightningAssemblyRecipeAdapter();
        for (RecipeHolder<com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe> holder :
                recipeManager.getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = assemblyAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        OverloadProcessingRecipeAdapter overloadAdapter = new OverloadProcessingRecipeAdapter();
        for (RecipeHolder<com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe> holder :
                recipeManager.getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = overloadAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        CrystalCatalyzerRecipeAdapter catalyzerAdapter = new CrystalCatalyzerRecipeAdapter();
        for (RecipeHolder<com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe> holder :
                recipeManager.getAllRecipesFor(com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())) {
            AdvancedAlloyFurnaceRecipe converted = catalyzerAdapter.convert(holder, level);
            if (converted != null) convertedRecipes.add(converted);
        }

        return convertedRecipes;
    }

    /**
     * 转换 DataEnergistics 配方为高级熔炉配方用于JEI显示
     */
    @SuppressWarnings("unchecked")
    private List<AdvancedAlloyFurnaceRecipe> convertDataEnergisticsRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        DataReassemblerRecipeAdapter reassemblerAdapter = new DataReassemblerRecipeAdapter();
        for (RecipeHolder<com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe> holder :
                recipeManager.getAllRecipesFor(com.fish_dan_.data_energistics.registry.ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get())) {
            List<AdvancedAlloyFurnaceRecipe> converted = reassemblerAdapter.convertAll(holder, level);
            convertedRecipes.addAll(converted);
        }

        return convertedRecipes;
    }

    /**
     * 安全地转换配方持有者类型
     */
    @SuppressWarnings("unchecked")
    private <T extends AbstractCookingRecipe> RecipeHolder<AbstractCookingRecipe> castHolder(RecipeHolder<T> holder) {
        return (RecipeHolder<AbstractCookingRecipe>) holder;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 高级合金炉作为配方催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE
        );

        // 添加催化剂信息类别的催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                CatalystInfoCategory.TYPE
        );

        // 使用ModTags.CATALYSTS动态注册所有催化剂
        BuiltInRegistries.ITEM.getTag(ModTags.CATALYSTS).ifPresent(tag -> {
            for (var holder : tag) {
                registration.addRecipeCatalyst(new ItemStack(holder.value()), CatalystInfoCategory.TYPE);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEIPlugin.runtime = jeiRuntime;
    }

    /**
     * 获取JEI运行时实例
     */
    public static IJeiRuntime getRuntime() {
        return runtime;
    }

    /**
     * 打开高级合金炉配方界面
     */
    public static void showAdvancedAlloyFurnaceRecipes() {
        if (runtime != null) {
            runtime.getRecipesGui().showTypes(List.of(AdvancedAlloyFurnaceRecipeCategory.TYPE));
        }
    }

    /**
     * 检查JEI是否可用
     */
    public static boolean isAvailable() {
        return runtime != null;
    }
}
