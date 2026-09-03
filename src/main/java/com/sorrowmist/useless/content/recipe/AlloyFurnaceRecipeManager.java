package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.init.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 高级熔炉配方管理器
 * <p>
 * 统一管理高级熔炉的所有配方来源，包括：
 * - 自定义的 AdvancedAlloyFurnaceRecipe
 * - 原版熔炉配方（通过适配器转换）
 * - 其他模组配方（通过适配器扩展）
 * <p>
 * 提供高效的配方查找和缓存机制，支持按优先级匹配：
 * 物品+流体+模具 > 物品+模具 > 流体+模具 > 物品+流体 > 物品 > 流体
 */
public class AlloyFurnaceRecipeManager {
    private static final AlloyFurnaceRecipeManager INSTANCE = new AlloyFurnaceRecipeManager();

    /** 按模具物品直接查找 adapter（getMoldItem() != null 的注册到这里） */
    private final Map<Item, List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>>> moldAdapterMap = new ConcurrentHashMap<>();
    /** 无固定模具的 adapter，需通过 matchesMold() 动态判断（如 SeedEssenceRecipeAdapter） */
    private final List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> fallbackAdapters = new CopyOnWriteArrayList<>();
    private final List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> allAdapters = new CopyOnWriteArrayList<>();
    private final Map<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>, String> adapterSourceIds = new ConcurrentHashMap<>();

