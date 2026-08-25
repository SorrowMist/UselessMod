package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulBindingRecipeAdapter;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;

public final class OmniversalPatternDetails extends DynamicComponentPatternDetails {
    private static final int DEFAULT_DECODE_CACHE_CAPACITY = 2048;
    private static final Object DECODE_CACHE_LOCK = new Object();
    private static final Map<Level, LevelDecodeCache> DECODE_CACHES = new WeakHashMap<>();

    private final OmniversalPatternData data;
    private final AdvancedAlloyFurnaceRecipe recipe;

    private OmniversalPatternDetails(Decoded decoded) {
        super(decoded.source, OmniversalPatternEncoding.resolveItemIdInputSlots(
                        decoded.entry.recipe(), decoded.source, decoded.data.itemIdInputSlots()),
                decoded.data.itemIdOutputSlots(),
                tagInputTags(decoded.data),
                fluidTagInputTags(decoded.data),
                dynamicInputMatchers(decoded),
                Map.of(),
                decoded.level.registryAccess());
        this.data = decoded.data;
        this.recipe = decoded.entry.recipe();
    }

    public static OmniversalPatternDetails decode(AEItemKey definition, Level level) {
        if (definition == null || level == null) return null;

        LevelDecodeCache cache;
        synchronized (DECODE_CACHE_LOCK) {
            cache = DECODE_CACHES.computeIfAbsent(level, ignored -> new LevelDecodeCache());
        }
        while (true) {
            long generation = AlloyFurnaceRecipeCatalog.generation();
            cache.prepare(generation);
            if (generation != AlloyFurnaceRecipeCatalog.generation()) continue;

            CachedDecode cached = cache.get(definition);
            if (cached != null) {
                cache.hits.incrementAndGet();
                if (generation != AlloyFurnaceRecipeCatalog.generation()) continue;
                return cached.valueOrThrow();
            }
            cache.misses.incrementAndGet();

            CompletableFuture<CachedDecode> ownerFuture = new CompletableFuture<>();
            InFlightDecode owner = new InFlightDecode(generation, ownerFuture);
            InFlightDecode existing = cache.inFlight.putIfAbsent(definition, owner);
            if (existing != null) {
                if (existing.generation != generation) {
                    cache.inFlight.remove(definition, existing);
                    continue;
                }
                try {
                    CachedDecode result = existing.future.join();
                    if (generation != AlloyFurnaceRecipeCatalog.generation()) continue;
                    return result.valueOrThrow();
                } catch (CompletionException exception) {
                    if (generation != AlloyFurnaceRecipeCatalog.generation()) continue;
                    throw unwrap(exception);
                }
            }

            CachedDecode result;
            try {
                cache.actualDecodes.incrementAndGet();
                try {
                    result = CachedDecode.success(decodeUncached(definition, level));
                } catch (RuntimeException exception) {
                    result = CachedDecode.failure(exception);
                }
                if (generation == AlloyFurnaceRecipeCatalog.generation()) {
                    cache.putIfCurrent(definition, result, generation);
                }
                ownerFuture.complete(result);
            } catch (Throwable throwable) {
                ownerFuture.completeExceptionally(throwable);
                if (throwable instanceof RuntimeException runtimeException) throw runtimeException;
                if (throwable instanceof Error error) throw error;
                throw new RuntimeException(throwable);
            } finally {
                cache.inFlight.remove(definition, owner);
            }

            if (generation != AlloyFurnaceRecipeCatalog.generation()) continue;
            return result.valueOrThrow();
        }
    }

