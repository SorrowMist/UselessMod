package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.mekanism.MekanismChemicalCompat;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Shared conversion and matching helpers for AppMek-backed Mekanism recipes. */
public final class MekanismChemicalRecipeSupport {
    public static final long NON_ITEM_QUANTITY_MULTIPLIER = 1_000L;

    private MekanismChemicalRecipeSupport() {
    }

    @Nullable
    public static GenericStack key(ChemicalStack stack) {
        return MekanismChemicalCompat.toGenericStack(stack);
    }

    static List<GenericStack> keys(ChemicalStackIngredient ingredient) {
        List<GenericStack> result = new ArrayList<>();
        if (ingredient == null || ingredient.hasNoMatchingInstances()) return result;
        for (ChemicalStack representation : ingredient.getRepresentations()) {
            GenericStack key = key(representation);
            if (key != null && key.amount() > 0L && result.stream().noneMatch(existing -> existing.what().equals(key.what()))) {
                result.add(key);
            }
        }
        return result;
    }

    static Ingredient itemIngredient(ItemStackIngredient ingredient) {
        if (ingredient == null || ingredient.hasNoMatchingInstances()) return Ingredient.EMPTY;
        return Ingredient.of(ingredient.getRepresentations().stream().map(stack -> stack.copyWithCount(1)));
    }

    @Nullable
    static CountedIngredient item( ItemStackIngredient ingredient) {
        Ingredient converted = itemIngredient(ingredient);
        return converted.isEmpty() ? null : new CountedIngredient(converted, ingredient.ingredient().count());
    }

    static List<CountedIngredient> items(ItemStackIngredient ingredient) {
        CountedIngredient converted = item(ingredient);
        return converted == null ? List.of() : List.of(converted);
    }

    static boolean matchesItem(Map<Ingredient, Long> inputs, ItemStackIngredient required) {
        if (required == null || required.hasNoMatchingInstances()) return false;
        return AdapterUtils.hasMatchingIngredient(inputs, itemIngredient(required), required.ingredient().count());
    }

    static boolean matchesChemical(Map<AEKey, Long> inputs, ChemicalStackIngredient required) {
        for (GenericStack key : keys(required)) {
            if (inputs.getOrDefault(key.what(), 0L) >= key.amount()) return true;
        }
        return false;
    }

    static boolean matchesFluid(Map<FluidStack, Long> inputs, FluidStackIngredient required) {
        if (required == null || required.hasNoMatchingInstances()) return false;
        List<FluidStack> matchingFluids = new ArrayList<>();
        List<Long> matchingAmounts = new ArrayList<>();
        for (Map.Entry<FluidStack, Long> entry : inputs.entrySet()) {
            FluidStack input = entry.getKey();
            Long amount = entry.getValue();
            if (input == null || input.isEmpty() || amount == null || amount <= 0L
                    || !required.testType(input)) continue;

            boolean merged = false;
            for (int index = 0; index < matchingFluids.size(); index++) {
                if (!FluidStack.isSameFluidSameComponents(matchingFluids.get(index), input)) continue;
                matchingAmounts.set(index, saturatingAdd(matchingAmounts.get(index), amount));
                merged = true;
                break;
            }
            if (!merged) {
                matchingFluids.add(input.copy());
                matchingAmounts.add(amount);
            }
        }
        long requiredAmount = required.ingredient().amount();
        return matchingAmounts.stream().anyMatch(amount -> amount >= requiredAmount);
    }

