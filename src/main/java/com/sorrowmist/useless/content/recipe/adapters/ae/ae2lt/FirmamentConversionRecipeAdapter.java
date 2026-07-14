package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionIngredient;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirmamentConversionRecipeAdapter implements IRecipeAdapter<FirmamentConversionRecipe> {

    private static final int ENERGY_PER_TICK = 20;

    @Override
    public Class<FirmamentConversionRecipe> getRecipeClass() {
        return FirmamentConversionRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "firmament_conversion_core")));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<FirmamentConversionRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        FirmamentConversionRecipe recipe = holder.value();
        ResourceLocation originalId = holder.id();
        List<FirmamentConversionIngredient> inputs = recipe.inputs();
        List<ItemStack> outputs = recipe.getResultStacks();

        if (inputs.isEmpty() || outputs.isEmpty()) {
            return result;
        }

        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (FirmamentConversionIngredient input : inputs) {
            AdapterUtils.mergeIngredient(ingredientCounts, input.ingredient(), input.count());
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredientCounts.entrySet()) {
            countedIngredients.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        Ingredient moldIngredient = Ingredient.of(new ItemStack(
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ae2lt", "firmament_conversion_core")
                )
        ));

        AdvancedAlloyFurnaceRecipe convertedRecipe = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(originalId),
                countedIngredients,
                List.of(),
                List.of(),
                outputs.stream().map(ItemStack::copy).toList(),
                List.of(),
                List.of(),
                Math.max(1, recipe.processTime() * ENERGY_PER_TICK),
                recipe.processTime(),
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
    public List<RecipeHolder<FirmamentConversionRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) return List.of();

        if (mold != null && !mold.isEmpty()) {
            ResourceLocation moldId = BuiltInRegistries.ITEM.getKey(mold.getItem());
            if (!"ae2lt".equals(moldId.getNamespace()) || !"firmament_conversion_core".equals(moldId.getPath())) return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FirmamentConversionRecipe>> recipes = new ArrayList<>(recipeManager.getAllRecipesFor(ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get()));
        recipes.sort(Comparator
                .<RecipeHolder<FirmamentConversionRecipe>>comparingInt(holder -> holder.value().priority()).reversed()
                .thenComparing(holder -> holder.value().inputs().size(), Comparator.reverseOrder())
                .thenComparing(holder -> holder.value().totalInputCount(), Comparator.reverseOrder())
                .thenComparing(holder -> holder.id().toString()));

        List<RecipeHolder<FirmamentConversionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<FirmamentConversionRecipe> holder : recipes) {
            FirmamentConversionRecipe recipe = holder.value();
            List<FirmamentConversionIngredient> recipeInputs = recipe.inputs();
            List<ItemStack> outputs = recipe.getResultStacks();
            if (recipeInputs.isEmpty() || outputs.isEmpty()) continue;

            Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
            for (FirmamentConversionIngredient input : recipeInputs) {
                AdapterUtils.mergeIngredient(requiredCounts, input.ingredient(), input.count());
            }

            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
