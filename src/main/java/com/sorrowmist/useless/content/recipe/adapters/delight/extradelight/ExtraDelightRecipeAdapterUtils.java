package com.sorrowmist.useless.content.recipe.adapters.delight.extradelight;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import com.vomiter.extradelight.common.recipes.ingredients.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
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
                result.add(DelightRecipeAdapterUtils.exactStack(container));
            }
        }
        return List.copyOf(result);
    }

    static List<LongSizedFluidIngredient> fluids(List<SizedFluidIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return ingredients.stream().map(ExtraDelightRecipeAdapterUtils::nativeFluid).toList();
    }

    /** The backport's ingredient is its own Forge Ingredient, not NeoForge's fluid type. */
    static LongSizedFluidIngredient nativeFluid(SizedFluidIngredient source) {
        if (source == null || source.getAmount() <= 0) {
            throw new IllegalArgumentException("Invalid Extra Delight fluid ingredient");
        }
        // Verified native serialization: {fluid:{tag|fluid, amount}}. Preserve live tags,
        // including tags currently without members; never snapshot getFluids().
        var json = source.toJson().getAsJsonObject().getAsJsonObject("fluid");
        FluidIngredient ingredient;
        if (json.has("tag")) {
            ingredient = FluidIngredient.tag(TagKey.create(Registries.FLUID,
                    new ResourceLocation(json.get("tag").getAsString())));
        } else {
            var id = new ResourceLocation(json.get("fluid").getAsString());
            var fluid = BuiltInRegistries.FLUID.getOptional(id).orElseThrow();
            ingredient = FluidIngredient.single(new FluidStack(fluid, 1));
        }
        return new LongSizedFluidIngredient(ingredient, (long) source.getAmount());
    }

    static List<LongSizedFluidIngredient> fluid(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) {
            return List.of();
        }
        return List.of(LongSizedFluidIngredient.from(AdapterUtils.toSizedFluidIngredient(stack)));
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
        return new net.minecraftforge.fluids.FluidStack(fluid, (int) amount);
    }
}
