package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe;
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
 * Mystical Agriculture 注魔祭坛配方适配器
 * <p>
 * 将注魔祭坛配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 祭坛物品(input) + 基座物品(inputs) → 普通输入（合并相同物品，全部消耗）
 * - 产物(result) → 输出
 * - mysticalagriculture:infusion_altar → 模具（不消耗）
 */
public class InfusionRecipeAdapter implements IRecipeAdapter<IInfusionRecipe> {

    @Override
    public Class<IInfusionRecipe> getRecipeClass() {
        return IInfusionRecipe.class;
    }

    @Nullable
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "infusion_altar")));
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null
                && BuiltInRegistries.ITEM.getKey(mold.getItem()).equals(
                        ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "infusion_altar"));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<IInfusionRecipe> holder, Level level) {
        if (holder == null) return List.of();

        IInfusionRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        Ingredient altarIngredient = recipe.getAltarIngredient();
        List<Ingredient> pedestalItems = recipe.getIngredients();
        ItemStack output = recipe.getResultItem(null);

        if (output.isEmpty()) {
            return List.of();
        }

        List<CountedIngredient> countedIngredients = AdapterUtils.mergeIngredients(
                new ArrayList<>() {{
                    if (!altarIngredient.isEmpty()) add(altarIngredient);
                    for (Ingredient ing : pedestalItems) {
                        if (!ing.isEmpty()) add(ing);
                    }
                }}
        );

        if (countedIngredients.isEmpty()) {
            return List.of();
        }

        int processTime = AdapterUtils.DEFAULT_PROCESS_TIME + pedestalItems.size() * 15;

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                List.of(output.copy()),
                List.of(),
                2000,
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
    public RecipeHolder<IInfusionRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return null;
        }

        if (!matchesMold(mold)) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<IInfusionRecipe>> recipes = (List<RecipeHolder<IInfusionRecipe>>) (List<?>) recipeManager.getAllRecipesFor(
                ModRecipeTypes.INFUSION.get()
        );

        for (RecipeHolder<IInfusionRecipe> holder : recipes) {
            IInfusionRecipe recipe = holder.value();

            Ingredient altarIngredient = recipe.getAltarIngredient();
            List<Ingredient> pedestalItems = recipe.getIngredients();
            ItemStack output = recipe.getResultItem(null);

            if (output.isEmpty()) continue;

            // 合并所有需要的配料
            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            if (!altarIngredient.isEmpty()) {
                AdapterUtils.mergeIngredient(requiredCounts, altarIngredient, 1L);
            }
            for (Ingredient ing : pedestalItems) {
                if (!ing.isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredCounts, ing, 1L);
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
