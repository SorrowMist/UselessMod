package com.sorrowmist.useless.compat.jei;

import appeng.recipes.AERecipeTypes;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.DissolutionChamberRecipe;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.glodblock.github.extendedae.recipe.CircuitCutterRecipe;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
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
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.sorrowmist.useless.init.ModTags;
import de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import mekanism.api.recipes.MekanismRecipeTypes;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.blakebr0.mysticalagriculture.init.ModRecipeTypes.AWAKENING;
import static com.blakebr0.mysticalagriculture.init.ModRecipeTypes.INFUSION;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.CRYSTAL_CATALYZER_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.LIGHTNING_SIMULATION_TYPE;
import static com.moakiee.ae2lt.registry.ModRecipeTypes.OVERLOAD_PROCESSING_TYPE;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "jei_plugin");
    private static IJeiRuntime runtime;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(guiHelper));
        registration.addRecipeCategories(new CatalystInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, List.of());
            registration.addRecipes(CatalystInfoCategory.TYPE, List.of(new CatalystInfoCategory.CatalystInfo()));
            return;
        }

        RecipeManager recipeManager = level.getRecipeManager();

        // 获取所有高级合金炉配方
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get())) {
            recipes.add(holder.value());
        }

        // 添加转换后的原版熔炉配方
        recipes.addAll(convertFurnaceRecipes(recipeManager, level));

        // 添加转换后的 ExtendedAE 配方（如果EAE已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.EXTENDED_AE)) {
            recipes.addAll(convertExtendedAERecipes(recipeManager, level));
        }

        // 添加转换后的 AdvancedAE 配方（如果AAE已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.ADVANCED_AE)) {
            recipes.addAll(convertAdvancedAERecipes(recipeManager, level));
        }

        // 添加转换后的 Mekanism 配方（如果Mek已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.MEKANISM)) {
            recipes.addAll(convertMekanismRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 配方（如果AE2已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2)) {
            recipes.addAll(convertAE2Recipes(recipeManager, level));
        }

        // 添加转换后的 Industrial Foregoing 配方（如果IF已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.INDUSTRIAL_FOREGOING)) {
            recipes.addAll(convertIndustrialForegoingRecipes(recipeManager, level));
        }

        // 添加转换后的 Actually Additions 配方（如果AA已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.ACTUALLY_ADDITIONS)) {
            recipes.addAll(convertActuallyAdditionsRecipes(recipeManager, level));
        }

        // 添加转换后的 Ars Nouveau 配方（如果AN已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.ARS_NOUVEAU)) {
            recipes.addAll(convertArsNouveauRecipes(recipeManager, level));
        }

        // 添加转换后的 Mystical Agriculture 配方（如果MA已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.MYSTICAL_AGRICULTURE)) {
            recipes.addAll(convertMysticalAgricultureRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 Crystal Science 配方（如果AECS已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2CS)) {
            recipes.addAll(convertAECSRecipes(recipeManager, level));
        }

        // 添加转换后的 AE2 Lightning Tech 配方（如果AE2LT已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2LT)) {
            recipes.addAll(convertAELightningTechRecipes(recipeManager, level));
        }

        // 添加转换后的 DataEnergistics 配方（如果DataEnergistics已加载）
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.DATA_ENERGISTICS)) {
            recipes.addAll(convertDataEnergisticsRecipes(recipeManager, level));
        }

        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);

        // 添加催化剂信息
        registration.addRecipes(CatalystInfoCategory.TYPE, List.of(new CatalystInfoCategory.CatalystInfo()));
    }

    private <I extends RecipeInput, T extends Recipe<I>> void addConvertedRecipes(List<AdvancedAlloyFurnaceRecipe> convertedRecipes,
                                                                                  RecipeManager recipeManager,
                                                                                  RecipeType<T> recipeType,
                                                                                  IRecipeAdapter<T> adapter,
                                                                                  Level level) {
        for (RecipeHolder<T> holder : recipeManager.getAllRecipesFor(recipeType)) {
            convertedRecipes.addAll(adapter.convertAll(holder, level));
        }
    }

    /**
     * 转换原版熔炉配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertFurnaceRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();
        SmeltingRecipeAdapter adapter = new SmeltingRecipeAdapter();

        // 转换熔炉配方
        for (RecipeHolder<SmeltingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
            convertedRecipes.addAll(adapter.convertAll(castHolder(holder), level));
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
        addConvertedRecipes(convertedRecipes, recipeManager, CircuitCutterRecipe.TYPE, cutterAdapter, level);

        // 转换水晶装配器配方
        CrystalAssemblerRecipeAdapter assemblerAdapter = new CrystalAssemblerRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, CrystalAssemblerRecipe.TYPE, assemblerAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 AdvancedAE 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAdvancedAERecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换反应仓配方
        ReactionChamberRecipeAdapter chamberAdapter = new ReactionChamberRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, ReactionChamberRecipe.TYPE, chamberAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 Mekanism 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertMekanismRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换冶金灌注机配方（支持批量版本）
        MetallurgicInfuserRecipeAdapter infuserAdapter = new MetallurgicInfuserRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value(), infuserAdapter, level);

        // 转换富集仓配方
        EnrichmentChamberRecipeAdapter enrichmentAdapter = new EnrichmentChamberRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, MekanismRecipeTypes.TYPE_ENRICHING.value(), enrichmentAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 AE2 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAE2Recipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换压印器配方
        InscriberRecipeAdapter inscriberAdapter = new InscriberRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, AERecipeTypes.INSCRIBER, inscriberAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 Industrial Foregoing 配方为高级熔炉配方用于JEI显示
     */
    @SuppressWarnings({"unchecked"})
    private List<AdvancedAlloyFurnaceRecipe> convertIndustrialForegoingRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换溶解成型机配方
        DissolutionChamberRecipeAdapter dissolutionAdapter = new DissolutionChamberRecipeAdapter();
        RecipeType<DissolutionChamberRecipe> recipeType =
                (RecipeType<DissolutionChamberRecipe>) ModuleCore.DISSOLUTION_TYPE.get();
        addConvertedRecipes(convertedRecipes, recipeManager, recipeType, dissolutionAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 Actually Additions 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertActuallyAdditionsRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换原子再构机配方
        LaserRecipeAdapter laserAdapter = new LaserRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, ActuallyRecipes.Types.LASER.get(), laserAdapter, level);

        // 转换充能台配方
        EmpowererRecipeAdapter empowererAdapter = new EmpowererRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, ActuallyRecipes.Types.EMPOWERING.get(), empowererAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 Ars Nouveau 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertArsNouveauRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换附魔装置配方
        EnchantingApparatusRecipeAdapter apparatusAdapter = new EnchantingApparatusRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, RecipeRegistry.APPARATUS_TYPE.get(), apparatusAdapter, level);

        // 转换灌魔室配方
        ImbuementRecipeAdapter imbuementAdapter = new ImbuementRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, RecipeRegistry.IMBUEMENT_TYPE.get(), imbuementAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 Mystical Agriculture 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertMysticalAgricultureRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        // 转换注魔祭坛配方
        InfusionRecipeAdapter infusionAdapter = new InfusionRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, INFUSION.get(), infusionAdapter, level);

        // 转换觉醒祭坛配方
        AwakeningRecipeAdapter awakeningAdapter = new AwakeningRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, AWAKENING.get(), awakeningAdapter, level);

        // 添加种子→精华转换配方
        SeedEssenceRecipeAdapter seedEssenceAdapter = new SeedEssenceRecipeAdapter();
        convertedRecipes.addAll(seedEssenceAdapter.getAllRecipes());

        return convertedRecipes;
    }

    /**
     * 转换 AE2 Crystal Science 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertAECSRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        CircuitEtcherRecipeAdapter etcherAdapter = new CircuitEtcherRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, AECSRecipeTypes.CIRCUIT_ETCHER.get(), etcherAdapter, level);

        CrystalAggregatorRecipeAdapter aggregatorAdapter = new CrystalAggregatorRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, AECSRecipeTypes.CRYSTAL_AGGREGATOR.get(), aggregatorAdapter, level);

        CrystalPulverizerRecipeAdapter pulverizerAdapter = new CrystalPulverizerRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, AECSRecipeTypes.CRYSTAL_PULVERIZER.get(), pulverizerAdapter, level);

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
        addConvertedRecipes(convertedRecipes, recipeManager, LIGHTNING_SIMULATION_TYPE.get(), simulationAdapter, level);

        LightningAssemblyRecipeAdapter assemblyAdapter = new LightningAssemblyRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, LIGHTNING_ASSEMBLY_TYPE.get(), assemblyAdapter, level);

        OverloadProcessingRecipeAdapter overloadAdapter = new OverloadProcessingRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, OVERLOAD_PROCESSING_TYPE.get(), overloadAdapter, level);

        CrystalCatalyzerRecipeAdapter catalyzerAdapter = new CrystalCatalyzerRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, CRYSTAL_CATALYZER_TYPE.get(), catalyzerAdapter, level);

        return convertedRecipes;
    }

    /**
     * 转换 DataEnergistics 配方为高级熔炉配方用于JEI显示
     */
    private List<AdvancedAlloyFurnaceRecipe> convertDataEnergisticsRecipes(RecipeManager recipeManager, Level level) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        DataReassemblerRecipeAdapter reassemblerAdapter = new DataReassemblerRecipeAdapter();
        addConvertedRecipes(convertedRecipes, recipeManager, ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get(), reassemblerAdapter, level);

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
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
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
