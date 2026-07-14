package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
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
 * Ars Nouveau 附魔装置配方适配器
 * <p>
 * 将附魔装置配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 催化剂(reagent) + 基座物品(pedestalItems) → 普通输入（分别消耗）
 * - 产物(result) → 输出
 * - ars_nouveau:enchanting_apparatus → 模具（不消耗）
 * - 魔力消耗(sourceCost) → 能量消耗
 */
public class EnchantingApparatusRecipeAdapter implements IRecipeAdapter<EnchantingApparatusRecipe> {
    @Override
    public Class<EnchantingApparatusRecipe> getRecipeClass() {
        return EnchantingApparatusRecipe.class;
    }

    @Nullable
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "enchanting_apparatus")));
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null
                && BuiltInRegistries.ITEM.getKey(mold.getItem()).equals(
                        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "enchanting_apparatus"));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<EnchantingApparatusRecipe> holder, Level level) {
        if (holder == null) return List.of();

        EnchantingApparatusRecipe originalRecipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient reagent = originalRecipe.reagent();
        List<Ingredient> pedestalItems = originalRecipe.pedestalItems();
        ItemStack output = originalRecipe.result();
        int sourceCost = originalRecipe.sourceCost();

        if (output.isEmpty()) {
            return List.of();
        }

        List<CountedIngredient> countedIngredients = AdapterUtils.mergeIngredients(
                new ArrayList<>() {{
                    if (!reagent.isEmpty()) add(reagent);
                    for (Ingredient ing : pedestalItems) {
                        if (!ing.isEmpty()) add(ing);
                    }
                }}
        );

        if (countedIngredients.isEmpty()) {
            return List.of();
        }

        // 魔力消耗转换为能量（1 source = 10 FE）
        int energy = Math.max(sourceCost * 10, 1000);
        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME + pedestalItems.size() * 20;

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                energy,
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
    public List<RecipeHolder<EnchantingApparatusRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        if (!matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<EnchantingApparatusRecipe>> recipes = (List<RecipeHolder<EnchantingApparatusRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                RecipeRegistry.APPARATUS_TYPE.get()
        );

        List<RecipeHolder<EnchantingApparatusRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<EnchantingApparatusRecipe> holder : recipes) {
            EnchantingApparatusRecipe recipe = holder.value();

            Ingredient reagent = recipe.reagent();
            List<Ingredient> pedestalItems = recipe.pedestalItems();
            ItemStack output = recipe.result();

            if (output.isEmpty()) continue;

            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            if (!reagent.isEmpty()) {
                AdapterUtils.mergeIngredient(requiredCounts, reagent, 1L);
            }
            for (Ingredient pedestalIngredient : pedestalItems) {
                if (!pedestalIngredient.isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredCounts, pedestalIngredient, 1L);
                }
            }

            if (requiredCounts.isEmpty()) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
