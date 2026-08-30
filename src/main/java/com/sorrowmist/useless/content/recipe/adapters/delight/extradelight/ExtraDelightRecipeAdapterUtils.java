package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ExtraDelightRecipeAdapterUtils {
    private static final int BASE_PROCESS_TIME = 200;

    private ExtraDelightRecipeAdapterUtils() {
    }

    @Nullable
    static ItemStack mold(ResourceLocation id) {
        Item item = DelightRecipeAdapterUtils.registeredItem(id);
        return item == null ? null : item.getDefaultInstance();
    }

    static List<Ingredient> withContainer(List<Ingredient> ingredients, @Nullable ItemStack container) {
        List<Ingredient> result = new ArrayList<>();
        if (ingredients != null) {
            result.addAll(ingredients);
        }
        if (!DelightRecipeAdapterUtils.isBakingTray(container)
                && container != null && !container.isEmpty() && container.getCount() > 0) {
            for (int i = 0; i < container.getCount(); i++) {
                result.add(Ingredient.of(container.copyWithCount(1)));
            }
        }
        return List.copyOf(result);
    }

    static List<LongSizedFluidIngredient> fluids(List<SizedFluidIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return ingredients.stream()
                .filter(ingredient -> ingredient != null && !ingredient.ingredient().isEmpty()
                        && ingredient.amount() > 0)
                .map(LongSizedFluidIngredient::from)
                .toList();
    }

    static List<LongSizedFluidIngredient> fluid(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) {
            return List.of();
        }
        return List.of(LongSizedFluidIngredient.from(stack));
    }

    static int processTime(int time) {
        return Math.max(1, time);
    }

    static long energy(int time) {
        return Math.max(1L, (long) processTime(time) * AdapterUtils.DEFAULT_ENERGY / BASE_PROCESS_TIME);
    }

    static List<CountedIngredient> scaleInputs(List<CountedIngredient> inputs, int operations) {
        if (inputs == null || inputs.isEmpty() || operations <= 0) {
            return List.of();
        }
        List<CountedIngredient> result = new ArrayList<>(inputs.size());
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.count() <= 0) {
                return List.of();
            }
            long count;
            try {
                count = Math.multiplyExact(input.count(), (long) operations);
            } catch (ArithmeticException exception) {
                return List.of();
            }
            result.add(new CountedIngredient(input.ingredient(), count));
        }
        return List.copyOf(result);
    }

    @Nullable
    static FluidStack scaledFluid(@Nullable FluidStack fluid, int operations) {
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() <= 0 || operations <= 0) {
            return null;
        }
        var amount = Math.multiplyExact((long) fluid.getAmount(), (long) operations);
        if (amount > Integer.MAX_VALUE) {
            return null;
        }
        return fluid.copyWithAmount((int) amount);
    }
}
