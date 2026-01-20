package com.sorrowmist.useless.compat.embers;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 余烬mod配方转换器，用于将余烬系列模组的配方转换为高级合金炉配方
 */
public class EmbersRecipeConverter {

    /**
     * 将余烬炼金台(Alchemy Tablet)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertAlchemyTabletRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> iAlchemyRecipeClass = Class.forName("com.rekindled.embers.recipe.IAlchemyRecipe");
            Class<?> registryManagerClass = Class.forName("com.rekindled.embers.RegistryManager");
            Field alchemyRecipeTypeField = registryManagerClass.getField("ALCHEMY");
            Object alchemyRecipeTypeObject = alchemyRecipeTypeField.get(null);
            
            // 获取配方类型的get方法
            Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
            Method getMethod = registryObjectClass.getMethod("get");
            net.minecraft.world.item.crafting.RecipeType<?> recipeType = (net.minecraft.world.item.crafting.RecipeType<?>) getMethod.invoke(alchemyRecipeTypeObject);
            
            // 使用反射调用RecipeManager.getAllRecipesFor方法
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (iAlchemyRecipeClass.isInstance(recipe)) {
                    Object alchemyRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    // 使用getter方法获取配方数据
                    java.lang.reflect.Method getCenterInputMethod = alchemyRecipe.getClass().getMethod("getCenterInput");
                    Ingredient tablet = (Ingredient) getCenterInputMethod.invoke(alchemyRecipe);

                    java.lang.reflect.Method getInputsMethod = alchemyRecipe.getClass().getMethod("getInputs");
                    List<Ingredient> inputs = (List<Ingredient>) getInputsMethod.invoke(alchemyRecipe);

                    java.lang.reflect.Method getResultItemMethod = alchemyRecipe.getClass().getMethod("getResultItem");
                    ItemStack output = (ItemStack) getResultItemMethod.invoke(alchemyRecipe);

                    if (output == null || output.isEmpty()) {
                        continue;
                    }

                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "embers_alchemy_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    List<Ingredient> convertedInputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();

                    // 合并相同的输入物品
                    java.util.Map<net.minecraft.world.item.Item, Long> itemCountMap = new java.util.HashMap<>();
                    
                    // 添加中心物品
                    ItemStack[] tabletItems = tablet.getItems();
                    if (tabletItems != null && tabletItems.length > 0) {
                        ItemStack itemStack = tabletItems[0];
                        net.minecraft.world.item.Item item = itemStack.getItem();
                        long count = itemStack.getCount();
                        itemCountMap.put(item, itemCountMap.getOrDefault(item, 0L) + count);
                    }
                    
                    // 添加其他输入物品
                    for (Ingredient ingredient : inputs) {
                        ItemStack[] items = ingredient.getItems();
                        if (items != null && items.length > 0) {
                            ItemStack itemStack = items[0];
                            net.minecraft.world.item.Item item = itemStack.getItem();
                            long count = itemStack.getCount();
                            itemCountMap.put(item, itemCountMap.getOrDefault(item, 0L) + count);
                        }
                    }
                    
                    // 构建合并后的输入列表
                    for (java.util.Map.Entry<net.minecraft.world.item.Item, Long> entry : itemCountMap.entrySet()) {
                        net.minecraft.world.item.Item item = entry.getKey();
                        long count = entry.getValue();
                        convertedInputIngredients.add(Ingredient.of(item));
                        inputItemCounts.add(count * 64);
                    }

                    List<ItemStack> outputItems = new ArrayList<>();
                    ItemStack output64 = output.copy();
                    output64.setCount(output.getCount() * 64);
                    outputItems.add(output64);

                    List<FluidStack> inputFluids = new ArrayList<>();
                    List<FluidStack> outputFluids = new ArrayList<>();

                    int energy = 2000;
                    int processTime = 40;

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

                    ResourceLocation alchemyTabletId = ResourceLocation.fromNamespaceAndPath("embers", "alchemy_tablet");
                    net.minecraft.world.item.Item alchemyTablet = ForgeRegistries.ITEMS.getValue(alchemyTabletId);
                    if (alchemyTablet == null) {
                        // 尝试使用物品形式的ID
                        alchemyTabletId = ResourceLocation.fromNamespaceAndPath("embers", "alchemy_tablet_item");
                        alchemyTablet = ForgeRegistries.ITEMS.getValue(alchemyTabletId);
                    }
                    if (alchemyTablet == null) {
                        throw new RuntimeException("Could not find item: " + alchemyTabletId);
                    }
                    Ingredient mold = Ingredient.of(alchemyTablet);

                    AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                            newId,
                            convertedInputIngredients,
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
            e.printStackTrace();
            System.err.println("Failed to convert embers alchemy recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将余烬地质分离器(Geologic Separator)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertGeoSeparatorRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> iMeltingRecipeClass = Class.forName("com.rekindled.embers.recipe.IMeltingRecipe");
            Class<?> registryManagerClass = Class.forName("com.rekindled.embers.RegistryManager");
            Field meltingRecipeTypeField = registryManagerClass.getField("MELTING");
            Object meltingRecipeTypeObject = meltingRecipeTypeField.get(null);
            
            // 获取配方类型的get方法
            Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
            Method getMethod = registryObjectClass.getMethod("get");
            net.minecraft.world.item.crafting.RecipeType<?> recipeType = (net.minecraft.world.item.crafting.RecipeType<?>) getMethod.invoke(meltingRecipeTypeObject);
            
            // 使用反射调用RecipeManager.getAllRecipesFor方法
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (iMeltingRecipeClass.isInstance(recipe)) {
                    Object meltingRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    // 使用getter方法获取配方数据
                    java.lang.reflect.Method getDisplayInputMethod = meltingRecipe.getClass().getMethod("getDisplayInput");
                    Ingredient input = (Ingredient) getDisplayInputMethod.invoke(meltingRecipe);

                    java.lang.reflect.Method getDisplayOutputMethod = meltingRecipe.getClass().getMethod("getDisplayOutput");
                    FluidStack outputFluid = (FluidStack) getDisplayOutputMethod.invoke(meltingRecipe);

                    // 获取bonus fluid
                    java.lang.reflect.Method getBonusMethod = meltingRecipe.getClass().getMethod("getBonus");
                    FluidStack bonusFluid = (FluidStack) getBonusMethod.invoke(meltingRecipe);

                    if (outputFluid == null || outputFluid.isEmpty()) {
                        continue;
                    }

                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "embers_geo_separator_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();

                    // 处理输入物品
                    ItemStack[] items = input.getItems();
                    if (items != null && items.length > 0) {
                        ItemStack itemStack = items[0];
                        inputIngredients.add(Ingredient.of(itemStack.getItem()));
                        inputItemCounts.add(itemStack.getCount() * 64L);
                    }

                    List<ItemStack> outputItems = new ArrayList<>();
                    List<FluidStack> inputFluids = new ArrayList<>();
                    List<FluidStack> outputFluids = new ArrayList<>();
                    outputFluids.add(new FluidStack(outputFluid.getFluid(), outputFluid.getAmount() * 64));
                    
                    // 添加bonus fluid，如果存在且不为空
                    if (bonusFluid != null && !bonusFluid.isEmpty()) {
                        outputFluids.add(new FluidStack(bonusFluid.getFluid(), bonusFluid.getAmount() * 64));
                    }

                    int energy = 2000;
                    int processTime = 40;

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

                    ResourceLocation geoSeparatorId = ResourceLocation.fromNamespaceAndPath("embers", "geologic_separator");
                    net.minecraft.world.item.Item geoSeparator = ForgeRegistries.ITEMS.getValue(geoSeparatorId);
                    if (geoSeparator == null) {
                        // 尝试使用物品形式的ID
                        geoSeparatorId = ResourceLocation.fromNamespaceAndPath("embers", "geologic_separator_item");
                        geoSeparator = ForgeRegistries.ITEMS.getValue(geoSeparatorId);
                    }
                    if (geoSeparator == null) {
                        throw new RuntimeException("Could not find item: " + geoSeparatorId);
                    }
                    Ingredient mold = Ingredient.of(geoSeparator);

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
            e.printStackTrace();
            System.err.println("Failed to convert embers geo separator recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将余烬熔炼炉(Melter)配方转换为高级合金炉配方
     * 熔炼炉是地质分离器的基础方块，用于处理没有副产物的基础熔炼配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertMelterRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> iMeltingRecipeClass = Class.forName("com.rekindled.embers.recipe.IMeltingRecipe");
            Class<?> registryManagerClass = Class.forName("com.rekindled.embers.RegistryManager");
            Field meltingRecipeTypeField = registryManagerClass.getField("MELTING");
            Object meltingRecipeTypeObject = meltingRecipeTypeField.get(null);
            
            // 获取配方类型的get方法
            Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
            Method getMethod = registryObjectClass.getMethod("get");
            net.minecraft.world.item.crafting.RecipeType<?> recipeType = (net.minecraft.world.item.crafting.RecipeType<?>) getMethod.invoke(meltingRecipeTypeObject);
            
            // 使用反射调用RecipeManager.getAllRecipesFor方法
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (iMeltingRecipeClass.isInstance(recipe)) {
                    Object meltingRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    // 使用getter方法获取配方数据
                    java.lang.reflect.Method getDisplayInputMethod = meltingRecipe.getClass().getMethod("getDisplayInput");
                    Ingredient input = (Ingredient) getDisplayInputMethod.invoke(meltingRecipe);

                    java.lang.reflect.Method getDisplayOutputMethod = meltingRecipe.getClass().getMethod("getDisplayOutput");
                    FluidStack outputFluid = (FluidStack) getDisplayOutputMethod.invoke(meltingRecipe);

                    // 获取bonus fluid，用于判断是否为基础熔炼配方
                    java.lang.reflect.Method getBonusMethod = meltingRecipe.getClass().getMethod("getBonus");
                    FluidStack bonusFluid = (FluidStack) getBonusMethod.invoke(meltingRecipe);

                    // 只有没有副产物的配方才由熔炼炉处理
                    if (outputFluid != null && !outputFluid.isEmpty() && (bonusFluid == null || bonusFluid.isEmpty())) {
                        ResourceLocation newId = new ResourceLocation(
                                "useless_mod",
                                "embers_melter_" + originalId.getNamespace() + "_" + originalId.getPath()
                        );

                        List<Ingredient> inputIngredients = new ArrayList<>();
                        List<Long> inputItemCounts = new ArrayList<>();

                        // 处理输入物品
                        ItemStack[] items = input.getItems();
                        if (items != null && items.length > 0) {
                            ItemStack itemStack = items[0];
                            inputIngredients.add(Ingredient.of(itemStack.getItem()));
                            inputItemCounts.add(itemStack.getCount() * 64L);
                        }

                        List<ItemStack> outputItems = new ArrayList<>();
                        List<FluidStack> inputFluids = new ArrayList<>();
                        List<FluidStack> outputFluids = new ArrayList<>();
                        outputFluids.add(new FluidStack(outputFluid.getFluid(), outputFluid.getAmount() * 64));

                        int energy = 2000;
                        int processTime = 40;

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

                        // 尝试不同的熔炼炉物品ID，包括正确的melter
                        net.minecraft.world.item.Item melter = null;
                        ResourceLocation[] melterIds = {
                                ResourceLocation.fromNamespaceAndPath("embers", "melter"),
                                ResourceLocation.fromNamespaceAndPath("embers", "smelting_furnace")
                        };
                        
                        for (ResourceLocation melterId : melterIds) {
                            melter = ForgeRegistries.ITEMS.getValue(melterId);
                            if (melter != null) {
                                break;
                            }
                        }
                        
                        if (melter == null) {
                            throw new RuntimeException("Could not find embers melter item");
                        }
                        
                        Ingredient mold = Ingredient.of(melter);

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
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to convert embers melter recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将余烬压印锤(Stamper)配方转换为高级合金炉配方
     * 压印锤配方的模具(stamp)将作为转化后配方的模具(mold)
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertStampingRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            // 加载必要的类
            Class<?> iStampingRecipeClass = Class.forName("com.rekindled.embers.recipe.IStampingRecipe");
            Class<?> registryManagerClass = Class.forName("com.rekindled.embers.RegistryManager");
            Class<?> fluidIngredientClass = Class.forName("com.rekindled.embers.recipe.FluidIngredient");
            
            // 获取STAMPING RecipeType
            Field stampingRecipeTypeField = registryManagerClass.getField("STAMPING");
            Object stampingRecipeTypeObject = stampingRecipeTypeField.get(null);
            
            // 获取配方类型的get方法
            Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
            Method getMethod = registryObjectClass.getMethod("get");
            net.minecraft.world.item.crafting.RecipeType<?> recipeType = (net.minecraft.world.item.crafting.RecipeType<?>) getMethod.invoke(stampingRecipeTypeObject);
            
            // 使用反射调用RecipeManager.getAllRecipesFor方法
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (iStampingRecipeClass.isInstance(recipe)) {
                    Object stampingRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    // 使用getter方法获取配方数据
                    java.lang.reflect.Method getDisplayInputMethod = stampingRecipe.getClass().getMethod("getDisplayInput");
                    Ingredient input = (Ingredient) getDisplayInputMethod.invoke(stampingRecipe);

                    java.lang.reflect.Method getDisplayInputFluidMethod = stampingRecipe.getClass().getMethod("getDisplayInputFluid");
                    Object fluidIngredient = getDisplayInputFluidMethod.invoke(stampingRecipe);

                    java.lang.reflect.Method getDisplayStampMethod = stampingRecipe.getClass().getMethod("getDisplayStamp");
                    Ingredient stamp = (Ingredient) getDisplayStampMethod.invoke(stampingRecipe);

                    java.lang.reflect.Method getResultItemMethod = stampingRecipe.getClass().getMethod("getResultItem");
                    ItemStack output = (ItemStack) getResultItemMethod.invoke(stampingRecipe);

                    if (output == null || output.isEmpty()) {
                        continue;
                    }

                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "embers_stamping_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();
                    List<FluidStack> inputFluids = new ArrayList<>();

                    // 处理输入物品
                    ItemStack[] items = input.getItems();
                    if (items != null && items.length > 0) {
                        ItemStack itemStack = items[0];
                        inputIngredients.add(Ingredient.of(itemStack.getItem()));
                        inputItemCounts.add(itemStack.getCount() * 64L);
                    }

                    // 处理输入流体
                    if (fluidIngredient != null) {
                        try {
                            // 获取FluidIngredient的流体列表
                            java.lang.reflect.Method getFluidsMethod = fluidIngredientClass.getMethod("getFluids");
                            List<FluidStack> fluids = (List<FluidStack>) getFluidsMethod.invoke(fluidIngredient);
                            if (fluids != null && !fluids.isEmpty()) {
                                // 只添加第一个FluidStack，与物品Ingredient类似，相同tag的流体应该任选其一即可
                                FluidStack fluidStack = fluids.get(0);
                                inputFluids.add(new FluidStack(fluidStack.getFluid(), fluidStack.getAmount() * 64));
                            }
                        } catch (Exception e) {
                            // 如果无法获取流体，尝试另一种方式
                            try {
                                // 尝试获取所有流体，包括流动流体
                                java.lang.reflect.Method getAllFluidsMethod = fluidIngredientClass.getMethod("getAllFluids");
                                List<FluidStack> allFluids = (List<FluidStack>) getAllFluidsMethod.invoke(fluidIngredient);
                                if (allFluids != null && !allFluids.isEmpty()) {
                                    // 只添加第一个FluidStack，与物品Ingredient类似，相同tag的流体应该任选其一即可
                                    FluidStack fluidStack = allFluids.get(0);
                                    inputFluids.add(new FluidStack(fluidStack.getFluid(), fluidStack.getAmount() * 64));
                                }
                            } catch (Exception ex) {
                                // 如果仍无法获取流体，跳过流体处理
                            }
                        }
                    }

                    List<ItemStack> outputItems = new ArrayList<>();
                    ItemStack output64 = output.copy();
                    output64.setCount(output.getCount() * 64);
                    outputItems.add(output64);

                    List<FluidStack> outputFluids = new ArrayList<>();

                    int energy = 2000;
                    int processTime = 40;

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

                    // 使用原配方的stamp作为mold
                    Ingredient mold = stamp;

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
            e.printStackTrace();
            System.err.println("Failed to convert embers stamping recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将余烬混合离心器(Mixer Centrifuge)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertMixingRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            // 加载必要的类
            Class<?> iMixingRecipeClass = Class.forName("com.rekindled.embers.recipe.IMixingRecipe");
            Class<?> registryManagerClass = Class.forName("com.rekindled.embers.RegistryManager");
            Class<?> fluidIngredientClass = Class.forName("com.rekindled.embers.recipe.FluidIngredient");
            
            // 获取MIXING RecipeType
            Field mixingRecipeTypeField = registryManagerClass.getField("MIXING");
            Object mixingRecipeTypeObject = mixingRecipeTypeField.get(null);
            
            // 获取配方类型的get方法
            Class<?> registryObjectClass = Class.forName("net.minecraftforge.registries.RegistryObject");
            Method getMethod = registryObjectClass.getMethod("get");
            net.minecraft.world.item.crafting.RecipeType<?> recipeType = (net.minecraft.world.item.crafting.RecipeType<?>) getMethod.invoke(mixingRecipeTypeObject);
            
            // 使用反射调用RecipeManager.getAllRecipesFor方法
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (iMixingRecipeClass.isInstance(recipe)) {
                    Object mixingRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    // 仿照JEI集成的做法，获取输入和输出
                    List<FluidStack> inputFluids = new ArrayList<>();
                    List<FluidStack> outputFluids = new ArrayList<>();

                    // 1. 获取配方的输入流体 - 直接访问inputs字段，它是一个ArrayList<FluidIngredient>
                    try {
                        // 获取配方的inputs字段
                        java.lang.reflect.Field inputsField = mixingRecipe.getClass().getDeclaredField("inputs");
                        inputsField.setAccessible(true);
                        ArrayList<?> inputs = (ArrayList<?>) inputsField.get(mixingRecipe);
                        
                        if (inputs != null && !inputs.isEmpty()) {
                            // 遍历每个FluidIngredient
                            for (Object input : inputs) {
                                if (input != null) {
                                    // 获取FluidIngredient的流体列表
                                    java.lang.reflect.Method getFluidsMethod = input.getClass().getMethod("getFluids");
                                    List<FluidStack> fluids = (List<FluidStack>) getFluidsMethod.invoke(input);
                                    if (fluids != null && !fluids.isEmpty()) {
                                        // 只添加第一个FluidStack到输入流体列表中
                                        // 与物品Ingredient类似，相同tag的流体应该任选其一即可
                                        FluidStack fluidStack = fluids.get(0);
                                        inputFluids.add(new FluidStack(fluidStack.getFluid(), fluidStack.getAmount() * 64));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("Failed to get input fluids: " + e.getMessage());
                    }

                    // 2. 获取输出流体 - 直接访问output字段，它是一个FluidOutput
                    try {
                        // 获取配方的output字段
                        java.lang.reflect.Field outputField = mixingRecipe.getClass().getDeclaredField("output");
                        outputField.setAccessible(true);
                        Object fluidOutput = outputField.get(mixingRecipe);
                        
                        if (fluidOutput != null) {
                            // 获取FluidOutput的流体Stack
                            java.lang.reflect.Method getFluidStackMethod = fluidOutput.getClass().getMethod("getFluidStack");
                            FluidStack outputFluid = (FluidStack) getFluidStackMethod.invoke(fluidOutput);
                            if (outputFluid != null && !outputFluid.isEmpty()) {
                                outputFluids.add(new FluidStack(outputFluid.getFluid(), outputFluid.getAmount() * 64));
                            }
                        }
                    } catch (Exception e) {
                        // 如果直接访问output字段失败，尝试使用getDisplayOutput()方法
                        try {
                            java.lang.reflect.Method getDisplayOutputMethod = mixingRecipe.getClass().getMethod("getDisplayOutput");
                            FluidStack displayOutput = (FluidStack) getDisplayOutputMethod.invoke(mixingRecipe);
                            if (displayOutput != null && !displayOutput.isEmpty()) {
                                outputFluids.add(new FluidStack(displayOutput.getFluid(), displayOutput.getAmount() * 64));
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            System.err.println("Failed to get output fluid: " + ex.getMessage());
                        }
                    }

                    // 只有当有输出时才创建配方
                    if (outputFluids != null && !outputFluids.isEmpty()) {
                        ResourceLocation newId = new ResourceLocation(
                                "useless_mod",
                                "embers_mixer_centrifuge_" + originalId.getNamespace() + "_" + originalId.getPath()
                        );

                        List<Ingredient> inputIngredients = new ArrayList<>();
                        List<Long> inputItemCounts = new ArrayList<>();
                        // 创建空的物品输出列表，因为混合离心器只输出流体
                        List<ItemStack> outputItems = new ArrayList<>();

                        int energy = 2000;
                        int processTime = 40;

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

                        // 获取混合离心器物品作为模具
                        ResourceLocation mixerCentrifugeId = ResourceLocation.fromNamespaceAndPath("embers", "mixer_centrifuge");
                        net.minecraft.world.item.Item mixerCentrifuge = ForgeRegistries.ITEMS.getValue(mixerCentrifugeId);
                        if (mixerCentrifuge == null) {
                            // 尝试使用物品形式的ID
                            mixerCentrifugeId = ResourceLocation.fromNamespaceAndPath("embers", "mixer_centrifuge_item");
                            mixerCentrifuge = ForgeRegistries.ITEMS.getValue(mixerCentrifugeId);
                        }
                        if (mixerCentrifuge == null) {
                            throw new RuntimeException("Could not find item: " + mixerCentrifugeId);
                        }
                        Ingredient mold = Ingredient.of(mixerCentrifuge);

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
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to convert embers mixing recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }
}