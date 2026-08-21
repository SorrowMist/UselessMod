package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Optional UFO/Mekanism/AppMek bridge kept out of the normal recipe path. */
final class UfoChemicalCompat {
    private static final String UFO_COMPAT_CLASS =
            "com.raishxn.ufo.compat.mekanism.MekanismChemicalCompat";
    private static final String MEKANISM_STACK_CLASS = "mekanism.api.chemical.ChemicalStack";
    private static final String FURNACE_CHEMICAL_COMPAT_CLASS =
            "com.sorrowmist.useless.compat.mekanism.MekanismChemicalCompat";

    private UfoChemicalCompat() {
    }

    @Nullable
    static GenericStack toGenericStack(ResourceLocation chemicalId, long amount) {
        if (chemicalId == null || amount <= 0L) {
            return null;
        }

        try {
            ClassLoader loader = UfoChemicalCompat.class.getClassLoader();
            Class<?> ufoCompat = Class.forName(UFO_COMPAT_CLASS, true, loader);
            Method createStack = ufoCompat.getMethod("createStack", ResourceLocation.class, long.class);
            Object chemical = createStack.invoke(null, chemicalId, amount);
            if (chemical == null) {
                return null;
            }

            Class<?> chemicalStack = Class.forName(MEKANISM_STACK_CLASS, true, loader);
            Class<?> furnaceCompat = Class.forName(FURNACE_CHEMICAL_COMPAT_CLASS, true, loader);
            Method toGenericStack = furnaceCompat.getMethod("toGenericStack", chemicalStack);
            Object generic = toGenericStack.invoke(null, chemical);
            return generic instanceof GenericStack stack && stack.what() != null && stack.amount() > 0L
                    ? stack
                    : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
