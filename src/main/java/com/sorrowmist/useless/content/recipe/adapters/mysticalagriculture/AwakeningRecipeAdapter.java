package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
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

    @Override
    public Class<IAwakeningRecipe> getRecipeClass() {
        return IAwakeningRecipe.class;
    }

    @Nullable
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "awakening_altar")));
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null
                && BuiltInRegistries.ITEM.getKey(mold.getItem()).equals(
                        ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "awakening_altar"));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<IAwakeningRecipe> holder, Level level) {
        if (holder == null) return List.of();

        IAwakeningRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient altarIngredient = recipe.getAltarIngredient();
        List<Ingredient> allIngredients = recipe.getIngredients();
        List<ItemStack> essences = recipe.getEssences();
        ItemStack output = recipe.getResultItem(null);

        if (output.isEmpty()) {
            return List.of();
        }

        // 合并所有输入（祭坛物品 + 基座物品 + 精华）
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();

        if (!altarIngredient.isEmpty()) {
            AdapterUtils.mergeIngredient(ingredientCounts, altarIngredient, 1L);
        }

        // 基座物品在 getIngredients() 中位于奇数索引 (1,3,5,7)
        for (int i = 1; i < allIngredients.size(); i += 2) {
            Ingredient ing = allIngredients.get(i);
            if (!ing.isEmpty()) {
                AdapterUtils.mergeIngredient(ingredientCounts, ing, 1L);
            }
        }

        // 精华物品（附带数量）
        for (ItemStack essence : essences) {
            if (!essence.isEmpty()) {
                AdapterUtils.mergeIngredient(ingredientCounts, Ingredient.of(essence), (long) essence.getCount());
            }
        }

        if (ingredientCounts.isEmpty()) {
            return List.of();
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME + (allIngredients.size() / 2 + essences.size()) * 15;

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                5000,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );

        return List.of(convertedRecipe);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<IAwakeningRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return null;
        }

        if (!matchesMold(mold)) {
            return null;
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
                AdapterUtils.mergeIngredient(requiredCounts, altarIngredient, 1L);
            }
            for (int i = 1; i < allIngredients.size(); i += 2) {
                Ingredient ing = allIngredients.get(i);
                if (!ing.isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredCounts, ing, 1L);
                }
            }
            for (ItemStack essence : essences) {
                if (!essence.isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredCounts, Ingredient.of(essence), (long) essence.getCount());
                }
            }

            if (requiredCounts.isEmpty()) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts)) {
                return holder;
            }
        }

        return null;
    }
}
