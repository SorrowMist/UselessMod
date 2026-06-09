package com.sorrowmist.useless.content.recipe.adapters.ae2lt;

import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 Lightning Tech 闪电装配室配方适配器
 * <p>
 * 将闪电装配室配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 所有输入物品 → 普通输入（合并相同物品）
 * - 产物 → 输出
 * - ae2lt:lightning_assembly_chamber → 模具（不消耗）
 */
public class LightningAssemblyRecipeAdapter implements IRecipeAdapter<LightningAssemblyRecipe> {

    private static final int BASE_PROCESS_TIME = 80;

    @Override
    public Class<LightningAssemblyRecipe> getRecipeClass() {
        return LightningAssemblyRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<LightningAssemblyRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        LightningAssemblyRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        List<LightningSimulationIngredient> inputs = recipe.inputs();
        ItemStack output = recipe.getResultStack();

        if (output.isEmpty() || inputs.isEmpty()) {
            return result;
        }

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (LightningSimulationIngredient input : inputs) {
            mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        AELightningIngredientHelper.addLightningIngredient(countedIngredients, recipe.lightningTier(), recipe.lightningCost());

        int processTime = BASE_PROCESS_TIME + inputs.size() * 15;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "lightning_assembly_chamber")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                4000,
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
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<LightningAssemblyRecipe> holder, Level level) {
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
    public RecipeHolder<LightningAssemblyRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<LightningAssemblyRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ae2lt".equals(moldId.getNamespace()) || !"lightning_assembly_chamber".equals(moldId.getPath())) {
                return null;
            }
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<LightningAssemblyRecipe>> recipes = (List<RecipeHolder<LightningAssemblyRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get()
        );

        for (RecipeHolder<LightningAssemblyRecipe> holder : recipes) {
            LightningAssemblyRecipe recipe = holder.value();

            List<LightningSimulationIngredient> recipeInputs = recipe.inputs();
            ItemStack output = recipe.getResultStack();

            if (output.isEmpty() || recipeInputs.isEmpty()) continue;

            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (LightningSimulationIngredient input : recipeInputs) {
                mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            if (matchesCountedIngredients(inputs, requiredCounts)
                    && AELightningIngredientHelper.matchesLightning(inputs, recipe.lightningTier(), recipe.lightningCost())) {
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
        return 56;
    }
}
