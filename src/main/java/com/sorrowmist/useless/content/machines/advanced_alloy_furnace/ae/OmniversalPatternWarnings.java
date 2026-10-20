package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import org.slf4j.Logger;

import java.util.List;

/** Reports actionable pattern failures without logging normal encoding and dispatch work. */
public final class OmniversalPatternWarnings {
    private static final Logger LOGGER = LogUtils.getLogger();

    private OmniversalPatternWarnings() {}

    public static void wrapFailed(String kind, Object definition, Exception exception) {
        RateLimitedLogger.warn(LOGGER, "Omniversal {} pattern wrapping failed for {}; keeping the plain pattern",
                kind, definition, exception);
    }

    public static void decodeFailed(Object definition, OmniversalPatternData data, Exception exception) {
        RateLimitedLogger.warn(LOGGER, "Omniversal pattern decode failed for {} (recipe={}, fingerprint={})",
                definition, data == null ? "<no data>" : data.recipeId(),
                data == null ? "<no data>" : data.recipeFingerprint(), exception);
    }

    public static void idOnlySlotsNarrowed(Object definition, List<Integer> proven, List<Integer> derived) {
        RateLimitedLogger.warn(LOGGER, "Omniversal pattern {} decoded with fewer id-only input slots {} than proven {}; keeping the wider set",
                definition, derived, proven);
    }

    public static void notPublished(String owner, Object slot, String reason) {
        RateLimitedLogger.warn(LOGGER, "Omniversal pattern not published by {} (slot {}): {}", owner, slot, reason);
    }
}