    private static RuntimeException unwrap(CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) return runtimeException;
        if (cause instanceof Error error) throw error;
        return new RuntimeException(cause == null ? exception : cause);
    }

    private static OmniversalPatternDetails decodeUncached(AEItemKey definition, Level level) {
        OmniversalPatternData data = definition.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null || data.version() > OmniversalPatternData.CURRENT_VERSION) {
            throw new IllegalArgumentException("Missing or unsupported omniversal pattern data");
        }
        AEProcessingPattern source = new AEProcessingPattern(definition);
        Optional<AlloyFurnaceRecipeCatalog.Entry> resolved =
                data.version() < OmniversalPatternData.TAG_INPUT_VERSION
                        ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(
                                level, data.sourceId(), data.identity(), source, data.version())
                        : AlloyFurnaceRecipeCatalog.resolvePattern(
                                level, data.sourceId(), data.identity(), source);
        AlloyFurnaceRecipeCatalog.Entry entry = resolved
                .orElseThrow(() -> new IllegalArgumentException(
                        "The bound alloy-furnace recipe is missing or has changed: " + data.recipeId()));
        return new OmniversalPatternDetails(new Decoded(
                source, data, entry, level));
    }

    private static final class LevelDecodeCache {
        private long generation = Long.MIN_VALUE;
        private final Object lock = new Object();
        private final ConcurrentHashMap<AEItemKey, InFlightDecode> inFlight =
                new ConcurrentHashMap<>();
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();
        private final AtomicLong evictions = new AtomicLong();
        private final AtomicLong actualDecodes = new AtomicLong();
        private final LinkedHashMap<AEItemKey, CachedDecode> entries =
                new LinkedHashMap<>(64, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<AEItemKey, CachedDecode> eldest) {
                        boolean remove = size() > capacity();
                        if (remove) evictions.incrementAndGet();
                        return remove;
                    }
                };

        private void prepare(long currentGeneration) {
            synchronized (lock) {
                if (currentGeneration > generation) {
                    generation = currentGeneration;
                    entries.clear();
                    inFlight.clear();
                }
            }
        }

        @Nullable
        private CachedDecode get(AEItemKey definition) {
            synchronized (lock) {
                return entries.get(definition);
            }
        }

        private void putIfCurrent(AEItemKey definition, CachedDecode result, long expectedGeneration) {
            synchronized (lock) {
                if (generation == expectedGeneration) entries.put(definition, result);
            }
        }

        private int capacity() {
            try {
                return ConfigManager.getOmniversalDecodeCacheCapacity();
            } catch (RuntimeException ignored) {
                return DEFAULT_DECODE_CACHE_CAPACITY;
            }
        }
    }

    private record InFlightDecode(long generation, CompletableFuture<CachedDecode> future) {
    }

    private static final class CachedDecode {
        @Nullable
        private final OmniversalPatternDetails details;
        private final RuntimeException failure;

        private CachedDecode(@Nullable OmniversalPatternDetails details,
                             RuntimeException failure) {
            this.details = details;
            this.failure = failure;
        }

        private static CachedDecode success(OmniversalPatternDetails details) {
            return new CachedDecode(details, null);
        }

        private static CachedDecode failure(RuntimeException failure) {
            return new CachedDecode(null, failure);
        }

        private OmniversalPatternDetails valueOrThrow() {
            if (failure != null) throw failure;
            if (details == null) throw new IllegalStateException("Cached omniversal pattern has no result");
            return details;
        }
    }

    public record DecodeCacheStats(long hits, long misses, long evictions, long actualDecodes) {
    }

    public static DecodeCacheStats stats(Level level) {
        if (level == null) return new DecodeCacheStats(0L, 0L, 0L, 0L);
        synchronized (DECODE_CACHE_LOCK) {
            LevelDecodeCache cache = DECODE_CACHES.get(level);
            return cache == null ? new DecodeCacheStats(0L, 0L, 0L, 0L)
                    : new DecodeCacheStats(cache.hits.get(), cache.misses.get(),
                    cache.evictions.get(), cache.actualDecodes.get());
        }
    }

    public OmniversalPatternData data() {
        return data;
    }

    public AdvancedAlloyFurnaceRecipe recipe() {
        return recipe;
    }

    private static java.util.Map<Integer, DynamicComponentPatternDetails.InputMatcher>
            dynamicInputMatchers(Decoded decoded) {
        PatternStackView view = PatternStackView.fromPattern(decoded.source);
        if (view == null) return java.util.Map.of();
        String sourceId = RecipeSourceIds.normalize(decoded.data.sourceId());
        if (RecipeSourceIds.NEOVITAE.equals(sourceId)) {
            if (!ModList.get().isLoaded("neovitae")) return java.util.Map.of();
            return AdvancedAlloyFurnacePatternResolver.findNeoVitaeDynamicPatternProfile(
                            decoded.level, view)
                    .map(DynamicPatternProfile::inputMatchers)
                    .orElseGet(java.util.Map::of);
        }
        if (!ModList.get().isLoaded("enderio")) {
            return java.util.Map.of();
        }
        return SoulBindingRecipeAdapter.findDynamicPatternProfileLong(
                        decoded.data.sourceId().isBlank() ? "enderio" : decoded.data.sourceId(),
                        decoded.level,
                        view)
                .map(SoulBindingRecipeAdapter.DynamicPatternProfile::inputMatchers)
                .orElseGet(java.util.Map::of);
    }

    private static Map<Integer, List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>>
            tagInputTags(OmniversalPatternData data) {
        if (data.version() < OmniversalPatternData.TAG_INPUT_VERSION) return Map.of();
        Map<Integer, List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>> result = new LinkedHashMap<>();
        for (OmniversalPatternData.TagInputSlot slot : data.tagInputSlots()) {
            result.computeIfAbsent(slot.slot(), ignored -> new java.util.ArrayList<>()).add(slot.tag());
        }
        return result;
    }

    private static Map<Integer, List<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>>>
            fluidTagInputTags(OmniversalPatternData data) {
        if (data.version() < OmniversalPatternData.FLUID_TAG_INPUT_VERSION) return Map.of();
        Map<Integer, List<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>>> result =
                new LinkedHashMap<>();
        for (OmniversalPatternData.FluidTagInputSlot slot : data.fluidTagInputSlots()) {
            result.computeIfAbsent(slot.slot(), ignored -> new java.util.ArrayList<>()).add(slot.tag());
        }
        return result;
    }

    @Override
    public String dynamicPatternIdentity() {
        return "useless_mod:omniversal|recipe=" + data.recipeId()
                + "|fingerprint=" + data.recipeFingerprint()
                + "|dynamic=" + super.dynamicPatternIdentity();
    }

    private record Decoded(AEProcessingPattern source, OmniversalPatternData data,
                           AlloyFurnaceRecipeCatalog.Entry entry, Level level) {
    }
}
