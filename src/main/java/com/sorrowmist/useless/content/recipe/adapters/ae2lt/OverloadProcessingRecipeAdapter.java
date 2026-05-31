package com.sorrowmist.useless.content.recipe.adapters.ae2lt;

import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 Lightning Tech 过载处理工厂配方适配器
 * <p>
 * 将过载处理工厂配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 物品输入 → 普通输入（合并相同物品）
 * - 流体输入 → 流体输入
 * - 物品产物 → 输出
 * - 流体产物 → 流体输出
 * - ae2lt:overload_processing_factory → 模具（不消耗）
 */
public class OverloadProcessingRecipeAdapter implements IRecipeAdapter<OverloadProcessingRecipe> {

    private static final int BASE_PROCESS_TIME = 100;

    @Override
    public Class<OverloadProcessingRecipe> getRecipeClass() {
        return OverloadProcessingRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<OverloadProcessingRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        OverloadProcessingRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        List<OverloadProcessingIngredient> itemInputs = recipe.itemInputs();
        FluidStack fluidInput = recipe.fluidInput();
        List<ItemStack> itemResults = recipe.itemResults();
        FluidStack fluidResult = recipe.fluidResult();

        boolean hasItemInputs = !itemInputs.isEmpty();
        boolean hasFluidInput = !fluidInput.isEmpty();
        boolean hasItemOutputs = !itemResults.isEmpty();
        boolean hasFluidOutput = !fluidResult.isEmpty();

        if (!hasItemInputs && !hasFluidInput) {
            return result;
        }
        if (!hasItemOutputs && !hasFluidOutput) {
            return result;
        }

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (OverloadProcessingIngredient input : itemInputs) {
            mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<FluidStack> inputFluids = hasFluidInput ? List.of(fluidInput.copy()) : List.of();
        List<ItemStack> outputs = hasItemOutputs ? itemResults.stream().map(ItemStack::copy).toList() : List.of();
        List<FluidStack> outputFluids = hasFluidOutput ? List.of(fluidResult.copy()) : List.of();

        int processTime = BASE_PROCESS_TIME + itemInputs.size() * 20;
        long energy = recipe.totalEnergy();
        int scaledEnergy = energy > Integer.MAX_VALUE ? 10000 : (int) Math.max(energy, 1000);

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "overload_processing_factory")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                inputFluids,
                outputs,
                outputFluids,
                scaledEnergy,
                processTime,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<OverloadProcessingRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<OverloadProcessingRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<OverloadProcessingRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ae2lt".equals(moldId.getNamespace()) || !"overload_processing_factory".equals(moldId.getPath())) {
                return null;
            }
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<OverloadProcessingRecipe>> recipes = (List<RecipeHolder<OverloadProcessingRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get()
        );

        for (RecipeHolder<OverloadProcessingRecipe> holder : recipes) {
            OverloadProcessingRecipe recipe = holder.value();

            List<OverloadProcessingIngredient> recipeInputs = recipe.itemInputs();

            if (recipeInputs.isEmpty()) continue;

            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (OverloadProcessingIngredient input : recipeInputs) {
                mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            if (matchesCountedIngredients(inputs, requiredCounts)) {
                return holder;
            }
        }

        return null;
    }

    private boolean matchesCountedIngredients(List<ItemStack> inputs, Map<Ingredient, Long> requiredCounts) {
        Map<Ingredient, Long> inputMatchCounts = new LinkedHashMap<>();

        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;
            int stackCount = stack.getCount();

            for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
                if (entry.getKey().test(stack)) {
                    inputMatchCounts.merge(entry.getKey(), (long) stackCount, Long::sum);
                    break;
                }
            }
        }

        for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
            long required = entry.getValue();
            long actual = inputMatchCounts.getOrDefault(entry.getKey(), 0L);
            if (actual < required) return false;
        }
        return true;
    }

    private void mergeIngredient(Map<Ingredient, Long> ingredientCounts, Ingredient ingredient, long count) {
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            if (areIngredientsEqual(entry.getKey(), ingredient)) {
                ingredientCounts.put(entry.getKey(), entry.getValue() + count);
                return;
            }
        }
        ingredientCounts.put(ingredient, count);
    }

    private boolean areIngredientsEqual(Ingredient a, Ingredient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();

        if (stacksA.length != stacksB.length) return false;

        for (ItemStack stackA : stacksA) {
            boolean found = false;
            for (ItemStack stackB : stacksB) {
                if (ItemStack.isSameItem(stackA, stackB) && ItemStack.isSameItemSameComponents(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    @Override
    public int getPriority() {
        return 57;
    }
}
