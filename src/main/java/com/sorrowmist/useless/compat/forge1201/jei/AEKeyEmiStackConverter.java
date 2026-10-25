package com.sorrowmist.useless.compat.forge1201.jei;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.jei.AEKeyJeiIngredients;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.Nullable;

/** Connects JEMI custom ingredients to AE2's native fill, drag/drop and inventory paths. */
public final class AEKeyEmiStackConverter implements EmiStackConverter {
    public static void register() {
        if (EmiStackConverters.getConverters().stream().anyMatch(AEKeyEmiStackConverter.class::isInstance)) return;
        if (!EmiStackConverters.register(new AEKeyEmiStackConverter())) {
            RateLimitedLogger.warn(LogUtils.getLogger(), "Could not register the JEMI AE-key stack converter");
        }
    }

    @Override public Class<?> getKeyType() {
        // Custom JemiStack keys are their JEI UID strings; items/fluids keep AE2's converters.
        return String.class;
    }

    @Override public @Nullable EmiStack toEmiStack(GenericStack stack) {
        if (stack == null || stack.what() == null || stack.amount() <= 0
                || stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey) return null;
        return new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(stack.what(), stack.amount()));
    }

    @Override public @Nullable GenericStack toGenericStack(EmiStack stack) {
        if (!(stack instanceof AEKeyEmiStack key) || stack.getAmount() <= 0) return null;
        return new GenericStack(key.ingredient.key(), stack.getAmount());
    }
}
