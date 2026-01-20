package com.sorrowmist.useless.compat.bloodmagic;

import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.ModIngots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 血魔法配方转换器，用于将血魔法系列模组的配方转换为高级合金炉配方
 */
public class BloodMagicRecipeConverter {

    /**
     * 将血魔法血之祭坛(BloodAltar)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertBloodAltarRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> bloodMagicRecipeTypeClass = Class.forName("wayoftime.bloodmagic.common.recipe.BloodMagicRecipeType");
            Object altarRecipeType = bloodMagicRecipeTypeClass.getField("ALTAR").get(null);
            
            Class<?> wrappedRegistryObjectClass = Class.forName("wayoftime.bloodmagic.common.registration.WrappedRegistryObject");
            java.lang.reflect.Method getMethod = wrappedRegistryObjectClass.getMethod("get");
            Object recipeType = getMethod.invoke(altarRecipeType);
            
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                Object bloodAltarRecipe = recipe;

                ResourceLocation originalId = recipe.getId();

                // 使用getter方法获取配方数据
                java.lang.reflect.Method getInputMethod = recipe.getClass().getMethod("getInput");
                Ingredient input = (Ingredient) getInputMethod.invoke(bloodAltarRecipe);

                java.lang.reflect.Method getOutputMethod = recipe.getClass().getMethod("getOutput");
                ItemStack output = (ItemStack) getOutputMethod.invoke(bloodAltarRecipe);

                java.lang.reflect.Method getMinimumTierMethod = recipe.getClass().getMethod("getMinimumTier");
                int minimumTier = (int) getMinimumTierMethod.invoke(bloodAltarRecipe);

                java.lang.reflect.Method getSyphonMethod = recipe.getClass().getMethod("getSyphon");
                int syphon = (int) getSyphonMethod.invoke(bloodAltarRecipe);

                if (output == null || output.isEmpty()) {
                    continue;
                }

                ResourceLocation newId = new ResourceLocation(
                        "useless_mod",
                        "bm_altar_" + originalId.getNamespace() + "_" + originalId.getPath()
                );

                List<Ingredient> inputIngredients = new ArrayList<>();
                List<Long> inputItemCounts = new ArrayList<>();

                // 合并相同的输入物品
                java.util.Map<net.minecraft.world.item.Item, Long> itemCountMap = new java.util.HashMap<>();
                
                ItemStack[] inputItems = input.getItems();
                if (inputItems != null && inputItems.length > 0) {
                    ItemStack itemStack = inputItems[0];
                    net.minecraft.world.item.Item item = itemStack.getItem();
                    long count = itemStack.getCount();
                    
                    itemCountMap.put(item, itemCountMap.getOrDefault(item, 0L) + count);
                }
                
                // 构建合并后的输入列表
                for (java.util.Map.Entry<net.minecraft.world.item.Item, Long> entry : itemCountMap.entrySet()) {
                    net.minecraft.world.item.Item item = entry.getKey();
                    long count = entry.getValue();
                    inputIngredients.add(Ingredient.of(item));
                    inputItemCounts.add(count * 64);
                }

                List<ItemStack> outputItems = new ArrayList<>();
                ItemStack output16 = output.copy();
                output16.setCount(output.getCount() * 64);
                outputItems.add(output16);

                List<FluidStack> inputFluids = new ArrayList<>();
                List<FluidStack> outputFluids = new ArrayList<>();

                int energy = syphon * 2;
                int processTime = 40 ;

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

                ResourceLocation bloodAltarId = ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar");
                net.minecraft.world.item.Item bloodAltar = ForgeRegistries.ITEMS.getValue(bloodAltarId);
                if (bloodAltar == null) {
                    throw new RuntimeException("Could not find item: " + bloodAltarId);
                }
                Ingredient mold = Ingredient.of(bloodAltar);

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
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to convert blood altar recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将血魔法狱火熔炉(TartaricForge)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertTartaricForgeRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> bloodMagicRecipeTypeClass = Class.forName("wayoftime.bloodmagic.common.recipe.BloodMagicRecipeType");
            Object soulForgeRecipeType = bloodMagicRecipeTypeClass.getField("TARTARICFORGE").get(null);
            
            Class<?> wrappedRegistryObjectClass = Class.forName("wayoftime.bloodmagic.common.registration.WrappedRegistryObject");
            java.lang.reflect.Method getMethod = wrappedRegistryObjectClass.getMethod("get");
            Object recipeType = getMethod.invoke(soulForgeRecipeType);
            
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                Object soulForgeRecipe = recipe;

                ResourceLocation originalId = recipe.getId();

                // 使用getter方法获取配方数据
                java.lang.reflect.Method getInputMethod = recipe.getClass().getMethod("getInput");
                List<Ingredient> inputIngredients = (List<Ingredient>) getInputMethod.invoke(soulForgeRecipe);

                java.lang.reflect.Method getOutputMethod = recipe.getClass().getMethod("getOutput");
                ItemStack output = (ItemStack) getOutputMethod.invoke(soulForgeRecipe);

                if (output == null || output.isEmpty()) {
                    continue;
                }

                ResourceLocation newId = new ResourceLocation(
                        "useless_mod",
                        "bm_soulforge_" + originalId.getNamespace() + "_" + originalId.getPath()
                );

                List<Ingredient> convertedInputIngredients = new ArrayList<>();
                List<Long> inputItemCounts = new ArrayList<>();

                // 合并相同的输入物品
                java.util.Map<net.minecraft.world.item.Item, Long> itemCountMap = new java.util.HashMap<>();
                
                for (Ingredient ingredient : inputIngredients) {
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
                ItemStack output16 = output.copy();
                output16.setCount(output.getCount() * 64);
                outputItems.add(output16);

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

                ResourceLocation soulForgeId = ResourceLocation.fromNamespaceAndPath("bloodmagic", "soulforge");
                net.minecraft.world.item.Item soulForge = ForgeRegistries.ITEMS.getValue(soulForgeId);
                if (soulForge == null) {
                    throw new RuntimeException("Could not find item: " + soulForgeId);
                }
                Ingredient mold = Ingredient.of(soulForge);

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
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to convert soul forge recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }

    /**
     * 将血魔法炼金术桌(AlchemyTable)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertAlchemyTableRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> bloodMagicRecipeTypeClass = Class.forName("wayoftime.bloodmagic.common.recipe.BloodMagicRecipeType");
            Object alchemyTableRecipeType = bloodMagicRecipeTypeClass.getField("ALCHEMYTABLE").get(null);
            
            Class<?> wrappedRegistryObjectClass = Class.forName("wayoftime.bloodmagic.common.registration.WrappedRegistryObject");
            java.lang.reflect.Method getMethod = wrappedRegistryObjectClass.getMethod("get");
            Object recipeType = getMethod.invoke(alchemyTableRecipeType);
            
            java.lang.reflect.Method getAllRecipesForMethod = RecipeManager.class.getMethod("getAllRecipesFor", net.minecraft.world.item.crafting.RecipeType.class);
            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = (Collection<? extends net.minecraft.world.item.crafting.Recipe<?>>) getAllRecipesForMethod.invoke(recipeManager, recipeType);

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                Object alchemyTableRecipe = recipe;

                ResourceLocation originalId = recipe.getId();

                // 使用getter方法获取配方数据
                java.lang.reflect.Method getInputMethod = recipe.getClass().getMethod("getInput");
                List<Ingredient> inputIngredients = (List<Ingredient>) getInputMethod.invoke(alchemyTableRecipe);

                java.lang.reflect.Method getOutputMethod = recipe.getClass().getMethod("getOutput");
                ItemStack output = (ItemStack) getOutputMethod.invoke(alchemyTableRecipe);

                if (output == null || output.isEmpty()) {
                    continue;
                }

                ResourceLocation newId = new ResourceLocation(
                        "useless_mod",
                        "bm_alchemytable_" + originalId.getNamespace() + "_" + originalId.getPath()
                );

                List<Ingredient> convertedInputIngredients = new ArrayList<>();
                List<Long> inputItemCounts = new ArrayList<>();

                // 合并相同的输入物品
                java.util.Map<net.minecraft.world.item.Item, Long> itemCountMap = new java.util.HashMap<>();
                
                for (Ingredient ingredient : inputIngredients) {
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
                ItemStack output16 = output.copy();
                output16.setCount(output.getCount() * 64);
                outputItems.add(output16);

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

                ResourceLocation alchemyTableId = ResourceLocation.fromNamespaceAndPath("bloodmagic", "alchemytable");
                net.minecraft.world.item.Item alchemyTable = ForgeRegistries.ITEMS.getValue(alchemyTableId);
                if (alchemyTable == null) {
                    throw new RuntimeException("Could not find item: " + alchemyTableId);
                }
                Ingredient mold = Ingredient.of(alchemyTable);

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
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to convert alchemy table recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }
}
