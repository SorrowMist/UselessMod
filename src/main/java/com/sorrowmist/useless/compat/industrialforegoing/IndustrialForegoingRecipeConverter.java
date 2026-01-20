package com.sorrowmist.useless.compat.industrialforegoing;

import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.registry.ModIngots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 工业先锋配方转换器，用于将工业先锋系列模组的配方转换为高级合金炉配方
 */
public class IndustrialForegoingRecipeConverter {

    /**
     * 将工业先锋化学溶解室(DissolutionChamber)配方转换为高级合金炉配方
     *
     * @param recipeManager 游戏配方管理器
     * @return 转换后的高级合金炉配方列表
     */
    public static List<AdvancedAlloyFurnaceRecipe> convertDissolutionChamberRecipes(RecipeManager recipeManager) {
        List<AdvancedAlloyFurnaceRecipe> convertedRecipes = new ArrayList<>();

        try {
            Class<?> dissolutionChamberRecipeClass = Class.forName("com.buuz135.industrial.recipe.DissolutionChamberRecipe");

            Collection<? extends net.minecraft.world.item.crafting.Recipe<?>> allRecipes = recipeManager.getRecipes();

            for (net.minecraft.world.item.crafting.Recipe<?> recipe : allRecipes) {
                if (dissolutionChamberRecipeClass.isInstance(recipe)) {
                    Object dissolutionRecipe = recipe;

                    ResourceLocation originalId = recipe.getId();

                    java.lang.reflect.Field inputField = dissolutionChamberRecipeClass.getField("input");
                    Ingredient.Value[] inputValues = (Ingredient.Value[]) inputField.get(dissolutionRecipe);

                    java.lang.reflect.Field inputFluidField = dissolutionChamberRecipeClass.getField("inputFluid");
                    FluidStack inputFluid = (FluidStack) inputFluidField.get(dissolutionRecipe);

                    java.lang.reflect.Field processingTimeField = dissolutionChamberRecipeClass.getField("processingTime");
                    int processingTime = (int) processingTimeField.get(dissolutionRecipe);

                    java.lang.reflect.Field outputField = dissolutionChamberRecipeClass.getField("output");
                    ItemStack outputItem = (ItemStack) outputField.get(dissolutionRecipe);

                    java.lang.reflect.Field outputFluidField = dissolutionChamberRecipeClass.getField("outputFluid");
                    FluidStack outputFluid = (FluidStack) outputFluidField.get(dissolutionRecipe);

                    if (outputItem == null || outputItem.isEmpty()) {
                        continue;
                    }

                    ResourceLocation newId = new ResourceLocation(
                            "useless_mod",
                            "if_dissolution_" + originalId.getNamespace() + "_" + originalId.getPath()
                    );

                    List<Ingredient> inputIngredients = new ArrayList<>();
                    List<Long> inputItemCounts = new ArrayList<>();

                    java.util.Map<net.minecraft.world.item.Item, Long> itemCountMap = new java.util.HashMap<>();
                    
                    for (Ingredient.Value inputValue : inputValues) {
                        ItemStack[] items = inputValue.getItems().toArray(new ItemStack[0]);
                        if (items != null && items.length > 0) {
                            ItemStack item = items[0];
                            net.minecraft.world.item.Item itemType = item.getItem();
                            long count = item.getCount();
                            
                            itemCountMap.put(itemType, itemCountMap.getOrDefault(itemType, 0L) + count);
                        }
                    }
                    
                    for (java.util.Map.Entry<net.minecraft.world.item.Item, Long> entry : itemCountMap.entrySet()) {
                        net.minecraft.world.item.Item item = entry.getKey();
                        long count = entry.getValue();
                        inputIngredients.add(Ingredient.of(item));
                        inputItemCounts.add(count * 16);
                    }

                    List<ItemStack> outputItems = new ArrayList<>();
                    ItemStack output64 = outputItem.copy();
                    output64.setCount(outputItem.getCount() * 16);
                    outputItems.add(output64);

                    List<FluidStack> inputFluids = new ArrayList<>();
                    if (inputFluid != null && !inputFluid.isEmpty()) {
                        FluidStack scaledInputFluid = inputFluid.copy();
                        scaledInputFluid.setAmount(inputFluid.getAmount() * 16);
                        inputFluids.add(scaledInputFluid);
                    }

                    List<FluidStack> outputFluids = new ArrayList<>();
                    if (outputFluid != null && !outputFluid.isEmpty()) {
                        FluidStack scaledOutputFluid = outputFluid.copy();
                        scaledOutputFluid.setAmount(outputFluid.getAmount() * 16);
                        outputFluids.add(scaledOutputFluid);
                    }

                    int energy = processingTime * 60;
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

                    ResourceLocation dissolutionChamberId = ResourceLocation.fromNamespaceAndPath("industrialforegoing", "dissolution_chamber");
                    net.minecraft.world.item.Item dissolutionChamber = ForgeRegistries.ITEMS.getValue(dissolutionChamberId);
                    if (dissolutionChamber == null) {
                        throw new RuntimeException("Could not find item: " + dissolutionChamberId);
                    }
                    Ingredient mold = Ingredient.of(dissolutionChamber);

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
            System.err.println("Failed to convert dissolution chamber recipes: " + e.getMessage());
        }

        return convertedRecipes;
    }
}
