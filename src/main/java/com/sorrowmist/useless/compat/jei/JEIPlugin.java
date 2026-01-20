package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter;
import com.sorrowmist.useless.recipes.ModRecipeTypes;
import com.sorrowmist.useless.registry.ModIngots;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AdvancedAlloyFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CatalystInfoCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Objects.requireNonNull(Minecraft.getInstance().level).getRecipeManager();

        // 获取所有高级合金炉配方
        List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        recipes.addAll(recipeManager.getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()));
        
        // 添加所有原版熔炉配方，转换为高级合金炉配方格式
        Collection<net.minecraft.world.item.crafting.SmeltingRecipe> smeltingRecipes = recipeManager.getAllRecipesFor(
                net.minecraft.world.item.crafting.RecipeType.SMELTING
        );
        
        for (net.minecraft.world.item.crafting.SmeltingRecipe recipe : smeltingRecipes) {
            // 将原版熔炉配方转换为高级合金炉配方
            List<net.minecraft.world.item.crafting.Ingredient> smeltingIngredients = recipe.getIngredients();
            List<net.minecraft.world.item.crafting.Ingredient> inputIngredients = new ArrayList<>();
            List<Integer> inputCounts = new ArrayList<>();
            
            for (net.minecraft.world.item.crafting.Ingredient ingredient : smeltingIngredients) {
                inputIngredients.add(ingredient);
                inputCounts.add(1);
            }
            
            List<ItemStack> outputItems = new ArrayList<>();
            outputItems.add(recipe.getResultItem(null));
            
            List<net.minecraftforge.fluids.FluidStack> emptyFluids = new ArrayList<>();
            net.minecraft.world.item.crafting.Ingredient furnaceIngredient = net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.FURNACE);
            
            // 创建无用锭标签的催化剂，允许使用所有无用锭
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> uselessIngotTag = 
                    net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.ITEM,
                            new net.minecraft.resources.ResourceLocation("useless_mod", "useless_ingots")
                    );
            net.minecraft.world.item.crafting.Ingredient uselessIngotCatalyst = net.minecraft.world.item.crafting.Ingredient.of(uselessIngotTag);
            
            // 将List<Integer>转换为List<Long>
            List<Long> longInputCounts = new ArrayList<>();
            for (Integer count : inputCounts) {
                longInputCounts.add(count.longValue());
            }
            
            // 创建临时的高级合金炉配方，使用原版熔炉作为模具
            com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe convertedRecipe = new com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe(
                    recipe.getId(),
                    inputIngredients,
                    longInputCounts,
                    emptyFluids,
                    outputItems,
                    emptyFluids,
                    1000, // 能量消耗
                    40, // 处理时间：熔炉配方为40tick
                    uselessIngotCatalyst, // 催化剂：无用锭标签
                    1, // 催化剂数量
                    furnaceIngredient // 模具：原版熔炉
            );
            
            recipes.add(convertedRecipe);
        }

        // 添加热力配方转换
        try {
            // 直接从RecipeManager获取所有InsolatorRecipe并转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> convertedRecipes = 
                    ThermalRecipeConverter.convertInsolatorRecipes(recipeManager);
            recipes.addAll(convertedRecipes);
            
            // 添加热力感应炉(Smelter)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> smelterRecipes = 
                    ThermalRecipeConverter.convertSmelterRecipes(recipeManager);
            recipes.addAll(smelterRecipes);
            
            // 添加热力多驱冲压机(Press)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> pressRecipes = 
                    ThermalRecipeConverter.convertPressRecipes(recipeManager);
            recipes.addAll(pressRecipes);
        } catch (Exception e) {
            // 如果热力模组未加载，捕获异常以避免崩溃
        }
        
        // 添加Mekanism配方转换
        try {
            // 添加Mekanism冶金灌注机(Metallurgic Infuser)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> mekanismRecipes = 
                    com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertMetallurgicInfuserRecipes(recipeManager);
            recipes.addAll(mekanismRecipes);
            
            // 添加Mekanism富集仓(Enrichment Chamber)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> enrichmentRecipes = 
                    com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertEnrichmentChamberRecipes(recipeManager);
            recipes.addAll(enrichmentRecipes);
        } catch (Exception e) {
            // 如果Mekanism模组未加载，捕获异常以避免崩溃
        }
        
        // 添加工业先锋配方转换
        try {
            // 添加工业先锋化学溶解室(Dissolution Chamber)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> dissolutionRecipes = 
                    com.sorrowmist.useless.compat.industrialforegoing.IndustrialForegoingRecipeConverter.convertDissolutionChamberRecipes(recipeManager);
            recipes.addAll(dissolutionRecipes);
        } catch (Exception e) {
            // 如果工业先锋模组未加载，捕获异常以避免崩溃
        }
        
        // 添加血魔法配方转换
        try {
            // 添加血魔法血之祭坛(Blood Altar)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> bloodAltarRecipes = 
                    com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertBloodAltarRecipes(recipeManager);
            recipes.addAll(bloodAltarRecipes);
            
            // 添加血魔法狱火熔炉(Soul Forge)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> soulForgeRecipes = 
                    com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertTartaricForgeRecipes(recipeManager);
            recipes.addAll(soulForgeRecipes);
            
            // 添加血魔法炼金术桌(Alchemy Table)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> alchemyTableRecipes = 
                    com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertAlchemyTableRecipes(recipeManager);
            recipes.addAll(alchemyTableRecipes);
        } catch (Exception e) {
            // 如果血魔法模组未加载，捕获异常以避免崩溃
        }
        
        // 添加余烬mod配方转换
        try {
            // 添加余烬炼金台(Alchemy Tablet)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> embersAlchemyRecipes = 
                    com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertAlchemyTabletRecipes(recipeManager);
            recipes.addAll(embersAlchemyRecipes);
            
            // 添加余烬地质分离器(Geologic Separator)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> embersGeoSeparatorRecipes = 
                    com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertGeoSeparatorRecipes(recipeManager);
            recipes.addAll(embersGeoSeparatorRecipes);
            
            // 添加余烬熔炼炉(Melter)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> embersMelterRecipes = 
                    com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMelterRecipes(recipeManager);
            recipes.addAll(embersMelterRecipes);
            
            // 添加余烬压印锤(Stamper)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> embersStampingRecipes = 
                    com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertStampingRecipes(recipeManager);
            recipes.addAll(embersStampingRecipes);
            
            // 添加余烬混合离心器(Mixer Centrifuge)配方转换
            List<com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe> embersMixingRecipes = 
                    com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMixingRecipes(recipeManager);
            recipes.addAll(embersMixingRecipes);
        } catch (Exception e) {
            // 如果余烬mod未加载，捕获异常以避免崩溃
        }

        registration.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, recipes);

        // 添加催化剂信息
        List<CatalystInfoCategory.CatalystInfo> catalystInfos = new ArrayList<>();
        catalystInfos.add(new CatalystInfoCategory.CatalystInfo());
        registration.addRecipes(CatalystInfoCategory.TYPE, catalystInfos);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(AdvancedAlloyFurnaceBlock.ADVANCED_ALLOY_FURNACE_BLOCK.get()),
                AdvancedAlloyFurnaceRecipeCategory.TYPE,
                CatalystInfoCategory.TYPE
        );

        // 添加各个等级的催化剂作为配方催化剂
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_1.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_2.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_3.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_4.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_5.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_6.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_7.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_8.get()),
                CatalystInfoCategory.TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(ModIngots.USELESS_INGOT_TIER_9.get()),
                CatalystInfoCategory.TYPE
        );
    }
}