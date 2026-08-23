package com.sorrowmist.useless.api.recipe;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Entry point for third-party alloy-furnace compatibility modules.
 *
 * <pre>{@code
 * event.enqueueWork(() -> AlloyFurnaceCompatApi.register(
 *         "example_compat", Config.ENABLED::get, new ExampleRecipeAdapter()));
 * }</pre>
 */
public final class AlloyFurnaceCompatApi {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AlloyFurnaceCompatApi() {
    }

    /** Registers adapters with a source that is always enabled. */
    public static void register(String sourceId, IRecipeAdapter<?>... adapters) {
        register(sourceId, () -> true, adapters);
    }

    /**
     * Registers adapters when {@code enabled} is true.
     *
     * <p>The supplier is evaluated once during registration. Configuration changes should take
     * effect after the normal restart required by the owning mod's configuration.</p>
     */
    public static void register(String sourceId, BooleanSupplier enabled,
                                IRecipeAdapter<?>... adapters) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(enabled, "enabled");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId cannot be blank");
        }
        if (adapters == null || adapters.length == 0) {
            return;
        }

        final boolean isEnabled;
        try {
            isEnabled = enabled.getAsBoolean();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to evaluate alloy-furnace compatibility switch: source={}",
                    sourceId, exception);
            return;
        }
        if (!isEnabled) {
            return;
        }

        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        for (IRecipeAdapter<?> adapter : adapters) {
            if (adapter == null) {
                LOGGER.warn("Skipping null alloy-furnace compatibility adapter: source={}", normalizedSource);
                continue;
            }
            try {
                manager.registerAdapter(adapter, normalizedSource);
            } catch (RuntimeException | LinkageError exception) {
                LOGGER.error("Failed to register alloy-furnace compatibility adapter: source={}, adapter={}",
                        normalizedSource, adapter.getClass().getName(), exception);
            }
        }
    }
}
