package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
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
 * AE2 Lightning Tech 闪电模拟室配方适配器
 * <p>
 * 将闪电模拟室配方转换为高级合金熔炉配方
 * <p>
 * 处理逻辑：
 * - 所有输入物品 → 普通输入（合并相同物品）
 * - 产物 → 输出
 * - ae2lt:lightning_simulation_room → 模具（不消耗）
 */
public class LightningSimulationRecipeAdapter implements IRecipeAdapter<LightningSimulationRecipe> {

    private static final int BASE_PROCESS_TIME = 60;

    @Override
    public Class<LightningSimulationRecipe> getRecipeClass() {
        return LightningSimulationRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "lightning_simulation_room")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<LightningSimulationRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        LightningSimulationRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        List<LightningSimulationIngredient> inputs = recipe.inputs();
        ItemStack output = recipe.getResultStack();

        if (output.isEmpty() || inputs.isEmpty()) {
            return result;
        }

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (LightningSimulationIngredient input : inputs) {
            AdapterUtils.mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        var keyInputs = List.of(AELightningIngredientHelper.createLightningKeyInput(recipe.lightningTier(), recipe.lightningCost()));

        int processTime = BASE_PROCESS_TIME + inputs.size() * 20;

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "lightning_simulation_room")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                keyInputs,
                List.of(output.copy()),
                List.of(),
                List.of(),
                2000,
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
    public List<RecipeHolder<LightningSimulationRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) return List.of();

        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ae2lt".equals(moldId.getNamespace()) || !"lightning_simulation_room".equals(moldId.getPath())) return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<LightningSimulationRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<LightningSimulationRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())) {
            LightningSimulationRecipe recipe = holder.value();
            List<LightningSimulationIngredient> recipeInputs = recipe.inputs();
            ItemStack output = recipe.getResultStack();
            if (output.isEmpty() || recipeInputs.isEmpty()) continue;

            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (LightningSimulationIngredient input : recipeInputs) {
                AdapterUtils.mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts)
                    && AELightningIngredientHelper.matchesSimulationOrAssemblyLightning(mergedInputs, mergedKeys, recipe.lightningTier(), recipe.lightningCost())) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
