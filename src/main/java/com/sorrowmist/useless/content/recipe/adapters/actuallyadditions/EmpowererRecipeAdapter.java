package com.sorrowmist.useless.content.recipe.adapters.actuallyadditions;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.actuallyadditions.mod.blocks.ActuallyBlocks;
import de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Actually Additions 充能台配方适配器
 * <p>
 * 将充能台配方转换为高级熔炉配方
 * 充能台使用1个基础物品 + 4个辅助物品来产生输出
 * 注意：会合并相同的输入物品
 */
public class EmpowererRecipeAdapter implements IRecipeAdapter<EmpowererRecipe> {

    @Override
    public Class<EmpowererRecipe> getRecipeClass() {
        return EmpowererRecipe.class;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<EmpowererRecipe> holder, Level level) {
        if (holder == null) return null;

        EmpowererRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();
        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 获取所有输入并合并相同物品
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();

        // 基础输入
        Ingredient baseInput = originalRecipe.getInput();
        if (baseInput != null && baseInput.getItems().length > 0) {
            mergeIngredient(ingredientCounts, baseInput, 1L);
        }

        // 4个辅助物品（modifiers）
        NonNullList<Ingredient> modifiers = NonNullList.create();
        modifiers.add(originalRecipe.getStandOne());
        modifiers.add(originalRecipe.getStandTwo());
        modifiers.add(originalRecipe.getStandThree());
        modifiers.add(originalRecipe.getStandFour());

        for (Ingredient modifier : modifiers) {
            if (modifier != null && modifier.getItems().length > 0) {
                mergeIngredient(ingredientCounts, modifier, 1L);
            }
        }

        if (ingredientCounts.isEmpty()) {
            return null;
        }

        // 转换为 CountedIngredient 列表
        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        // 获取输出物品
        ItemStack output = originalRecipe.getOutput();
        if (output.isEmpty()) {
            return null;
        }

        // 获取配方能量和处理时间
        int energyPerStand = originalRecipe.getEnergyPerStand();
        int totalEnergy = energyPerStand * 4; // 4个充能台
        int time = originalRecipe.getTime();

        // 创建充能台模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(ActuallyBlocks.EMPOWERER.get()));

        return new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),           // 无流体输入
                List.of(output),     // 输出物品
                List.of(),           // 无流体输出
                totalEnergy,
                time,
                Ingredient.EMPTY,    // 无催化剂
                0,
                moldIngredient,      // 充能台作为模具
                AlloyFurnaceMode.NORMAL
        );
    }

    /**
     * 合并相同物品到计数映射中
     * 使用物品的标签系统来识别相同物品
     */
    private void mergeIngredient(Map<Ingredient, Long> ingredientCounts, Ingredient ingredient, long count) {
        // 检查是否已存在相同的 Ingredient
        boolean found = false;
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            if (areIngredientsEqual(entry.getKey(), ingredient)) {
                ingredientCounts.put(entry.getKey(), entry.getValue() + count);
                found = true;
                break;
            }
        }

        if (!found) {
            ingredientCounts.put(ingredient, count);
        }
    }

    /**
     * 检查两个 Ingredient 是否代表相同的物品
     * 支持标签系统的比较
     */
    private boolean areIngredientsEqual(Ingredient a, Ingredient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        // 比较物品栈列表
        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();

        if (stacksA.length != stacksB.length) {
            return false;
        }

        // 检查每个物品是否在两个列表中都存在
        for (ItemStack stackA : stacksA) {
            boolean found = false;
            for (ItemStack stackB : stacksB) {
                if (ItemStack.isSameItem(stackA, stackB) && ItemStack.isSameItemSameComponents(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<EmpowererRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (inputs.isEmpty() || level == null) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<EmpowererRecipe>> recipes = recipeManager.getAllRecipesFor(de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes.Types.EMPOWERING.get());

        for (RecipeHolder<EmpowererRecipe> holder : recipes) {
            EmpowererRecipe recipe = holder.value();
            // 检查输入是否足够匹配配方
            if (matchesRecipeInputs(recipe, inputs)) {
                return holder;
            }
        }
        return null;
    }

    /**
     * 检查输入是否匹配配方
     * <p>
     * 注意：这里需要将输入按照与 convert 方法相同的逻辑合并后再匹配
     */
    private boolean matchesRecipeInputs(EmpowererRecipe recipe, List<ItemStack> inputs) {
        if (inputs.isEmpty()) {
            return false;
        }

        // 统计输入物品（按 Ingredient 合并）
        Map<Ingredient, Long> inputCounts = new LinkedHashMap<>();
        for (ItemStack input : inputs) {
            if (input.isEmpty()) continue;

            // 查找或创建对应的 Ingredient
            boolean found = false;
            for (Map.Entry<Ingredient, Long> entry : inputCounts.entrySet()) {
                if (entry.getKey().test(input)) {
                    inputCounts.put(entry.getKey(), entry.getValue() + input.getCount());
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 创建新的 Ingredient 代表这个物品
                inputCounts.put(Ingredient.of(input), (long) input.getCount());
            }
        }

        // 统计配方所需的物品（按 Ingredient 合并）
        Map<Ingredient, Long> recipeCounts = new LinkedHashMap<>();

        // 基础输入
        Ingredient baseInput = recipe.getInput();
        if (baseInput != null && baseInput.getItems().length > 0) {
            mergeIngredient(recipeCounts, baseInput, 1L);
        }

        // 4个辅助物品
        mergeIngredient(recipeCounts, recipe.getStandOne(), 1L);
        mergeIngredient(recipeCounts, recipe.getStandTwo(), 1L);
        mergeIngredient(recipeCounts, recipe.getStandThree(), 1L);
        mergeIngredient(recipeCounts, recipe.getStandFour(), 1L);

        // 检查输入是否满足配方需求
        for (Map.Entry<Ingredient, Long> recipeEntry : recipeCounts.entrySet()) {
            long requiredCount = recipeEntry.getValue();
            long foundCount = 0;

            for (Map.Entry<Ingredient, Long> inputEntry : inputCounts.entrySet()) {
                if (areIngredientsEqual(recipeEntry.getKey(), inputEntry.getKey())) {
                    foundCount += inputEntry.getValue();
                }
            }

            if (foundCount < requiredCount) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getPriority() {
        return 10; // 与激光配方相同的高优先级
    }
}