    public static List<FluidStack> fluidRepresentations(FluidStackIngredient ingredient) {
        if (ingredient == null || ingredient.hasNoMatchingInstances()) return List.of();
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack representation : ingredient.getRepresentations()) {
            FluidStack copy = representation.copy();
            copy.setAmount(AdapterUtils.safeInt(ingredient.ingredient().amount()));
            result.add(copy);
        }
        return result;
    }

    public static ResourceLocation variantId(ResourceLocation originalId, String suffix) {
        String clean = suffix == null || suffix.isBlank() ? "converted" : suffix;
        return ResourceLocation.fromNamespaceAndPath(originalId.getNamespace(),
                originalId.getPath() + "_" + clean.replace('/', '_'));
    }

    public static AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> items,
                                             List<FluidStack> fluids, List<GenericStack> chemicalInputs,
                                             List<ItemStack> outputs, List<FluidStack> outputFluids,
                                             List<GenericStack> chemicalOutputs, long energy, int processTime,
                                             @Nullable ItemStack mold) {
        List<CountedIngredient> safeItems = items == null ? List.of() : items;
        List<FluidStack> safeFluids = fluids == null ? List.of() : fluids;
        List<GenericStack> safeChemicalInputs = chemicalInputs == null ? List.of() : chemicalInputs;
        List<ItemStack> safeOutputs = outputs == null ? List.of() : outputs;
        List<FluidStack> safeOutputFluids = outputFluids == null ? List.of() : outputFluids;
        List<GenericStack> safeChemicalOutputs = chemicalOutputs == null ? List.of() : chemicalOutputs;

        // Mekanism's non-item conversion recipes use the same machine operation for a much
        // larger resource batch. Keep item-bearing conversions unchanged because their item
        // quantities are part of the original recipe's material balance.
        boolean itemFree = safeItems.isEmpty() && safeOutputs.isEmpty();
        if (itemFree) {
            safeFluids = scaleFluidStacks(safeFluids);
            safeChemicalInputs = scaleGenericStacks(safeChemicalInputs);
            safeOutputFluids = scaleFluidStacks(safeOutputFluids);
            safeChemicalOutputs = scaleGenericStacks(safeChemicalOutputs);
        }

        return new AdvancedAlloyFurnaceRecipe(
                id, safeItems, safeFluids, safeChemicalInputs, safeOutputs, safeOutputFluids, safeChemicalOutputs,
                energy, Math.max(1, processTime), Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(mold), AlloyFurnaceMode.NORMAL);
    }

    private static List<GenericStack> scaleGenericStacks(List<GenericStack> stacks) {
        if (stacks.isEmpty()) return stacks;
        List<GenericStack> scaled = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null) {
                scaled.add(stack);
                continue;
            }
            scaled.add(new GenericStack(stack.what(),
                    saturatingMultiply(stack.amount(), NON_ITEM_QUANTITY_MULTIPLIER)));
        }
        return scaled;
    }

    private static List<FluidStack> scaleFluidStacks(List<FluidStack> stacks) {
        if (stacks.isEmpty()) return stacks;
        List<FluidStack> scaled = new ArrayList<>(stacks.size());
        for (FluidStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                scaled.add(stack);
                continue;
            }
            FluidStack copy = stack.copy();
            copy.setAmount(saturatingFluidAmount(copy.getAmount()));
            scaled.add(copy);
        }
        return scaled;
    }

    private static int saturatingFluidAmount(int amount) {
        if (amount <= 0) return amount;
        long maximum = Integer.MAX_VALUE;
        return amount > maximum / NON_ITEM_QUANTITY_MULTIPLIER
                ? Integer.MAX_VALUE
                : (int) (amount * NON_ITEM_QUANTITY_MULTIPLIER);
    }

    public static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public static RecipeHolder<MekanismSyntheticRecipe> syntheticHolder(
            ResourceLocation id, AdvancedAlloyFurnaceRecipe recipe) {
        return new RecipeHolder<>(id, new MekanismSyntheticRecipe(recipe));
    }

    static boolean matchesConvertedRecipe(AdvancedAlloyFurnaceRecipe recipe,
                                          Map<Ingredient, Long> mergedInputs,
                                          Map<FluidStack, Long> mergedFluids,
                                          Map<AEKey, Long> mergedKeys) {
        if (recipe == null) return false;

        Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(requiredItems, input.ingredient(), input.count());
        }
        if (!AdapterUtils.matchesRequired(mergedInputs, requiredItems)) return false;

        for (RequiredFluid required : mergeFluidRequirements(recipe.inputFluids())) {
            long available = 0L;
            for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
                if (FluidStack.isSameFluidSameComponents(required.stack(), entry.getKey())) {
                    available = saturatingAdd(available, entry.getValue());
                }
            }
            if (available < required.amount()) return false;
        }

        Map<AEKey, Long> requiredKeys = new LinkedHashMap<>();
        for (GenericStack required : recipe.keyInputs()) {
            if (required == null || required.what() == null || required.amount() <= 0L) continue;
            requiredKeys.merge(required.what(), required.amount(), MekanismChemicalRecipeSupport::saturatingAdd);
        }
        for (Map.Entry<AEKey, Long> required : requiredKeys.entrySet()) {
            if (mergedKeys.getOrDefault(required.getKey(), 0L) < required.getValue()) return false;
        }
        return true;
    }

    private static List<RequiredFluid> mergeFluidRequirements(List<FluidStack> fluids) {
        List<RequiredFluid> result = new ArrayList<>();
        if (fluids == null) return result;
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) continue;
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                RequiredFluid existing = result.get(i);
                if (!FluidStack.isSameFluidSameComponents(existing.stack(), fluid)) continue;
                result.set(i, new RequiredFluid(existing.stack(), saturatingAdd(existing.amount(), fluid.getAmount())));
                merged = true;
                break;
            }
            if (!merged) result.add(new RequiredFluid(fluid.copy(), fluid.getAmount()));
        }
        return result;
    }

    private record RequiredFluid(FluidStack stack, long amount) {
    }
}
