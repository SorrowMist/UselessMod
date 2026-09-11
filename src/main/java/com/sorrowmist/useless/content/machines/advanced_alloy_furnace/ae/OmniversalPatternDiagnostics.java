package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opt-in diagnostics for the omniversal pattern encoding pipeline.
 *
 * <p>Every gate in the encoding pipeline fails silently by design: a pattern that cannot be bound to
 * a recipe must stay a plain AE2 processing pattern rather than throw. That makes a report like
 * "the pattern refuses to encode" impossible to attribute to a specific gate. These hooks only emit
 * when debug logging is enabled for this mod, so the hot paths stay free in production.
 *
 * <p>Decode and publish failures are always reported, rate limited per key: a crafting entry that
 * silently disappears is indistinguishable from a pattern that was never written.
 */
public final class OmniversalPatternDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, AtomicInteger> WARNING_COUNTS = new ConcurrentHashMap<>();
    private static final int MAX_WARNINGS_PER_KEY = 3;

    private OmniversalPatternDiagnostics() {
    }

    public static boolean enabled() {
        return LOGGER.isDebugEnabled();
    }

    public static void skip(String stage) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("omniversal encoding skipped at [{}]", stage);
        }
    }

    public static void skip(String stage, Object detail) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("omniversal encoding skipped at [{}]: {}", stage, detail);
        }
    }

    public static void identity(String stage, AlloyFurnaceRecipeIdentity identity, String sourceId) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        if (identity == null) {
            LOGGER.debug("omniversal encoding [{}]: no recipe identity (source={})", stage, sourceId);
            return;
        }
        LOGGER.debug("omniversal encoding [{}]: recipe={}, fingerprint={}, source={}",
                stage, identity.recipeId(), identity.fingerprint(), sourceId);
    }

    /** Always-visible: a JEI pick is on record, so an encode attempt is expected to convert. */
    public static void attempt(AlloyFurnaceRecipeIdentity identity, String sourceId) {
        LOGGER.info("Omniversal pattern encoding attempt: recipe={}, source={}",
                identity == null ? "<null>" : identity.recipeId(), sourceId);
    }

    /** Always-visible: a conversion that had a JEI pick still fell back to a plain pattern. */
    public static void blocked(String stage) {
        LOGGER.info("Omniversal pattern conversion blocked at [{}]", stage);
    }

    public static void blocked(String stage, Object detail) {
        LOGGER.info("Omniversal pattern conversion blocked at [{}]: {}", stage, detail);
    }

    public static void converted(AlloyFurnaceRecipeIdentity identity) {
        LOGGER.info("Omniversal pattern written for recipe={}",
                identity == null ? "<null>" : identity.recipeId());
    }

    /** A definition could not be wrapped; the caller keeps the plain, unwrapped pattern. */
    public static void wrapFailed(String kind, Object definition, Exception exception) {
        if (warnLimited("wrap|" + kind + "|" + definition)) {
            LOGGER.warn("omniversal {} pattern wrapping failed for {}, keeping the plain pattern: {}",
                    kind, definition, exception.toString());
        }
    }

    /** A decode threw. The failure is not cached, so the next lookup retries it. */
    public static void decodeFailed(Object definition, OmniversalPatternData data, Exception exception) {
        if (warnLimited("decode|" + definition)) {
            LOGGER.warn("omniversal pattern decode failed for {} (recipe={}, fingerprint={}): {}",
                    definition,
                    data == null ? "<no data>" : data.recipeId(),
                    data == null ? "<no data>" : data.recipeFingerprint(),
                    exception.toString());
        }
    }

    /**
     * A definition decoded to fewer id-only input slots than it already proved, which would make it
     * require the exact NBT those slots were encoded to ignore. The wider set is kept.
     */
    public static void idOnlySlotsNarrowed(Object definition, List<Integer> proven, List<Integer> derived) {
        if (warnLimited("narrow|" + definition)) {
            LOGGER.warn("omniversal pattern {} decoded with fewer id-only input slots {} than already "
                            + "proven {}, keeping the wider set",
                    definition, derived, proven);
        }
    }

    /** A machine could not publish a pattern to AE2, so the crafting index entry is stale. */
    public static void notPublished(String owner, Object slot, String reason) {
        if (warnLimited("publish|" + owner + "|" + slot + "|" + reason)) {
            LOGGER.info("omniversal pattern not published by {} (slot {}): {}", owner, slot, reason);
        }
    }

    private static boolean warnLimited(String key) {
        int count = WARNING_COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        return count <= MAX_WARNINGS_PER_KEY;
    }
}
