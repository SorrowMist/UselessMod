package com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.recipe.circuit_etcher.CircuitEtcherRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2CS 电路蚀刻机配方适配器
 */
public class CircuitEtcherRecipeAdapter implements IRecipeAdapter<CircuitEtcherRecipe> {

    @Override
    public Class<CircuitEtcherRecipe> getRecipeClass() {
        return CircuitEtcherRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2cs", "circuit_etcher")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CircuitEtcherRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        CircuitEtcherRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();

        ItemStack output = recipe.result();
        if (output.isEmpty()) return result;

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        addSizedIngredient(ingredientCounts, recipe.inputA());
        addSizedIngredient(ingredientCounts, recipe.inputB());
        addSizedIngredient(ingredientCounts, recipe.inputC());

        if (ingredientCounts.isEmpty()) return result;

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients, List.of(),
                List.of(output.copy()), List.of(),
                AdapterUtils.ae2csEnergyCost(recipe.energyCost()), 100,
                Ingredient.EMPTY, 0,
                makeMold("circuit_etcher"),
                AlloyFurnaceMode.NORMAL
        );

        result.add(convertedRecipe);
        return result;
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<CircuitEtcherRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> recipes = convertAll(holder, level);
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public RecipeHolder<CircuitEtcherRecipe> findMatchingRecipe(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null) return null;
        if (!checkMold(mold, "circuit_etcher")) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<CircuitEtcherRecipe> holder : (List<RecipeHolder<CircuitEtcherRecipe>>) (List<?>)
                recipeManager.getAllRecipesFor(AECSRecipeTypes.CIRCUIT_ETCHER.get())) {
            CircuitEtcherRecipe recipe = holder.value();
            if (recipe.result().isEmpty()) continue;

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            addSizedIngredient(required, recipe.inputA());
            addSizedIngredient(required, recipe.inputB());
            addSizedIngredient(required, recipe.inputC());
            if (required.isEmpty()) continue;

            if (AdapterUtils.matchesRequired(mergedInputs, required)) return holder;
        }
        return null;
    }

    static void addSizedIngredient(Map<Ingredient, Long> map, SizedIngredient si) {
        if (si == null) return;
        Ingredient ing = si.ingredient();
        long count = si.count();
        if (ing.isEmpty() || count <= 0) return;
        for (Map.Entry<Ingredient, Long> entry : map.entrySet()) {
            if (AdapterUtils.areIngredientsEqual(entry.getKey(), ing)) {
                map.put(entry.getKey(), entry.getValue() + count);
                return;
            }
        }
        map.put(ing, count);
    }

    static boolean matchesCounted(List<ItemStack> inputs, Map<Ingredient, Long> requiredCounts) {
        Map<Ingredient, Long> matched = new LinkedHashMap<>();
        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;
            int stackCount = stack.getCount();
            for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
                if (entry.getKey().test(stack)) {
                    matched.merge(entry.getKey(), (long) stackCount, Long::sum);
                    break;
                }
            }
        }
        for (Map.Entry<Ingredient, Long> entry : requiredCounts.entrySet()) {
            if (matched.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
        }
        return true;
    }

    static boolean checkMold(@Nullable ItemStack mold, String machineName) {
        if (mold == null || mold.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(mold.getItem());
        return "ae2cs".equals(id.getNamespace()) && machineName.equals(id.getPath());
    }

    static Ingredient makeMold(String machineName) {
        return Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2cs", machineName))));
    }
}
