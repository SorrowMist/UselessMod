package com.sorrowmist.useless.recipes.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.*;

public class AdvancedAlloyFurnaceRecipeManager {
    private static final AdvancedAlloyFurnaceRecipeManager INSTANCE = new AdvancedAlloyFurnaceRecipeManager();

    private final Map<String, List<AdvancedAlloyFurnaceRecipe>> recipeCache = new HashMap<>();

    public static AdvancedAlloyFurnaceRecipeManager getInstance() {
        return INSTANCE;
    }

    private AdvancedAlloyFurnaceRecipeManager() {}

    // 转换后的配方缓存
    private final List<AdvancedAlloyFurnaceRecipe> thermalConvertedRecipes = new ArrayList<>();
    private boolean thermalRecipesConverted = false;
    
    private final List<AdvancedAlloyFurnaceRecipe> mekanismConvertedRecipes = new ArrayList<>();
    private boolean mekanismRecipesConverted = false;
    
    private final List<AdvancedAlloyFurnaceRecipe> industrialForegoingConvertedRecipes = new ArrayList<>();
    private boolean industrialForegoingRecipesConverted = false;
    
    private final List<AdvancedAlloyFurnaceRecipe> bloodMagicConvertedRecipes = new ArrayList<>();
    private boolean bloodMagicRecipesConverted = false;
    
    private final List<AdvancedAlloyFurnaceRecipe> embersConvertedRecipes = new ArrayList<>();
    private boolean embersRecipesConverted = false;

    // 优化后的配方查找方法（支持单个流体输入，保持向后兼容）
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipeWithCatalystOrMold(Level level, List<ItemStack> inputItems, 
                                                                  FluidStack inputFluid, ItemStack catalyst, 
                                                                  ItemStack mold) {
        // 将单个流体转换为列表，调用支持多种流体的方法
        List<FluidStack> fluidList = new ArrayList<>();
        if (!inputFluid.isEmpty()) {
            fluidList.add(inputFluid);
        }
        return getRecipeWithCatalystOrMold(level, inputItems, fluidList, catalyst, mold);
    }
    
    // 优化后的配方查找方法（支持多种流体输入）
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipeWithCatalystOrMold(Level level, List<ItemStack> inputItems, 
                                                                  List<FluidStack> inputFluids, ItemStack catalyst, 
                                                                  ItemStack mold) {
        if (level == null) {
            return null;
        }

        // 提前检查输入是否为空
        boolean hasItems = false;
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }
        
        boolean hasFluids = false;
        for (FluidStack fluid : inputFluids) {
            if (!fluid.isEmpty()) {
                hasFluids = true;
                break;
            }
        }
        
        if (!hasItems && !hasFluids) {
            return null;
        }
        
        // 检查缓存
        String cacheKey = generateCacheKey(inputItems, inputFluids, catalyst, mold);
        if (recipeCache.containsKey(cacheKey)) {
            return recipeCache.get(cacheKey).isEmpty() ? null : recipeCache.get(cacheKey).get(0);
        }

        RecipeManager recipeManager = level.getRecipeManager();
        
        // 转换热力配方并缓存（只在第一次调用时执行）
        if (!thermalRecipesConverted) {
            try {
                // 导入热力配方转换器
                // 转换有机灌注机配方
                List<AdvancedAlloyFurnaceRecipe> insolatorRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertInsolatorRecipes(recipeManager);
                thermalConvertedRecipes.addAll(insolatorRecipes);

                
                // 转换感应炉配方
                List<AdvancedAlloyFurnaceRecipe> smelterRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertSmelterRecipes(recipeManager);
                thermalConvertedRecipes.addAll(smelterRecipes);

                
                // 转换冲压机配方
                List<AdvancedAlloyFurnaceRecipe> pressRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertPressRecipes(recipeManager);
                thermalConvertedRecipes.addAll(pressRecipes);

                
                thermalRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Thermal recipes, continue with other recipes
            }
        }
        
        // 转换Mekanism配方并缓存（只在第一次调用时执行）
        if (!mekanismRecipesConverted) {
            try {
                // 导入Mekanism配方转换器
                List<AdvancedAlloyFurnaceRecipe> metallurgicInfuserRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertMetallurgicInfuserRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(metallurgicInfuserRecipes);
                
                // 添加Mekanism富集仓配方转换
                List<AdvancedAlloyFurnaceRecipe> enrichmentRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertEnrichmentChamberRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(enrichmentRecipes);
                
                mekanismRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Mekanism recipes, continue with other recipes
            }
        }
        
