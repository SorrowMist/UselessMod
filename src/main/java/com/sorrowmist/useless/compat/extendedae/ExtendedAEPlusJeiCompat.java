package com.sorrowmist.useless.compat.extendedae;

import com.sorrowmist.useless.UselessMod;

import java.lang.reflect.Method;

public final class ExtendedAEPlusJeiCompat {
    private static final String[] API_CLASS_NAMES = {
            "com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig",
            "com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil"
    };

    private static volatile Method lastProcessingNameSetter = resolveSetter();

    private ExtendedAEPlusJeiCompat() {
    }

    public static void presetAlloyFurnaceSearchKey() {
        Method setter = lastProcessingNameSetter;
        if (setter == null) {
            return;
        }

        try {
            setter.invoke(null, "Omni Alloy Furnace");
        } catch (ReflectiveOperationException | LinkageError exception) {
            lastProcessingNameSetter = null;
            UselessMod.LOGGER.warn(
                    "Failed to preset the ExtendedAE-Plus provider search key; skipping this preset. Cause: {}",
                    exception.toString());
        }
    }

    private static Method resolveSetter() {
        Throwable lastFailure = null;
        for (String className : API_CLASS_NAMES) {
            try {
                Class<?> apiClass = Class.forName(
                        className, false, ExtendedAEPlusJeiCompat.class.getClassLoader());
                return apiClass.getMethod("setLastProcessingName", String.class);
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                lastFailure = exception;
            } catch (LinkageError error) {
                lastFailure = error;
            }
        }

        UselessMod.LOGGER.warn(
                "No supported ExtendedAE-Plus provider search-key API was found; skipping this preset. Cause: {}",
                lastFailure == null ? "entry points not found" : lastFailure.toString());
        return null;
    }
}
