package com.sorrowmist.useless.client.render;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Caches the output used to paint encoded patterns in inventory screens. */
final class PatternOutputCache {
    private static final int MAX_ENTRIES_PER_LEVEL = 512;
    private static final Object LOCK = new Object();
    private static final Map<Level, LevelCache> LEVEL_CACHES = new java.util.WeakHashMap<>();
    private static final Map<AEItemKey, Optional<GenericStack>> LEVEL_INDEPENDENT_CACHE =
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<AEItemKey, Optional<GenericStack>> eldest) {
                    return size() > MAX_ENTRIES_PER_LEVEL;
                }
            };

    private PatternOutputCache() {
    }

    static Optional<GenericStack> get(
            AEItemKey key, @Nullable Level level, long generation, Supplier<GenericStack> resolver) {
        if (level == null) {
            synchronized (LOCK) {
                Optional<GenericStack> cached = LEVEL_INDEPENDENT_CACHE.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            Optional<GenericStack> resolved = resolve(resolver);
            synchronized (LOCK) {
                LEVEL_INDEPENDENT_CACHE.put(key, resolved);
            }
            return resolved;
        }

        LevelCache cache;
        synchronized (LOCK) {
            cache = LEVEL_CACHES.computeIfAbsent(level, ignored -> new LevelCache());
            if (cache.generation != generation) {
                cache.generation = generation;
                cache.entries.clear();
            }
            Optional<GenericStack> cached = cache.entries.get(key);
            if (cached != null) {
                return cached;
            }
        }

        Optional<GenericStack> resolved = resolve(resolver);
        synchronized (LOCK) {
            cache.entries.put(key, resolved);
        }
        return resolved;
    }

    private static Optional<GenericStack> resolve(Supplier<GenericStack> resolver) {
        try {
            return Optional.ofNullable(resolver.get());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static final class LevelCache {
        private long generation = Long.MIN_VALUE;
        private final LinkedHashMap<AEItemKey, Optional<GenericStack>> entries =
                new LinkedHashMap<>(64, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(
                            Map.Entry<AEItemKey, Optional<GenericStack>> eldest) {
                        return size() > MAX_ENTRIES_PER_LEVEL;
                    }
                };
    }
}
