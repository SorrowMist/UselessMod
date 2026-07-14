package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AlloyFurnaceRecipeManager INSTANCE;

    /** 按模具物品直接查找 adapter（getMoldItem() != null 的注册到这里） */
    private final Map<Item, IRecipeAdapter<?>> moldAdapterMap = new ConcurrentHashMap<>();
    /** 无固定模具的 adapter，需通过 matchesMold() 动态判断（如 SeedEssenceRecipeAdapter） */
    private final List<IRecipeAdapter<?>> fallbackAdapters = new CopyOnWriteArrayList<>();

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

    public void registerAdapter(IRecipeAdapter<?> adapter) {
        ItemStack moldItem = adapter.getMoldItem();
        if (moldItem != null && !moldItem.isEmpty()) {
            IRecipeAdapter<?> existing = moldAdapterMap.put(moldItem.getItem(), adapter);
            if (existing != null) {
                LOGGER.warn("模具物品 {} 已注册到 adapter: {}, 被覆盖为: {}", moldItem.getItem(), existing.getClass().getSimpleName(), adapter.getClass().getSimpleName());
            }
        } else {
            fallbackAdapters.add(adapter);
        }
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
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    inputItemIndex
                            .computeIfAbsent(stack.getItem(), k -> new CopyOnWriteArrayList<>())
                            .add(recipe);
                }
            }
        }

        Ingredient mold = recipe.mold();
        if (mold != null && !mold.isEmpty()) {
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
        return findRecipe(level, new RecipeLookupContext(inputs, fluidInputs, keyInputs, mold, expectedOutputs, operations));
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

        for (ItemStack input : context.inputs()) {
            if (input.isEmpty()) continue;
            List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
            if (recipes != null) candidates.addAll(recipes);
        }

        if (!context.fluidInputs().isEmpty()) {
            candidates.addAll(hasFluidInputRecipes);
        }
        if (!context.keyInputs().isEmpty()) {
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
        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            if (!matchesLookup(recipe, context)) continue;
            if (best == null || isMoreSpecific(recipe, best)) {
                best = recipe;
            }
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
                inputs, fluidInputs, keyInputs, mold, expectedOutputs, operations);
        return getInstance().selectBestRecipe(candidates, context);
    }

    private boolean matchesLookup(AdvancedAlloyFurnaceRecipe recipe, RecipeLookupContext context) {
        return matchesMold(recipe, context.mold())
                && matchesItems(recipe, context.inputs(), context.operations())
                && matchesFluids(recipe, context.fluidInputs(), context.operations())
                && matchesKeys(recipe, context.keyInputs(), context.operations())
                && matchesExpectedOutputs(recipe, context.expectedOutputs());
    }

    /** 按“模具专用、输入种类、各类数量、来源、ID”稳定比较具体度。 */
    private boolean isMoreSpecific(AdvancedAlloyFurnaceRecipe candidate, AdvancedAlloyFurnaceRecipe current) {
        boolean candidateHasMold = !candidate.mold().isEmpty();
        boolean currentHasMold = !current.mold().isEmpty();
        if (candidateHasMold != currentHasMold) return candidateHasMold;

        long candidateKinds = inputKindCount(candidate);
        long currentKinds = inputKindCount(current);
        if (candidateKinds != currentKinds) return candidateKinds > currentKinds;

        long candidateItems = requiredItemAmount(candidate);
        long currentItems = requiredItemAmount(current);
        if (candidateItems != currentItems) return candidateItems > currentItems;

        long candidateFluids = requiredFluidAmount(candidate);
        long currentFluids = requiredFluidAmount(current);
        if (candidateFluids != currentFluids) return candidateFluids > currentFluids;

        long candidateKeys = requiredKeyAmount(candidate);
        long currentKeys = requiredKeyAmount(current);
        if (candidateKeys != currentKeys) return candidateKeys > currentKeys;

        boolean candidateConverted = isConvertedRecipe(candidate);
        boolean currentConverted = isConvertedRecipe(current);
        if (candidateConverted != currentConverted) return !candidateConverted;

        return candidate.id().toString().compareTo(current.id().toString()) < 0;
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
        for (FluidStack input : recipe.inputFluids()) {
            result = saturatingAdd(result, Math.max(0, input.getAmount()));
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

    private boolean matchesItems(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputs, long operations) {
        for (CountedIngredient countedIngredient : recipe.inputs()) {
            long required = saturatingMultiply(Math.max(0L, countedIngredient.count()), operations);
            long found = 0;
            for (ItemStack input : inputs) {
                if (!input.isEmpty() && countedIngredient.ingredient().test(input)) {
                    found = saturatingAdd(found, input.getCount());
                }
            }
            if (found < required) return false;
        }
        return true;
    }

    private boolean matchesFluids(AdvancedAlloyFurnaceRecipe recipe, List<FluidStack> inputs, long operations) {
        Map<AEKey, Long> available = snapshotFluids(inputs);
        Map<AEKey, Long> required = snapshotFluids(recipe.inputFluids());
        return containsScaled(available, required, operations);
    }

    private boolean matchesKeys(AdvancedAlloyFurnaceRecipe recipe, List<GenericStack> inputs, long operations) {
        return containsScaled(snapshotGenericStacks(inputs), snapshotGenericStacks(recipe.keyInputs()), operations);
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

        for (GenericStack expected : expectedOutputs) {
            if (expected == null || expected.what() == null || !available.contains(expected.what())) return false;
        }
        return true;
    }

    /** 收集所有可能匹配的外部配方，最终由统一匹配器过滤和排序。 */
    private List<AdvancedAlloyFurnaceRecipe> findAdaptedRecipes(Level level, RecipeLookupContext context) {
        List<AdvancedAlloyFurnaceRecipe> candidates = new ArrayList<>();
        Map<Ingredient, Long> mergedInputs = AdapterUtils.mergeInputs(context.inputs());
        Map<FluidStack, Long> mergedFluids = AdapterUtils.mergeFluids(context.fluidInputs());
        Map<AEKey, Long> mergedKeys = AdapterUtils.mergeKeys(context.keyInputs());

        ItemStack mold = context.mold();
        if (mold != null && !mold.isEmpty()) {
            IRecipeAdapter<?> exactAdapter = moldAdapterMap.get(mold.getItem());
            if (exactAdapter != null) {
                collectAdapterRecipes(exactAdapter, level, mergedInputs, mergedFluids, mergedKeys, mold, candidates);
            }
        }

        for (IRecipeAdapter<?> adapter : fallbackAdapters) {
            if (adapter.matchesMold(mold)) {
                collectAdapterRecipes(adapter, level, mergedInputs, mergedFluids, mergedKeys, mold, candidates);
            }
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> void collectAdapterRecipes(
            IRecipeAdapter<?> adapter, Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold,
            List<AdvancedAlloyFurnaceRecipe> candidates) {
        IRecipeAdapter<T> typedAdapter = (IRecipeAdapter<T>) adapter;
        for (RecipeHolder<T> holder : typedAdapter.findMatchingRecipes(level, mergedInputs, mergedFluids, mergedKeys, mold)) {
            candidates.addAll(typedAdapter.convertAll(holder, level));
        }
    }

    private boolean matchesMold(AdvancedAlloyFurnaceRecipe recipe, @Nullable ItemStack mold) {
        Ingredient requiredMold = recipe.mold();

        if (requiredMold == null || requiredMold.isEmpty()) {
            return true;
        }

        if (mold == null || mold.isEmpty()) {
            return false;
        }

        return requiredMold.test(mold);
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
    }

    private record RecipeLookupContext(
            List<ItemStack> inputs,
            List<FluidStack> fluidInputs,
            List<GenericStack> keyInputs,
            @Nullable ItemStack mold,
            List<GenericStack> expectedOutputs,
            long operations
    ) {
        private RecipeLookupContext {
            inputs = inputs == null ? List.of() : inputs;
            fluidInputs = fluidInputs == null ? List.of() : fluidInputs;
            keyInputs = keyInputs == null ? List.of() : keyInputs;
            expectedOutputs = expectedOutputs == null ? List.of() : expectedOutputs;
            operations = Math.max(1L, operations);
        }
    }

    /** 只保存不可变的 AEKey/数量快照，不持有调用方可变的 ItemStack。 */
    static record RecipeCacheKey(
            Map<AEKey, Long> items,
            Map<AEKey, Long> fluids,
            Map<AEKey, Long> keys,
            @Nullable AEKey mold,
            Map<AEKey, Long> expectedOutputs,
            long operations
    ) {
        private static RecipeCacheKey from(RecipeLookupContext context) {
            GenericStack moldStack = context.mold() == null || context.mold().isEmpty()
                    ? null
                    : GenericStack.fromItemStack(context.mold());
            return new RecipeCacheKey(
                    snapshotItems(context.inputs()),
                    snapshotFluids(context.fluidInputs()),
                    snapshotGenericStacks(context.keyInputs()),
                    moldStack == null ? null : moldStack.what(),
                    snapshotGenericStacks(context.expectedOutputs()),
                    context.operations()
            );
        }

        static RecipeCacheKey create(List<ItemStack> inputs, List<FluidStack> fluidInputs,
                                     List<GenericStack> keyInputs, @Nullable ItemStack mold,
                                     List<GenericStack> expectedOutputs, long operations) {
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

    private static Map<AEKey, Long> snapshotGenericStacks(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        if (stacks == null) return Map.of();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) continue;
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
