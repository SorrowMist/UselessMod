package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
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
 * Ars Nouveau 附魔装置配方适配器
 * <p>
 * 将附魔装置配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 催化剂(reagent) → 普通输入（被消耗）
 * - 基座物品(pedestalItems) → 普通输入（被消耗）
 * - 产物(result) → 输出
 * - ars_nouveau:enchanting_apparatus → 模具（不消耗）
 * - 魔力消耗(sourceCost) → 能量消耗
 */
public class EnchantingApparatusRecipeAdapter implements IRecipeAdapter<EnchantingApparatusRecipe> {

    // 基础处理时间（ticks）
    private static final int BASE_PROCESS_TIME = 20;

    @Override
    public Class<EnchantingApparatusRecipe> getRecipeClass() {
        return EnchantingApparatusRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<EnchantingApparatusRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        EnchantingApparatusRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient reagent = originalRecipe.reagent();
        List<Ingredient> pedestalItems = originalRecipe.pedestalItems();
        ItemStack output = originalRecipe.result();
        int sourceCost = originalRecipe.sourceCost();

        if (output.isEmpty()) {
            return result;
        }

        // 合并试剂和基座物品（相同物品合并计数）
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();

        if (!reagent.isEmpty()) {
            mergeIngredient(ingredientCounts, reagent, 1L);
        }

        for (Ingredient pedestalIngredient : pedestalItems) {
            if (!pedestalIngredient.isEmpty()) {
                mergeIngredient(ingredientCounts, pedestalIngredient, 1L);
            }
        }

        if (ingredientCounts.isEmpty()) {
            return result;
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        // 魔力消耗转换为能量（1 source = 10 FE）
        int energy = Math.max(sourceCost * 10, 1000);
        int processTime = BASE_PROCESS_TIME + pedestalItems.size() * 20;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        // 使用附魔装置作为模具
        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "enchanting_apparatus")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                energy,
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
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<EnchantingApparatusRecipe> holder, Level level) {
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
    public RecipeHolder<EnchantingApparatusRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<EnchantingApparatusRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        // 模具必须是附魔装置
        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ars_nouveau".equals(moldId.getNamespace()) || !"enchanting_apparatus".equals(moldId.getPath())) {
                return null;
            }
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<EnchantingApparatusRecipe>> recipes = (List<RecipeHolder<EnchantingApparatusRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                RecipeRegistry.APPARATUS_TYPE.get()
        );

        for (RecipeHolder<EnchantingApparatusRecipe> holder : recipes) {
            EnchantingApparatusRecipe recipe = holder.value();

            Ingredient reagent = recipe.reagent();
            List<Ingredient> pedestalItems = recipe.pedestalItems();
            ItemStack output = recipe.result();

            if (output.isEmpty()) continue;

            // 合并试剂和基座物品
            List<Ingredient> requiredIngredients = new ArrayList<>();
            if (!reagent.isEmpty()) {
                requiredIngredients.add(reagent);
            }
            for (Ingredient pedestalIngredient : pedestalItems) {
                if (!pedestalIngredient.isEmpty()) {
                    requiredIngredients.add(pedestalIngredient);
                }
            }

            if (requiredIngredients.isEmpty()) continue;

            // 检查所有输入是否都匹配
            if (matchesAllIngredients(inputs, requiredIngredients)) {
                return holder;
            }
        }

        return null;
    }

    /**
     * 检查输入物品是否完全覆盖所需配料（支持合并计数）
     */
    private boolean matchesAllIngredients(List<ItemStack> inputs, List<Ingredient> requiredIngredients) {
        // 合并相同配料并统计数量
        Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
        for (Ingredient ing : requiredIngredients) {
            mergeIngredient(requiredCounts, ing, 1L);
        }

        // 统计输入物品匹配各配料的数量
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

        // 检查每种配料的数量是否满足
        for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
            long required = entry.getValue();
            long actual = inputMatchCounts.getOrDefault(entry.getKey(), 0L);
            if (actual < required) return false;
        }
        return true;
    }

    /**
     * 合并相同物品到计数映射中
     */
    private void mergeIngredient(Map<Ingredient, Long> ingredientCounts, Ingredient ingredient, long count) {
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            if (areIngredientsEqual(entry.getKey(), ingredient)) {
                ingredientCounts.put(entry.getKey(), entry.getValue() + count);
                return;
            }
        }
        ingredientCounts.put(ingredient, count);
    }

    /**
     * 检查两个 Ingredient 是否代表相同的物品
     */
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
        return 45;
    }
}
