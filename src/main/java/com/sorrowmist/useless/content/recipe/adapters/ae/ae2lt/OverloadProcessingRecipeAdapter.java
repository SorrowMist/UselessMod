package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
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
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "overload_processing_factory")));
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

        // 只要有输入并且有输出就可以转换，不管是物品还是流体
        if (!hasItemInputs && !hasFluidInput) {
            return result;
        }
        if (!hasItemOutputs && !hasFluidOutput) {
            return result;
        }

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (OverloadProcessingIngredient input : itemInputs) {
            AdapterUtils.mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        var keyInputs = List.of(AELightningIngredientHelper.createLightningKeyInput(recipe.lightningTier(), recipe.lightningCost()));

        var sizedFluid = hasFluidInput ? AdapterUtils.toSizedFluidIngredient(fluidInput) : null;
        List<?> inputFluids = sizedFluid == null ? List.of() : List.of(sizedFluid);
        List<ItemStack> outputs = hasItemOutputs ? itemResults.stream().map(ItemStack::copy).toList() : List.of();
        List<FluidStack> outputFluids = hasFluidOutput ? List.of(fluidResult.copy()) : List.of();

        int processTime = BASE_PROCESS_TIME;
        long energy = recipe.totalEnergy();
        int scaledEnergy = energy > Integer.MAX_VALUE ? 10000 : (int) energy;

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "overload_processing_factory")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                inputFluids,
                keyInputs,
                outputs,
                outputFluids,
                List.of(),
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
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<OverloadProcessingRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null) return List.of();

        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ae2lt".equals(moldId.getNamespace()) || !"overload_processing_factory".equals(moldId.getPath())) return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<OverloadProcessingRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<OverloadProcessingRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())) {
            OverloadProcessingRecipe recipe = holder.value();

            List<OverloadProcessingIngredient> recipeItemInputs = recipe.itemInputs();
            FluidStack recipeFluidInput = recipe.fluidInput();

            // 检查物品输入匹配
            boolean itemsMatch = true;
            if (!recipeItemInputs.isEmpty()) {
                Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
                for (OverloadProcessingIngredient input : recipeItemInputs) {
                    AdapterUtils.mergeIngredient(requiredCounts, input.ingredient(), input.count());
                }
                itemsMatch = AdapterUtils.matchesRequired(mergedInputs, requiredCounts);
            }

            // 检查流体输入匹配（使用已合并的流体数据）
            boolean fluidsMatch = recipeFluidInput.isEmpty()
                    || (AdapterUtils.toSizedFluidIngredient(recipeFluidInput) != null
                    && com.sorrowmist.useless.content.recipe.FluidIngredientAllocator.matches(
                    List.of(AdapterUtils.toSizedFluidIngredient(recipeFluidInput)),
                    mergedFluids, 1L));

            if ((recipeItemInputs.isEmpty() || itemsMatch)
                    && (recipeFluidInput.isEmpty() || fluidsMatch)
                    && AELightningIngredientHelper.matchesOverloadLightning(mergedInputs, mergedKeys, recipe.lightningTier(), recipe.lightningCost())) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
