package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.ModIngots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mekanism配方转换器，用于将Mekanism系列模组的配方转换为高级合金炉配方
 */
public class MekanismRecipeConverter {

    /**
     * 将Mekanism冶金灌注机(MetallurgicInfuser)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertMetallurgicInfuserRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            // 获取MetallurgicInfuserRecipe类
            Class<?> metallurgicInfuserRecipeClass = Class.forName("mekanism.common.recipe.impl.MetallurgicInfuserIRecipe");
            // 获取ItemStackToChemicalRecipe类
            Class<?> itemStackToChemicalRecipeClass = Class.forName("mekanism.api.recipes.chemical.ItemStackToChemicalRecipe");
            // 获取InfusionStack类
            Class<?> infusionStackClass = Class.forName("mekanism.api.chemical.infuse.InfusionStack");

            // 获取所有配方
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();

            // 首先收集所有Infusion类型的转换配方
            List<Object> infusionConversionRecipes = new ArrayList<>();
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 收集所有ItemStackToChemicalRecipe类型的配方
                if (itemStackToChemicalRecipeClass.isInstance(recipe)) {
                    // 检查是否为Infusion类型的转换配方
                    try {
                        Method getOutputMethod = itemStackToChemicalRecipeClass.getMethod("getOutputDefinition");
                        List<?> outputDefinition = (List<?>) getOutputMethod.invoke(recipe);
                        if (!outputDefinition.isEmpty() && infusionStackClass.isInstance(outputDefinition.get(0))) {
                            infusionConversionRecipes.add(recipe);
                        }
                    } catch (Exception e) {
                        // 忽略异常，继续处理其他配方
                    }
                }
            }

            // 筛选出MetallurgicInfuserRecipe类型的配方
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 检查配方是否为MetallurgicInfuserRecipe类型
                if (metallurgicInfuserRecipeClass.isInstance(recipe)) {
                    // 使用反射获取配方信息
                    Object metallurgicRecipe = recipe;

                    // 1. 获取配方ID
                    ResourceLocation originalId = recipe.getId();

                    // 2. 获取输入物品B
                    Method getItemInputMethod = metallurgicRecipe.getClass().getMethod("getItemInput");
                    Object itemInput = getItemInputMethod.invoke(metallurgicRecipe);
                    Class<?> itemStackIngredientClass = Class.forName("mekanism.api.recipes.ingredients.ItemStackIngredient");
                    Method getRepresentationsMethod = itemStackIngredientClass.getMethod("getRepresentations");
                    List<ItemStack> itemRepresentations = (List<ItemStack>) getRepresentationsMethod.invoke(itemInput);
                    Ingredient inputB = Ingredient.of(itemRepresentations.toArray(new ItemStack[0]));

                    // 3. 获取输入灌注类型A和数量
                    Method getChemicalInputMethod = metallurgicRecipe.getClass().getMethod("getChemicalInput");
                    Object chemicalInput = getChemicalInputMethod.invoke(metallurgicRecipe);
                    Class<?> infusionStackIngredientClass = Class.forName("mekanism.api.recipes.ingredients.ChemicalStackIngredient$InfusionStackIngredient");
                    Method getChemicalRepresentationsMethod = infusionStackIngredientClass.getMethod("getRepresentations");
                    List<?> infusionRepresentations = (List<?>) getChemicalRepresentationsMethod.invoke(chemicalInput);

                    // 4. 获取输出物品C
                    Method getOutputDefinitionMethod = metallurgicRecipe.getClass().getMethod("getOutputDefinition");
                    List<ItemStack> outputRepresentations = (List<ItemStack>) getOutputDefinitionMethod.invoke(metallurgicRecipe);
                    if (outputRepresentations.isEmpty()) {
                        continue; // 跳过没有输出的配方
                    }
                    ItemStack outputC = outputRepresentations.get(0);

                    // 遍历所有输入灌注类型A的表示
                    for (Object infusionStackObj : infusionRepresentations) {
                        // 获取灌注类型A的类型和数量
                        Method getTypeMethod = infusionStackClass.getMethod("getType");
                        Object infuseType = getTypeMethod.invoke(infusionStackObj);
                        Method getAmountMethod = infusionStackClass.getMethod("getAmount");
                        long requiredInfusionAmount = (long) getAmountMethod.invoke(infusionStackObj);

                        // 遍历所有可以转换为A的D物品
                        for (Object conversionRecipe : infusionConversionRecipes) {
                            // 获取转换配方的输出
                            Method conversionOutputMethod = itemStackToChemicalRecipeClass.getMethod("getOutputDefinition");
                            List<?> conversionOutputs = (List<?>) conversionOutputMethod.invoke(conversionRecipe);
                            if (conversionOutputs.isEmpty()) {
                                continue;
                            }
                            Object conversionOutput = conversionOutputs.get(0);

                            // 检查转换配方的输出是否为当前需要的灌注类型A
                            Method conversionOutputTypeMethod = conversionOutput.getClass().getMethod("getType");
                            Object conversionOutputType = conversionOutputTypeMethod.invoke(conversionOutput);
                            if (!conversionOutputType.equals(infuseType)) {
                                continue;
                            }

                            // 获取转换配方的输出数量
                            Method conversionOutputAmountMethod = conversionOutput.getClass().getMethod("getAmount");
                            long conversionOutputAmount = (long) conversionOutputAmountMethod.invoke(conversionOutput);

                            // 获取转换配方的输入D
                            Method conversionInputMethod = itemStackToChemicalRecipeClass.getMethod("getInput");
                            Object conversionInput = conversionInputMethod.invoke(conversionRecipe);
                            Method conversionInputRepresentationsMethod = itemStackIngredientClass.getMethod("getRepresentations");
                            List<ItemStack> conversionInputRepresentations = (List<ItemStack>) conversionInputRepresentationsMethod.invoke(conversionInput);
                            Ingredient inputD = Ingredient.of(conversionInputRepresentations.toArray(new ItemStack[0]));

                            // 5. 计算每64个B需要消耗多少D
                            // 公式：每1个B需要A的数量为 requiredInfusionAmount
                            //       每1个D可以转换为A的数量为 conversionOutputAmount
                            //       所以每1个B需要D的数量为 requiredInfusionAmount / conversionOutputAmount
                            //       每64个B需要D的数量为 64 * requiredInfusionAmount / conversionOutputAmount
                            long dPer64B = (64 * requiredInfusionAmount) / conversionOutputAmount;
                            if ((64 * requiredInfusionAmount) % conversionOutputAmount != 0) {
                                dPer64B += 1; // 向上取整
                            }

                            // 6. 创建配方ID
                            ResourceLocation newId = new ResourceLocation(
                                    "useless_mod",
                                    "mek_metallurgic_" + originalId.getNamespace() + "_" + originalId.getPath() + "_" + infuseType.hashCode() + "_" + conversionRecipe.hashCode()
                            );

                            // 7. 处理输入物品和数量
                            List<Ingredient> inputIngredients = new ArrayList<>();
                            List<Long> inputItemCounts = new ArrayList<>();
                            
                            // 添加输入物品B：64个
                            inputIngredients.add(inputB);
                            inputItemCounts.add(64L);
                            
                            // 添加输入物品D：对应的数量
                            inputIngredients.add(inputD);
                            inputItemCounts.add(dPer64B);

                            // 8. 处理输出物品：64个C
                            List<ItemStack> outputItems = new ArrayList<>();
                            ItemStack output64C = outputC.copy();
                            output64C.setCount(outputC.getCount() * 64);
                            outputItems.add(output64C);

                            // 9. 处理输入流体：无
                            List<FluidStack> inputFluids = new ArrayList<>();

                            // 10. 处理输出流体：无
                            List<FluidStack> outputFluids = new ArrayList<>();

                            // 11. 处理能量消耗：使用默认值
                            int energy = 10000; // 默认能量消耗

                            // 12. 处理处理时间：使用默认值
                            int processTime = 40; // 默认为40tick

                            // 13. 处理催化剂：使用无用锭标签作为催化剂
                            Ingredient catalyst = Ingredient.of(ModIngots.USELESS_INGOT_TIER_1.get(),
                                    ModIngots.USELESS_INGOT_TIER_2.get(),
                                    ModIngots.USELESS_INGOT_TIER_3.get(),
                                    ModIngots.USELESS_INGOT_TIER_4.get(),
                                    ModIngots.USELESS_INGOT_TIER_5.get(),
                                    ModIngots.USELESS_INGOT_TIER_6.get(),
                                    ModIngots.USELESS_INGOT_TIER_7.get(),
                                    ModIngots.USELESS_INGOT_TIER_8.get(),
                                    ModIngots.USELESS_INGOT_TIER_9.get(),
                                    ModIngots.USEFUL_INGOT.get());
                            int catalystCount = 1;

                            // 14. 处理模具：要求放入"mekanism:metallurgic_infuser"
                            ResourceLocation metallurgicInfuserId = ResourceLocation.fromNamespaceAndPath("mekanism", "metallurgic_infuser");
                            net.minecraft.world.item.Item metallurgicInfuser = ForgeRegistries.ITEMS.getValue(metallurgicInfuserId);
                            if (metallurgicInfuser == null) {
                                throw new RuntimeException("Could not find item: " + metallurgicInfuserId);
                            }
                            Ingredient mold = Ingredient.of(metallurgicInfuser);

                            // 创建并返回转换后的高级合金炉配方
                            AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                                    newId,
                                    inputIngredients,
                                    inputItemCounts,
                                    inputFluids,
                                    outputItems,
                                    outputFluids,
                                    energy,
                                    processTime,
                                    catalyst,
                                    catalystCount,
                                    mold,
                                    false
                            );

                            convertedRecipes.add(convertedRecipe);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，避免崩溃
            e.printStackTrace();
            System.err.println("Failed to convert metallurgic infuser recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将Mekanism富集仓(EnrichmentChamber)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertEnrichmentChamberRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            // 获取EnrichingIRecipe类
            Class<?> enrichingIRecipeClass = Class.forName("mekanism.common.recipe.impl.EnrichingIRecipe");

            // 获取所有配方
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();

            // 筛选出EnrichingIRecipe类型的配方
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 检查配方是否为EnrichingIRecipe类型
                if (enrichingIRecipeClass.isInstance(recipe)) {
                    // 使用反射获取配方信息
                    Object enrichingRecipe = recipe;

                    // 1. 获取配方ID
                    ResourceLocation originalId = recipe.getId();

                    // 2. 获取输入物品
                    Method getInputMethod = enrichingRecipe.getClass().getMethod("getInput");
                    Object itemInput = getInputMethod.invoke(enrichingRecipe);
                    Class<?> itemStackIngredientClass = Class.forName("mekanism.api.recipes.ingredients.ItemStackIngredient");
                    Method getRepresentationsMethod = itemStackIngredientClass.getMethod("getRepresentations");
                    List<ItemStack> itemRepresentations = (List<ItemStack>) getRepresentationsMethod.invoke(itemInput);
                    Ingredient inputIngredient = Ingredient.of(itemRepresentations.toArray(new ItemStack[0]));

                    // 3. 获取输出物品
                    Method getOutputDefinitionMethod = enrichingRecipe.getClass().getMethod("getOutputDefinition");
                    List<ItemStack> outputRepresentations = (List<ItemStack>) getOutputDefinitionMethod.invoke(enrichingRecipe);
                    if (outputRepresentations.isEmpty()) {
                        continue; // 跳过没有输出的配方
                    }
                    ItemStack outputItem = outputRepresentations.get(0);

                    // 4. 创建配方ID
                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "mek_enrichment_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    // 5. 处理输入物品和数量：获取原始数量并乘以64
                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();

                    inputIngredients.add(inputIngredient);
                    
                    // 获取原始输入数量
                    long originalInputCount = 1;
                    if (!itemRepresentations.isEmpty()) {
                        originalInputCount = itemRepresentations.get(0).getCount();
                    }
                    inputItemCounts.add(originalInputCount * 64);

                    // 6. 处理输出物品：原始数量乘以64
                    List<ItemStack> outputItems = new ArrayList<>();
                    ItemStack output64 = outputItem.copy();
                    output64.setCount(outputItem.getCount() * 64);
                    outputItems.add(output64);

                    // 7. 处理输入流体：无
                    List<FluidStack> inputFluids = new ArrayList<>();

                    // 8. 处理输出流体：无
                    List<FluidStack> outputFluids = new ArrayList<>();

                    // 9. 处理能量消耗：使用默认值
                    int energy = 2000; // 默认能量消耗

                    // 10. 处理处理时间：使用默认值
                    int processTime = 40; // 默认为200tick

                    // 11. 处理催化剂：使用无用锭标签作为催化剂
                    Ingredient catalyst = Ingredient.of(ModIngots.USELESS_INGOT_TIER_1.get(),
                            ModIngots.USELESS_INGOT_TIER_2.get(),
                            ModIngots.USELESS_INGOT_TIER_3.get(),
                            ModIngots.USELESS_INGOT_TIER_4.get(),
                            ModIngots.USELESS_INGOT_TIER_5.get(),
                            ModIngots.USELESS_INGOT_TIER_6.get(),
                            ModIngots.USELESS_INGOT_TIER_7.get(),
                            ModIngots.USELESS_INGOT_TIER_8.get(),
                            ModIngots.USELESS_INGOT_TIER_9.get(),
                            ModIngots.USEFUL_INGOT.get());
                    int catalystCount = 1;

                    // 12. 处理模具：要求放入"mekanism:enrichment_chamber"
                    ResourceLocation enrichmentChamberId = ResourceLocation.fromNamespaceAndPath("mekanism", "enrichment_chamber");
                    net.minecraft.world.item.Item enrichmentChamber = ForgeRegistries.ITEMS.getValue(enrichmentChamberId);
                    if (enrichmentChamber == null) {
                        throw new RuntimeException("Could not find item: " + enrichmentChamberId);
                    }
                    Ingredient mold = Ingredient.of(enrichmentChamber);

                    // 创建并返回转换后的高级合金炉配方
                    AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                            newId,
                            inputIngredients,
                            inputItemCounts,
                            inputFluids,
                            outputItems,
                            outputFluids,
                            energy,
                            processTime,
                            catalyst,
                            catalystCount,
                            mold,
                            false,
                            false
                    );

                    convertedRecipes.add(convertedRecipe);
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，避免崩溃
            e.printStackTrace();
            System.err.println("Failed to convert enrichment chamber recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }
}