package com.sorrowmist.useless.content.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalGrowthRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatLoader;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/** Server/client recipe directory used by JEI, the encoder and pattern validation. */
public final class AlloyFurnaceRecipeCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Object, Snapshot> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> BUILD_LOCKS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger CURRENT_RECIPE_COUNT = new AtomicInteger();
    private static final AtomicLong GENERATION = new AtomicLong();

    private AlloyFurnaceRecipeCatalog() {
    }

    public record Entry(AlloyFurnaceRecipeIdentity identity, AdvancedAlloyFurnaceRecipe recipe, String sourceId) {
        public Entry {
            sourceId = RecipeSourceIds.normalize(sourceId);
        }

        public Entry(AlloyFurnaceRecipeIdentity identity, AdvancedAlloyFurnaceRecipe recipe) {
            this(identity, recipe, RecipeSourceIds.UNKNOWN);
        }
    }

    public static List<Entry> entries(Level level) {
        if (level == null) return List.of();
        return snapshot(level).entries;
    }

    public static List<Entry> entries(Level level, String sourceId) {
        if (level == null) return List.of();
        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        return snapshot(level).bySource.getOrDefault(normalizedSource, List.of());
    }

    /** Builds and publishes the immutable current-generation snapshot synchronously. */
    public static void prewarm(Level level) {
        if (level != null) snapshot(level);
    }

    public static List<AdvancedAlloyFurnaceRecipe> recipes(Level level) {
        return entries(level).stream().map(Entry::recipe).toList();
    }

    /** Returns the number of unique recipes in the most recently built catalog snapshot. */
    public static int currentRecipeCount() {
        return CURRENT_RECIPE_COUNT.get();
    }

    /**
     * Returns whether the stack is used as a mold by any recipe currently
     * exposed through the shared alloy-furnace recipe directory.
     */
    public static boolean isKnownMold(Level level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return false;

        Snapshot snapshot = snapshot(level);
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return false;
        Boolean cached = snapshot.knownMoldCache.get(key);
        if (cached != null) return cached;

        boolean result = isKnownMoldInEntries(stack, snapshot.entries);
        snapshot.knownMoldCache.put(key, result);
        return result;
    }

    private static boolean isKnownMoldInEntries(ItemStack stack, List<Entry> entries) {
        for (Entry entry : entries) {
            AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
            if (recipe == null) continue;
            for (var mold : recipe.molds()) {
                if (mold != null && !mold.isEmpty() && AdapterUtils.matchesMold(mold, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isKnownMold(ItemStack stack, Iterable<AdvancedAlloyFurnaceRecipe> recipes) {
        if (stack == null || stack.isEmpty() || recipes == null) return false;
        for (AdvancedAlloyFurnaceRecipe recipe : recipes) {
            if (recipe == null) continue;
            for (var mold : recipe.molds()) {
                if (mold != null && !mold.isEmpty() && AdapterUtils.matchesMold(mold, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Optional<Entry> resolve(Level level, AlloyFurnaceRecipeIdentity identity) {
        if (level == null || identity == null) return Optional.empty();
        Object cacheKey = level.getRecipeManager();
        Snapshot snapshot = snapshot(level);
        Entry resolved = snapshot.byIdentity.get(identity);
        if (resolved != null) {
            return Optional.of(resolved);
        }
        if (!snapshot.misses.claimCompensationRebuild(identity)) {
            return Optional.empty();
        }

        // A pattern can be decoded while a datapack/compat adapter is still
        // finishing its recipe registration. Rebuild once on an identity miss
        // for the whole snapshot generation. Use the same per-recipe-manager
        // lock as the normal prewarm path so concurrent misses cannot build
        // duplicate snapshots.
        Snapshot rebuilt = rebuildAfterMiss(level, cacheKey, snapshot);
        if (GENERATION.get() != snapshot.generation) {
            return resolve(level, identity);
        }
        synchronized (CACHE) {
            Snapshot current = CACHE.get(cacheKey);
            if (current == snapshot) {
                CACHE.put(cacheKey, rebuilt);
                snapshot = rebuilt;
            } else if (current != null) {
                snapshot = current;
            }
        }
        resolved = snapshot.byIdentity.get(identity);
        if (resolved == null) {
            snapshot.misses.remember(identity);
        }
        return Optional.ofNullable(resolved);
    }

    /** Resolves by source first, retaining the all-source path for old metadata and unknown sources. */
    public static Optional<Entry> resolve(
            Level level, String sourceId, AlloyFurnaceRecipeIdentity identity) {
        if (level == null || identity == null) return Optional.empty();
        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        if (RecipeSourceIds.UNKNOWN.equals(normalizedSource)) return resolve(level, identity);
        Snapshot snapshot = snapshot(level);
        Entry resolved = snapshot.bySourceAndRecipeId
                .getOrDefault(new SourceRecipeKey(normalizedSource, identity.recipeId()), List.of())
                .stream()
                .filter(entry -> entry.identity().equals(identity))
                .findFirst()
                .orElse(null);
        return resolved == null ? resolve(level, identity) : Optional.of(resolved);
    }

    /**
     * Resolves a pattern binding, accepting a cross-side catalog rebuild when the stored fingerprint
     * no longer exists locally but the recipe id and encoded processing contents identify exactly
     * one current recipe. The exact identity remains the first choice; the fallback is deliberately
     * unique-only so two recipes sharing a pattern cannot silently acquire the wrong mold.
     */
    public static Optional<Entry> resolvePattern(
            Level level, AlloyFurnaceRecipeIdentity identity, IPatternDetails pattern) {
        if (level == null || identity == null || pattern == null) return Optional.empty();

        Optional<Entry> exact = resolve(level, identity);
        if (exact.isPresent()) return exact;

        Snapshot snapshot = snapshot(level);
        Set<Integer> componentAgnosticOutputs = componentAgnosticOutputSlots(pattern, level, null);
        List<Entry> candidates = snapshot.byRecipeId
                .getOrDefault(identity.recipeId(), List.of())
                .stream()
                .filter(entry -> matchesPattern(
                        entry.recipe(), pattern, componentAgnosticOutputs, true))
                .toList();
        if (candidates.size() != 1) {
            // A pattern encoded on the other side of a client/server boundary can carry the same
            // item output with a different component patch (or a legacy encoder can omit a
            // secondary output). The recipe id is already bound by the JEI selection, so use a
            // component-insensitive shape check only when the normal exact candidate search found
            // none. This still refuses ambiguous ids and never turns an unrelated pattern into a
            // recipe binding.
            if (!candidates.isEmpty()) return Optional.empty();
            candidates = snapshot.byRecipeId
                    .getOrDefault(identity.recipeId(), List.of())
                    .stream()
                    .filter(entry -> !hasComponentSensitiveItemOutputs(entry.recipe())
                            && matchesPatternShape(entry.recipe(), pattern))
                    .toList();
        }
        if (candidates.size() != 1) return Optional.empty();

        Entry compatible = candidates.getFirst();
        snapshot.compatibilityAliases.putIfAbsent(identity, compatible);
        return Optional.of(compatible);
    }

    public static Optional<Entry> resolvePattern(
            Level level, String sourceId, AlloyFurnaceRecipeIdentity identity, IPatternDetails pattern) {
        if (level == null || identity == null || pattern == null) return Optional.empty();
        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        if (RecipeSourceIds.UNKNOWN.equals(normalizedSource)) {
            return resolvePattern(level, identity, pattern);
        }
        Optional<Entry> exact = resolve(level, normalizedSource, identity);
        if (exact.isPresent()) return exact;
        Snapshot snapshot = snapshot(level);
        Set<Integer> componentAgnosticOutputs = componentAgnosticOutputSlots(pattern, level, normalizedSource);
        List<Entry> candidates = snapshot.bySourceAndRecipeId
                .getOrDefault(new SourceRecipeKey(normalizedSource, identity.recipeId()), List.of())
                .stream()
                .filter(entry -> matchesPattern(
                        entry.recipe(), pattern, componentAgnosticOutputs, true))
                .toList();
        if (candidates.size() == 1) return Optional.of(candidates.getFirst());
        return resolvePattern(level, identity, pattern);
    }

    /**
     * Version-one patterns used representation-sensitive fingerprints. Accept
     * them only when their recipe id and encoded processing contents identify
     * exactly one current recipe, then cache that old identity as an alias.
     */
    public static Optional<Entry> resolveLegacyPattern(
            Level level, AlloyFurnaceRecipeIdentity legacyIdentity, IPatternDetails pattern) {
        return resolveLegacyPattern(level, legacyIdentity, pattern, 1);
    }

    public static Optional<Entry> resolveLegacyPattern(
            Level level, AlloyFurnaceRecipeIdentity legacyIdentity, IPatternDetails pattern, int version) {
        if (level == null || legacyIdentity == null || pattern == null) return Optional.empty();
        Snapshot snapshot = snapshot(level);
        Entry cached = snapshot.byIdentity.get(legacyIdentity);
        if (cached != null) return Optional.of(cached);
        cached = snapshot.compatibilityAliases.get(legacyIdentity);
        if (cached != null) return Optional.of(cached);
        if (snapshot.legacyMisses.contains(legacyIdentity)) return Optional.empty();

        Entry match = null;
        for (Entry entry : snapshot.byRecipeId.getOrDefault(legacyIdentity.recipeId(), List.of())) {
            boolean validLegacyIdentity = level.isClientSide
                    ? matchesPattern(entry.recipe, pattern)
                    : legacyFingerprint(entry.recipe, level, version)
                            .equals(legacyIdentity.fingerprint());
            if (!validLegacyIdentity) continue;
            if (match != null) {
                snapshot.legacyMisses.add(legacyIdentity);
                return Optional.empty();
            }
            match = entry;
        }
        if (match == null) {
            snapshot.legacyMisses.add(legacyIdentity);
            return Optional.empty();
        }
        Entry existing = snapshot.compatibilityAliases.putIfAbsent(legacyIdentity, match);
        return Optional.of(existing == null ? match : existing);
    }

    public static Optional<Entry> resolveLegacyPattern(
            Level level, String sourceId, AlloyFurnaceRecipeIdentity legacyIdentity,
            IPatternDetails pattern, int version) {
        if (level == null || legacyIdentity == null || pattern == null) return Optional.empty();
        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        if (RecipeSourceIds.UNKNOWN.equals(normalizedSource)) {
            return resolveLegacyPattern(level, legacyIdentity, pattern, version);
        }
        Snapshot snapshot = snapshot(level);
        Entry match = null;
        for (Entry entry : snapshot.bySourceAndRecipeId
                .getOrDefault(new SourceRecipeKey(normalizedSource, legacyIdentity.recipeId()), List.of())) {
            boolean valid = level.isClientSide
                    ? matchesPattern(entry.recipe(), pattern)
                    : legacyFingerprint(entry.recipe(), level, version).equals(legacyIdentity.fingerprint());
            if (!valid) continue;
            if (match != null) return resolveLegacyPattern(level, legacyIdentity, pattern, version);
            match = entry;
        }
        return match == null
                ? resolveLegacyPattern(level, legacyIdentity, pattern, version)
                : Optional.of(match);
    }

    private static String legacyFingerprint(
            AdvancedAlloyFurnaceRecipe recipe, Level level, int version) {
        return version >= OmniversalPatternData.SEMANTIC_FINGERPRINT_VERSION
                ? AlloyFurnaceRecipeFingerprint.createLegacySemantic(recipe, level.registryAccess())
                : AlloyFurnaceRecipeFingerprint.createLegacy(recipe, level.registryAccess());
    }

    public static List<Entry> findPatternCandidates(Level level, IPatternDetails pattern) {
        if (level == null || pattern == null) return List.of();
        // Resolved once per lookup: identifying dynamic slots scans every fusion recipe, and the
        // answer depends only on the pattern, not on the candidate being tested.
        Set<Integer> componentAgnosticOutputs = componentAgnosticOutputSlots(pattern, level);
        return entries(level).stream()
                .filter(entry -> matchesPattern(entry.recipe, pattern, componentAgnosticOutputs, false))
                .sorted(Comparator.comparing(entry -> entry.identity.recipeId().toString()))
                .toList();
    }

    /**
     * Resolves an encoded processing pattern to a single recipe that has a reusable mold. This is
     * used only as a conservative fallback when the JEI selection packet and AE2's encoding update
     * arrive in the opposite order. Recipes without molds are intentionally excluded because a
     * plain processing pattern is already the correct representation for them.
     */
    public static Optional<Entry> findUniqueMoldPatternCandidate(Level level, IPatternDetails pattern) {
        List<Entry> candidates = findPatternCandidates(level, pattern).stream()
                .filter(entry -> entry.recipe() != null && !entry.recipe().molds().isEmpty())
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /**
     * Whether the encoded contents of {@code pattern} describe {@code recipe}.
     *
     * <p>Used when the recipe is already known — the player picked it in JEI — so the only remaining
     * question is whether the slots AE2 filled actually correspond to it. Unlike
     * {@link #findPatternCandidates}, this never searches, so a recipe that shares its inputs and
     * outputs with another one is still accepted: ambiguity was resolved by the player's click.
     */
    public static boolean matchesRecipe(Level level, AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        return matchesRecipe(level, null, recipe, pattern);
    }

    public static boolean matchesRecipe(
            Level level, String sourceId, AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        if (level == null || recipe == null || pattern == null) return false;
        return matchesPattern(recipe, pattern,
                componentAgnosticOutputSlots(pattern, level, sourceId), true);
    }

    /**
     * Output slots whose components must be ignored when matching a pattern against a recipe.
     *
     * <p>Draconic Evolution fusion recipes copy components from the catalyst onto the result
     * ({@code IFusionDataTransfer}), so the encoded pattern carries components that the catalogued
     * recipe — built from the recipe's static result — cannot have. Those slots are exactly the ones
     * the resolver already marks as item-id-only, so reuse that decision instead of relaxing every
     * output (which would collapse recipes differing only by an output component, e.g. Productive
     * Bees honeycomb {@code bee_type}).
     */
    private static Set<Integer> componentAgnosticOutputSlots(IPatternDetails pattern, Level level) {
        return componentAgnosticOutputSlots(pattern, level, null);
    }

    private static Set<Integer> componentAgnosticOutputSlots(
            IPatternDetails pattern, Level level, String sourceId) {
        IPatternDetails resolved = AdvancedAlloyFurnacePatternResolver.resolve(pattern, level, sourceId);
        if (!(resolved instanceof DynamicComponentPattern dynamic)) return Set.of();

        Set<Integer> slots = new LinkedHashSet<>();
        List<GenericStack> outputs = resolved.getOutputs();
        for (int slot = 0; slot < outputs.size(); slot++) {
            if (dynamic.isItemIdOutput(slot)) slots.add(slot);
        }
        return slots;
    }

    public static void invalidate() {
        CACHE.clear();
        CURRENT_RECIPE_COUNT.set(0);
        GENERATION.incrementAndGet();
    }

    public static long generation() {
        return GENERATION.get();
    }

    private static Snapshot snapshot(Level level) {
        Object cacheKey = level.getRecipeManager();
        while (true) {
            long generation = GENERATION.get();
            Snapshot cached;
            synchronized (CACHE) {
                cached = CACHE.get(cacheKey);
            }
            if (cached != null && cached.generation == generation) {
                return cached;
            }

            Object buildLock = buildLock(cacheKey);
            synchronized (buildLock) {
                generation = GENERATION.get();
                synchronized (CACHE) {
                    cached = CACHE.get(cacheKey);
                    if (cached != null && cached.generation == generation) {
                        return cached;
                    }
                }
                Snapshot built = build(level, generation, false);
                if (GENERATION.get() != generation) continue;
                synchronized (CACHE) {
                    cached = CACHE.get(cacheKey);
                    if (cached != null && cached.generation == generation) {
                        return cached;
                    }
                    CACHE.put(cacheKey, built);
                    return built;
                }
            }
        }
    }

    private static Snapshot rebuildAfterMiss(Level level, Object cacheKey, Snapshot expected) {
        Object buildLock = buildLock(cacheKey);
        synchronized (buildLock) {
            long generation = GENERATION.get();
            if (generation != expected.generation) {
                return snapshot(level);
            }

            synchronized (CACHE) {
                Snapshot current = CACHE.get(cacheKey);
                if (current != null && current != expected && current.generation == generation) {
                    return current;
                }
            }

            Snapshot rebuilt = build(level, generation, true);
            if (GENERATION.get() != generation) {
                return snapshot(level);
            }
            synchronized (CACHE) {
                Snapshot current = CACHE.get(cacheKey);
                if (current != null && current != expected && current.generation == generation) {
                    return current;
                }
                CACHE.put(cacheKey, rebuilt);
                return rebuilt;
            }
        }
    }

    private static Object buildLock(Object cacheKey) {
        synchronized (BUILD_LOCKS) {
            return BUILD_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        }
    }

    private static Snapshot build(Level level, long generation, boolean compensationRebuildUsed) {
        long startedAt = System.nanoTime();
        List<CollectedRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get())) {
            recipes.add(new CollectedRecipe(holder.value(), RecipeSourceIds.CORE));
        }

        Collection<RecipeHolder<?>> sourceRecipes = level.getRecipeManager().getRecipes();
        for (com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter : AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters()) {
            if (adapter.getClass().getPackageName().contains(".ae.ae2lt")) continue;
            String sourceId = AlloyFurnaceRecipeManager.getInstance().getAdapterSourceId(adapter);
            if (adapter instanceof SeedEssenceRecipeAdapter synthetic) {
                synthetic.getAllRecipes().forEach(recipe -> recipes.add(new CollectedRecipe(recipe, sourceId)));
                continue;
            }
            if (adapter instanceof CrystalGrowthRecipeAdapter synthetic) {
                synthetic.getAllRecipes().forEach(recipe -> recipes.add(new CollectedRecipe(recipe, sourceId)));
                continue;
            }
            collectGenerated(adapter, sourceId, level, recipes);
            collectConverted(adapter, sourceId, sourceRecipes, level, recipes);
        }
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2LT)) {
            AELightningTechCompatLoader.getJeiRecipes(level.getRecipeManager(), level)
                    .forEach(recipe -> recipes.add(new CollectedRecipe(recipe, RecipeSourceIds.AE2LT)));
        }

        Map<AlloyFurnaceRecipeIdentity, Entry> unique = new LinkedHashMap<>();
        for (CollectedRecipe collected : recipes) {
            AdvancedAlloyFurnaceRecipe recipe = collected.recipe();
            if (recipe == null) continue;
            try {
                AlloyFurnaceRecipeIdentity identity = new AlloyFurnaceRecipeIdentity(
                        recipe.id(), AlloyFurnaceRecipeFingerprint.create(recipe, level.registryAccess()));
                unique.putIfAbsent(identity, new Entry(identity, recipe, collected.sourceId()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping alloy-furnace recipe with an unencodable identity: {}", recipe.id(), exception);
            }
        }
        List<Entry> ordered = unique.values().stream()
                .sorted(Comparator.comparing((Entry entry) -> entry.identity.recipeId().toString())
                        .thenComparing(entry -> entry.identity.fingerprint()))
                .toList();
        CURRENT_RECIPE_COUNT.set(ordered.size());
        Map<ResourceLocation, List<Entry>> byRecipeId = new LinkedHashMap<>();
        for (Entry entry : ordered) {
            byRecipeId.computeIfAbsent(entry.identity.recipeId(), ignored -> new ArrayList<>()).add(entry);
        }
        byRecipeId.replaceAll((ignored, entries) -> List.copyOf(entries));
        Map<String, List<Entry>> bySource = new LinkedHashMap<>();
        Map<SourceRecipeKey, List<Entry>> bySourceAndRecipeId = new LinkedHashMap<>();
        for (Entry entry : ordered) {
            bySource.computeIfAbsent(entry.sourceId(), ignored -> new ArrayList<>()).add(entry);
            bySourceAndRecipeId.computeIfAbsent(
                    new SourceRecipeKey(entry.sourceId(), entry.identity().recipeId()),
                    ignored -> new ArrayList<>()).add(entry);
        }
        bySource.replaceAll((ignored, entries) -> List.copyOf(entries));
        bySourceAndRecipeId.replaceAll((ignored, entries) -> List.copyOf(entries));
        Snapshot snapshot = new Snapshot(ordered, Map.copyOf(unique), Map.copyOf(byRecipeId),
                Map.copyOf(bySource), Map.copyOf(bySourceAndRecipeId), generation,
                new ConcurrentHashMap<>(),
                new ResolutionMisses(compensationRebuildUsed), new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet());
        LOGGER.info("Built alloy-furnace recipe catalog: generation={}, recipes={}, sources={}, elapsed={} ms",
                generation, ordered.size(), bySource.size(), (System.nanoTime() - startedAt) / 1_000_000L);
        return snapshot;
    }

    private static void collectConverted(com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, String sourceId,
                                         Collection<RecipeHolder<?>> holders, Level level,
                                         List<CollectedRecipe> output) {
        Class<?> recipeClass = adapter.getRecipeClass();
        for (RecipeHolder<?> holder : holders) {
            if (!recipeClass.isInstance(holder.value())) continue;
            RecipeConversionUtils.convertAll(adapter, holder, level)
                    .forEach(recipe -> output.add(new CollectedRecipe(recipe, sourceId)));
        }
    }

    private static void collectGenerated(com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, String sourceId, Level level,
                                         List<CollectedRecipe> output) {
        List<? extends RecipeHolder<?>> generated;
        try {
            generated = adapter.getGeneratedRecipes(level);
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping generated recipes: adapter={}", adapter.getClass().getName(), exception);
            return;
        }
        if (generated == null) return;
        for (RecipeHolder<?> holder : generated) {
            RecipeConversionUtils.convertAll(adapter, holder, level)
                    .forEach(recipe -> output.add(new CollectedRecipe(recipe, sourceId)));
        }
    }

    // Package-private for regression testing of component-exact output matching.
    static boolean matchesPattern(AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        return matchesPattern(recipe, pattern, Set.of(), false);
    }

    private static boolean matchesPattern(AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern,
                                          Set<Integer> componentAgnosticOutputs,
                                          boolean allowMissingOutputs) {
        PatternContents contents = PatternContents.read(pattern);
        if (contents == null) return false;

        long requiredItemCount = recipe.inputs().stream().mapToLong(CountedIngredient::count).sum();
        long actualItemCount = contents.items.stream().mapToLong(GenericStack::amount).sum();
        if (requiredItemCount != actualItemCount
                || !ItemIngredientAllocator.matches(recipe.inputs(), List.of(), contents.items, 1L)) return false;

        if (!matchesFluidIngredients(contents.fluids, recipe.inputFluids())) return false;
        if (!sameGeneric(contents.keys, recipe.keyInputs())) return false;

        List<GenericStack> expectedOutputs = new ArrayList<>();
        recipe.outputs().stream().map(GenericStack::fromItemStack).forEach(expectedOutputs::add);
        recipe.outputFluids().stream().map(GenericStack::fromFluidStack).forEach(expectedOutputs::add);
        expectedOutputs.addAll(recipe.keyOutputs());
        // Outputs match component-exactly by default, mirroring the server-side identity built by
        // AlloyFurnaceRecipeFingerprint.encodeExactOutput. Relaxing item components everywhere would
        // collapse recipes that differ only by an output component (e.g. Productive Bees honeycomb
        // carrying distinct bee_type values), so a single honeycomb pattern would match every bee
        // recipe and candidate selection would pick the alphabetically-first one (black_quartz),
        // encoding the wrong mold. Only the slots the resolver marked as item-id-only ignore
        // components, which keeps Draconic Evolution's component-transferring fusion results working.
        if (allowMissingOutputs) {
            return matchesGenericSubset(
                    pattern.getOutputs(), expectedOutputs, componentAgnosticOutputs);
        }
        return componentAgnosticOutputs.isEmpty()
                ? sameGeneric(pattern.getOutputs(), expectedOutputs)
                : sameGenericIgnoringSlotComponents(
                        pattern.getOutputs(), expectedOutputs, componentAgnosticOutputs);
    }

    /**
     * Compatibility matcher for a pattern made by a different recipe-directory representation.
     * Inputs, fluids, keys, counts, and item identities remain strict; only item output components
     * are ignored. The exact matcher above remains the first choice so recipes distinguished by
     * output components cannot become ambiguous during normal resolution.
     */
    private static boolean matchesPatternShape(
            AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        PatternContents contents = PatternContents.read(pattern);
        if (contents == null) return false;

        long requiredItemCount = recipe.inputs().stream().mapToLong(CountedIngredient::count).sum();
        long actualItemCount = contents.items.stream().mapToLong(GenericStack::amount).sum();
        if (requiredItemCount != actualItemCount
                || !ItemIngredientAllocator.matches(recipe.inputs(), List.of(), contents.items, 1L)) {
            return false;
        }
        if (!matchesFluidIngredients(contents.fluids, recipe.inputFluids())
                || !sameGeneric(contents.keys, recipe.keyInputs())) {
            return false;
        }

        List<GenericStack> expectedOutputs = new ArrayList<>();
        recipe.outputs().stream().map(GenericStack::fromItemStack).forEach(expectedOutputs::add);
        recipe.outputFluids().stream().map(GenericStack::fromFluidStack).forEach(expectedOutputs::add);
        expectedOutputs.addAll(recipe.keyOutputs());
        return matchesGenericSubsetIgnoringItemComponents(pattern.getOutputs(), expectedOutputs);
    }

    private static boolean hasComponentSensitiveItemOutputs(AdvancedAlloyFurnaceRecipe recipe) {
        for (ItemStack output : recipe.outputs()) {
            if (output != null && !output.isEmpty() && !output.getComponentsPatch().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGenericSubsetIgnoringItemComponents(
            List<GenericStack> actual, List<GenericStack> expected) {
        if (actual == null || actual.isEmpty() || expected == null || actual.size() > expected.size()) {
            return false;
        }

        boolean[] matched = new boolean[expected.size()];
        for (GenericStack actualStack : actual) {
            if (actualStack == null || actualStack.what() == null || actualStack.amount() <= 0L) {
                return false;
            }
            int matchedSlot = -1;
            for (int expectedSlot = 0; expectedSlot < expected.size(); expectedSlot++) {
                if (matched[expectedSlot]) continue;
                GenericStack expectedStack = expected.get(expectedSlot);
                if (expectedStack == null || expectedStack.what() == null
                        || expectedStack.amount() != actualStack.amount()
                        || !sameKeyIgnoringItemComponents(actualStack.what(), expectedStack.what())) {
                    continue;
                }
                matchedSlot = expectedSlot;
                break;
            }
            if (matchedSlot < 0) return false;
            matched[matchedSlot] = true;
        }
        return true;
    }

    private static boolean sameKeyIgnoringItemComponents(AEKey left, AEKey right) {
        if (left instanceof AEItemKey leftItem && right instanceof AEItemKey rightItem) {
            return leftItem.getItem() == rightItem.getItem();
        }
        return left.equals(right);
    }

    /**
     * Matches the outputs retained by a known recipe-bound pattern. The encoder may omit
     * secondary outputs, but every retained stack must still match one complete recipe output.
     */
    private static boolean matchesGenericSubset(
            List<GenericStack> actual, List<GenericStack> expected,
            Set<Integer> componentAgnosticSlots) {
        if (actual == null || actual.isEmpty() || expected == null || actual.size() > expected.size()) {
            return false;
        }

        boolean[] matched = new boolean[expected.size()];
        for (int actualSlot = 0; actualSlot < actual.size(); actualSlot++) {
            GenericStack actualOutput = actual.get(actualSlot);
            if (actualOutput == null || actualOutput.what() == null || actualOutput.amount() <= 0L) {
                return false;
            }

            int matchedSlot = -1;
            for (int expectedSlot = 0; expectedSlot < expected.size(); expectedSlot++) {
                if (matched[expectedSlot]) {
                    continue;
                }
                GenericStack expectedOutput = expected.get(expectedSlot);
                if (expectedOutput == null || expectedOutput.what() == null
                        || expectedOutput.amount() != actualOutput.amount()) {
                    continue;
                }

                boolean matches = componentAgnosticSlots.contains(actualSlot)
                        ? matchesItemId(actualOutput.what(), expectedOutput.what())
                        : actualOutput.what().equals(expectedOutput.what());
                if (matches) {
                    matchedSlot = expectedSlot;
                    break;
                }
            }
            if (matchedSlot < 0) {
                return false;
            }
            matched[matchedSlot] = true;
        }
        return true;
    }

    private static boolean matchesItemId(AEKey actual, AEKey expected) {
        return actual instanceof AEItemKey actualItem
                && expected instanceof AEItemKey expectedItem
                && actualItem.getItem() == expectedItem.getItem();
    }

    /**
     * Compares outputs slot-by-slot, ignoring item components in the given slots. Slot indices are
     * positional, so this cannot use the order-insensitive multiset comparison.
     */
    private static boolean sameGenericIgnoringSlotComponents(
            List<GenericStack> patternOutputs, List<GenericStack> recipeOutputs, Set<Integer> ignoredSlots) {
        if (patternOutputs.size() != recipeOutputs.size()) return false;
        for (int slot = 0; slot < patternOutputs.size(); slot++) {
            GenericStack left = patternOutputs.get(slot);
            GenericStack right = recipeOutputs.get(slot);
            if (left == null || right == null || left.amount() != right.amount()) return false;
            if (!ignoredSlots.contains(slot)) {
                if (!left.what().equals(right.what())) return false;
            } else if (!(left.what() instanceof AEItemKey leftItem)
                    || !(right.what() instanceof AEItemKey rightItem)
                    || leftItem.getItem() != rightItem.getItem()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameAmounts(List<FluidStack> left, List<FluidStack> right) {
        Map<AEKey, Long> leftMap = new LinkedHashMap<>();
        for (FluidStack stack : left) leftMap.merge(AEFluidKey.of(stack), (long) stack.getAmount(), Long::sum);
        Map<AEKey, Long> rightMap = new LinkedHashMap<>();
        for (FluidStack stack : right) rightMap.merge(AEFluidKey.of(stack), (long) stack.getAmount(), Long::sum);
        return leftMap.equals(rightMap);
    }

    private static boolean matchesFluidIngredients(
            List<PatternFluidInput> actual,
            List<LongSizedFluidIngredient> required) {
        long actualAmount = 0L;
        if (actual != null) {
            for (PatternFluidInput input : actual) {
                if (input != null && input.amount() > 0) {
                    actualAmount = addAmount(actualAmount, input.amount());
                }
            }
        }
        long requiredAmount = 0L;
        if (required != null) {
            for (var ingredient : required) {
                if (ingredient != null) requiredAmount = addAmount(requiredAmount, ingredient.amount());
            }
        }
        if (actualAmount != requiredAmount) return false;
        return matchesFluidCandidates(actual, required, 0, new ArrayList<>());
    }

    private static boolean matchesFluidCandidates(
            List<PatternFluidInput> actual,
            List<LongSizedFluidIngredient> required,
            int index, List<GenericStack> selected) {
        if (index >= actual.size()) {
            return FluidIngredientAllocator.matchesLong(required, List.of(), selected, 1L);
        }
        PatternFluidInput input = actual.get(index);
        if (input == null || input.candidates().isEmpty()) return false;
        for (GenericStack candidate : input.candidates()) {
            if (candidate == null || candidate.what() == null || candidate.amount() <= 0L) continue;
            selected.add(candidate);
            if (matchesFluidCandidates(actual, required, index + 1, selected)) return true;
            selected.removeLast();
        }
        return false;
    }

    private static long addAmount(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static boolean sameGeneric(List<GenericStack> left, List<GenericStack> right) {
        Map<AEKey, Long> leftMap = genericMap(left);
        Map<AEKey, Long> rightMap = genericMap(right);
        return leftMap.equals(rightMap);
    }

    private static Map<AEKey, Long> genericMap(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) continue;
            result.merge(stack.what(), stack.amount(), Long::sum);
        }
        return result;
    }

    private record Snapshot(
            List<Entry> entries,
            Map<AlloyFurnaceRecipeIdentity, Entry> byIdentity,
            Map<ResourceLocation, List<Entry>> byRecipeId,
            Map<String, List<Entry>> bySource,
            Map<SourceRecipeKey, List<Entry>> bySourceAndRecipeId,
            long generation,
            Map<AEItemKey, Boolean> knownMoldCache,
            ResolutionMisses misses,
            Map<AlloyFurnaceRecipeIdentity, Entry> compatibilityAliases,
            Set<AlloyFurnaceRecipeIdentity> legacyMisses) {
    }

    private record CollectedRecipe(AdvancedAlloyFurnaceRecipe recipe, String sourceId) {
        private CollectedRecipe {
            sourceId = RecipeSourceIds.normalize(sourceId);
        }
    }

    private record SourceRecipeKey(String sourceId, ResourceLocation recipeId) {
        private SourceRecipeKey {
            sourceId = RecipeSourceIds.normalize(sourceId);
        }
    }

    static final class ResolutionMisses {
        private final AtomicBoolean compensationRebuildClaimed;
        private final Set<AlloyFurnaceRecipeIdentity> identities = ConcurrentHashMap.newKeySet();

        ResolutionMisses(boolean compensationRebuildUsed) {
            this.compensationRebuildClaimed = new AtomicBoolean(compensationRebuildUsed);
        }

        boolean claimCompensationRebuild(AlloyFurnaceRecipeIdentity identity) {
            return !identities.contains(identity)
                    && compensationRebuildClaimed.compareAndSet(false, true);
        }

        void remember(AlloyFurnaceRecipeIdentity identity) {
            identities.add(identity);
        }
    }

    private record PatternContents(List<GenericStack> items, List<PatternFluidInput> fluids,
                                   List<GenericStack> keys) {
        private static PatternContents read(IPatternDetails pattern) {
            List<GenericStack> items = new ArrayList<>();
            List<PatternFluidInput> fluids = new ArrayList<>();
            List<GenericStack> keys = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null) return null;
                GenericStack[] possible = input.getPossibleInputs();
                long amount = input.getMultiplier();
                if (possible.length == 0 || amount <= 0) return null;

                List<GenericStack> fluidCandidates = new ArrayList<>();
                boolean hasNonFluidCandidate = false;
                for (GenericStack candidate : possible) {
                    if (candidate == null || candidate.what() == null) continue;
                    if (candidate.what() instanceof AEFluidKey fluidKey) {
                        GenericStack fluid = new GenericStack(fluidKey, amount);
                        if (fluidCandidates.stream().noneMatch(existing ->
                                existing.what().equals(fluid.what()))) {
                            fluidCandidates.add(fluid);
                        }
                    } else {
                        hasNonFluidCandidate = true;
                    }
                }
                // A processing slot cannot be represented faithfully when its encoded candidates
                // cross the item/fluid boundary. Keep the pattern invalid rather than silently
                // discarding one side of the slot.
                if (!fluidCandidates.isEmpty() && hasNonFluidCandidate) return null;
                if (!fluidCandidates.isEmpty()) {
                    fluids.add(new PatternFluidInput(List.copyOf(fluidCandidates), amount));
                    continue;
                }

                GenericStack first = java.util.Arrays.stream(possible)
                        .filter(candidate -> candidate != null && candidate.what() != null)
                        .findFirst().orElse(null);
                if (first == null) return null;
                AEKey key = first.what();
                if (key instanceof AEItemKey itemKey) items.add(new GenericStack(itemKey, amount));
                else keys.add(new GenericStack(key, amount));
            }
            return new PatternContents(items, fluids, keys);
        }
    }

    private record PatternFluidInput(List<GenericStack> candidates, long amount) {
    }
}
