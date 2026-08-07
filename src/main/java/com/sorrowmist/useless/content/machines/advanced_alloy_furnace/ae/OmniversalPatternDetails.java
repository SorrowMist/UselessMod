package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulBindingRecipeAdapter;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

public final class OmniversalPatternDetails extends DynamicComponentPatternDetails {
    // Keep every distinct pattern in a maximum-size 540-slot container hot, with room for
    // patterns from another container in the same client world.
    private static final int MAX_DECODE_CACHE_ENTRIES = 1024;
    private static final Object DECODE_CACHE_LOCK = new Object();
    private static final Map<Level, LevelDecodeCache> DECODE_CACHES = new WeakHashMap<>();

    private final OmniversalPatternData data;
    private final AdvancedAlloyFurnaceRecipe recipe;

    private OmniversalPatternDetails(Decoded decoded) {
        super(decoded.source, OmniversalPatternEncoding.resolveItemIdInputSlots(
                        decoded.entry.recipe(), decoded.source, decoded.data.itemIdInputSlots()),
                decoded.data.itemIdOutputSlots(),
                soulBindingInputMatchers(decoded),
                decoded.level.registryAccess());
        this.data = decoded.data;
        this.recipe = decoded.entry.recipe();
    }

    public static OmniversalPatternDetails decode(AEItemKey definition, Level level) {
        if (definition == null || level == null) return null;

        synchronized (DECODE_CACHE_LOCK) {
            LevelDecodeCache cache = DECODE_CACHES.computeIfAbsent(level, ignored -> new LevelDecodeCache());
            cache.prepare(AlloyFurnaceRecipeCatalog.generation());
            CachedDecode cached = cache.entries.get(definition);
            if (cached != null) {
                if (cached.failure != null) {
                    throw cached.failure;
                }
                OmniversalPatternDetails details = cached.details;
                if (details != null) {
                    return details;
                }
                cache.entries.remove(definition);
            }

            try {
                OmniversalPatternDetails details = decodeUncached(definition, level);
                cache.entries.put(definition, CachedDecode.success(details));
                return details;
            } catch (RuntimeException exception) {
                cache.entries.put(definition, CachedDecode.failure(exception));
                throw exception;
            }
        }
    }

    private static OmniversalPatternDetails decodeUncached(AEItemKey definition, Level level) {
        OmniversalPatternData data = definition.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null || data.version() > OmniversalPatternData.CURRENT_VERSION) {
            throw new IllegalArgumentException("Missing or unsupported omniversal pattern data");
        }
        AEProcessingPattern source = new AEProcessingPattern(definition);
        Optional<AlloyFurnaceRecipeCatalog.Entry> resolved =
                data.version() < OmniversalPatternData.SEMANTIC_FINGERPRINT_VERSION
                        ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(level, data.identity(), source)
                        : AlloyFurnaceRecipeCatalog.resolve(level, data.identity());
        AlloyFurnaceRecipeCatalog.Entry entry = resolved
                .orElseThrow(() -> new IllegalArgumentException(
                        "The bound alloy-furnace recipe is missing or has changed: " + data.recipeId()));
        return new OmniversalPatternDetails(new Decoded(
                source, data, entry, level));
    }

    private static final class LevelDecodeCache {
        private long generation = Long.MIN_VALUE;
        private final LinkedHashMap<AEItemKey, CachedDecode> entries =
                new LinkedHashMap<>(64, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<AEItemKey, CachedDecode> eldest) {
                        return size() > MAX_DECODE_CACHE_ENTRIES;
                    }
                };

        private void prepare(long currentGeneration) {
            if (generation != currentGeneration) {
                generation = currentGeneration;
                entries.clear();
            }
        }
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
    }

    public OmniversalPatternData data() {
        return data;
    }

    public AdvancedAlloyFurnaceRecipe recipe() {
        return recipe;
    }

    private static java.util.Map<Integer, DynamicComponentPatternDetails.InputMatcher>
            soulBindingInputMatchers(Decoded decoded) {
        if (!ModList.get().isLoaded("enderio")) {
            return java.util.Map.of();
        }
        return SoulBindingRecipeAdapter.findDynamicPatternProfile(
                        decoded.level,
                        AdvancedAlloyFurnacePatternResolver.itemInputs(decoded.source),
                        AdvancedAlloyFurnacePatternResolver.itemOutputs(decoded.source))
                .map(SoulBindingRecipeAdapter.DynamicPatternProfile::inputMatchers)
                .orElseGet(java.util.Map::of);
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
