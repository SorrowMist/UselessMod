package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.base.common.util.ExperienceUtil;
import com.enderio.base.common.init.EIOFluids;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small validation and conversion helpers shared by the Ender IO adapters. */
final class EnderIOAdapterUtils {
    private EnderIOAdapterUtils() {
    }

    @Nullable
    static CountedIngredient counted(@Nullable com.enderio.core.common.recipes.CountedIngredient ingredient) {
        if (ingredient == null || ingredient.count() <= 0 || ingredient.ingredient() == null
                || ingredient.ingredient().isEmpty()) {
            return null;
        }
        return new CountedIngredient(ingredient.ingredient(), ingredient.count());
    }

    @Nullable
    static List<CountedIngredient> counted(@Nullable List<com.enderio.core.common.recipes.CountedIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (com.enderio.core.common.recipes.CountedIngredient sized : ingredients) {
            CountedIngredient counted = counted(sized);
            if (counted == null) {
                return null;
            }
            AdapterUtils.mergeIngredient(requirements, counted.ingredient(), counted.count());
        }
        return requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    static Map<Ingredient, Long> requirements(List<CountedIngredient> inputs) {
        Map<Ingredient, Long> result = new LinkedHashMap<>();
        if (inputs != null) {
            for (CountedIngredient input : inputs) {
                if (input != null && input.ingredient() != null && input.count() > 0) {
                    AdapterUtils.mergeIngredient(result, input.ingredient(), input.count());
                }
            }
        }
        return result;
    }

    @Nullable
    static List<FluidStack> fluidChoices(@Nullable SizedFluidIngredient ingredient) {
        if (ingredient == null || ingredient.amount() <= 0 || ingredient.ingredient().isEmpty()) {
            return null;
        }
        Map<String, FluidStack> unique = new LinkedHashMap<>();
        for (FluidStack candidate : ingredient.getFluids()) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            var fluid = candidate.getFluid();
            if (fluid instanceof FlowingFluid flowing) {
                fluid = flowing.getSource();
            }
            FluidStack normalized = new FluidStack(fluid, ingredient.amount());
            if (candidate.hasTag()) normalized.setTag(candidate.getTag().copy());
            unique.putIfAbsent(fluidKey(normalized), normalized);
        }
        return unique.isEmpty() ? null : List.copyOf(unique.values());
    }

    static boolean matchesFluid(
            Map<FluidStack, Long> available, SizedFluidIngredient required) {
        return required != null && required.amount() > 0
                && FluidIngredientAllocator.matches(List.of(required), available, 1L);
    }

    @Nullable
    static FluidStack experienceFluid(int levels) {
        if (levels <= 0) {
            return null;
        }
        long amount = ExperienceUtil.getFluidFromLevel(levels);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new FluidStack(EIOFluids.XP_JUICE.getSource(), (int) amount);
    }

    static String fluidKey(FluidStack stack) {
        return stack.getFluid().builtInRegistryHolder().key().location()
                + "|" + com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical.snbt(stack.getTag());
    }

    @Nullable
    static List<ItemStack> itemOutputs(List<com.enderio.core.common.recipes.OutputStack> results) {
        if (results == null || results.isEmpty()) return null;
        List<ItemStack> items = new ArrayList<>();
        for (var result : results) {
            if (result == null || result.isEmpty() || !result.isItem()) return null;
            ItemStack item = result.getItem();
            if (item == null || item.isEmpty() || item.getCount() <= 0) return null;
            items.add(item.copy());
        }
        return List.copyOf(items);
    }

    static boolean sameItemStack(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty()
                && ItemStack.isSameItemSameTags(left, right);
    }

    static List<ItemStack> distinctStacks(@Nullable List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (result.stream().noneMatch(existing -> sameItemStack(existing, stack))) {
                result.add(stack.copy());
            }
        }
        return result;
    }
}