        // 转换工业先锋配方并缓存（只在第一次调用时执行）
        if (!industrialForegoingRecipesConverted) {
            try {
                // 导入工业先锋配方转换器
                List<AdvancedAlloyFurnaceRecipe> dissolutionRecipes = com.sorrowmist.useless.compat.industrialforegoing.IndustrialForegoingRecipeConverter.convertDissolutionChamberRecipes(recipeManager);
                industrialForegoingConvertedRecipes.addAll(dissolutionRecipes);
                
                industrialForegoingRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Industrial Foregoing recipes, continue with other recipes
            }
        }
        
        // 转换血魔法配方并缓存（只在第一次调用时执行）
        if (!bloodMagicRecipesConverted) {
            try {
                // 导入血魔法配方转换器
                List<AdvancedAlloyFurnaceRecipe> bloodAltarRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertBloodAltarRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(bloodAltarRecipes);
                
                // 添加血魔法狱火熔炉配方转换
                List<AdvancedAlloyFurnaceRecipe> soulForgeRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertTartaricForgeRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(soulForgeRecipes);
                
                // 添加血魔法炼金术桌配方转换
                List<AdvancedAlloyFurnaceRecipe> alchemyTableRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertAlchemyTableRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(alchemyTableRecipes);
                
                bloodMagicRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Blood Magic recipes, continue with other recipes
            }
        }
        
        // 转换余烬mod配方并缓存（只在第一次调用时执行）
        if (!embersRecipesConverted) {
            try {
                // 导入余烬配方转换器
                List<AdvancedAlloyFurnaceRecipe> embersAlchemyRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertAlchemyTabletRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersAlchemyRecipes);
                
                // 添加余烬地质分离器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersGeoSeparatorRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertGeoSeparatorRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersGeoSeparatorRecipes);
                
                // 添加余烬熔炼炉配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMelterRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMelterRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMelterRecipes);
                
                // 添加余烬压印锤配方转换
                List<AdvancedAlloyFurnaceRecipe> embersStampingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertStampingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersStampingRecipes);
                
                // 添加余烬混合离心器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMixingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMixingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMixingRecipes);
                
                embersRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Embers recipes, continue with other recipes
            }
        }
        
        // 获取所有高级合金炉配方
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = new ArrayList<>(recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        ));
        
        // 添加转换后的热力配方
        allRecipes.addAll(thermalConvertedRecipes);
        // 添加转换后的Mekanism配方
        allRecipes.addAll(mekanismConvertedRecipes);
        // 添加转换后的工业先锋配方
        allRecipes.addAll(industrialForegoingConvertedRecipes);
        // 添加转换后的血魔法配方
        allRecipes.addAll(bloodMagicConvertedRecipes);
        // 添加转换后的余烬配方
        allRecipes.addAll(embersConvertedRecipes);

        boolean hasCatalyst = !catalyst.isEmpty();
        boolean hasMold = !mold.isEmpty();
        
        AdvancedAlloyFurnaceRecipe catalystRecipe = null;
        AdvancedAlloyFurnaceRecipe moldRecipe = null;
        AdvancedAlloyFurnaceRecipe normalRecipe = null;
        AdvancedAlloyFurnaceRecipe smeltingRecipe = null;

        // 先寻找完全匹配的配方
        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            if (recipe.matches(inputItems, inputFluids, catalyst, mold)) {
                // 找到完全匹配的配方，直接返回
                List<AdvancedAlloyFurnaceRecipe> resultList = Collections.singletonList(recipe);
                recipeCache.put(cacheKey, resultList);
                return recipe;
            }
        }

        // 如果没有完全匹配的配方，再根据优先级寻找可能匹配的配方
        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            // 检查是否匹配输入物品和流体（不考虑催化剂和模具）
            if (recipe.matches(inputItems, inputFluids, ItemStack.EMPTY, ItemStack.EMPTY)) {
                // 根据优先级找到第一个匹配的配方
                if (recipe.requiresCatalyst() && !hasCatalyst && catalystRecipe == null) {
                    catalystRecipe = recipe;
                } else if (recipe.requiresMold() && !hasMold && moldRecipe == null) {
                    moldRecipe = recipe;
                } else if (!recipe.requiresCatalyst() && !recipe.requiresMold() && normalRecipe == null) {
                    normalRecipe = recipe;
                }
            }
            
            // 如果所有类型都找到了，提前退出循环
            if (catalystRecipe != null && moldRecipe != null && normalRecipe != null) {
                break;
            }
        }
        
        // 检查原版熔炉配方
        boolean hasAnyFluids = false;
        for (FluidStack fluid : inputFluids) {
            if (!fluid.isEmpty()) {
                hasAnyFluids = true;
                break;
            }
        }
        
        if (normalRecipe == null && !hasAnyFluids && hasMold) {
            // 检查模具是否为原版熔炉
            boolean isFurnaceMold = mold.getItem() == net.minecraft.world.item.Items.FURNACE;
            if (isFurnaceMold) {
                // 创建一个只包含非空输入物品的列表
                List<ItemStack> nonEmptyInputs = new ArrayList<>();
                for (ItemStack stack : inputItems) {
                    if (!stack.isEmpty()) {
                        nonEmptyInputs.add(stack);
                    }
                }
                
                // 只处理有非空输入物品的情况
                if (!nonEmptyInputs.isEmpty()) {
                    // 获取所有熔炉配方
                    Collection<net.minecraft.world.item.crafting.SmeltingRecipe> smeltingRecipes = recipeManager.getAllRecipesFor(
                            net.minecraft.world.item.crafting.RecipeType.SMELTING
                    );
                    
                    for (ItemStack stack : nonEmptyInputs) {
                        for (net.minecraft.world.item.crafting.SmeltingRecipe recipe : smeltingRecipes) {
                            if (recipe.getIngredients().get(0).test(stack)) {
                                // 将原版熔炉配方转换为高级合金炉配方
                                List<net.minecraft.world.item.crafting.Ingredient> inputIngredients = new ArrayList<>();
                                List<Integer> inputCounts = new ArrayList<>();
                                
                                // 只添加配方中定义的输入物品
                                inputIngredients.add(recipe.getIngredients().get(0));
                                inputCounts.add(1);
                                
                                List<ItemStack> outputItems = new ArrayList<>();
                                outputItems.add(recipe.getResultItem(null));
                                
                                List<FluidStack> emptyFluids = new ArrayList<>();
                                
                                // 创建临时的高级合金炉配方，使用原版熔炉作为模具
                                Ingredient furnaceIngredient = Ingredient.of(net.minecraft.world.item.Items.FURNACE);
                                
                                // 创建无用锭标签的催化剂，允许使用所有无用锭
                                net.minecraft.tags.TagKey<net.minecraft.world.item.Item> uselessIngotTag = 
                                        net.minecraft.tags.TagKey.create(
                                                net.minecraft.core.registries.Registries.ITEM,
                                                new net.minecraft.resources.ResourceLocation("useless_mod", "useless_ingots")
                                        );
                                Ingredient uselessIngotCatalyst = Ingredient.of(uselessIngotTag);
                                
                                // 将List<Integer>转换为List<Long>
                                List<Long> longInputCounts = new ArrayList<>();
                                for (Integer count : inputCounts) {
                                    longInputCounts.add(count.longValue());
                                }
                                
                                smeltingRecipe = new AdvancedAlloyFurnaceRecipe(
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
                                break;
                            }
                        }
                        if (smeltingRecipe != null) {
                            break;
                        }
                    }
                }
            }
        }

        // 选择要返回的配方
        AdvancedAlloyFurnaceRecipe selectedRecipe = null;
        if (catalystRecipe != null) {
            selectedRecipe = catalystRecipe;
        } else if (moldRecipe != null) {
            selectedRecipe = moldRecipe;
        } else if (normalRecipe != null) {
            selectedRecipe = normalRecipe;
        } else if (smeltingRecipe != null) {
            selectedRecipe = smeltingRecipe;
        }

        // 更新缓存
        List<AdvancedAlloyFurnaceRecipe> resultList = new ArrayList<>();
        if (selectedRecipe != null) {
            resultList.add(selectedRecipe);
        }
        recipeCache.put(cacheKey, resultList);

        return selectedRecipe;
    }


    // 修改原有的getRecipe方法，使用新的优先级逻辑
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipe(Level level, List<ItemStack> inputItems, FluidStack inputFluid) {
        // 使用空的催化剂和模具槽位来调用新方法
        return getRecipeWithCatalystOrMold(level, inputItems, inputFluid, ItemStack.EMPTY, ItemStack.EMPTY);
    }
    
    // 添加支持多种流体输入的getRecipe方法
    @Nullable
    public AdvancedAlloyFurnaceRecipe getRecipe(Level level, List<ItemStack> inputItems, List<FluidStack> inputFluids) {
        // 使用空的催化剂和模具槽位来调用新方法
        return getRecipeWithCatalystOrMold(level, inputItems, inputFluids, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    // 支持多种流体输入的缓存键生成方法
    private String generateCacheKey(List<ItemStack> inputItems, List<FluidStack> inputFluids, ItemStack catalyst, ItemStack mold) {
        StringBuilder key = new StringBuilder();

        // 物品部分（排序以确保顺序不影响缓存键）
        List<ItemStack> sortedItems = new ArrayList<>();
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                sortedItems.add(stack);
            }
        }
        sortedItems.sort(Comparator.comparing(stack -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())));
        
        for (ItemStack stack : sortedItems) {
            key.append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
            key.append(":").append(stack.getCount()).append(";");
        }

        // 流体部分（排序以确保顺序不影响缓存键）
        List<FluidStack> sortedFluids = new ArrayList<>();
        for (FluidStack fluid : inputFluids) {
            if (!fluid.isEmpty()) {
                sortedFluids.add(fluid);
            }
        }
        sortedFluids.sort(Comparator.comparing(fluid -> net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid())));
        
        for (FluidStack fluid : sortedFluids) {
            key.append(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid()));
            key.append(":").append(fluid.getAmount()).append(";");
        }
        
        // 催化剂部分
        if (!catalyst.isEmpty()) {
            key.append("CAT:").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(catalyst.getItem()));
            key.append(":").append(catalyst.getCount()).append(";");
        }
        
        // 模具部分
        if (!mold.isEmpty()) {
            key.append("MOLD:").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mold.getItem()));
            key.append(":").append(mold.getCount()).append(";");
        }

        return key.toString();
    }
    
    // 原来的缓存键生成方法，保持向后兼容
    private String generateCacheKey(List<ItemStack> inputItems, FluidStack inputFluid, ItemStack catalyst, ItemStack mold) {
        List<FluidStack> fluidList = new ArrayList<>();
        if (!inputFluid.isEmpty()) {
            fluidList.add(inputFluid);
        }
        return generateCacheKey(inputItems, fluidList, catalyst, mold);
    }

    public void clearCache() {
        recipeCache.clear();
    }

    public boolean isValidInputItem(Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) return false;

        // 检查缓存
        String cacheKey = "ITEM_VALID:" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (recipeCache.containsKey(cacheKey)) {
            return !recipeCache.get(cacheKey).isEmpty();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        
        // 确保热力配方已转换
        if (!thermalRecipesConverted) {
            try {
                // 转换有机灌注机配方
                List<AdvancedAlloyFurnaceRecipe> insolatorRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertInsolatorRecipes(recipeManager);
                thermalConvertedRecipes.addAll(insolatorRecipes);
                
                // 转换感应炉配方
                List<AdvancedAlloyFurnaceRecipe> smelterRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertSmelterRecipes(recipeManager);
                thermalConvertedRecipes.addAll(smelterRecipes);
                
                // 转换冲压机配方
                List<AdvancedAlloyFurnaceRecipe> pressRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertPressRecipes(recipeManager);
                thermalConvertedRecipes.addAll(pressRecipes);
                
                thermalRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Thermal recipes, continue with other recipes
            }
        }
        
        // 确保Mekanism配方已转换
        if (!mekanismRecipesConverted) {
            try {
                // 转换Mekanism冶金灌注机配方
                List<AdvancedAlloyFurnaceRecipe> metallurgicInfuserRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertMetallurgicInfuserRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(metallurgicInfuserRecipes);
                
                // 添加Mekanism富集仓配方转换
                List<AdvancedAlloyFurnaceRecipe> enrichmentRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertEnrichmentChamberRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(enrichmentRecipes);
                
                mekanismRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Mekanism recipes, continue with other recipes
            }
        }
        
        // 确保工业先锋配方已转换
        if (!industrialForegoingRecipesConverted) {
            try {
                // 转换工业先锋化学溶解室配方
                List<AdvancedAlloyFurnaceRecipe> dissolutionRecipes = com.sorrowmist.useless.compat.industrialforegoing.IndustrialForegoingRecipeConverter.convertDissolutionChamberRecipes(recipeManager);
                industrialForegoingConvertedRecipes.addAll(dissolutionRecipes);
                
                industrialForegoingRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Industrial Foregoing recipes, continue with other recipes
            }
        }
        
        // 确保血魔法配方已转换
        if (!bloodMagicRecipesConverted) {
            try {
                // 转换血魔法血之祭坛配方
                List<AdvancedAlloyFurnaceRecipe> bloodAltarRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertBloodAltarRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(bloodAltarRecipes);
                
                // 添加血魔法狱火熔炉配方转换
                List<AdvancedAlloyFurnaceRecipe> soulForgeRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertTartaricForgeRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(soulForgeRecipes);
                
                // 添加血魔法炼金术桌配方转换
                List<AdvancedAlloyFurnaceRecipe> alchemyTableRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertAlchemyTableRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(alchemyTableRecipes);
                
                bloodMagicRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Blood Magic recipes, continue with other recipes
            }
        }
        
        // 确保余烬mod配方已转换
        if (!embersRecipesConverted) {
            try {
                // 转换余烬炼金台配方
                List<AdvancedAlloyFurnaceRecipe> embersAlchemyRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertAlchemyTabletRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersAlchemyRecipes);
                
                // 添加余烬地质分离器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersGeoSeparatorRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertGeoSeparatorRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersGeoSeparatorRecipes);
                
                // 添加余烬熔炼炉配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMelterRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMelterRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMelterRecipes);
                
                // 添加余烬压印锤配方转换
                List<AdvancedAlloyFurnaceRecipe> embersStampingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertStampingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersStampingRecipes);
                
                // 添加余烬混合离心器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMixingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMixingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMixingRecipes);
                
                embersRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Embers recipes, continue with other recipes
            }
        }
        
        // 获取所有高级合金炉配方
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = new ArrayList<>(recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        ));
        
        // 添加转换后的配方
        allRecipes.addAll(thermalConvertedRecipes);
        allRecipes.addAll(mekanismConvertedRecipes);
        allRecipes.addAll(industrialForegoingConvertedRecipes);
        allRecipes.addAll(bloodMagicConvertedRecipes);
        allRecipes.addAll(embersConvertedRecipes);

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getInputItems()) {
                if (ingredient.test(stack)) {
                    // 缓存结果
                    recipeCache.put(cacheKey, Collections.singletonList(recipe));
                    return true;
                }
            }
        }
        
        // 缓存结果
        recipeCache.put(cacheKey, Collections.emptyList());
        return false;
    }

    // 优化后的流体验证方法
    public boolean isValidInputFluid(Level level, FluidStack fluid) {
        if (fluid.isEmpty() || level == null) return false;

        // 检查缓存
        String cacheKey = "FLUID_VALID:" + net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (recipeCache.containsKey(cacheKey)) {
            return !recipeCache.get(cacheKey).isEmpty();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        
        // 确保热力配方已转换
        if (!thermalRecipesConverted) {
            try {
                List<AdvancedAlloyFurnaceRecipe> convertedRecipes = com.sorrowmist.useless.compat.thermal.ThermalRecipeConverter.convertInsolatorRecipes(recipeManager);
                thermalConvertedRecipes.addAll(convertedRecipes);
                thermalRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Thermal Insolator recipes, continue with other recipes
            }
        }
        
        // 确保Mekanism配方已转换
        if (!mekanismRecipesConverted) {
            try {
                // 转换Mekanism冶金灌注机配方
                List<AdvancedAlloyFurnaceRecipe> metallurgicInfuserRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertMetallurgicInfuserRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(metallurgicInfuserRecipes);
                
                // 添加Mekanism富集仓配方转换
                List<AdvancedAlloyFurnaceRecipe> enrichmentRecipes = com.sorrowmist.useless.compat.mekanism.MekanismRecipeConverter.convertEnrichmentChamberRecipes(recipeManager);
                mekanismConvertedRecipes.addAll(enrichmentRecipes);
                
                mekanismRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Mekanism recipes, continue with other recipes
            }
        }
        
        // 确保工业先锋配方已转换
        if (!industrialForegoingRecipesConverted) {
            try {
                // 转换工业先锋化学溶解室配方
                List<AdvancedAlloyFurnaceRecipe> dissolutionRecipes = com.sorrowmist.useless.compat.industrialforegoing.IndustrialForegoingRecipeConverter.convertDissolutionChamberRecipes(recipeManager);
                industrialForegoingConvertedRecipes.addAll(dissolutionRecipes);
                
                industrialForegoingRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Industrial Foregoing recipes, continue with other recipes
            }
        }
        
        // 确保血魔法配方已转换
        if (!bloodMagicRecipesConverted) {
            try {
                // 转换血魔法血之祭坛配方
                List<AdvancedAlloyFurnaceRecipe> bloodAltarRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertBloodAltarRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(bloodAltarRecipes);
                
                // 添加血魔法狱火熔炉配方转换
                List<AdvancedAlloyFurnaceRecipe> soulForgeRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertTartaricForgeRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(soulForgeRecipes);
                
                // 添加血魔法炼金术桌配方转换
                List<AdvancedAlloyFurnaceRecipe> alchemyTableRecipes = com.sorrowmist.useless.compat.bloodmagic.BloodMagicRecipeConverter.convertAlchemyTableRecipes(recipeManager);
                bloodMagicConvertedRecipes.addAll(alchemyTableRecipes);
                
                bloodMagicRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Blood Magic recipes, continue with other recipes
            }
        }
        
        // 确保余烬mod配方已转换
        if (!embersRecipesConverted) {
            try {
                // 转换余烬炼金台配方
                List<AdvancedAlloyFurnaceRecipe> embersAlchemyRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertAlchemyTabletRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersAlchemyRecipes);
                
                // 添加余烬地质分离器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersGeoSeparatorRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertGeoSeparatorRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersGeoSeparatorRecipes);
                
                // 添加余烬熔炼炉配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMelterRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMelterRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMelterRecipes);
                
                // 添加余烬压印锤配方转换
                List<AdvancedAlloyFurnaceRecipe> embersStampingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertStampingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersStampingRecipes);
                
                // 添加余烬混合离心器配方转换
                List<AdvancedAlloyFurnaceRecipe> embersMixingRecipes = com.sorrowmist.useless.compat.embers.EmbersRecipeConverter.convertMixingRecipes(recipeManager);
                embersConvertedRecipes.addAll(embersMixingRecipes);
                
                embersRecipesConverted = true;
            } catch (Exception e) {
                // Failed to convert Embers recipes, continue with other recipes
            }
        }
        
        // 获取所有高级合金炉配方
        Collection<AdvancedAlloyFurnaceRecipe> allRecipes = new ArrayList<>(recipeManager.getAllRecipesFor(
                com.sorrowmist.useless.recipes.ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()
        ));
        
        // 添加转换后的配方
        allRecipes.addAll(thermalConvertedRecipes);
        allRecipes.addAll(mekanismConvertedRecipes);
        allRecipes.addAll(industrialForegoingConvertedRecipes);
        allRecipes.addAll(bloodMagicConvertedRecipes);
        allRecipes.addAll(embersConvertedRecipes);

        for (AdvancedAlloyFurnaceRecipe recipe : allRecipes) {
            List<FluidStack> recipeFluids = recipe.getInputFluids();
            if (!recipeFluids.isEmpty()) {
                for (FluidStack recipeFluid : recipeFluids) {
                    if (recipeFluid.getFluid().isSame(fluid.getFluid())) {
                        // 缓存结果
                        recipeCache.put(cacheKey, Collections.singletonList(recipe));
                        return true;
                    }
                }
            }
        }
        
        // 缓存结果
        recipeCache.put(cacheKey, Collections.emptyList());
        return false;
    }
}