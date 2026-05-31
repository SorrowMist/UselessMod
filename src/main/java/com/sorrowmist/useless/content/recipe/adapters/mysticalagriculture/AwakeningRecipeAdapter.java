package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
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
 * Mystical Agriculture 觉醒祭坛配方适配器
 * <p>
 * 将觉醒祭坛配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 祭坛物品(input) + 基座物品(ingredients) + 精华(essences，含数量) → 普通输入（合并相同物品，全部消耗）
 * - 产物(result) → 输出
 * - mysticalagriculture:awakening_altar → 模具（不消耗）
 */
public class AwakeningRecipeAdapter implements IRecipeAdapter<IAwakeningRecipe> {

    private static final int BASE_PROCESS_TIME = 20;

    @Override
    public Class<IAwakeningRecipe> getRecipeClass() {
        return IAwakeningRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<IAwakeningRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        IAwakeningRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient altarIngredient = recipe.getAltarIngredient();
        List<Ingredient> allIngredients = recipe.getIngredients();
        List<ItemStack> essences = recipe.getEssences();
        ItemStack output = recipe.getResultItem(null);

        if (output.isEmpty()) {
            return result;
        }

        // 合并所有输入（祭坛物品 + 基座物品 + 精华）
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();

        if (!altarIngredient.isEmpty()) {
            mergeIngredient(ingredientCounts, altarIngredient, 1L);
        }

        // 基座物品在 getIngredients() 中位于奇数索引 (1,3,5,7)
        for (int i = 1; i < allIngredients.size(); i += 2) {
            Ingredient ing = allIngredients.get(i);
            if (!ing.isEmpty()) {
                mergeIngredient(ingredientCounts, ing, 1L);
            }
        }

        // 精华物品（附带数量）
        for (ItemStack essence : essences) {
            if (!essence.isEmpty()) {
                mergeIngredient(ingredientCounts, Ingredient.of(essence), (long) essence.getCount());
            }
        }

        if (ingredientCounts.isEmpty()) {
            return result;
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        // 处理时间基于输入物品数量
        int processTime = BASE_PROCESS_TIME + (allIngredients.size() / 2 + essences.size()) * 15;

        ResourceLocation convertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "awakening_altar")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                convertedId,
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                5000,
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
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<IAwakeningRecipe> holder, Level level) {
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
    public RecipeHolder<IAwakeningRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs, null);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<IAwakeningRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        // 模具必须是觉醒祭坛
        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"mysticalagriculture".equals(moldId.getNamespace()) || !"awakening_altar".equals(moldId.getPath())) {
                return null;
            }
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<IAwakeningRecipe>> recipes = (List<RecipeHolder<IAwakeningRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.AWAKENING.get()
        );

        for (RecipeHolder<IAwakeningRecipe> holder : recipes) {
            IAwakeningRecipe recipe = holder.value();

            Ingredient altarIngredient = recipe.getAltarIngredient();
            List<Ingredient> allIngredients = recipe.getIngredients();
            List<ItemStack> essences = recipe.getEssences();
            ItemStack output = recipe.getResultItem(null);

            if (output.isEmpty()) continue;

            // 合并所有需要的配料
            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            if (!altarIngredient.isEmpty()) {
                mergeIngredient(requiredCounts, altarIngredient, 1L);
            }
            for (int i = 1; i < allIngredients.size(); i += 2) {
                Ingredient ing = allIngredients.get(i);
                if (!ing.isEmpty()) {
                    mergeIngredient(requiredCounts, ing, 1L);
                }
            }
            for (ItemStack essence : essences) {
                if (!essence.isEmpty()) {
                    mergeIngredient(requiredCounts, Ingredient.of(essence), (long) essence.getCount());
                }
            }

            if (requiredCounts.isEmpty()) continue;

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
        return 38;
    }
}
