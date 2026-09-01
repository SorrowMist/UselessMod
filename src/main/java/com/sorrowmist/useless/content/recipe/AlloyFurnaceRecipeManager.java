package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static AlloyFurnaceRecipeManager INSTANCE;

    /** 按模具物品直接查找 adapter（getMoldItem() != null 的注册到这里） */
    private final Map<Item, List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>>> moldAdapterMap = new ConcurrentHashMap<>();
    /** 无固定模具的 adapter，需通过 matchesMold() 动态判断（如 SeedEssenceRecipeAdapter） */
    private final List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> fallbackAdapters = new CopyOnWriteArrayList<>();
    private final List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> allAdapters = new CopyOnWriteArrayList<>();
    private final Map<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>, String> adapterSourceIds = new ConcurrentHashMap<>();

    // access-order LinkedHashMap（LRU）。get() 会改动内部链表，非线程安全，
    // 而 findRecipe 可能被 AE 合成任务和主线程同时调用，故用 synchronizedMap 保护。
    private final Map<RecipeCacheKey, AdvancedAlloyFurnaceRecipe> recipeCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, AdvancedAlloyFurnaceRecipe> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    private final Map<RecipeCacheKey, Boolean> negativeCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RecipeCacheKey, Boolean> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private static final int MAX_CACHE_SIZE = 500;

    // ========== 预构建索引 ==========

    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> inputItemIndex = new ConcurrentHashMap<>();
    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> moldIndex = new ConcurrentHashMap<>();
    private final List<AdvancedAlloyFurnaceRecipe> hasFluidInputRecipes = new CopyOnWriteArrayList<>();
    private final List<AdvancedAlloyFurnaceRecipe> hasKeyInputRecipes = new CopyOnWriteArrayList<>();
    private final Set<AdvancedAlloyFurnaceRecipe> hasNonSimpleIngredientRecipes = ConcurrentHashMap.newKeySet();
    private final Set<AdvancedAlloyFurnaceRecipe> indexedRecipes = ConcurrentHashMap.newKeySet();

    private boolean indexBuilt = false;

    public static AlloyFurnaceRecipeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AlloyFurnaceRecipeManager();
        }
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
        if (level == null || level.isClientSide()) return;

        clearIndex();
        clearCache();

        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            indexRecipe(holder.value());
        }

        indexBuilt = true;
    }

    private void clearIndex() {
        inputItemIndex.clear();
        moldIndex.clear();
        hasFluidInputRecipes.clear();
        hasKeyInputRecipes.clear();
        hasNonSimpleIngredientRecipes.clear();
        indexedRecipes.clear();
        indexBuilt = false;
    }

    private void indexRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null || indexedRecipes.contains(recipe)) {
            return;
        }
        indexedRecipes.add(recipe);

        for (CountedIngredient countedIng : recipe.inputs()) {
            Ingredient ingredient = countedIng.ingredient();
            if (!ingredient.isSimple()) {
                // Non-simple/custom ingredient item lists are display hints and may be empty or incomplete.
                hasNonSimpleIngredientRecipes.add(recipe);
            }
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    inputItemIndex
                            .computeIfAbsent(stack.getItem(), k -> new CopyOnWriteArrayList<>())
                            .add(recipe);
                }
            }
        }

        for (Ingredient mold : recipe.molds()) {
            if (mold == null || mold.isEmpty()) continue;
            if (!mold.isSimple()) {
                hasNonSimpleIngredientRecipes.add(recipe);
            }
            for (ItemStack stack : mold.getItems()) {
                if (!stack.isEmpty()) {
                    moldIndex
                            .computeIfAbsent(stack.getItem(), k -> new CopyOnWriteArrayList<>())
                            .add(recipe);
                }
            }
        }

        if (!recipe.inputFluids().isEmpty()) {
            hasFluidInputRecipes.add(recipe);
        }
        if (!recipe.keyInputs().isEmpty()) {
            hasKeyInputRecipes.add(recipe);
        }
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

        if (!indexBuilt && !level.isClientSide()) {
            buildIndex(level);
        }

        RecipeCacheKey cacheKey = RecipeCacheKey.from(context);

        AdvancedAlloyFurnaceRecipe cachedRecipe = recipeCache.get(cacheKey);
        if (cachedRecipe != null) {
            return cachedRecipe;
        }

        if (negativeCache.containsKey(cacheKey)) {
            return null;
        }

        LinkedHashSet<AdvancedAlloyFurnaceRecipe> candidates = new LinkedHashSet<>(getCandidateRecipes(context));

        if (hasMold) {
            candidates.addAll(findAdaptedRecipes(level, context));
        }

        AdvancedAlloyFurnaceRecipe recipe = selectBestRecipe(candidates, context);
        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        cacheNegativeResult(cacheKey);
        return null;
    }

    /**
     * 索引只用于缩小候选集，所有集合都取并集；交集筛选可能在某个候选数量不足时
     * 错误隐藏仍可运行的通用配方。
     */
    private List<AdvancedAlloyFurnaceRecipe> getCandidateRecipes(RecipeLookupContext context) {
        LinkedHashSet<AdvancedAlloyFurnaceRecipe> candidates = new LinkedHashSet<>();

        candidates.addAll(hasNonSimpleIngredientRecipes);

        for (ItemStack input : context.inputs()) {
            if (input.isEmpty()) continue;
            List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
            if (recipes != null) candidates.addAll(recipes);
        }

        boolean hasFluidInput = !context.fluidInputs().isEmpty();
        boolean hasNonStackKeyInput = false;
        for (GenericStack input : context.keyInputs()) {
            if (input.what() instanceof AEItemKey itemKey) {
                List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(itemKey.getItem());
                if (recipes != null) candidates.addAll(recipes);
            } else if (input.what() instanceof AEFluidKey) {
                hasFluidInput = true;
            } else {
                hasNonStackKeyInput = true;
            }
        }
        if (hasFluidInput) {
            candidates.addAll(hasFluidInputRecipes);
        }
        if (hasNonStackKeyInput) {
            candidates.addAll(hasKeyInputRecipes);
        }
        if (context.mold() != null && !context.mold().isEmpty()) {
            List<AdvancedAlloyFurnaceRecipe> recipes = moldIndex.get(context.mold().getItem());
            if (recipes != null) candidates.addAll(recipes);
        }

        if (candidates.isEmpty()) {
            candidates.addAll(indexedRecipes);
        }
        return new ArrayList<>(candidates);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe selectBestRecipe(Iterable<AdvancedAlloyFurnaceRecipe> candidates,
                                                         RecipeLookupContext context) {
        AdvancedAlloyFurnaceRecipe best = null;
        List<AdvancedAlloyFurnaceRecipe> equallySpecific = new ArrayList<>();
        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            // The ordinary furnace exposes one mold slot. Multiblock recipes are resolved from
            // their bound Omniversal Pattern and are checked against the mold hub separately.
            if (recipe.molds().size() > 1) continue;
            if (!matchesLookup(recipe, context)) continue;
            if (best == null) {
                best = recipe;
                equallySpecific.add(recipe);
                continue;
            }

            int specificity = compareSpecificity(recipe, best);
            if (specificity > 0) {
                best = recipe;
                equallySpecific.clear();
                equallySpecific.add(recipe);
            } else if (specificity == 0) {
                equallySpecific.add(recipe);
                if (compareRecipeId(recipe, best) < 0) {
                    best = recipe;
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

    private boolean matchesLookup(AdvancedAlloyFurnaceRecipe recipe, RecipeLookupContext context) {
        return matchesMold(recipe, context.mold())
                && matchesItems(recipe, context.inputs(), context.keyInputs(), context.operations())
                && matchesFluids(recipe, context.fluidInputs(), context.keyInputs(), context.operations())
                && matchesKeys(recipe, context.keyInputs(), context.operations())
                && matchesOutputConstraints(recipe, context.expectedOutputs());
    }

    /** 按“模具专用、输入种类、各类数量、来源”比较具体度；并列项另按 ID 稳定选择。 */
    private int compareSpecificity(AdvancedAlloyFurnaceRecipe candidate, AdvancedAlloyFurnaceRecipe current) {
        boolean candidateHasMold = !candidate.molds().isEmpty();
        boolean currentHasMold = !current.molds().isEmpty();
        if (candidateHasMold != currentHasMold) return candidateHasMold ? 1 : -1;

        long candidateKinds = inputKindCount(candidate);
        long currentKinds = inputKindCount(current);
        if (candidateKinds != currentKinds) return Long.compare(candidateKinds, currentKinds);

        long candidateItems = requiredItemAmount(candidate);
        long currentItems = requiredItemAmount(current);
        if (candidateItems != currentItems) return Long.compare(candidateItems, currentItems);

        long candidateFluids = requiredFluidAmount(candidate);
        long currentFluids = requiredFluidAmount(current);
        if (candidateFluids != currentFluids) return Long.compare(candidateFluids, currentFluids);

        long candidateKeys = requiredKeyAmount(candidate);
        long currentKeys = requiredKeyAmount(current);
        if (candidateKeys != currentKeys) return Long.compare(candidateKeys, currentKeys);

        boolean candidateConverted = isConvertedRecipe(candidate);
        boolean currentConverted = isConvertedRecipe(current);
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

    private long inputKindCount(AdvancedAlloyFurnaceRecipe recipe) {
        return (long) recipe.inputs().size() + recipe.inputFluids().size() + recipe.keyInputs().size();
    }

    private long requiredItemAmount(AdvancedAlloyFurnaceRecipe recipe) {
        long result = 0;
        for (CountedIngredient input : recipe.inputs()) {
            result = saturatingAdd(result, Math.max(0L, input.count()));
        }
        return result;
    }

    private long requiredFluidAmount(AdvancedAlloyFurnaceRecipe recipe) {
        long result = 0;
        for (LongSizedFluidIngredient input : recipe.inputFluids()) {
            if (input != null) result = saturatingAdd(result, Math.max(0, input.amount()));
        }
        return result;
    }

    private long requiredKeyAmount(AdvancedAlloyFurnaceRecipe recipe) {
        long result = 0;
        for (GenericStack input : recipe.keyInputs()) {
            if (input != null) result = saturatingAdd(result, Math.max(0L, input.amount()));
        }
        return result;
    }

    private boolean isConvertedRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return recipe.id().getPath().endsWith("_converted");
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

    private boolean matchesKeys(AdvancedAlloyFurnaceRecipe recipe, List<GenericStack> inputs, long operations) {
        return containsScaled(
                snapshotNonStackKeyInputs(inputs), snapshotGenericStacks(recipe.keyInputs()), operations);
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
        List<GenericStack> validKeyInputs = requireKeyInputs(keyInputs);
        return ItemIngredientAllocator.matches(recipe.inputs(), inputs, validKeyInputs, operations)
                && FluidIngredientAllocator.matchesLong(recipe.inputFluids(), fluidInputs, validKeyInputs, operations)
                && containsScaled(snapshotNonStackKeyInputs(validKeyInputs),
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
    private List<AdvancedAlloyFurnaceRecipe> findAdaptedRecipes(Level level, RecipeLookupContext context) {
        List<AdvancedAlloyFurnaceRecipe> candidates = new ArrayList<>();
        Map<Ingredient, Long> mergedInputs = AdapterUtils.mergeInputs(context.inputs());
        Map<FluidStack, Long> mergedFluids = mergeFluidInputs(
                context.fluidInputs(), context.keyInputs());
        Map<AEKey, Long> mergedKeys = AdapterUtils.mergeKeys(context.keyInputs());

        ItemStack mold = context.mold();
        if (mold != null && !mold.isEmpty()) {
            List<com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>> exactAdapters = moldAdapterMap.get(mold.getItem());
            if (exactAdapters != null) {
                for (com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> exactAdapter : exactAdapters) {
                    collectAdapterRecipes(exactAdapter, level, context.inputs(), mergedInputs, mergedFluids,
                            mergedKeys, mold, candidates);
                }
            }
        }

        for (com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter : fallbackAdapters) {
            if (adapter.matchesMold(mold)) {
                collectAdapterRecipes(adapter, level, context.inputs(), mergedInputs, mergedFluids,
                        mergedKeys, mold, candidates);
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
            List<AdvancedAlloyFurnaceRecipe> candidates) {
        com.sorrowmist.useless.api.recipe.IRecipeAdapter<T> typedAdapter =
                (com.sorrowmist.useless.api.recipe.IRecipeAdapter<T>) adapter;
        for (RecipeHolder<T> holder : typedAdapter.findMatchingRecipes(
                level, mergedInputs, mergedFluids, mergedKeys, mold, actualInputs)) {
            candidates.addAll(RecipeConversionUtils.convertAll(typedAdapter, holder, level, actualInputs));
        }
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

    private void cacheRecipe(RecipeCacheKey key, AdvancedAlloyFurnaceRecipe recipe) {
        recipeCache.put(key, recipe);
    }

    private void cacheNegativeResult(RecipeCacheKey key) {
        negativeCache.put(key, Boolean.TRUE);
    }

    public void clearCache() {
        recipeCache.clear();
        negativeCache.clear();
    }

    /**
     * 强制重建索引（仅在配方注册表变更时调用，如数据包重载）
     */
    public void invalidateIndex() {
        indexBuilt = false;
        AlloyFurnaceRecipeCatalog.invalidate();
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
            GenericStack moldStack = context.mold() == null || context.mold().isEmpty()
                    ? null
                    : GenericStack.fromItemStack(context.mold());
            return new RecipeCacheKey(
                    snapshotItemInputs(context.inputs(), context.keyInputs()),
                    snapshotFluidInputs(context.fluidInputs(), context.keyInputs()),
                    snapshotNonStackKeyInputs(context.keyInputs()),
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

    private static Map<AEKey, Long> snapshotItems(List<ItemStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        if (stacks == null) return Map.of();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack != null) {
                result.merge(genericStack.what(), (long) stack.getCount(), AlloyFurnaceRecipeManager::saturatingAdd);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> snapshotItemInputs(
            List<ItemStack> stacks, List<GenericStack> keyInputs) {
        Map<AEKey, Long> result = new LinkedHashMap<>(snapshotItems(stacks));
        for (GenericStack input : keyInputs) {
            if (input.what() instanceof AEItemKey) {
                result.merge(input.what(), input.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> snapshotFluids(List<FluidStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        if (stacks == null) return Map.of();
        for (FluidStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            GenericStack genericStack = GenericStack.fromFluidStack(stack);
            if (genericStack != null) {
                result.merge(genericStack.what(), (long) stack.getAmount(), AlloyFurnaceRecipeManager::saturatingAdd);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> snapshotFluidInputs(
            List<FluidStack> stacks, List<GenericStack> keyInputs) {
        Map<AEKey, Long> result = new LinkedHashMap<>(snapshotFluids(stacks));
        for (GenericStack input : keyInputs) {
            if (input.what() instanceof AEFluidKey) {
                result.merge(input.what(), input.amount(), AlloyFurnaceRecipeManager::saturatingAdd);
            }
        }
        return Map.copyOf(result);
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
