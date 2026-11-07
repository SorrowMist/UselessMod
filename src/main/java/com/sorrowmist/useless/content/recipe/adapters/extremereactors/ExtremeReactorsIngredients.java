package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.google.gson.JsonObject;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import it.zerono.mods.zerocore.lib.recipe.ingredient.FluidStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.ingredient.ItemStackRecipeIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Lossless, fail-closed ingredient bridge for ZeroCore2 1.20.1-2.1.45. */
final class ExtremeReactorsIngredients {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_CHOICES = 256;

    private ExtremeReactorsIngredients() {
    }

    static CountedIngredient item(ItemStackRecipeIngredient source) {
        return item(source, 0);
    }

    private static CountedIngredient item(ItemStackRecipeIngredient source, int depth) {
        require(source != null && depth <= MAX_DEPTH, "Missing or excessively nested item ingredient");
        if (implementation(source, ItemStackRecipeIngredient.class, "$Impl")) {
            // asVanillaIngredients() is lossless ONLY for Impl. CompositeImpl materializes
            // display stacks here, losing both live tags and StrictNBTIngredient predicates.
            List<Ingredient> ingredients = source.asVanillaIngredients();
            require(ingredients.size() == 1 && ingredients.get(0) != null
                    && !ingredients.get(0).isEmpty(), "Empty native item ingredient");
            int count = source.serializeTo().getAsJsonObject().get("count").getAsInt();
            require(count > 0, "Nonpositive native item ingredient count");
            return new CountedIngredient(ingredients.get(0), count);
        }
        require(implementation(source, ItemStackRecipeIngredient.class, "$CompositeImpl"),
                "Unknown ZeroCore item ingredient implementation: " + source.getClass().getName());
        List<Ingredient> alternatives = new ArrayList<>();
        long amount = -1L;
        for (ItemStackRecipeIngredient child : children(source, ItemStackRecipeIngredient.class)) {
            CountedIngredient converted = item(child, depth + 1);
            require(amount < 0L || amount == converted.count(),
                    "Item alternatives with different consumption amounts cannot be represented statically");
            amount = converted.count();
            alternatives.add(converted.ingredient());
        }
        return new CountedIngredient(CompoundIngredient.of(alternatives.toArray(Ingredient[]::new)), amount);
    }

    static SizedFluidIngredient fluid(FluidStackRecipeIngredient source) {
        return fluid(source, 0);
    }

    private static SizedFluidIngredient fluid(FluidStackRecipeIngredient source, int depth) {
        require(source != null && depth <= MAX_DEPTH, "Missing or excessively nested fluid ingredient");
        if (implementation(source, FluidStackRecipeIngredient.class, "$Impl")) {
            List<FluidStack> stacks = source.getMatchingElements();
            require(stacks.size() == 1 && stacks.get(0) != null && !stacks.get(0).isEmpty(),
                    "Empty native fluid ingredient");
            FluidStack stack = stacks.get(0);
            // Native Impl uses FluidStack.isFluidEqual, including exact NBT equality even
            // when the required tag is null. An untagged stack must NOT accept tagged fluid.
            return new SizedFluidIngredient(FluidIngredient.nbt(true, stack), stack.getAmount());
        }
        if (implementation(source, FluidStackRecipeIngredient.class, "$TaggedImpl")) {
            // Do not call getMatchingElements(): ZeroCore caches its display list forever.
            JsonObject json = source.serializeTo().getAsJsonObject();
            int count = json.get("count").getAsInt();
            require(count > 0, "Nonpositive native fluid tag amount");
            return new SizedFluidIngredient(FluidIngredient.tag(TagKey.create(Registries.FLUID,
                    new ResourceLocation(json.get("tag").getAsString()))), count);
        }
        require(implementation(source, FluidStackRecipeIngredient.class, "$CompositeImpl"),
                "Unknown ZeroCore fluid ingredient implementation: " + source.getClass().getName());
        List<FluidIngredient> alternatives = new ArrayList<>();
        int amount = -1;
        for (FluidStackRecipeIngredient child : children(source, FluidStackRecipeIngredient.class)) {
            SizedFluidIngredient converted = fluid(child, depth + 1);
            require(amount < 0 || amount == converted.amount(),
                    "Fluid alternatives with different consumption amounts cannot be represented statically");
            amount = converted.amount();
            alternatives.add(converted.ingredient());
        }
        return new SizedFluidIngredient(FluidIngredient.any(alternatives), amount);
    }

    private static boolean implementation(Object source, Class<?> owner, String suffix) {
        return source.getClass().getEnclosingClass() == owner
                && source.getClass().getName().equals(owner.getName() + suffix);
    }

    private static <T> List<T> children(Object source, Class<T> type) {
        // ZeroCore's package-private CompositeImpl exposes no child accessor. Its public
        // serialization cannot prove that a nested custom implementation has native semantics.
        // Read only the javap-verified 2.1.45 field; if access/layout changes, reject rather
        // than rebuilding from display stacks or trusting an unknown child's JSON facade.
        try {
            Field field = source.getClass().getDeclaredField("_ingredients");
            field.setAccessible(true);
            Object value = field.get(source);
            require(value instanceof List<?>, "Invalid native composite ingredient list");
            List<?> values = (List<?>) value;
            require(!values.isEmpty() && values.size() <= MAX_CHOICES, "Empty or oversized ingredient alternatives");
            List<T> result = new ArrayList<>(values.size());
            for (Object child : values) {
                require(type.isInstance(child), "Invalid native composite ingredient child");
                result.add(type.cast(child));
            }
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Cannot inspect ZeroCore composite ingredients safely", exception);
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
