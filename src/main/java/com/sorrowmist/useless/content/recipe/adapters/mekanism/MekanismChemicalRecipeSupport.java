package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.mekanism.MekanismChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
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
    public static GenericStack key(ChemicalStack<?> stack) {
        return stack == null || stack.isEmpty() ? null : ChemicalKeyProviders.get().toGenericStack(new MekanismChemicalStackView(stack));
    }

    static List<GenericStack> keys(ChemicalStackIngredient<?,?> ingredient) {
        List<GenericStack> result = new ArrayList<>();
        if (ingredient == null || ingredient.hasNoMatchingInstances()) return result;
        for (ChemicalStack<?> representation : ingredient.getRepresentations()) {
            GenericStack key = key(representation);
            if (key != null && key.amount() > 0L && result.stream().noneMatch(existing -> existing.what().equals(key.what()))) {
                result.add(key);
            }
        }
        return result;
    }

    public static long energyCost(long perTick,long ticks,mekanism.api.math.FloatingLong multiplier) {
        var cost=new java.math.BigDecimal(multiplier.toString()).multiply(java.math.BigDecimal.valueOf(perTick))
                .multiply(java.math.BigDecimal.valueOf(ticks)).setScale(0,java.math.RoundingMode.CEILING);
        if(cost.signum()<0 || cost.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE))>0)
            throw new IllegalArgumentException("Mekanism energy cost overflow");
        return cost.longValueExact();
    }
    public static boolean perTickUsage(Object recipe) {
        // 10.4's gas-consuming item machines and dissolution consume per tick;
        // metallurgic infusion and painting consume their ingredient once.
        return recipe instanceof mekanism.api.recipes.ItemStackGasToItemStackRecipe
                || recipe instanceof mekanism.api.recipes.ChemicalDissolutionRecipe;
    }

    static Ingredient itemIngredient(ItemStackIngredient ingredient) {
        var item=item(ingredient); return item == null ? Ingredient.EMPTY : item.ingredient();
    }
    static CountedIngredient item(ItemStackIngredient ingredient) { return countedItem(ingredient,1L); }
    static CountedIngredient countedItem(ItemStackIngredient ingredient,long multiplier) {
        if(ingredient == null || multiplier<=0) return null;
        var selected=NativeMekanismVariants.selected(ingredient);
        if(selected instanceof CountedIngredient item) return new CountedIngredient(item.ingredient(),Math.multiplyExact(item.count(),multiplier));
        return NativeMekanismIngredients.item(ingredient.serialize(),multiplier);
    }

    static List<CountedIngredient> items(ItemStackIngredient ingredient) {
        CountedIngredient converted = item(ingredient);
        return converted == null ? List.of() : List.of(converted);
    }

    static boolean matchesItem(Map<Ingredient, Long> inputs, ItemStackIngredient required) {
        if(required==null) return false;
        for(var counted:NativeMekanismIngredients.itemVariants(required.serialize(),1L)) {
            Map<Ingredient,Long> requirements=new LinkedHashMap<>();
            requirements.put(counted.ingredient(),counted.count());
            if(AdapterUtils.matchesRequired(inputs,requirements)) return true;
        }
        return false;
    }

    static boolean matchesChemical(Map<AEKey, Long> inputs, ChemicalStackIngredient<?,?> required) {
        for (GenericStack key : keys(required)) {
            if (inputs.getOrDefault(key.what(), 0L) >= key.amount()) return true;
        }
        return false;
    }

    static boolean matchesFluid(Map<FluidStack, Long> inputs, FluidStackIngredient required) {
        if (required == null || required.hasNoMatchingInstances()) return false;
        return NativeMekanismIngredients.fluidVariants(required.serialize()).stream()
                .anyMatch(variant->FluidIngredientAllocator.matches(List.of(variant),inputs,1L));
    }

    /** Keeps Mekanism's fluid tag/compound/component ingredient intact. */
    public static List<SizedFluidIngredient> fluidIngredients(FluidStackIngredient ingredient) {
        if(ingredient == null) return List.of();
        var selected=NativeMekanismVariants.selected(ingredient);
        SizedFluidIngredient converted=selected instanceof SizedFluidIngredient fluid ? fluid
                : NativeMekanismIngredients.fluid(ingredient.serialize());
        return converted == null ? List.of() : List.of(converted);
    }

    private static List<SizedFluidIngredient> scaleFluidIngredients(
            List<SizedFluidIngredient> ingredients, long batch) {
        if (ingredients.isEmpty()) return ingredients;
        List<SizedFluidIngredient> scaled = new ArrayList<>(ingredients.size());
        for (SizedFluidIngredient ingredient : ingredients) {
            if (ingredient == null || ingredient.ingredient() == null || ingredient.amount() <= 0) {
                scaled.add(ingredient);
                continue;
            }
            scaled.add(new SizedFluidIngredient(ingredient.ingredient(),
                    Math.toIntExact(Math.multiplyExact(ingredient.amount(),batch))));
        }
        return scaled;
    }

    public static List<FluidStack> fluidRepresentations(FluidStackIngredient ingredient) {
        if (ingredient == null || ingredient.hasNoMatchingInstances()) return List.of();
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack representation : ingredient.getRepresentations()) {
            FluidStack copy = representation.copy();

            result.add(copy);
        }
        return result;
    }

    public static ResourceLocation variantId(ResourceLocation originalId, String suffix) {
        String clean = suffix == null || suffix.isBlank() ? "converted" : suffix;
        return new ResourceLocation(originalId.getNamespace(),
                originalId.getPath() + "_" + clean.replace('/', '_'));
    }

    public static AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> items,
                                             List<?> fluids, List<GenericStack> chemicalInputs,
                                             List<ItemStack> outputs, List<FluidStack> outputFluids,
                                             List<GenericStack> chemicalOutputs, long energy, int processTime,
                                             @Nullable ItemStack mold) {
        List<CountedIngredient> safeItems = items == null ? List.of() : items;
        List<SizedFluidIngredient> safeFluids = toSizedFluidIngredients(fluids);
        List<GenericStack> safeChemicalInputs = chemicalInputs == null ? List.of() : chemicalInputs;
        List<ItemStack> safeOutputs = outputs == null ? List.of() : outputs;
        List<FluidStack> safeOutputFluids = outputFluids == null ? List.of() : outputFluids;
        List<GenericStack> safeChemicalOutputs = chemicalOutputs == null ? List.of() : chemicalOutputs;

        // Mekanism's non-item conversion recipes use the same machine operation for a much
        // larger resource batch. Keep item-bearing conversions unchanged because their item
        // quantities are part of the original recipe's material balance.
        boolean itemFree = safeItems.isEmpty() && safeOutputs.isEmpty();
        if (itemFree) {
            // Use one representable batch for every material: independent saturation
            // would consume a truncated input while retaining a full-size output.
            long batch=NON_ITEM_QUANTITY_MULTIPLIER;
            for(var fluid:safeFluids) batch=Math.min(batch,Integer.MAX_VALUE/(long)fluid.amount());
            for(var fluid:safeOutputFluids) {
                if(fluid==null || fluid.isEmpty()) throw new IllegalArgumentException("Empty required Mekanism fluid output");
                batch=Math.min(batch,Integer.MAX_VALUE/(long)fluid.getAmount());
            }
            for(var stack:safeChemicalInputs) batch=boundedBatch(batch,stack);
            for(var stack:safeChemicalOutputs) batch=boundedBatch(batch,stack);
            if(batch<1) throw new IllegalArgumentException("Unrepresentable Mekanism batch");
            safeFluids = scaleFluidIngredients(safeFluids,batch);
            safeChemicalInputs = scaleGenericStacks(safeChemicalInputs,batch);
            safeOutputFluids = scaleOutputFluidStacks(safeOutputFluids,batch);
            safeChemicalOutputs = scaleGenericStacks(safeChemicalOutputs,batch);
        }

        return new AdvancedAlloyFurnaceRecipe(
                id, safeItems, safeFluids, safeChemicalInputs, safeOutputs, safeOutputFluids, safeChemicalOutputs,
                energy, Math.max(1, processTime), Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(mold), AlloyFurnaceMode.NORMAL);
    }

    private static List<GenericStack> scaleGenericStacks(List<GenericStack> stacks,long batch) {
        if (stacks.isEmpty()) return stacks;
        List<GenericStack> scaled = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null) {
                scaled.add(stack);
                continue;
            }
            scaled.add(new GenericStack(stack.what(),
                    Math.multiplyExact(stack.amount(),batch)));
        }
        return scaled;
    }

    private static List<SizedFluidIngredient> toSizedFluidIngredients(List<?> fluids) {
        if (fluids == null || fluids.isEmpty()) return List.of();
        List<SizedFluidIngredient> result = new ArrayList<>(fluids.size());
        for (Object value : fluids) {
            if (value instanceof SizedFluidIngredient ingredient) {
                if (ingredient.ingredient() != null && !ingredient.ingredient().isEmpty()
                        && ingredient.amount() > 0) {
                    result.add(ingredient);
                } else throw new IllegalArgumentException("Invalid required Mekanism fluid input");
            } else if (value instanceof FluidStack stack) {
                SizedFluidIngredient converted = AdapterUtils.toSizedFluidIngredient(stack);
                if (converted == null) throw new IllegalArgumentException("Empty required Mekanism fluid input");
                result.add(converted);
            } else throw new IllegalArgumentException("Unknown Mekanism fluid input");
        }
        return List.copyOf(result);
    }

    private static List<FluidStack> scaleOutputFluidStacks(List<FluidStack> stacks,long batch) {
        if (stacks.isEmpty()) return stacks;
        List<FluidStack> scaled = new ArrayList<>(stacks.size());
        for (FluidStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                scaled.add(stack);
                continue;
            }
            FluidStack copy = stack.copy();
            copy.setAmount(Math.toIntExact(Math.multiplyExact(copy.getAmount(),batch)));
            scaled.add(copy);
        }
        return scaled;
    }

    private static long boundedBatch(long batch,GenericStack stack) {
        if(stack==null || stack.what()==null || stack.amount()<=0)
            throw new IllegalArgumentException("Invalid required Mekanism chemical");
        return Math.min(batch,Long.MAX_VALUE/stack.amount());
    }

    public static long saturatingAdd(long left, long right) {
        return Math.addExact(left,right);
    }

    public static long saturatingMultiply(long left, long right) {
        if(left<0 || right<0) throw new IllegalArgumentException("Negative Mekanism quantity");
        return Math.multiplyExact(left,right);
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

        if (!FluidIngredientAllocator.matchesLong(recipe.inputFluids(), mergedFluids, 1L)) return false;

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

}
