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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    // ========== 得分常量 ==========
    private static final int SCORE_ITEM_FLUID_MOLD = 6;
    private static final int SCORE_ITEM_MOLD = 5;
    private static final int SCORE_FLUID_MOLD = 4;
    private static final int SCORE_ITEM_FLUID = 3;
    private static final int SCORE_ITEM = 2;
    private static final int SCORE_FLUID = 1;

    // ========== 预构建索引 ==========

    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> inputItemIndex = new ConcurrentHashMap<>();
    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> moldIndex = new ConcurrentHashMap<>();
    private final List<AdvancedAlloyFurnaceRecipe> noMoldRecipes = new CopyOnWriteArrayList<>();
    private final List<AdvancedAlloyFurnaceRecipe> hasFluidInputRecipes = new CopyOnWriteArrayList<>();
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
        noMoldRecipes.clear();
        hasFluidInputRecipes.clear();
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
        if (mold == null || mold.isEmpty()) {
            noMoldRecipes.add(recipe);
        } else {
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
    }

    /**
     * 按优先级统一查找配方（物品+流体+模具）
     * <p>
     * 匹配优先级：
     * 1. 物品+流体+模具 全部匹配
     * 2. 物品+模具 匹配
     * 3. 流体+模具 匹配
     * 4. 物品+流体 匹配
     * 5. 仅物品匹配
     * 6. 仅流体匹配
     */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs,
                                                  List<FluidStack> fluidInputs, @Nullable ItemStack mold) {
        return findRecipe(level, inputs, fluidInputs, List.of(), mold);
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs,
                                                  List<FluidStack> fluidInputs, List<GenericStack> keyInputs, @Nullable ItemStack mold) {
        if (level == null) {
            return null;
        }

        boolean hasItems = !inputs.isEmpty();
        boolean hasFluids = fluidInputs != null && !fluidInputs.isEmpty();
        boolean hasKeys = keyInputs != null && !keyInputs.isEmpty();
        boolean hasMold = mold != null && !mold.isEmpty();

        if (!hasItems && !hasFluids && !hasKeys && !hasMold) {
            return null;
        }

        if (!indexBuilt && !level.isClientSide()) {
            buildIndex(level);
        }

        RecipeCacheKey cacheKey = new RecipeCacheKey(inputs, fluidInputs, keyInputs, mold);

        AdvancedAlloyFurnaceRecipe cachedRecipe = recipeCache.get(cacheKey);
        if (cachedRecipe != null) {
            return cachedRecipe;
        }

        if (negativeCache.containsKey(cacheKey)) {
            return null;
        }

        AdvancedAlloyFurnaceRecipe recipe = findRecipeByScore(inputs, fluidInputs, keyInputs, mold);

        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        // 有模具时，通过适配器按模具预筛选查找外部配方
        if (hasMold) {
            recipe = findAdaptedRecipeByScore(level, inputs, fluidInputs, keyInputs, mold);
            if (recipe != null) {
                cacheRecipe(cacheKey, recipe);
                return recipe;
            }
        }

        cacheNegativeResult(cacheKey);
        return null;
    }

    /**
     * 基于评分系统查找配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipeByScore(List<ItemStack> inputs,
                                                          List<FluidStack> fluidInputs,
                                                          List<GenericStack> keyInputs,
                                                          @Nullable ItemStack mold) {
        List<AdvancedAlloyFurnaceRecipe> candidates = getCandidateRecipes(inputs, fluidInputs, mold);

        if (candidates.isEmpty()) {
            return null;
        }

        return selectBestByScore(candidates, inputs, fluidInputs, keyInputs, mold);
    }

    /**
     * 获取候选配方列表（基于索引快速筛选）。
     * 模具是最具区分度的过滤条件，优先使用模具索引缩小候选集。
     */
    private List<AdvancedAlloyFurnaceRecipe> getCandidateRecipes(List<ItemStack> inputs,
                                                                  List<FluidStack> fluidInputs,
                                                                  @Nullable ItemStack mold) {
        Set<AdvancedAlloyFurnaceRecipe> candidateSet = new HashSet<>();
        boolean hasItems = !inputs.isEmpty();
        boolean hasFluids = fluidInputs != null && !fluidInputs.isEmpty();
        boolean hasMold = mold != null && !mold.isEmpty();

        // 1. 模具优先：模具是最具区分度的过滤条件
        Set<AdvancedAlloyFurnaceRecipe> moldFiltered = null;
        if (hasMold) {
            List<AdvancedAlloyFurnaceRecipe> moldRecipes = moldIndex.get(mold.getItem());
            if (moldRecipes != null && !moldRecipes.isEmpty()) {
                moldFiltered = new HashSet<>(moldRecipes);
                candidateSet.addAll(moldFiltered);
            }
        }

        // 2. 物品过滤：与模具结果取交集
        if (hasItems) {
            Set<AdvancedAlloyFurnaceRecipe> itemFiltered = new HashSet<>();
            for (ItemStack input : inputs) {
                if (!input.isEmpty()) {
                    List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
                    if (recipes != null) {
                        itemFiltered.addAll(recipes);
                    }
                }
            }
            if (!itemFiltered.isEmpty()) {
                if (!candidateSet.isEmpty()) {
                    candidateSet.retainAll(itemFiltered); // 模具 ∩ 物品
                } else {
                    candidateSet.addAll(itemFiltered);
                }
            }
        }

        // 如果没有找到模具候选但有物品，说明模具筛选太窄，回退到仅物品
        if (candidateSet.isEmpty() && hasItems && moldFiltered != null) {
            // 模具+物品交集为空，仅使用物品过滤
            for (ItemStack input : inputs) {
                if (!input.isEmpty()) {
                    List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
                    if (recipes != null) candidateSet.addAll(recipes);
                }
            }
        }

        // 3. 流体过滤
        if (hasFluids && !hasFluidInputRecipes.isEmpty()) {
            if (!candidateSet.isEmpty()) {
                candidateSet.retainAll(hasFluidInputRecipes);
            } else {
                candidateSet.addAll(hasFluidInputRecipes);
            }
        }

        // 4. 兜底：无候选时加入无模具配方
        if (candidateSet.isEmpty()) {
            if (hasMold && moldFiltered != null && !moldFiltered.isEmpty()) {
                candidateSet.addAll(moldFiltered);
            }
            candidateSet.addAll(noMoldRecipes);
        }

        return new ArrayList<>(candidateSet);
    }

    /**
     * 按评分从候选列表中选出最佳配方。
     * 使用线性扫描（O(n)）替代收集+排序（O(n log n)），
     * 跟踪当前最佳，避免 ArrayList 分配和排序开销。
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe selectBestByScore(List<AdvancedAlloyFurnaceRecipe> candidates,
                                                          List<ItemStack> inputs,
                                                          List<FluidStack> fluidInputs,
                                                          List<GenericStack> keyInputs,
                                                          @Nullable ItemStack mold) {
        ScoredRecipe best = null;

        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            int score = calculateMatchScore(recipe, inputs, fluidInputs, keyInputs, mold, false);
            if (score <= 0) continue;

            // 满分原生配方：没有其他配方能超越，立即返回
            if (score == SCORE_ITEM_FLUID_MOLD && !isConvertedRecipe(recipe)) {
                return recipe;
            }

            ScoredRecipe current = new ScoredRecipe(recipe, score);
            if (best == null || isBetterThan(current, best)) {
                best = current;
            }
        }

        return best != null ? best.recipe : null;
    }

    /**
     * 比较两个评分配方，a 是否优于 b。
     * 优先比较得分，得分相同时按：原生>转换、更多输入>更少、需要模具>不需要。
     */
    private boolean isBetterThan(ScoredRecipe a, ScoredRecipe b) {
        if (a.score != b.score) return a.score > b.score;
        // 原生配方优先于转换配方
        if (isConvertedRecipe(a.recipe) != isConvertedRecipe(b.recipe))
            return !isConvertedRecipe(a.recipe);
        // 更多输入类型优先
        if (a.recipe.inputs().size() != b.recipe.inputs().size())
            return a.recipe.inputs().size() > b.recipe.inputs().size();
        // 更多流体输入优先
        if (a.recipe.inputFluids().size() != b.recipe.inputFluids().size())
            return a.recipe.inputFluids().size() > b.recipe.inputFluids().size();
        // 需要模具的配方优先
        return !a.recipe.mold().isEmpty() && b.recipe.mold().isEmpty();
    }

    private boolean isConvertedRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return recipe.id().getPath().endsWith("_converted");
    }

    /**
     * 计算配方匹配得分
     * <p>
     * 物品、流体、模具均为硬性要求：只要用户提供了某类输入，
     * 配方就必须完全匹配该类输入，否则直接排除。
     */
    private int calculateMatchScore(AdvancedAlloyFurnaceRecipe recipe,
                                     List<ItemStack> inputs,
                                     List<FluidStack> fluidInputs,
                                     List<GenericStack> keyInputs,
                                     @Nullable ItemStack mold) {
        return calculateMatchScore(recipe, inputs, fluidInputs, keyInputs, mold, false);
    }

    private int calculateMatchScore(AdvancedAlloyFurnaceRecipe recipe,
                                     List<ItemStack> inputs,
                                     List<FluidStack> fluidInputs,
                                     List<GenericStack> keyInputs,
                                     @Nullable ItemStack mold,
                                     boolean skipKeyCheck) {
        boolean hasMold = mold != null && !mold.isEmpty();

        // 模具检查最便宜，提前失败。同时缓存结果避免后续评分时重复调用
        boolean moldMatches = !hasMold || matchesMold(recipe, mold);
        if (!moldMatches) return 0;

        boolean recipeHasItems = !recipe.inputs().isEmpty();
        boolean recipeHasFluids = !recipe.inputFluids().isEmpty();
        boolean recipeHasKeys = !recipe.keyInputs().isEmpty() && !skipKeyCheck;

        // 惰性计算：仅在配方要求该类输入时才执行匹配检查
        if (recipeHasItems && !matchesRecipe(recipe, inputs)) return 0;
        if (recipeHasFluids && !matchesFluids(recipe, fluidInputs)) return 0;
        if (recipeHasKeys && !matchesKeys(recipe, keyInputs)) return 0;

        if (recipeHasKeys) {
            return moldMatches ? SCORE_ITEM_FLUID_MOLD : SCORE_ITEM_FLUID;
        }
        if (recipeHasItems && recipeHasFluids) {
            return moldMatches ? SCORE_ITEM_FLUID_MOLD : SCORE_ITEM_FLUID;
        }
        if (recipeHasItems) {
            return moldMatches ? SCORE_ITEM_MOLD : SCORE_ITEM;
        }
        if (recipeHasFluids) {
            return moldMatches ? SCORE_FLUID_MOLD : SCORE_FLUID;
        }
        return 0;
    }

    /**
     * 检查流体输入是否匹配配方
     */
    private boolean matchesFluids(AdvancedAlloyFurnaceRecipe recipe, List<FluidStack> fluidInputs) {
        if (fluidInputs == null || fluidInputs.isEmpty()) {
            return recipe.inputFluids().isEmpty();
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long foundAmount = 0;
            for (FluidStack input : fluidInputs) {
                if (FluidStack.isSameFluidSameComponents(input, requiredFluid)) {
                    foundAmount += input.getAmount();
                }
            }
            if (foundAmount < requiredFluid.getAmount()) return false;
        }
        return true;
    }

    private boolean matchesKeys(AdvancedAlloyFurnaceRecipe recipe, List<GenericStack> keyInputs) {
        return AdapterUtils.matchesKeyRequirements(AdapterUtils.mergeKeys(keyInputs), recipe.keyInputs());
    }

    /**
     * 通过适配器按评分查找
     * <p>
     * 查找策略：
     * 1. 优先通过 moldAdapterMap 精确查找（O(1)），适配器的 getMoldItem() 返回了具体物品
     * 2. 然后遍历 fallbackAdapters，通过 matchesMold() 动态判断（如 SeedEssenceRecipeAdapter 检查 instanceof）
     */
    @Nullable
    private <T extends Recipe<?>> AdvancedAlloyFurnaceRecipe findAdaptedRecipeByScore(
            Level level, List<ItemStack> inputs, List<FluidStack> fluidInputs, List<GenericStack> keyInputs, @Nullable ItemStack mold) {
        List<ScoredRecipe> scoredCandidates = new ArrayList<>();

        // 在 Manager 层统一合并输入
        Map<Ingredient, Long> mergedInputs = AdapterUtils.mergeInputs(inputs);
        Map<FluidStack, Long> mergedFluids = AdapterUtils.mergeFluids(fluidInputs);
        Map<AEKey, Long> mergedKeys = AdapterUtils.mergeKeys(keyInputs);

//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("mergeInputs: {} item types from {} stacks: {}", mergedInputs.size(), inputs.size(), mergedInputs);
//            LOGGER.debug("mergeFluids: {} fluid types from {} stacks: {}", mergedFluids.size(), fluidInputs != null ? fluidInputs.size() : 0, mergedFluids);
//        }

        // 1. 按模具物品精确查找 adapter
        if (mold != null && !mold.isEmpty()) {
            IRecipeAdapter<?> exactAdapter = moldAdapterMap.get(mold.getItem());
            if (exactAdapter != null) {
                tryAdapter(exactAdapter, level, mergedInputs, mergedFluids, mergedKeys, mold, scoredCandidates, inputs, fluidInputs, keyInputs);
            }
        }

        // 2. 遍历无固定模具的 adapter，通过 matchesMold() 动态判断
        for (IRecipeAdapter<?> adapter : fallbackAdapters) {
            if (!adapter.matchesMold(mold)) {
                continue;
            }
            tryAdapter(adapter, level, mergedInputs, mergedFluids, mergedKeys, mold, scoredCandidates, inputs, fluidInputs, keyInputs);
        }

        if (scoredCandidates.isEmpty()) {
            return null;
        }

        scoredCandidates.sort(Comparator.<ScoredRecipe>comparingInt(s -> s.score).reversed()
                .thenComparing(s -> isConvertedRecipe(s.recipe))
                .thenComparing(s -> s.recipe.inputs().size(), Comparator.reverseOrder())
                .thenComparing(s -> s.recipe.inputFluids().size(), Comparator.reverseOrder()));

        return scoredCandidates.getFirst().recipe;
    }

    /**
     * 尝试用给定的 adapter 查找配方并评分
     */
    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> void tryAdapter(
            IRecipeAdapter<?> adapter, Level level,
            Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ScoredRecipe> scoredCandidates,
            List<ItemStack> inputs, List<FluidStack> fluidInputs, List<GenericStack> keyInputs) {
        RecipeHolder<T> holder;
        holder = ((IRecipeAdapter<T>) adapter).findMatchingRecipe(level, mergedInputs, mergedFluids, mergedKeys, mold);

        if (holder != null) {
            List<AdvancedAlloyFurnaceRecipe> convertedRecipes = ((IRecipeAdapter<T>) adapter).convertAll(holder, level);
            for (AdvancedAlloyFurnaceRecipe recipe : convertedRecipes) {
                int score = calculateMatchScore(recipe, inputs, fluidInputs, keyInputs, mold, true);
                if (score > 0) {
                    scoredCandidates.add(new ScoredRecipe(recipe, score));
                }
            }
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

    private boolean matchesRecipe(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputs) {
        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (ItemStack stack : inputs) {
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) return false;
        }
        return true;
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

    private static final class RecipeCacheKey {
        private final List<ItemStack> inputs;
        @Nullable private final List<FluidStack> fluidInputs;
        @Nullable private final List<GenericStack> keyInputs;
        @Nullable private final ItemStack mold;
        private final int hash;

        RecipeCacheKey(List<ItemStack> inputs, @Nullable List<FluidStack> fluidInputs,
                       @Nullable List<GenericStack> keyInputs, @Nullable ItemStack mold) {
            this.inputs = inputs;
            this.fluidInputs = fluidInputs;
            this.keyInputs = keyInputs;
            this.mold = mold;
            this.hash = computeHash();
        }

        List<ItemStack> inputs() { return inputs; }
        @Nullable List<FluidStack> fluidInputs() { return fluidInputs; }
        @Nullable List<GenericStack> keyInputs() { return keyInputs; }
        @Nullable ItemStack mold() { return mold; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecipeCacheKey that = (RecipeCacheKey) o;

            if (!Objects.equals(mold, that.mold)) {
                if (mold == null || that.mold == null) return false;
                if (!ItemStack.isSameItemSameComponents(mold, that.mold)) return false;
            }

            if (inputs.size() != that.inputs.size()) return false;
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack a = inputs.get(i);
                ItemStack b = that.inputs.get(i);
                if (!ItemStack.isSameItemSameComponents(a, b)) {
                    return false;
                }
            }

            List<FluidStack> f1 = fluidInputs != null ? fluidInputs : List.of();
            List<FluidStack> f2 = that.fluidInputs != null ? that.fluidInputs : List.of();
            if (f1.size() != f2.size()) return false;
            for (int i = 0; i < f1.size(); i++) {
                if (!FluidStack.isSameFluidSameComponents(f1.get(i), f2.get(i))) {
                    return false;
                }
            }

            List<GenericStack> k1 = keyInputs != null ? keyInputs : List.of();
            List<GenericStack> k2 = that.keyInputs != null ? that.keyInputs : List.of();
            if (k1.size() != k2.size()) return false;
            for (int i = 0; i < k1.size(); i++) {
                GenericStack a = k1.get(i);
                GenericStack b = k2.get(i);
                if (!Objects.equals(a.what(), b.what()) || a.amount() != b.amount()) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private int computeHash() {
            int result = 1;

            if (mold != null && !mold.isEmpty()) {
                result = 31 * result + mold.getItem().hashCode();
                var components = mold.getComponentsPatch();
                if (!components.isEmpty()) {
                    result = 31 * result + components.hashCode();
                }
            }

            for (ItemStack stack : inputs) {
                result = 31 * result + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
                var components = stack.getComponentsPatch();
                if (!components.isEmpty()) {
                    result = 31 * result + components.hashCode();
                }
            }

            List<FluidStack> f = fluidInputs != null ? fluidInputs : List.of();
            for (FluidStack fs : f) {
                result = 31 * result + fs.getFluid().hashCode();
                result = 31 * result + fs.getComponentsPatch().hashCode();
            }

            List<GenericStack> k = keyInputs != null ? keyInputs : List.of();
            for (GenericStack keyInput : k) {
                result = 31 * result + keyInput.what().hashCode();
                result = 31 * result + Long.hashCode(keyInput.amount());
            }

            return result;
        }
    }

    private record ScoredRecipe(AdvancedAlloyFurnaceRecipe recipe, int score) {}
}