    // access-order LinkedHashMap（LRU）。get() 会改动内部链表，非线程安全，
    // 而 findRecipe 可能被 AE 合成任务和主线程同时调用，故用 synchronizedMap 保护。
    private final Map<LookupCacheKey, CachedLookup> recipeCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<LookupCacheKey, CachedLookup> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    private final Map<AdapterLookupKey, List<RecipeHolder<?>>> adapterMatchCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<AdapterLookupKey, List<RecipeHolder<?>>> eldest) {
                    return size() > MAX_ADAPTER_CACHE_SIZE;
                }
            });
    private final Map<AdapterConversionKey, List<AdvancedAlloyFurnaceRecipe>> conversionCache =
            new ConcurrentHashMap<>();
    private final Map<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>, Boolean> runtimeConversionCache =
            new ConcurrentHashMap<>();

    private static final int MAX_CACHE_SIZE = 500;
    private static final int MAX_ADAPTER_CACHE_SIZE = 500;

    // ========== 预构建索引 ==========

    /**
     * A client and an integrated server have different recipe managers in the same JVM. Keep one
     * immutable index per manager so a lookup can never observe recipes from the other side or a
     * previously closed world.
     */
    private final Map<RecipeManager, RecipeIndex> recipeIndexes =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static AlloyFurnaceRecipeManager getInstance() {
        return INSTANCE;
    }

    private AlloyFurnaceRecipeManager() {
    }

    public void registerAdapter(com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter) {
        registerAdapter(adapter, adapter == null ? RecipeSourceIds.UNKNOWN : adapter.sourceId());
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public void registerAdapter(IRecipeAdapter<?> adapter) {
        registerAdapter((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter);
    }

    public void registerAdapter(com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, String sourceId) {
        if (adapter == null) return;
        ItemStack moldItem = adapter.getMoldItem();

        String normalizedSource = RecipeSourceIds.normalize(sourceId);
        allAdapters.add(adapter);
        adapterSourceIds.put(adapter, normalizedSource);
        if (moldItem != null && !moldItem.isEmpty()) {
            moldAdapterMap.computeIfAbsent(moldItem.getItem(), ignored -> new CopyOnWriteArrayList<>())
                    .add(adapter);
        } else {
            fallbackAdapters.add(adapter);
        }
        clearCache();
        invalidateIndex();
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public void registerAdapter(IRecipeAdapter<?> adapter, String sourceId) {
        registerAdapter((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter, sourceId);
    }

    public List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> getRegisteredAdapters() {
        return List.copyOf(allAdapters);
    }

    public String getAdapterSourceId(com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter) {
        if (adapter == null) return RecipeSourceIds.UNKNOWN;
        return adapterSourceIds.getOrDefault(adapter, RecipeSourceIds.normalize(adapter.sourceId()));
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public String getAdapterSourceId(IRecipeAdapter<?> adapter) {
        return getAdapterSourceId((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter);
    }

    public void buildIndex(Level level) {
        if (level == null) return;

        synchronized (this) {
            clearCache();
            rebuildIndex(level.getRecipeManager());
        }
    }

    private RecipeIndex ensureIndex(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        RecipeIndex index = recipeIndexes.get(recipeManager);
        if (index != null) return index;

        synchronized (this) {
            index = recipeIndexes.get(recipeManager);
            return index != null ? index : rebuildIndex(recipeManager);
        }
    }

    private RecipeIndex rebuildIndex(RecipeManager recipeManager) {
        recipeIndexes.remove(recipeManager);

        RecipeIndexBuilder builder = new RecipeIndexBuilder();
        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = recipeManager
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());
        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            builder.indexRecipe(holder.value());
        }

        // Publish only after every map/list has been frozen.
        RecipeIndex index = builder.build();
        recipeIndexes.put(recipeManager, index);
        return index;
    }

    /** 按当前机器输入查找最具体的可运行配方。 */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs,
                                                  List<FluidStack> fluidInputs, @Nullable ItemStack mold) {
        return findRecipe(level, inputs, fluidInputs, List.of(), mold);
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs,
                                                  List<FluidStack> fluidInputs, List<GenericStack> keyInputs, @Nullable ItemStack mold) {
        return findRecipe(level, new RecipeLookupContext(inputs, fluidInputs, keyInputs, mold, List.of(), 1L));
    }

    /**
     * 按 AE 样板目标输出查找配方。
     *
     * @param expectedOutputs 样板声明的输出；只作为配方身份约束，不比较输出数量
     * @param operations      当前输入聚合了多少次样板操作
     */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipeForCrafting(Level level, List<ItemStack> inputs,
                                                             List<FluidStack> fluidInputs, List<GenericStack> keyInputs,
                                                             @Nullable ItemStack mold, List<GenericStack> expectedOutputs,
                                                             long operations) {
        return findRecipeForCraftingWithConstraints(level, inputs, fluidInputs, keyInputs, mold,
                RecipeOutputConstraint.exact(expectedOutputs), operations);
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipeForCraftingWithConstraints(
            Level level, List<ItemStack> inputs,
            List<FluidStack> fluidInputs, List<GenericStack> keyInputs,
            @Nullable ItemStack mold, List<RecipeOutputConstraint> expectedOutputs,
            long operations) {
        return findRecipe(level, new RecipeLookupContext(
                inputs, fluidInputs, keyInputs, mold, expectedOutputs, operations));
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipe(Level level, RecipeLookupContext context) {
        if (level == null) {
            return null;
        }

        boolean hasItems = !context.inputs().isEmpty();
        boolean hasFluids = !context.fluidInputs().isEmpty();
        boolean hasKeys = !context.keyInputs().isEmpty();
        boolean hasMold = context.mold() != null && !context.mold().isEmpty();

        if (!hasItems && !hasFluids && !hasKeys && !hasMold) {
            return null;
        }

        RecipeCacheKey cacheKey = RecipeCacheKey.from(context);
        LookupCacheKey lookupKey = new LookupCacheKey(level.getRecipeManager(), cacheKey);

        CachedLookup cached = recipeCache.get(lookupKey);
        if (cached != null) {
            return cached.recipe();
        }

        RecipeIndex index = ensureIndex(level);
        List<AdvancedAlloyFurnaceRecipe> indexedCandidates = getCandidateRecipes(index, context);
        Iterable<AdvancedAlloyFurnaceRecipe> candidates = indexedCandidates;
        if (hasMold) {
            RecipeAccumulator mergedCandidates = new RecipeAccumulator(indexedCandidates.size());
            mergedCandidates.addAll(indexedCandidates);
            mergedCandidates.addAll(findAdaptedRecipes(level, context, cacheKey));
            candidates = mergedCandidates.toList();
        }

        AdvancedAlloyFurnaceRecipe recipe = selectBestRecipe(
                candidates, context, new LookupSnapshot(cacheKey.keys()));
        if (recipe != null) {
            cacheRecipe(lookupKey, recipe);
            return recipe;
        }

        cacheNegativeResult(lookupKey);
        return null;
    }

    /**
     * 索引只用于缩小候选集，所有集合都取并集；交集筛选可能在某个候选数量不足时
     * 错误隐藏仍可运行的通用配方。
     */
    private List<AdvancedAlloyFurnaceRecipe> getCandidateRecipes(
            RecipeIndex index, RecipeLookupContext context) {
        RecipeAccumulator candidates = new RecipeAccumulator();

        // These recipes cannot be safely narrowed by a concrete item key. Keep them in every
        // lookup so custom ingredients and empty-display ingredients retain their semantics.
        candidates.addAll(index.nonSimpleIngredientRecipes());
        candidates.addAll(index.unindexedRecipes());

        for (ItemStack input : context.inputs()) {
            if (input == null || input.isEmpty()) continue;
            List<AdvancedAlloyFurnaceRecipe> recipes = index.inputItemIndex().get(input.getItem());
            if (recipes != null) candidates.addAll(recipes);
        }

        if (!context.fluidInputs().isEmpty()) {
            for (FluidStack input : context.fluidInputs()) {
                if (input == null || input.isEmpty()) continue;
                List<AdvancedAlloyFurnaceRecipe> recipes = index.inputFluidIndex().get(input.getFluid());
                if (recipes != null) candidates.addAll(recipes);
            }
            candidates.addAll(index.fluidFallbackRecipes());
        }
        if (!context.keyInputs().isEmpty()) {
            for (GenericStack input : context.keyInputs()) {
                if (input.what() instanceof AEItemKey itemKey) {
                    List<AdvancedAlloyFurnaceRecipe> itemRecipes =
                            index.inputItemIndex().get(itemKey.getItem());
                    if (itemRecipes != null) candidates.addAll(itemRecipes);
                } else if (input.what() instanceof AEFluidKey fluidKey) {
                    List<AdvancedAlloyFurnaceRecipe> fluidRecipes =
                            index.inputFluidIndex().get(fluidKey.getFluid());
                    if (fluidRecipes != null) candidates.addAll(fluidRecipes);
                    candidates.addAll(index.fluidFallbackRecipes());
                } else {
                    List<AdvancedAlloyFurnaceRecipe> recipes = index.keyInputIndex().get(input.what());
                    if (recipes != null) candidates.addAll(recipes);
                }
            }
        }
        if (context.mold() != null && !context.mold().isEmpty()) {
            List<AdvancedAlloyFurnaceRecipe> recipes = index.moldIndex().get(context.mold().getItem());
            if (recipes != null) candidates.addAll(recipes);
        }

        return candidates.toList();
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe selectBestRecipe(Iterable<AdvancedAlloyFurnaceRecipe> candidates,
                                                         RecipeLookupContext context) {
        return selectBestRecipe(candidates, context, LookupSnapshot.from(context));
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe selectBestRecipe(
            Iterable<AdvancedAlloyFurnaceRecipe> candidates,
            RecipeLookupContext context, LookupSnapshot snapshot) {
        AdvancedAlloyFurnaceRecipe best = null;
        RecipeSpecificity bestSpecificity = null;
        List<AdvancedAlloyFurnaceRecipe> equallySpecific = new ArrayList<>();
        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            // The ordinary furnace exposes one mold slot. Multiblock recipes are resolved from
            // their bound Omniversal Pattern and are checked against the mold hub separately.
            if (recipe.molds().size() > 1) continue;
            if (!matchesLookup(recipe, context, snapshot)) continue;
            if (best == null) {
                best = recipe;
                equallySpecific.add(recipe);
                continue;
            }

            RecipeSpecificity candidateSpecificity = RecipeSpecificity.from(recipe);
            if (bestSpecificity == null) {
                bestSpecificity = RecipeSpecificity.from(best);
            }
            int specificity = compareSpecificity(candidateSpecificity, bestSpecificity);
            if (specificity > 0) {
                best = recipe;
                bestSpecificity = candidateSpecificity;
                equallySpecific.clear();
                equallySpecific.add(recipe);
            } else if (specificity == 0) {
                equallySpecific.add(recipe);
                if (compareRecipeId(recipe, best) < 0) {
                    best = recipe;
                    bestSpecificity = candidateSpecificity;
                }
            }
        }

        if (best != null && isAmbiguousManualCraftingLookup(context, equallySpecific)) {
            return null;
        }
        return best;
    }

    /** 包级可见的纯选择入口，供回归测试和其他无世界上下文的调用者使用。 */
    @Nullable
    static AdvancedAlloyFurnaceRecipe selectBestCandidate(Iterable<AdvancedAlloyFurnaceRecipe> candidates,
                                                           List<ItemStack> inputs, List<FluidStack> fluidInputs,
                                                           List<GenericStack> keyInputs, @Nullable ItemStack mold,
                                                           List<GenericStack> expectedOutputs, long operations) {
        RecipeLookupContext context = new RecipeLookupContext(
                inputs, fluidInputs, keyInputs, mold,
                RecipeOutputConstraint.exact(expectedOutputs), operations);
        return getInstance().selectBestRecipe(candidates, context);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe selectBestCandidateWithConstraints(
            Iterable<AdvancedAlloyFurnaceRecipe> candidates,
            List<ItemStack> inputs, List<FluidStack> fluidInputs,
            List<GenericStack> keyInputs, @Nullable ItemStack mold,
            List<RecipeOutputConstraint> expectedOutputs, long operations) {
        RecipeLookupContext context = new RecipeLookupContext(
                inputs, fluidInputs, keyInputs, mold, expectedOutputs, operations);
        return getInstance().selectBestRecipe(candidates, context);
    }

    private boolean matchesLookup(AdvancedAlloyFurnaceRecipe recipe,
                                  RecipeLookupContext context, LookupSnapshot snapshot) {
        return matchesMold(recipe, context.mold())
                && matchesOutputConstraints(recipe, context.expectedOutputs())
                && matchesKeys(recipe, snapshot.keyInputs(), context.operations())
                && matchesItems(recipe, context.inputs(), context.keyInputs(), context.operations())
                && matchesFluids(recipe, context.fluidInputs(), context.keyInputs(), context.operations());
    }

    /** 按“模具专用、输入种类、各类数量、来源”比较具体度；并列项另按 ID 稳定选择。 */
    private int compareSpecificity(RecipeSpecificity candidateSpecificity,
                                   RecipeSpecificity currentSpecificity) {
        boolean candidateHasMold = candidateSpecificity.hasMold();
        boolean currentHasMold = currentSpecificity.hasMold();
        if (candidateHasMold != currentHasMold) return candidateHasMold ? 1 : -1;

        long candidateKinds = candidateSpecificity.inputKinds();
        long currentKinds = currentSpecificity.inputKinds();
        if (candidateKinds != currentKinds) return Long.compare(candidateKinds, currentKinds);

        long candidateItems = candidateSpecificity.itemAmount();
        long currentItems = currentSpecificity.itemAmount();
        if (candidateItems != currentItems) return Long.compare(candidateItems, currentItems);

        long candidateFluids = candidateSpecificity.fluidAmount();
        long currentFluids = currentSpecificity.fluidAmount();
        if (candidateFluids != currentFluids) return Long.compare(candidateFluids, currentFluids);

        long candidateKeys = candidateSpecificity.keyAmount();
        long currentKeys = currentSpecificity.keyAmount();
        if (candidateKeys != currentKeys) return Long.compare(candidateKeys, currentKeys);

        boolean candidateConverted = candidateSpecificity.converted();
        boolean currentConverted = currentSpecificity.converted();
        if (candidateConverted != currentConverted) return candidateConverted ? -1 : 1;

        return 0;
    }

    private int compareRecipeId(AdvancedAlloyFurnaceRecipe left, AdvancedAlloyFurnaceRecipe right) {
        return left.id().toString().compareTo(right.id().toString());
    }

    private boolean isAmbiguousManualCraftingLookup(
            RecipeLookupContext context, List<AdvancedAlloyFurnaceRecipe> candidates) {
        if (!context.expectedOutputs().isEmpty()
                || context.mold() == null
                || !context.mold().is(Items.CRAFTING_TABLE)
                || candidates.size() < 2) {
            return false;
        }

        Map<AEKey, Long> expected = outputSignature(candidates.getFirst());
        for (int index = 1; index < candidates.size(); index++) {
            if (!expected.equals(outputSignature(candidates.get(index)))) {
                return true;
            }
        }
        return false;
    }

    private Map<AEKey, Long> outputSignature(AdvancedAlloyFurnaceRecipe recipe) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (ItemStack output : recipe.outputs()) {
            mergeOutput(result, GenericStack.fromItemStack(output));
        }
        for (FluidStack output : recipe.outputFluids()) {
            mergeOutput(result, GenericStack.fromFluidStack(output));
        }
        for (GenericStack output : recipe.keyOutputs()) {
            mergeOutput(result, output);
        }
        return result;
    }

    private void mergeOutput(Map<AEKey, Long> outputs, @Nullable GenericStack output) {
        if (output == null || output.what() == null || output.amount() <= 0L) {
            return;
        }
        outputs.merge(output.what(), output.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
    }

    private boolean matchesItems(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputs,
                                 List<GenericStack> keyInputs, long operations) {
        return ItemIngredientAllocator.matches(recipe.inputs(), inputs, keyInputs, operations);
    }

    private boolean matchesFluids(AdvancedAlloyFurnaceRecipe recipe, List<FluidStack> inputs, long operations) {
        return FluidIngredientAllocator.matchesLong(recipe.inputFluids(), inputs, operations);
    }

    private boolean matchesFluids(AdvancedAlloyFurnaceRecipe recipe,
                                  List<FluidStack> inputs, List<GenericStack> keyInputs,
                                  long operations) {
        return FluidIngredientAllocator.matchesLong(recipe.inputFluids(), inputs, keyInputs, operations);
    }

    private boolean matchesKeys(AdvancedAlloyFurnaceRecipe recipe,
                                Map<AEKey, Long> inputs, long operations) {
        return containsScaled(inputs, snapshotGenericStacks(recipe.keyInputs()), operations);
    }

    private static boolean containsScaled(Map<AEKey, Long> available, Map<AEKey, Long> required, long operations) {
        for (Map.Entry<AEKey, Long> entry : required.entrySet()) {
            long amount = saturatingMultiply(entry.getValue(), operations);
            if (available.getOrDefault(entry.getKey(), 0L) < amount) return false;
        }
        return true;
    }

    /**
     * AE 输出约束只比较 AEKey（包含组件），不比较数量，以兼容已有倍量样板。
     */
    public static boolean matchesExpectedOutputs(AdvancedAlloyFurnaceRecipe recipe, List<GenericStack> expectedOutputs) {
        return matchesOutputConstraints(recipe, RecipeOutputConstraint.exact(expectedOutputs));
    }

    /**
     * Checks whether the supplied AE materials can cover an exact number of executions of a
     * recipe. Pattern output counts are deliberately not considered here: callers use this when
     * validating a manually scaled processing pattern after its output multiplier was resolved.
     */
    public static boolean matchesInputsForOperations(
            AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputs,
            List<FluidStack> fluidInputs, List<GenericStack> keyInputs, long operations) {
        if (recipe == null || operations <= 0L) {
            return false;
        }
        return ItemIngredientAllocator.matches(recipe.inputs(), inputs, keyInputs, operations)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), fluidInputs, keyInputs, operations)
                && containsScaled(snapshotNonStackKeyInputs(keyInputs),
                snapshotGenericStacks(recipe.keyInputs()), operations);
    }

    public static boolean matchesOutputConstraints(
            AdvancedAlloyFurnaceRecipe recipe, List<RecipeOutputConstraint> expectedOutputs) {
        if (expectedOutputs == null || expectedOutputs.isEmpty()) return true;

        Set<AEKey> available = new LinkedHashSet<>();
        for (ItemStack output : recipe.outputs()) {
            GenericStack stack = GenericStack.fromItemStack(output);
            if (stack != null) available.add(stack.what());
        }
        for (FluidStack output : recipe.outputFluids()) {
            GenericStack stack = GenericStack.fromFluidStack(output);
            if (stack != null) available.add(stack.what());
        }
        for (GenericStack output : recipe.keyOutputs()) {
            if (output != null && output.what() != null) available.add(output.what());
        }

        for (RecipeOutputConstraint expected : expectedOutputs) {
            if (expected == null || available.stream().noneMatch(expected::matches)) return false;
        }
        return true;
    }

    /** 收集所有可能匹配的外部配方，最终由统一匹配器过滤和排序。 */
    private List<AdvancedAlloyFurnaceRecipe> findAdaptedRecipes(
            Level level, RecipeLookupContext context, RecipeCacheKey cacheKey) {
        ItemStack mold = context.mold();
        if (mold == null || mold.isEmpty()) {
            return List.of();
        }

        List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> exactAdapters =
                moldAdapterMap.get(mold.getItem());
        if ((exactAdapters == null || exactAdapters.isEmpty()) && fallbackAdapters.isEmpty()) {
            return List.of();
        }

        List<AdvancedAlloyFurnaceRecipe> candidates = new ArrayList<>();
        Map<Ingredient, Long> mergedInputs = mergeItemInputs(context.inputs(), context.keyInputs());
        Map<FluidStack, Long> mergedFluids = mergeFluidInputs(
                context.fluidInputs(), context.keyInputs());
        Map<AEKey, Long> mergedKeys = AdapterUtils.mergeKeys(context.keyInputs());

        if (exactAdapters != null) {
            for (com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> exactAdapter : exactAdapters) {
                collectAdapterRecipes(exactAdapter, level, context.inputs(), mergedInputs, mergedFluids,
                        mergedKeys, mold, cacheKey, candidates);
            }
        }

        for (com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter : fallbackAdapters) {
            if (adapter.matchesMold(mold)) {
                collectAdapterRecipes(adapter, level, context.inputs(), mergedInputs, mergedFluids,
                        mergedKeys, mold, cacheKey, candidates);
            }
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> void collectAdapterRecipes(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, Level level,
            List<ItemStack> actualInputs,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold,
            RecipeCacheKey cacheKey,
            List<AdvancedAlloyFurnaceRecipe> candidates) {
        com.sorrowmist.useless.api.recipe.IRecipeAdapter<T> typedAdapter =
                (com.sorrowmist.useless.api.recipe.IRecipeAdapter<T>) adapter;
        for (RecipeHolder<T> holder : findAdapterMatches(
                typedAdapter, level, mergedInputs, mergedFluids, mergedKeys, mold, actualInputs, cacheKey)) {
            candidates.addAll(convertAdapterRecipes(typedAdapter, holder, level, actualInputs));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> List<RecipeHolder<T>> findAdapterMatches(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<T> adapter, Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs,
            RecipeCacheKey cacheKey) {
        AdapterLookupKey lookupKey = AdapterLookupKey.of(
                level.getRecipeManager(), adapter, cacheKey, actualInputs);
        List<RecipeHolder<?>> cached = adapterMatchCache.get(lookupKey);
        if (cached != null) {
            return (List<RecipeHolder<T>>) (List<?>) cached;
        }

        List<RecipeHolder<T>> matches = adapter.findMatchingRecipes(
                level, mergedInputs, mergedFluids, mergedKeys, mold, actualInputs);
        if (matches == null || matches.isEmpty()) {
            adapterMatchCache.put(lookupKey, List.of());
            return List.of();
        }

        List<RecipeHolder<?>> immutable = new ArrayList<>(matches.size());
        for (RecipeHolder<T> match : matches) {
            if (match != null) immutable.add(match);
        }
        List<RecipeHolder<?>> result = List.copyOf(immutable);
        adapterMatchCache.put(lookupKey, result);
        return (List<RecipeHolder<T>>) (List<?>) result;
    }

    private <T extends Recipe<?>> List<AdvancedAlloyFurnaceRecipe> convertAdapterRecipes(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<T> adapter,
            RecipeHolder<T> holder, Level level, List<ItemStack> actualInputs) {
        if (usesRuntimeConversion(adapter)) {
            return RecipeConversionUtils.convertAll(adapter, holder, level, actualInputs);
        }

        AdapterConversionKey key = new AdapterConversionKey(
                level.getRecipeManager(), adapter, holder.id());
        return conversionCache.computeIfAbsent(key, ignored -> {
            List<AdvancedAlloyFurnaceRecipe> converted = RecipeConversionUtils.convertAll(
                    adapter, holder, level);
            return converted == null || converted.isEmpty() ? List.of() : List.copyOf(converted);
        });
    }

    private boolean usesRuntimeConversion(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter) {
        return runtimeConversionCache.computeIfAbsent(adapter, current -> {
            try {
                Method method = current.getClass().getMethod(
                        "convertAll", RecipeHolder.class, Level.class, List.class);
                return method.getDeclaringClass()
                        != com.sorrowmist.useless.api.recipe.IRecipeAdapter.class;
            } catch (NoSuchMethodException | SecurityException exception) {
                // Be conservative for an adapter with an unusual bridge/default method.
                return true;
            }
        });
    }

    private boolean matchesMold(AdvancedAlloyFurnaceRecipe recipe, @Nullable ItemStack mold) {
        if (recipe.molds().size() > 1) return false;
        Ingredient requiredMold = recipe.mold();

        if (requiredMold == null || requiredMold.isEmpty()) {
            return true;
        }

        if (mold == null || mold.isEmpty()) {
            return false;
        }

        return AdapterUtils.matchesMold(requiredMold, mold);
    }

    private void cacheRecipe(LookupCacheKey key, AdvancedAlloyFurnaceRecipe recipe) {
        recipeCache.put(key, new CachedLookup(recipe));
    }

    private void cacheNegativeResult(LookupCacheKey key) {
        recipeCache.put(key, new CachedLookup(null));
    }

    public void clearCache() {
        recipeCache.clear();
        adapterMatchCache.clear();
        conversionCache.clear();
    }

    /**
     * 强制重建索引（仅在配方注册表变更时调用，如数据包重载）
     */
    public synchronized void invalidateIndex() {
        recipeIndexes.clear();
        clearCache();
        AlloyFurnaceRecipeCatalog.invalidate();
    }

    private record LookupSnapshot(Map<AEKey, Long> keyInputs) {
        private static LookupSnapshot from(RecipeLookupContext context) {
            return new LookupSnapshot(snapshotNonStackKeyInputs(context.keyInputs()));
        }
    }

    private record RecipeSpecificity(
            boolean hasMold,
            long inputKinds,
            long itemAmount,
            long fluidAmount,
            long keyAmount,
            boolean converted
    ) {
        private static RecipeSpecificity from(AdvancedAlloyFurnaceRecipe recipe) {
            long itemAmount = 0L;
            for (CountedIngredient input : recipe.inputs()) {
                if (input != null) {
                    itemAmount = saturatingAdd(itemAmount, Math.max(0L, input.count()));
                }
            }

            long fluidAmount = 0L;
            for (LongSizedFluidIngredient input : recipe.inputFluids()) {
                if (input != null) {
                    fluidAmount = saturatingAdd(fluidAmount, Math.max(0L, input.amount()));
                }
            }

            long keyAmount = 0L;
            for (GenericStack input : recipe.keyInputs()) {
                if (input != null) {
                    keyAmount = saturatingAdd(keyAmount, Math.max(0L, input.amount()));
                }
            }

            return new RecipeSpecificity(
                    !recipe.molds().isEmpty(),
                    (long) recipe.inputs().size()
                            + recipe.inputFluids().size()
                            + recipe.keyInputs().size(),
                    itemAmount,
                    fluidAmount,
                    keyAmount,
                    recipe.id().getPath().endsWith("_converted"));
        }
    }

    private record RecipeIndex(
            Map<Item, List<AdvancedAlloyFurnaceRecipe>> inputItemIndex,
            Map<Item, List<AdvancedAlloyFurnaceRecipe>> moldIndex,
            Map<Fluid, List<AdvancedAlloyFurnaceRecipe>> inputFluidIndex,
            Map<AEKey, List<AdvancedAlloyFurnaceRecipe>> keyInputIndex,
            List<AdvancedAlloyFurnaceRecipe> fluidFallbackRecipes,
            List<AdvancedAlloyFurnaceRecipe> nonSimpleIngredientRecipes,
            List<AdvancedAlloyFurnaceRecipe> unindexedRecipes
    ) {
    }

    /** Preserves insertion order while deduplicating by identity, avoiding recipe record hashing. */
    private static final class RecipeAccumulator {
        private final List<AdvancedAlloyFurnaceRecipe> recipes;
        private final IdentityHashMap<AdvancedAlloyFurnaceRecipe, Boolean> seen;

        private RecipeAccumulator() {
            this(8);
        }

        private RecipeAccumulator(int expectedSize) {
            int capacity = Math.max(1, expectedSize);
            this.recipes = new ArrayList<>(capacity);
            this.seen = new IdentityHashMap<>(capacity);
        }

        private boolean add(@Nullable AdvancedAlloyFurnaceRecipe recipe) {
            if (recipe == null || seen.put(recipe, Boolean.TRUE) != null) return false;
            recipes.add(recipe);
            return true;
        }

        private void addAll(Iterable<AdvancedAlloyFurnaceRecipe> additions) {
            if (additions == null) return;
            for (AdvancedAlloyFurnaceRecipe recipe : additions) add(recipe);
        }

        private List<AdvancedAlloyFurnaceRecipe> toList() {
            return List.copyOf(recipes);
        }
    }

    /** Builds with identity accumulators, then freezes the result for lock-free read access. */
    private static final class RecipeIndexBuilder {
        private final Map<Item, RecipeAccumulator> inputItemIndex = new LinkedHashMap<>();
        private final Map<Item, RecipeAccumulator> moldIndex = new LinkedHashMap<>();
        private final Map<Fluid, RecipeAccumulator> inputFluidIndex = new LinkedHashMap<>();
        private final Map<AEKey, RecipeAccumulator> keyInputIndex = new LinkedHashMap<>();
        private final RecipeAccumulator fluidFallbackRecipes = new RecipeAccumulator();
        private final RecipeAccumulator nonSimpleIngredientRecipes = new RecipeAccumulator();
        private final RecipeAccumulator unindexedRecipes = new RecipeAccumulator();
        private final IdentityHashMap<AdvancedAlloyFurnaceRecipe, Boolean> indexedRecipes =
                new IdentityHashMap<>();

        private void indexRecipe(AdvancedAlloyFurnaceRecipe recipe) {
            if (recipe == null || indexedRecipes.put(recipe, Boolean.TRUE) != null) return;

            boolean hasSearchableRequirement = false;
            boolean hasMeaningfulRequirement = false;

            for (CountedIngredient counted : recipe.inputs()) {
                if (counted == null || counted.count() <= 0L) continue;
                Ingredient ingredient = counted.ingredient();
                if (ingredient == null || ingredient.isEmpty()) continue;

                hasMeaningfulRequirement = true;
                boolean represented = false;
                try {
                    for (ItemStack stack : ingredient.getItems()) {
                        if (stack != null && !stack.isEmpty()) {
                            add(inputItemIndex, stack.getItem(), recipe);
                            represented = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // A custom ingredient may not expose display representatives.
                }
                if (!isSimple(ingredient)) {
                    nonSimpleIngredientRecipes.add(recipe);
                }
                if (!represented) {
                    unindexedRecipes.add(recipe);
                } else {
                    hasSearchableRequirement = true;
                }
            }

            for (Ingredient mold : recipe.molds()) {
                if (mold == null || mold.isEmpty()) continue;

                hasMeaningfulRequirement = true;
                boolean represented = false;
                try {
                    for (ItemStack stack : mold.getItems()) {
                        if (stack != null && !stack.isEmpty()) {
                            add(moldIndex, stack.getItem(), recipe);
                            represented = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Fall back to the broad list for opaque mold ingredients.
                }
                if (!isSimple(mold)) {
                    nonSimpleIngredientRecipes.add(recipe);
                }
                if (!represented) {
                    unindexedRecipes.add(recipe);
                } else {
                    hasSearchableRequirement = true;
                }
            }

            for (LongSizedFluidIngredient counted : recipe.inputFluids()) {
                if (counted == null || counted.amount() <= 0L
                        || counted.ingredient() == null || counted.ingredient().isEmpty()) {
                    continue;
                }

                hasMeaningfulRequirement = true;
                boolean represented = false;
                try {
                    for (FluidStack stack : counted.ingredient().getStacks()) {
                        if (stack != null && !stack.isEmpty()) {
                            add(inputFluidIndex, stack.getFluid(), recipe);
                            represented = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Custom fluid ingredients are handled by the fallback list.
                }
                if (!isSimple(counted.ingredient()) || !represented) {
                    fluidFallbackRecipes.add(recipe);
                }
                hasSearchableRequirement = true;
            }

            for (GenericStack input : recipe.keyInputs()) {
                if (input == null || input.what() == null || input.amount() <= 0L) continue;
                hasMeaningfulRequirement = true;
                add(keyInputIndex, input.what(), recipe);
                hasSearchableRequirement = true;
            }

            // Recipes with no concrete lookup key (for example catalyst-only recipes) still need
            // to be considered for every query.
            if (!hasMeaningfulRequirement || !hasSearchableRequirement) {
                unindexedRecipes.add(recipe);
            }
        }

        private RecipeIndex build() {
            return new RecipeIndex(
                    freeze(inputItemIndex),
                    freeze(moldIndex),
                    freeze(inputFluidIndex),
                    freeze(keyInputIndex),
                    fluidFallbackRecipes.toList(),
                    nonSimpleIngredientRecipes.toList(),
                    unindexedRecipes.toList());
        }

        private static boolean isSimple(Ingredient ingredient) {
            try {
                return ingredient.isSimple();
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private static boolean isSimple(FluidIngredient ingredient) {
            try {
                return ingredient.isSimple();
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private static <K> void add(
                Map<K, RecipeAccumulator> index,
                K key, AdvancedAlloyFurnaceRecipe recipe) {
            index.computeIfAbsent(key, ignored -> new RecipeAccumulator()).add(recipe);
        }

        private static <K> Map<K, List<AdvancedAlloyFurnaceRecipe>> freeze(
                Map<K, RecipeAccumulator> source) {
            Map<K, List<AdvancedAlloyFurnaceRecipe>> result = new LinkedHashMap<>(source.size());
            for (Map.Entry<K, RecipeAccumulator> entry : source.entrySet()) {
                result.put(entry.getKey(), entry.getValue().toList());
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private record RecipeLookupContext(
            List<ItemStack> inputs,
            List<FluidStack> fluidInputs,
            List<GenericStack> keyInputs,
            @Nullable ItemStack mold,
            List<RecipeOutputConstraint> expectedOutputs,
            long operations
    ) {
        private RecipeLookupContext {
            inputs = inputs == null ? List.of() : inputs;
            fluidInputs = fluidInputs == null ? List.of() : fluidInputs;
            keyInputs = requireKeyInputs(keyInputs);
            expectedOutputs = expectedOutputs == null ? List.of() : expectedOutputs;
            operations = Math.max(1L, operations);
        }
    }

    private static List<GenericStack> requireKeyInputs(@Nullable List<GenericStack> inputs) {
        if (inputs == null) {
            return List.of();
        }
        for (GenericStack input : inputs) {
            Objects.requireNonNull(input, "Recipe key input");
            if (input.what() == null || input.amount() <= 0L) {
                throw new IllegalArgumentException("Recipe key inputs must contain a key and positive amount");
            }
        }
        return inputs;
    }

    /** Represents both a successful and a negative lookup without a second map. */
    private record CachedLookup(@Nullable AdvancedAlloyFurnaceRecipe recipe) {
    }

    /** The public cache fingerprint remains order-independent; manager identity is internal. */
    private record LookupCacheKey(RecipeManager recipeManager, RecipeCacheKey lookup) {
    }

    /** Static conversions are scoped to the recipe manager that supplied the holder. */
    private record AdapterConversionKey(
            RecipeManager recipeManager,
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter,
            ResourceLocation recipeId
    ) {
    }

    /**
     * Adapter matching can inspect the concrete input list, so its key keeps stack order and
     * components in addition to the aggregated lookup fingerprint.
     */
    private record AdapterLookupKey(
            RecipeManager recipeManager,
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter,
            Map<AEKey, Long> items,
            Map<AEKey, Long> fluids,
            Map<AEKey, Long> keys,
            @Nullable AEKey mold,
            List<OrderedItemSignature> orderedInputs
    ) {
        private static AdapterLookupKey of(
                RecipeManager recipeManager,
                com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter,
                RecipeCacheKey lookup,
                List<ItemStack> actualInputs) {
            return new AdapterLookupKey(
                    recipeManager,
                    adapter,
                    lookup.items(),
                    lookup.fluids(),
                    lookup.keys(),
                    lookup.mold(),
                    orderedItemSignature(actualInputs));
        }
    }

    private record OrderedItemSignature(@Nullable AEKey key, int count) {
    }

    /** 只保存不可变的 AEKey/数量快照，不持有调用方可变的 ItemStack。 */
    static record RecipeCacheKey(
            Map<AEKey, Long> items,
            Map<AEKey, Long> fluids,
            Map<AEKey, Long> keys,
            @Nullable AEKey mold,
            List<RecipeOutputConstraint> expectedOutputs,
            long operations
    ) {
        private static RecipeCacheKey from(RecipeLookupContext context) {
            LookupInputFingerprint fingerprint = LookupInputFingerprint.from(context);
            GenericStack moldStack = context.mold() == null || context.mold().isEmpty()
                    ? null
                    : GenericStack.fromItemStack(context.mold());
            return new RecipeCacheKey(
                    fingerprint.items(),
                    fingerprint.fluids(),
                    fingerprint.keys(),
                    moldStack == null ? null : moldStack.what(),
                    List.copyOf(context.expectedOutputs()),
                    context.operations()
            );
        }

        static RecipeCacheKey create(List<ItemStack> inputs, List<FluidStack> fluidInputs,
                                     List<GenericStack> keyInputs, @Nullable ItemStack mold,
                                     List<GenericStack> expectedOutputs, long operations) {
            return from(new RecipeLookupContext(
                    inputs, fluidInputs, keyInputs, mold,
                    RecipeOutputConstraint.exact(expectedOutputs), operations));
        }

        static RecipeCacheKey createWithConstraints(
                List<ItemStack> inputs, List<FluidStack> fluidInputs,
                List<GenericStack> keyInputs, @Nullable ItemStack mold,
                List<RecipeOutputConstraint> expectedOutputs, long operations) {
            return from(new RecipeLookupContext(
                    inputs, fluidInputs, keyInputs, mold, expectedOutputs, operations));
        }
    }

    /** Builds all cache-key input maps in one pass over each supplied collection. */
    private record LookupInputFingerprint(
            Map<AEKey, Long> items,
            Map<AEKey, Long> fluids,
            Map<AEKey, Long> keys
    ) {
        private static LookupInputFingerprint from(RecipeLookupContext context) {
            Map<AEKey, Long> items = new LinkedHashMap<>();
            for (ItemStack stack : context.inputs()) {
                if (stack == null || stack.isEmpty()) continue;
                GenericStack genericStack = GenericStack.fromItemStack(stack);
                if (genericStack != null) {
                    items.merge(genericStack.what(), (long) stack.getCount(),
                            AlloyFurnaceRecipeManager::saturatingAdd);
                }
            }

            Map<AEKey, Long> fluids = new LinkedHashMap<>();
            for (FluidStack stack : context.fluidInputs()) {
                if (stack == null || stack.isEmpty()) continue;
                GenericStack genericStack = GenericStack.fromFluidStack(stack);
                if (genericStack != null) {
                    fluids.merge(genericStack.what(), (long) stack.getAmount(),
                            AlloyFurnaceRecipeManager::saturatingAdd);
                }
            }

            Map<AEKey, Long> keys = new LinkedHashMap<>();
            for (GenericStack stack : context.keyInputs()) {
                if (stack.what() instanceof AEItemKey) {
                    items.merge(stack.what(), stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
                } else if (stack.what() instanceof AEFluidKey) {
                    fluids.merge(stack.what(), stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
                } else {
                    keys.merge(stack.what(), stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
                }
            }

            return new LookupInputFingerprint(
                    Map.copyOf(items), Map.copyOf(fluids), Map.copyOf(keys));
        }
    }

    private static List<OrderedItemSignature> orderedItemSignature(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return List.of();

        List<OrderedItemSignature> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                result.add(new OrderedItemSignature(null, 0));
                continue;
            }

            GenericStack genericStack = GenericStack.fromItemStack(stack);
            result.add(new OrderedItemSignature(
                    genericStack == null ? null : genericStack.what(),
                    stack.getCount()));
        }
        return List.copyOf(result);
    }

    /** Gives item-based adapters the same long amounts for physical stacks and AE item keys. */
    private static Map<Ingredient, Long> mergeItemInputs(
            List<ItemStack> stacks, List<GenericStack> keyInputs) {
        Object2LongLinkedOpenHashMap<AEItemKey> amounts = new Object2LongLinkedOpenHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            amounts.mergeLong(AEItemKey.of(stack),
                    stack.getCount(), AlloyFurnaceRecipeManager::saturatingAdd);
        }
        for (GenericStack stack : keyInputs) {
            if (stack.what() instanceof AEItemKey itemKey) {
                amounts.mergeLong(itemKey,
                        stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
            }
        }
        Object2LongLinkedOpenHashMap<Ingredient> result = new Object2LongLinkedOpenHashMap<>(amounts.size());
        for (var entry : amounts.object2LongEntrySet()) {
            result.put(DataComponentIngredient.of(true, entry.getKey().toStack(1)), entry.getLongValue());
        }
        return result;
    }

    private static Map<FluidStack, Long> mergeFluidInputs(
            List<FluidStack> stacks, List<GenericStack> keyInputs) {
        Map<FluidStack, Long> result = new LinkedHashMap<>();
        if (stacks != null) {
            for (FluidStack stack : stacks) {
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0) {
                    mergeFluidAmount(result, stack, stack.getAmount());
                }
            }
        }
        for (GenericStack input : keyInputs) {
            if (input.what() instanceof AEFluidKey fluidKey) {
                mergeFluidAmount(result, fluidKey.toStack(1), input.amount());
            }
        }
        return result;
    }

    private static void mergeFluidAmount(Map<FluidStack, Long> target,
                                          FluidStack stack, long amount) {
        for (Map.Entry<FluidStack, Long> entry : target.entrySet()) {
            if (FluidStack.isSameFluidSameComponents(entry.getKey(), stack)) {
                entry.setValue(saturatingAdd(entry.getValue(), amount));
                return;
            }
        }
        target.put(stack.copyWithAmount(1), amount);
    }

    private static Map<AEKey, Long> snapshotNonStackKeyInputs(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey) {
                continue;
            }
            result.merge(stack.what(), stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> snapshotGenericStacks(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            result.merge(stack.what(), stack.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
        }
        return Map.copyOf(result);
    }

    private static long saturatingAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0 || multiplier <= 0) return 0;
        if (amount > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return amount * multiplier;
    }
}
