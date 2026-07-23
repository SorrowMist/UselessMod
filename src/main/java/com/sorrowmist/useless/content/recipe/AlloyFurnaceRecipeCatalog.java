package com.sorrowmist.useless.content.recipe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalGrowthRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatLoader;
import com.sorrowmist.useless.content.recipe.adapters.minecraft.SmeltingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Server/client recipe directory used by JEI, the encoder and pattern validation. */
public final class AlloyFurnaceRecipeCatalog {
    private static final Map<Object, Snapshot> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong();

    private AlloyFurnaceRecipeCatalog() {
    }

    public record Entry(AlloyFurnaceRecipeIdentity identity, AdvancedAlloyFurnaceRecipe recipe) {
    }

    public static List<Entry> entries(Level level) {
        if (level == null) return List.of();
        return snapshot(level).entries;
    }

    public static List<AdvancedAlloyFurnaceRecipe> recipes(Level level) {
        return entries(level).stream().map(Entry::recipe).toList();
    }

    /**
     * Returns whether the stack is used as a mold by any recipe currently
     * exposed through the shared alloy-furnace recipe directory.
     */
    public static boolean isKnownMold(Level level, ItemStack stack) {
        return level != null && isKnownMold(stack, recipes(level));
    }

    static boolean isKnownMold(ItemStack stack, Iterable<AdvancedAlloyFurnaceRecipe> recipes) {
        if (stack == null || stack.isEmpty() || recipes == null) return false;
        for (AdvancedAlloyFurnaceRecipe recipe : recipes) {
            if (recipe == null || recipe.mold() == null || recipe.mold().isEmpty()) continue;
            if (recipe.mold().test(stack)) return true;
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
        // for the whole snapshot generation. Build outside the global cache
        // monitor so client rendering cannot block the integrated server.
        Snapshot rebuilt = build(level, snapshot.generation, true);
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

    /**
     * Version-one patterns used representation-sensitive fingerprints. Accept
     * them only when their recipe id and encoded processing contents identify
     * exactly one current recipe, then cache that old identity as an alias.
     */
    public static Optional<Entry> resolveLegacyPattern(
            Level level, AlloyFurnaceRecipeIdentity legacyIdentity, IPatternDetails pattern) {
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
                    : AlloyFurnaceRecipeFingerprint.createLegacy(entry.recipe, level.registryAccess())
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

    public static List<Entry> findPatternCandidates(Level level, IPatternDetails pattern) {
        if (level == null || pattern == null) return List.of();
        return entries(level).stream()
                .filter(entry -> matchesPattern(entry.recipe, pattern))
                .sorted(Comparator.comparing(entry -> entry.identity.recipeId().toString()))
                .toList();
    }

    public static void invalidate() {
        CACHE.clear();
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

    private static Snapshot build(Level level, long generation, boolean compensationRebuildUsed) {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get())) {
            recipes.add(holder.value());
        }

        Collection<RecipeHolder<?>> sourceRecipes = level.getRecipeManager().getRecipes();
        for (IRecipeAdapter<?> adapter : AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters()) {
            if (adapter.getClass().getPackageName().contains(".ae.ae2lt")) continue;
            if (adapter instanceof SeedEssenceRecipeAdapter synthetic) {
                recipes.addAll(synthetic.getAllRecipes());
                continue;
            }
            if (adapter instanceof CrystalGrowthRecipeAdapter synthetic) {
                recipes.addAll(synthetic.getAllRecipes());
                continue;
            }
            collectConverted(adapter, sourceRecipes, level, recipes);
        }
        if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2LT)) {
            recipes.addAll(AELightningTechCompatLoader.getJeiRecipes(level.getRecipeManager(), level));
        }

        Map<AlloyFurnaceRecipeIdentity, Entry> unique = new LinkedHashMap<>();
        for (AdvancedAlloyFurnaceRecipe recipe : recipes) {
            if (recipe == null) continue;
            AlloyFurnaceRecipeIdentity identity = new AlloyFurnaceRecipeIdentity(
                    recipe.id(), AlloyFurnaceRecipeFingerprint.create(recipe, level.registryAccess()));
            unique.putIfAbsent(identity, new Entry(identity, recipe));
        }
        List<Entry> ordered = unique.values().stream()
                .sorted(Comparator.comparing((Entry entry) -> entry.identity.recipeId().toString())
                        .thenComparing(entry -> entry.identity.fingerprint()))
                .toList();
        Map<ResourceLocation, List<Entry>> byRecipeId = new LinkedHashMap<>();
        for (Entry entry : ordered) {
            byRecipeId.computeIfAbsent(entry.identity.recipeId(), ignored -> new ArrayList<>()).add(entry);
        }
        byRecipeId.replaceAll((ignored, entries) -> List.copyOf(entries));
        return new Snapshot(ordered, Map.copyOf(unique), Map.copyOf(byRecipeId), generation,
                new ResolutionMisses(compensationRebuildUsed), new ConcurrentHashMap<>(),
                ConcurrentHashMap.newKeySet());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectConverted(IRecipeAdapter adapter, Collection<RecipeHolder<?>> holders,
                                         Level level, List<AdvancedAlloyFurnaceRecipe> output) {
        Class<?> recipeClass = adapter.getRecipeClass();
        for (RecipeHolder<?> holder : holders) {
            if (!recipeClass.isInstance(holder.value())) continue;
            if (adapter instanceof SmeltingRecipeAdapter && holder.value().getType() != RecipeType.SMELTING) continue;
            output.addAll(adapter.convertAll((RecipeHolder) holder, level));
        }
    }

    private static boolean matchesPattern(AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        PatternContents contents = PatternContents.read(pattern);
        if (contents == null) return false;

        long requiredItemCount = recipe.inputs().stream().mapToLong(CountedIngredient::count).sum();
        long actualItemCount = contents.items.stream().mapToLong(ItemStack::getCount).sum();
        if (requiredItemCount != actualItemCount
                || !ItemIngredientAllocator.matches(recipe.inputs(), contents.items, 1L)) return false;

        if (!sameAmounts(contents.fluids, recipe.inputFluids())) return false;
        if (!sameGeneric(contents.keys, recipe.keyInputs(), false)) return false;

        List<GenericStack> expectedOutputs = new ArrayList<>();
        recipe.outputs().stream().map(GenericStack::fromItemStack).forEach(expectedOutputs::add);
        recipe.outputFluids().stream().map(GenericStack::fromFluidStack).forEach(expectedOutputs::add);
        expectedOutputs.addAll(recipe.keyOutputs());
        return sameGeneric(pattern.getOutputs(), expectedOutputs, true);
    }

    private static boolean sameAmounts(List<FluidStack> left, List<FluidStack> right) {
        Map<AEKey, Long> leftMap = new LinkedHashMap<>();
        for (FluidStack stack : left) leftMap.merge(AEFluidKey.of(stack), (long) stack.getAmount(), Long::sum);
        Map<AEKey, Long> rightMap = new LinkedHashMap<>();
        for (FluidStack stack : right) rightMap.merge(AEFluidKey.of(stack), (long) stack.getAmount(), Long::sum);
        return leftMap.equals(rightMap);
    }

    private static boolean sameGeneric(List<GenericStack> left, List<GenericStack> right, boolean relaxItemComponents) {
        Map<Object, Long> leftMap = genericMap(left, relaxItemComponents);
        Map<Object, Long> rightMap = genericMap(right, relaxItemComponents);
        return leftMap.equals(rightMap);
    }

    private static Map<Object, Long> genericMap(List<GenericStack> stacks, boolean relaxItemComponents) {
        Map<Object, Long> result = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) continue;
            Object key = relaxItemComponents && stack.what() instanceof AEItemKey itemKey
                    ? itemKey.getItem()
                    : stack.what();
            result.merge(key, stack.amount(), Long::sum);
        }
        return result;
    }

    private record Snapshot(
            List<Entry> entries,
            Map<AlloyFurnaceRecipeIdentity, Entry> byIdentity,
            Map<ResourceLocation, List<Entry>> byRecipeId,
            long generation,
            ResolutionMisses misses,
            Map<AlloyFurnaceRecipeIdentity, Entry> compatibilityAliases,
            Set<AlloyFurnaceRecipeIdentity> legacyMisses) {
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

    private record PatternContents(List<ItemStack> items, List<FluidStack> fluids, List<GenericStack> keys) {
        private static PatternContents read(IPatternDetails pattern) {
            List<ItemStack> items = new ArrayList<>();
            List<FluidStack> fluids = new ArrayList<>();
            List<GenericStack> keys = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length == 0 || possible[0] == null || input.getMultiplier() <= 0) return null;
                AEKey key = possible[0].what();
                long amount = input.getMultiplier();
                if (amount > Integer.MAX_VALUE) return null;
                if (key instanceof AEItemKey itemKey) items.add(itemKey.toStack((int) amount));
                else if (key instanceof AEFluidKey fluidKey) fluids.add(new FluidStack(fluidKey.getFluid(), (int) amount));
                else keys.add(new GenericStack(key, amount));
            }
            return new PatternContents(items, fluids, keys);
        }
    }
}
