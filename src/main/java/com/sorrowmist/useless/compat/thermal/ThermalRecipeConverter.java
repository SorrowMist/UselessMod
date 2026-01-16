package com.sorrowmist.useless.compat.thermal;

import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.ModIngots;
import cofh.thermal.core.util.recipes.machine.InsolatorRecipe;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 热力配方转换器，用于将热力系列模组的配方转换为高级合金炉配方
 */
public class ThermalRecipeConverter {

    /**
     * 将热力有机灌注机(Insolator)配方转换为高级合金炉配方
     * 
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertInsolatorRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();
        
        // 直接获取所有配方，然后筛选出InsolatorRecipe类型的配方
        try {
            // 获取InsolatorRecipe类
            Class<?> insolatorRecipeClass = Class.forName("cofh.thermal.core.util.recipes.machine.InsolatorRecipe");
            
            // 获取所有配方
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();
            
            // 筛选出InsolatorRecipe类型的配方
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 检查配方是否为InsolatorRecipe类型
                if (insolatorRecipeClass.isInstance(recipe)) {
                    InsolatorRecipe insolatorRecipe = (InsolatorRecipe) recipe;
                    
                    // 创建配方ID，使用"thermal_insolator_"前缀加上原配方ID
                    ResourceLocation originalId = insolatorRecipe.getId();
                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "thermal_insolator_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    // 1. 处理输入物品：直接使用原配方的输入物品
                    List<Ingredient> inputIngredients = insolatorRecipe.getInputItems();
                    
                    // 2. 处理输入物品数量：从原始配方中读取需求数量并乘以64倍
                    List<Long> inputItemCounts = new ArrayList<>();
                    for (Ingredient ingredient : inputIngredients) {
                        // 默认数量为1
                        long originalCount = 1;
                        
                        // 检查是否为IngredientWithCount类型，获取原始数量
                        try {
                            Class<?> ingredientWithCountClass = Class.forName("cofh.lib.util.crafting.IngredientWithCount");
                            if (ingredientWithCountClass.isInstance(ingredient)) {
                                // 使用反射获取count字段值
                                java.lang.reflect.Field countField = ingredientWithCountClass.getDeclaredField("count");
                                countField.setAccessible(true);
                                originalCount = countField.getLong(ingredient);
                            }
                        } catch (Exception e) {
                            // 如果不是IngredientWithCount类型，使用默认数量1
                            originalCount = 1;
                        }
                        
                        // 使用原始数量，不需要乘以64倍
                        long newCount = originalCount;
                        inputItemCounts.add(newCount);
                    }

                    // 3. 处理输入流体：直接使用原配方的输入流体
                    FluidStack inputFluid = FluidStack.EMPTY;
                    if (!insolatorRecipe.getInputFluids().isEmpty()) {
                        // 获取第一个流体输入
                        cofh.lib.common.fluid.FluidIngredient fluidIngredient = insolatorRecipe.getInputFluids().get(0);
                        if (fluidIngredient != null) {
                            // 获取流体列表，然后取第一个流体作为代表
                            FluidStack[] fluidStacks = fluidIngredient.getFluids();
                            if (fluidStacks.length > 0) {
                                FluidStack fluidStack = fluidStacks[0];
                                // 使用流体栈中的流体，保留原始数量
                                inputFluid = fluidStack;
                            }
                        }
                    }

                    // 4. 处理输出物品：概率向上取整作为输出数量
                    List<ItemStack> outputItems = new ArrayList<>();
                    List<ItemStack> originalOutputs = insolatorRecipe.getOutputItems();
                    List<Float> outputChances = insolatorRecipe.getOutputItemChances();
                    
                    for (int i = 0; i < originalOutputs.size(); i++) {
                        ItemStack originalOutput = originalOutputs.get(i);
                        float chance = i < outputChances.size() ? outputChances.get(i) : 1.0f;
                        
                        // 概率向上取整作为输出数量
                        int outputCount = (int) Math.ceil(chance);
                        
                        ItemStack output = originalOutput.copy();
                        output.setCount(outputCount);
                        outputItems.add(output);
                    }

                    // 5. 处理输出流体：直接使用原配方的输出流体
                    FluidStack outputFluid = FluidStack.EMPTY;
                    if (!insolatorRecipe.getOutputFluids().isEmpty()) {
                        outputFluid = insolatorRecipe.getOutputFluids().get(0).copy();
                    }

                    // 6. 处理能量消耗：使用原配方的能量值
                    int energy = insolatorRecipe.getEnergy();

                    // 7. 处理处理时间：使用原配方的时间
                    int processTime = 40; // 默认为40tick

                    // 8. 处理催化剂：使用无用锭标签作为催化剂
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

                    // 9. 处理模具：要求放入"thermal:machine_insolator"
                    // 创建物品的ResourceLocation，然后获取对应的物品
                    ResourceLocation machineInsolatorId = ResourceLocation.fromNamespaceAndPath("thermal", "machine_insolator");
                    net.minecraft.world.item.Item machineInsolator = ForgeRegistries.ITEMS.getValue(machineInsolatorId);
                    if (machineInsolator == null) {
                        throw new RuntimeException("Could not find item: " + machineInsolatorId);
                    }
                    Ingredient mold = Ingredient.of(machineInsolator);

                    // 创建并返回转换后的高级合金炉配方，设置isInsolatorRecipe为true
                    AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                            newId,
                            inputIngredients,
                            inputItemCounts,
                            inputFluid,
                            outputItems,
                            outputFluid,
                            energy,
                            processTime,
                            catalyst,
                            catalystCount,
                            mold,
                            true
                    );
                    
                    convertedRecipes.add(convertedRecipe);
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，避免崩溃
            e.printStackTrace();
            System.err.println("Failed to convert insolator recipes: " + e.getMessage());
        }
        
        return convertedRecipes;
    }
    
    /**
     * 将热力感应炉(Smelter)配方转换为高级合金炉配方
     * 
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertSmelterRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();
        
        // 直接获取所有配方，然后筛选出SmelterRecipe类型的配方
        try {
            // 获取SmelterRecipe类
            Class<?> smelterRecipeClass = Class.forName("cofh.thermal.core.util.recipes.machine.SmelterRecipe");
            
            // 获取所有配方
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();
            
            // 筛选出SmelterRecipe类型的配方
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 检查配方是否为SmelterRecipe类型
                if (smelterRecipeClass.isInstance(recipe)) {
                    // 使用反射获取SmelterRecipe的方法
                    Object smelterRecipe = recipe;
                    
                    // 1. 获取配方ID
                    ResourceLocation originalId = recipe.getId();
                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "thermal_smelter_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    // 2. 获取输入物品
                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();
                    
                    Method getInputItemsMethod = smelterRecipeClass.getMethod("getInputItems");
                    List<Ingredient> originalInputs = (List<Ingredient>) getInputItemsMethod.invoke(smelterRecipe);
                    
                    // 3. 处理输入物品：从原始配方中读取需求数量并乘以64倍
                    for (int i = 0; i < originalInputs.size(); i++) {
                        Ingredient ingredient = originalInputs.get(i);
                        inputIngredients.add(ingredient);
                        
                        // 默认数量为1
                        long originalCount = 1;
                        
                        // 检查是否为IngredientWithCount类型，获取原始数量
                        try {
                            Class<?> ingredientWithCountClass = Class.forName("cofh.lib.util.crafting.IngredientWithCount");
                            if (ingredientWithCountClass.isInstance(ingredient)) {
                                // 使用反射获取count字段值
                                java.lang.reflect.Field countField = ingredientWithCountClass.getDeclaredField("count");
                                countField.setAccessible(true);
                                originalCount = countField.getLong(ingredient);
                            }
                        } catch (Exception e) {
                            // 如果不是IngredientWithCount类型，使用默认数量1
                            originalCount = 1;
                        }
                        
                        // 输入数量变为原来的64倍
                        long newCount = originalCount * 64;
                        inputItemCounts.add(newCount);
                    }

                    // 4. 处理输入流体
                    FluidStack inputFluid = FluidStack.EMPTY;
                    Method getInputFluidsMethod = smelterRecipeClass.getMethod("getInputFluids");
                    List<?> inputFluids = (List<?>) getInputFluidsMethod.invoke(smelterRecipe);
                    
                    if (!inputFluids.isEmpty()) {
                        // 获取第一个流体输入
                        Object fluidIngredient = inputFluids.get(0);
                        Class<?> fluidIngredientClass = fluidIngredient.getClass();
                        
                        // 获取流体栈数组
                        Method getFluidsMethod = fluidIngredientClass.getMethod("getFluids");
                        FluidStack[] fluidStacks = (FluidStack[]) getFluidsMethod.invoke(fluidIngredient);
                        
                        if (fluidStacks.length > 0) {
                            FluidStack fluidStack = fluidStacks[0].copy();
                            // 输入流体数量乘以64倍
                            fluidStack.setAmount(fluidStack.getAmount() * 64);
                            inputFluid = fluidStack;
                        }
                    }

                    // 5. 处理输出物品：数量乘以64倍
                    List<ItemStack> outputItems = new ArrayList<>();
                    Method getOutputItemsMethod = smelterRecipeClass.getMethod("getOutputItems");
                    List<ItemStack> originalOutputs = (List<ItemStack>) getOutputItemsMethod.invoke(smelterRecipe);
                    
                    for (ItemStack originalOutput : originalOutputs) {
                        ItemStack output = originalOutput.copy();
                        // 输出数量乘以64倍
                        output.setCount(output.getCount() * 64);
                        outputItems.add(output);
                    }

                    // 6. 处理输出流体：数量乘以64倍
                    FluidStack outputFluid = FluidStack.EMPTY;
                    Method getOutputFluidsMethod = smelterRecipeClass.getMethod("getOutputFluids");
                    List<FluidStack> originalOutputFluids = (List<FluidStack>) getOutputFluidsMethod.invoke(smelterRecipe);
                    
                    if (!originalOutputFluids.isEmpty()) {
                        FluidStack fluidStack = originalOutputFluids.get(0).copy();
                        // 输出流体数量乘以64倍
                        fluidStack.setAmount(fluidStack.getAmount() * 64);
                        outputFluid = fluidStack;
                    }

                    // 7. 处理能量消耗：使用原配方的能量值，乘以64倍
                    Method getEnergyMethod = smelterRecipeClass.getMethod("getEnergy");
                    int energy = (int) getEnergyMethod.invoke(smelterRecipe) * 64;

                    // 8. 处理处理时间：使用原配方的时间
                    int processTime = 40; // 默认为40tick

                    // 9. 处理催化剂：使用无用锭标签作为催化剂
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

                    // 10. 处理模具：要求放入"thermal:machine_smelter"
                    // 创建物品的ResourceLocation，然后获取对应的物品
                    ResourceLocation machineSmelterId = ResourceLocation.fromNamespaceAndPath("thermal", "machine_smelter");
                    net.minecraft.world.item.Item machineSmelter = ForgeRegistries.ITEMS.getValue(machineSmelterId);
                    if (machineSmelter == null) {
                        throw new RuntimeException("Could not find item: " + machineSmelterId);
                    }
                    Ingredient mold = Ingredient.of(machineSmelter);

                    // 创建并返回转换后的高级合金炉配方
                    AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                            newId,
                            inputIngredients,
                            inputItemCounts,
                            inputFluid,
                            outputItems,
                            outputFluid,
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
            System.err.println("Failed to convert smelter recipes: " + e.getMessage());
        }
        
        return convertedRecipes;
    }
    
    /**
     * 将热力多驱冲压机(Press)配方转换为高级合金炉配方
     * 
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertPressRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();
        
        // 直接获取所有配方，然后筛选出PressRecipe类型的配方
        try {
            // 获取PressRecipe类
            Class<?> pressRecipeClass = Class.forName("cofh.thermal.core.util.recipes.machine.PressRecipe");
            
            // 获取所有配方
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();
            
            // 筛选出PressRecipe类型的配方
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                // 检查配方是否为PressRecipe类型
                if (pressRecipeClass.isInstance(recipe)) {
                    // 使用反射获取PressRecipe的方法
                    Object pressRecipe = recipe;
                    
                    // 1. 获取配方ID
                    ResourceLocation originalId = recipe.getId();
                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "thermal_press_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    // 2. 获取输入物品
                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();
                    
                    Method getInputItemsMethod = pressRecipeClass.getMethod("getInputItems");
                    List<Ingredient> originalInputs = (List<Ingredient>) getInputItemsMethod.invoke(pressRecipe);
                    
                    // 3. 处理输入物品：从原始配方中读取需求数量并乘以64倍
                    for (int i = 0; i < originalInputs.size(); i++) {
                        Ingredient ingredient = originalInputs.get(i);
                        inputIngredients.add(ingredient);
                        
                        // 默认数量为1
                        long originalCount = 1;
                        
                        // 检查是否为IngredientWithCount类型，获取原始数量
                        try {
                            Class<?> ingredientWithCountClass = Class.forName("cofh.lib.util.crafting.IngredientWithCount");
                            if (ingredientWithCountClass.isInstance(ingredient)) {
                                // 使用反射获取count字段值
                                java.lang.reflect.Field countField = ingredientWithCountClass.getDeclaredField("count");
                                countField.setAccessible(true);
                                originalCount = countField.getLong(ingredient);
                            }
                        } catch (Exception e) {
                            // 如果不是IngredientWithCount类型，使用默认数量1
                            originalCount = 1;
                        }
                        
                        // 输入数量变为原来的64倍
                        long newCount = originalCount * 64;
                        inputItemCounts.add(newCount);
                    }

                    // 4. 处理输入流体
                    FluidStack inputFluid = FluidStack.EMPTY;
                    Method getInputFluidsMethod = pressRecipeClass.getMethod("getInputFluids");
                    List<?> inputFluids = (List<?>) getInputFluidsMethod.invoke(pressRecipe);
                    
                    if (!inputFluids.isEmpty()) {
                        // 获取第一个流体输入
                        Object fluidIngredient = inputFluids.get(0);
                        Class<?> fluidIngredientClass = fluidIngredient.getClass();
                        
                        // 获取流体栈数组
                        Method getFluidsMethod = fluidIngredientClass.getMethod("getFluids");
                        FluidStack[] fluidStacks = (FluidStack[]) getFluidsMethod.invoke(fluidIngredient);
                        
                        if (fluidStacks.length > 0) {
                            FluidStack fluidStack = fluidStacks[0].copy();
                            // 输入流体数量乘以64倍
                            fluidStack.setAmount(fluidStack.getAmount() * 64);
                            inputFluid = fluidStack;
                        }
                    }

                    // 5. 处理输出物品：数量乘以64倍
                    List<ItemStack> outputItems = new ArrayList<>();
                    Method getOutputItemsMethod = pressRecipeClass.getMethod("getOutputItems");
                    List<ItemStack> originalOutputs = (List<ItemStack>) getOutputItemsMethod.invoke(pressRecipe);
                    
                    for (ItemStack originalOutput : originalOutputs) {
                        ItemStack output = originalOutput.copy();
                        // 输出数量乘以64倍
                        output.setCount(output.getCount() * 64);
                        outputItems.add(output);
                    }

                    // 6. 处理输出流体：数量乘以64倍
                    FluidStack outputFluid = FluidStack.EMPTY;
                    Method getOutputFluidsMethod = pressRecipeClass.getMethod("getOutputFluids");
                    List<FluidStack> originalOutputFluids = (List<FluidStack>) getOutputFluidsMethod.invoke(pressRecipe);
                    
                    if (!originalOutputFluids.isEmpty()) {
                        FluidStack fluidStack = originalOutputFluids.get(0).copy();
                        // 输出流体数量乘以64倍
                        fluidStack.setAmount(fluidStack.getAmount() * 64);
                        outputFluid = fluidStack;
                    }

                    // 7. 处理能量消耗：使用原配方的能量值，乘以64倍
                    Method getEnergyMethod = pressRecipeClass.getMethod("getEnergy");
                    int energy = (int) getEnergyMethod.invoke(pressRecipe) * 64;

                    // 8. 处理处理时间：使用原配方的时间
                    int processTime = 40; // 默认为40tick

                    // 9. 处理催化剂：使用无用锭标签作为催化剂
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

                    // 10. 处理模具：将底部输入栏对应的物品（索引为1）作为模具
                    Ingredient mold;
                    if (originalInputs.size() > 1) {
                        // 如果有底部输入栏物品，将其作为模具
                        mold = originalInputs.get(1);
                        // 移除底部输入栏物品，不再作为输入物品
                        inputIngredients.remove(1);
                        inputItemCounts.remove(1);
                    } else {
                        // 否则使用默认模具：金属板模具
                        ResourceLocation metalMoldPlateId = ResourceLocation.fromNamespaceAndPath("useless_mod", "metal_mold_plate");
                        net.minecraft.world.item.Item metalMoldPlate = ForgeRegistries.ITEMS.getValue(metalMoldPlateId);
                        if (metalMoldPlate == null) {
                            throw new RuntimeException("Could not find item: " + metalMoldPlateId);
                        }
                        mold = Ingredient.of(metalMoldPlate);
                    }

                    // 创建并返回转换后的高级合金炉配方
                    AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                            newId,
                            inputIngredients,
                            inputItemCounts,
                            inputFluid,
                            outputItems,
                            outputFluid,
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
        } catch (Exception e) {
            // 捕获所有异常，避免崩溃
            e.printStackTrace();
            System.err.println("Failed to convert press recipes: " + e.getMessage());
        }
        
        return convertedRecipes;
    }
}