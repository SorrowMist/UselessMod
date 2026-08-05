package com.sorrowmist.useless.compat.jei;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.UselessMod;
import mezz.jei.api.ingredients.IIngredientType;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Uses every registered AE2 JEI ingredient converter, with optional local fallbacks. */
public final class GenericStackJeiIngredientProviders {
    private static final String APPMEK_MOD_ID = "appmek";
    private static final String APPMEK_PROVIDER_CLASS =
            "com.sorrowmist.useless.compat.appmek.AppMekJeiChemicalCompat";
    private static final String CONVERTERS_CLASS =
            "tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters";
    private static final String CONVERTER_INTERFACE =
            "tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter";

    private static volatile GenericStackJeiIngredientProvider optionalProvider =
            GenericStackJeiIngredientProvider.NONE;
    private static volatile @Nullable ConverterAccess converterAccess;
    private static volatile boolean initialized;

    private GenericStackJeiIngredientProviders() {
    }

    public static void initialize() {
        if (initialized) return;
        synchronized (GenericStackJeiIngredientProviders.class) {
            if (initialized) return;
            initialized = true;

            converterAccess = findConverterAccess();
            if (ModList.get().isLoaded(APPMEK_MOD_ID)) {
                optionalProvider = loadOptionalProvider();
            }
        }
    }

    public static @Nullable GenericStackJeiIngredientProvider.Ingredient resolve(GenericStack stack) {
        GenericStackJeiIngredientProvider.Ingredient fallbackIngredient = optionalProvider.resolve(stack);
        if (fallbackIngredient != null) {
            return fallbackIngredient;
        }

        ConverterAccess access = converterAccess;
        if (access != null) {
            try {
                Object converters = access.getConverters().invoke(null);
                if (converters instanceof Iterable<?> iterable) {
                    for (Object converter : iterable) {
                        GenericStackJeiIngredientProvider.Ingredient ingredient =
                                resolveConvertedIngredient(stack, converter, access);
                        if (ingredient != null) {
                            return ingredient;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A missing or incompatible optional converter should not hide the recipe.
            }
        }

        return null;
    }

    @Nullable
    private static GenericStackJeiIngredientProvider.Ingredient resolveConvertedIngredient(
            GenericStack stack, Object converter, ConverterAccess access) {
        try {
            Object ingredientType = access.getIngredientType().invoke(converter);
            Object ingredient = access.getIngredientFromStack().invoke(converter, stack);
            if (!(ingredientType instanceof IIngredientType<?> type) || ingredient == null) {
                return null;
            }
            return new GenericStackJeiIngredientProvider.Ingredient(type, ingredient);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static GenericStackJeiIngredientProvider loadOptionalProvider() {
        try {
            Object instance = Class.forName(APPMEK_PROVIDER_CLASS)
                    .getMethod("createProvider")
                    .invoke(null);
            return instance instanceof GenericStackJeiIngredientProvider loaded
                    ? loaded
                    : GenericStackJeiIngredientProvider.NONE;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | LinkageError exception) {
            UselessMod.LOGGER.warn("Could not enable native Mekanism chemical JEI rendering", exception);
            return GenericStackJeiIngredientProvider.NONE;
        }
    }

    @Nullable
    private static ConverterAccess findConverterAccess() {
        try {
            Class<?> convertersClass = Class.forName(CONVERTERS_CLASS);
            Class<?> converterInterface = Class.forName(CONVERTER_INTERFACE);
            return new ConverterAccess(
                    convertersClass.getMethod("getConverters"),
                    converterInterface.getMethod("getIngredientType"),
                    converterInterface.getMethod("getIngredientFromStack", GenericStack.class));
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            return null;
        }
    }

    private record ConverterAccess(Method getConverters,
                                    Method getIngredientType,
                                    Method getIngredientFromStack) {
    }
}
