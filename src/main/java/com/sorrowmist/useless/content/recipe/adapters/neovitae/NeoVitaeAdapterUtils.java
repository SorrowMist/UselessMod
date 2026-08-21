package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NeoVitaeAdapterUtils {
    private static final BigDecimal FE_PER_COST = BigDecimal.valueOf(10_000L);
    private static final BigDecimal MAX_FE = BigDecimal.valueOf(Integer.MAX_VALUE);

    private NeoVitaeAdapterUtils() {
    }

    static long energyFor(double cost) {
        if (Double.isNaN(cost) || cost < 0.0) {
            return AdapterUtils.DEFAULT_ENERGY;
        }
        if (cost == 0.0) {
            return AdapterUtils.DEFAULT_ENERGY;
        }
        if (Double.isInfinite(cost)) {
            return Integer.MAX_VALUE;
        }

        BigDecimal scaled = BigDecimal.valueOf(cost)
                .multiply(FE_PER_COST)
                .setScale(0, RoundingMode.CEILING);
        return scaled.compareTo(MAX_FE) >= 0 ? Integer.MAX_VALUE : Math.max(1L, scaled.longValue());
    }

    static double sumCosts(Iterable<Double> costs) {
        BigDecimal total = BigDecimal.ZERO;
        if (costs != null) {
            for (Double cost : costs) {
                if (cost == null || Double.isNaN(cost) || cost <= 0.0) continue;
                if (Double.isInfinite(cost)) return Double.POSITIVE_INFINITY;
                total = total.add(BigDecimal.valueOf(cost));
            }
        }
        return total.doubleValue();
    }

    static int ceilDivide(long numerator, long denominator) {
        if (numerator <= 0L) return 0;
        if (denominator <= 0L) return 0;
        long result = (numerator - 1L) / denominator + 1L;
        return AdapterUtils.safeInt(result);
    }

    static List<CountedIngredient> counted(List<Ingredient> ingredients) {
        return AdapterUtils.mergeIngredients(ingredients == null ? List.of() : ingredients);
    }

    static List<CountedIngredient> append(List<CountedIngredient> inputs, Ingredient ingredient) {
        List<CountedIngredient> result = new ArrayList<>(inputs == null ? List.of() : inputs);
        if (ingredient != null && !ingredient.isEmpty()) {
            result.add(new CountedIngredient(ingredient, 1L));
        }
        return List.copyOf(result);
    }

    static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
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

    static boolean matchesItems(List<CountedIngredient> requirements, List<ItemStack> actualInputs) {
        return ItemIngredientAllocator.matches(requirements, actualInputs, 1L);
    }

    static boolean matchesItems(Map<Ingredient, Long> available, List<CountedIngredient> requirements) {
        return AdapterUtils.matchesRequired(available, requirements(requirements));
    }

    static boolean matchesFluids(@Nullable Map<FluidStack, Long> available,
                                 @Nullable SizedFluidIngredient requirement) {
        if (requirement == null) return true;
        return com.sorrowmist.useless.content.recipe.FluidIngredientAllocator.matches(
                List.of(requirement), available, 1L);
    }

    static List<SizedFluidIngredient> inputFluid(@Nullable SizedFluidIngredient input) {
        return input == null ? List.of() : List.of(input);
    }

    static List<FluidStack> outputFluid(@Nullable FluidStack output) {
        return output == null || output.isEmpty() ? List.of() : List.of(output.copy());
    }

    static AdvancedAlloyFurnaceRecipe recipe(
            ResourceLocation id,
            List<CountedIngredient> inputs,
            List<SizedFluidIngredient> inputFluids,
            List<ItemStack> outputs,
            List<FluidStack> outputFluids,
            long energy,
            int processTime,
            List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                inputFluids,
                List.of(),
                copies(outputs),
                fluidCopies(outputFluids),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    static List<ItemStack> copies(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                    result.add(stack.copy());
                }
            }
        }
        return List.copyOf(result);
    }

    static List<FluidStack> fluidCopies(List<FluidStack> stacks) {
        List<FluidStack> result = new ArrayList<>();
        if (stacks != null) {
            for (FluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0) {
                    result.add(stack.copy());
                }
            }
        }
        return List.copyOf(result);
    }

    static ResourceLocation variantId(ResourceLocation source, ItemStack... stacks) {
        StringBuilder suffix = new StringBuilder("_converted");
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                suffix.append('_').append(itemId.getNamespace()).append('_')
                        .append(itemId.getPath().replace('/', '_'));
            }
        }
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath() + suffix);
    }

    static List<ItemStack> distinctMatches(List<ItemStack> inputs, Ingredient ingredient) {
        List<ItemStack> result = new ArrayList<>();
        if (inputs == null || ingredient == null || ingredient.isEmpty()) return result;
        for (ItemStack input : inputs) {
            if (input == null || input.isEmpty() || !ingredient.test(input)
                    || result.stream().anyMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, input))) {
                continue;
            }
            result.add(input.copyWithCount(1));
        }
        return result;
    }

    static ItemStack representative(Ingredient ingredient) {
        ItemStack stack = AdapterUtils.itemRepresentative(ingredient);
        return stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    static List<ItemStack> representatives(List<Ingredient> ingredients) {
        List<ItemStack> result = new ArrayList<>();
        if (ingredients == null) return result;
        for (Ingredient ingredient : ingredients) {
            ItemStack stack = representative(ingredient);
            if (stack.isEmpty()) return List.of();
            result.add(stack);
        }
        return result;
    }

    static List<ItemStack> candidates(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return List.of();
        List<ItemStack> result = new ArrayList<>();
        try {
            for (ItemStack stack : ingredient.getItems()) {
                if (stack != null && !stack.isEmpty()
                        && result.stream().noneMatch(existing ->
                        ItemStack.isSameItemSameComponents(existing, stack))) {
                    result.add(stack.copyWithCount(1));
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the shared representative probe for custom ingredients.
        }
        if (result.isEmpty()) {
            ItemStack representative = representative(ingredient);
            if (!representative.isEmpty()) result.add(representative);
        }
        return List.copyOf(result);
    }

    static boolean sameItemAndCount(ItemStack expected, ItemStack actual) {
        return expected != null && !expected.isEmpty()
                && actual != null && !actual.isEmpty()
                && expected.getCount() == actual.getCount()
                && expected.is(actual.getItem());
    }
}
