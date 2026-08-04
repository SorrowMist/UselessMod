package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIOFluids;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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
    static CountedIngredient counted(@Nullable SizedIngredient ingredient) {
        if (ingredient == null || ingredient.count() <= 0 || ingredient.ingredient() == null
                || ingredient.ingredient().isEmpty()) {
            return null;
        }
        return new CountedIngredient(ingredient.ingredient(), ingredient.count());
    }

    @Nullable
    static List<CountedIngredient> counted(@Nullable List<SizedIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (SizedIngredient sized : ingredients) {
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
            normalized.applyComponents(candidate.getComponentsPatch());
            unique.putIfAbsent(fluidKey(normalized), normalized);
        }
        return unique.isEmpty() ? null : List.copyOf(unique.values());
    }

    static boolean matchesFluid(
            Map<FluidStack, Long> available, SizedFluidIngredient required) {
        if (available == null || required == null || required.amount() <= 0) {
            return false;
        }
        for (Map.Entry<FluidStack, Long> entry : available.entrySet()) {
            if (entry.getValue() >= required.amount() && required.test(entry.getKey())) {
                return true;
            }
        }
        return false;
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
        return new FluidStack(EIOFluids.XP_JUICE.source().get(), (int) amount);
    }

    static String fluidKey(FluidStack stack) {
        return stack.getFluid().builtInRegistryHolder().key().location()
                + "|" + stack.getComponentsPatch();
    }

    static boolean sameItemStack(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty()
                && ItemStack.isSameItemSameComponents(left, right);
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
