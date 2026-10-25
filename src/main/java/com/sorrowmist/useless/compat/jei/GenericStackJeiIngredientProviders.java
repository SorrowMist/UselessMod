package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import appeng.api.integrations.jei.IngredientConverter;
import appeng.api.integrations.jei.IngredientConverters;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.ItemIngredientConverter;
import appeng.integration.modules.jei.FluidIngredientConverter;
import com.mojang.logging.LogUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Use AE2 15's live converter registry, never a wrapped ItemStack as a JEI ingredient. */
public final class GenericStackJeiIngredientProviders {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final ItemIngredientConverter ITEMS = new ItemIngredientConverter();
    private static final FluidIngredientConverter FLUIDS = new FluidIngredientConverter();
    private static volatile GenericStackJeiIngredientProvider optionalProvider = GenericStackJeiIngredientProvider.NONE;
    private static volatile boolean initialized;
    private GenericStackJeiIngredientProviders() {}

    public static synchronized void initialize() {
        if (initialized) return;
        if (ModList.get().isLoaded("appmek")) {
            try {
                optionalProvider = (GenericStackJeiIngredientProvider) Class.forName(
                        "com.sorrowmist.useless.compat.appmek.AppMekJeiChemicalCompat")
                        .getMethod("createProvider").invoke(null);
            } catch (ReflectiveOperationException | LinkageError exception) {
                RateLimitedLogger.warn(LOGGER, "Could not enable native Mekanism chemical JEI rendering", exception);
            }
        }
        initialized = true;
    }

    public static @Nullable GenericStackJeiIngredientProvider.Ingredient resolve(GenericStack stack) {
        if (!valid(stack)) return null;
        // JEMI 1.1 does not preserve helper amounts and native int stacks truncate long values.
        // Its dedicated serializer stores the full key, so EMI does not need the JEI UID catalogue.
        if (ModList.get() != null && ModList.get().isLoaded("emi")) return new GenericStackJeiIngredientProvider.Ingredient(
                AEKeyJeiIngredients.TYPE, new AEKeyJeiIngredients.KeyIngredient(stack.what(), stack.amount()));
        var nativeIngredient = resolveNative(stack);
        return nativeIngredient != null ? nativeIngredient : AEKeyJeiIngredients.resolve(stack);
    }

    /** Also used by the fallback converter; exclude itself to avoid recursive resolution. */
    public static @Nullable GenericStackJeiIngredientProvider.Ingredient resolveNative(GenericStack stack) {
        if (!valid(stack)) return null;
        var item = convert(stack, ITEMS);
        if (item != null) return item;
        var fluid = convert(stack, FLUIDS);
        if (fluid != null) return fluid;
        var chemical = optionalProvider.resolve(stack);
        if (chemical != null) return chemical;
        // Fetch on every call: Applied Mekanistics registers its four converters at runtime,
        // after JEI's category registration, and other addons may do the same.
        for (var converter : IngredientConverters.getConverters()) {
            if (converter == AEKeyJeiIngredients.CONVERTER) continue;
            var converted = convert(stack, converter);
            if (converted != null) return converted;
        }
        return null;
    }

    /** Read the live addon converters: AppMek installs its four types at JEI runtime creation. */
    public static @Nullable GenericStack fromJei(IIngredientType<?> type, Object ingredient) {
        if (type == null || ingredient == null || !type.getIngredientClass().isInstance(ingredient)) return null;
        for (var converter : IngredientConverters.getConverters()) {
            var stack = fromJei(type, ingredient, converter);
            if (valid(stack)) return stack;
        }
        return null;
    }

    private static <T> @Nullable GenericStack fromJei(
            IIngredientType<?> type, Object ingredient, IngredientConverter<T> converter) {
        try {
            if (converter.getIngredientType() != type) return null;
            return converter.getStackFromIngredient(converter.getIngredientType().getIngredientClass().cast(ingredient));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static boolean valid(GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }

    private static <T> @Nullable GenericStackJeiIngredientProvider.Ingredient convert(
            GenericStack stack, IngredientConverter<T> converter) {
        try {
            var type = converter.getIngredientType();
            T ingredient = converter.getIngredientFromStack(stack);
            if (type == null || ingredient == null || !type.getIngredientClass().isInstance(ingredient)) return null;
            // A custom slot renderer cannot fix bookmark identity, so reject wrapped fallback items.
            if (type == VanillaTypes.ITEM_STACK && ingredient instanceof ItemStack item
                    && (item.isEmpty() || GenericStack.isWrapped(item))) return null;
            return new GenericStackJeiIngredientProvider.Ingredient(type, ingredient);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }
}
