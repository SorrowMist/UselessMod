package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

final class UfoRecipeAdapterSupport {
    private static final ResourceLocation COOLANTS_ID =
            ResourceLocation.fromNamespaceAndPath("c", "coolants");
    private static final TagKey<net.minecraft.world.level.material.Fluid> COOLANTS =
            TagKey.create(Registries.FLUID, COOLANTS_ID);

    private UfoRecipeAdapterSupport() {
    }

    static long energy(long aeEnergy) {
        return saturatingMultiply(aeEnergy, 2L);
    }

    static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    static <T> List<CountedIngredient> itemInputs(
            @Nullable Iterable<T> source,
            Function<T, Ingredient> ingredientGetter,
            ToLongFunction<T> amountGetter) {
        if (source == null) {
            return List.of();
        }
        List<CountedIngredient> result = new ArrayList<>();
        for (T value : source) {
            if (value == null) {
                continue;
            }
            Ingredient ingredient = ingredientGetter.apply(value);
            long amount = amountGetter.applyAsLong(value);
            if (ingredient != null && !ingredient.isEmpty() && amount > 0L) {
                result.add(new CountedIngredient(ingredient, amount));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    static <T> List<SizedFluidIngredient> fluidInputs(
            @Nullable Iterable<T> source,
            Function<T, FluidIngredient> ingredientGetter,
            ToLongFunction<T> amountGetter) {
        if (source == null) {
            return List.of();
        }
        List<SizedFluidIngredient> result = new ArrayList<>();
        for (T value : source) {
            if (value == null) {
                continue;
            }
            FluidIngredient ingredient = ingredientGetter.apply(value);
            long amount = amountGetter.applyAsLong(value);
            if (amount > Integer.MAX_VALUE) {
                return null;
            }
            if (ingredient != null && !ingredient.isEmpty() && amount > 0L) {
                result.add(new SizedFluidIngredient(ingredient, (int) amount));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    static <T> List<SizedFluidIngredient> concreteFluidInputs(
            @Nullable Iterable<T> source,
            Function<T, FluidStack> fluidGetter,
            ToLongFunction<T> amountGetter) {
        if (source == null) {
            return List.of();
        }
        List<SizedFluidIngredient> result = new ArrayList<>();
        for (T value : source) {
            if (value == null) {
                continue;
            }
            FluidStack fluid = fluidGetter.apply(value);
            long amount = amountGetter.applyAsLong(value);
            if (amount > Integer.MAX_VALUE) {
                return null;
            }
            if (fluid == null || fluid.isEmpty() || amount <= 0L) {
                continue;
            }
            FluidStack representative = fluid.copyWithAmount((int) amount);
            SizedFluidIngredient ingredient = AdapterUtils.toSizedFluidIngredient(representative);
            if (ingredient != null) {
                result.add(ingredient);
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    static SizedFluidIngredient namedFluid(@Nullable String id, long amount) {
        if (id == null || id.isBlank() || amount <= 0L) {
            return null;
        }
        if (amount > Integer.MAX_VALUE) {
            return null;
        }
        final ResourceLocation location;
        try {
            location = ResourceLocation.parse(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        var fluid = BuiltInRegistries.FLUID.getOptional(location).orElse(null);
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return null;
        }
        return new SizedFluidIngredient(FluidIngredient.single(fluid), (int) amount);
    }

    static SizedFluidIngredient coolant(long amount) {
        return new SizedFluidIngredient(FluidIngredient.tag(COOLANTS), AdapterUtils.safeInt(amount));
    }

    static <T> List<GenericStack> chemicalInputs(
            @Nullable Iterable<T> source,
            Function<T, ResourceLocation> idGetter,
            ToLongFunction<T> amountGetter) {
        if (source == null) {
            return List.of();
        }
        List<GenericStack> result = new ArrayList<>();
        for (T value : source) {
            if (value == null) {
                continue;
            }
            long amount = amountGetter.applyAsLong(value);
            if (amount <= 0L) {
                continue;
            }
            GenericStack converted = UfoChemicalCompat.toGenericStack(idGetter.apply(value), amount);
            if (converted == null) {
                return null;
            }
            result.add(converted);
        }
        return List.copyOf(result);
    }

    static void addItemOutput(List<ItemStack> outputs, List<GenericStack> keyOutputs,
                              @Nullable ItemStack source, long amount) {
        if (source == null || source.isEmpty() || amount <= 0L) {
            return;
        }
        if (amount <= Integer.MAX_VALUE) {
            outputs.add(source.copyWithCount((int) amount));
            return;
        }
        GenericStack key = GenericStack.fromItemStack(source.copyWithCount(1));
        if (key != null && key.what() != null) {
            keyOutputs.add(new GenericStack(key.what(), amount));
        }
    }

    static void addFluidOutput(List<FluidStack> outputs, List<GenericStack> keyOutputs,
                               @Nullable FluidStack source, long amount) {
        if (source == null || source.isEmpty() || amount <= 0L) {
            return;
        }
        if (amount <= Integer.MAX_VALUE) {
            outputs.add(source.copyWithAmount((int) amount));
            return;
        }
        GenericStack key = GenericStack.fromFluidStack(source.copyWithAmount(1));
        if (key != null && key.what() != null) {
            keyOutputs.add(new GenericStack(key.what(), amount));
        }
    }

    static void addGenericOutputs(List<GenericStack> target, @Nullable Iterable<GenericStack> source) {
        if (source == null) {
            return;
        }
        for (GenericStack output : source) {
            if (output != null && output.what() != null && output.amount() > 0L) {
                target.add(output);
            }
        }
    }

    static boolean matches(AdvancedAlloyFurnaceRecipe recipe,
                           @Nullable Map<Ingredient, Long> mergedInputs,
                           @Nullable Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
                           @Nullable Map<AEKey, Long> mergedKeys) {
        if (recipe == null) {
            return false;
        }
        Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input != null && input.ingredient() != null && input.count() > 0L) {
                requiredItems.merge(input.ingredient(), input.count(), UfoRecipeAdapterSupport::saturatingAdd);
            }
        }
        if (!AdapterUtils.matchesRequired(mergedInputs == null ? Map.of() : mergedInputs, requiredItems)) {
            return false;
        }
        if (!FluidIngredientAllocator.matchesLong(recipe.inputFluids(),
                mergedFluids == null ? Map.of() : mergedFluids, 1L)) {
            return false;
        }

        Map<AEKey, Long> requiredKeys = new LinkedHashMap<>();
        for (GenericStack input : recipe.keyInputs()) {
            if (input != null && input.what() != null && input.amount() > 0L) {
                requiredKeys.merge(input.what(), input.amount(), UfoRecipeAdapterSupport::saturatingAdd);
            }
        }
        Map<AEKey, Long> availableKeys = mergedKeys == null ? Map.of() : mergedKeys;
        for (Map.Entry<AEKey, Long> required : requiredKeys.entrySet()) {
            if (availableKeys.getOrDefault(required.getKey(), 0L) < required.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
