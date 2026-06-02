package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.content.recipe.adapters.SmeltingRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private final List<IRecipeAdapter<?>> adapters = new ArrayList<>();

    private final Map<RecipeCacheKey, AdvancedAlloyFurnaceRecipe> recipeCache = new HashMap<>();
    private final Map<RecipeCacheKey, Boolean> negativeCache = new HashMap<>();

    private static final int MAX_CACHE_SIZE = 1000;
    private static final int CACHE_CLEAN_THRESHOLD = 800;

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
        registerAdapter(new SmeltingRecipeAdapter());
    }

    public void registerAdapter(IRecipeAdapter<?> adapter) {
        adapters.add(adapter);
        adapters.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        indexBuilt = false;
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
        if (level == null) {
            return null;
        }

        boolean hasItems = !inputs.isEmpty();
        boolean hasFluids = fluidInputs != null && !fluidInputs.isEmpty();
        boolean hasMold = mold != null && !mold.isEmpty();

        if (!hasItems && !hasFluids && !hasMold) {
            return null;
        }

        if (!indexBuilt && !level.isClientSide()) {
            buildIndex(level);
        }

        RecipeCacheKey cacheKey = new RecipeCacheKey(inputs, fluidInputs, mold);

        AdvancedAlloyFurnaceRecipe cachedRecipe = recipeCache.get(cacheKey);
        if (cachedRecipe != null) {
            return cachedRecipe;
        }

        if (negativeCache.containsKey(cacheKey)) {
            return null;
        }

        AdvancedAlloyFurnaceRecipe recipe = findRecipeByScore(inputs, fluidInputs, mold);

        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        recipe = findAdaptedRecipeByScore(level, inputs, fluidInputs, mold);
        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        cacheNegativeResult(cacheKey);
        return null;
    }

    /**
     * 向后兼容的无流体查找方法
     */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        return findRecipe(level, inputs, List.of(), mold);
    }

    /**
     * 基于评分系统查找配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipeByScore(List<ItemStack> inputs,
                                                          List<FluidStack> fluidInputs,
                                                          @Nullable ItemStack mold) {
        List<AdvancedAlloyFurnaceRecipe> candidates = getCandidateRecipes(inputs, fluidInputs, mold);

        if (candidates.isEmpty()) {
            return null;
        }

        return selectBestByScore(candidates, inputs, fluidInputs, mold);
    }

    /**
     * 获取候选配方列表（基于索引快速筛选）
     */
    private List<AdvancedAlloyFurnaceRecipe> getCandidateRecipes(List<ItemStack> inputs,
                                                                  List<FluidStack> fluidInputs,
                                                                  @Nullable ItemStack mold) {
        Set<AdvancedAlloyFurnaceRecipe> candidateSet = new LinkedHashSet<>();
        boolean hasItems = !inputs.isEmpty();
        boolean hasFluids = fluidInputs != null && !fluidInputs.isEmpty();
        boolean hasMold = mold != null && !mold.isEmpty();

        Set<AdvancedAlloyFurnaceRecipe> itemFiltered = null;
        if (hasItems) {
            itemFiltered = new HashSet<>();
            for (ItemStack input : inputs) {
                if (!input.isEmpty()) {
                    List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
                    if (recipes != null) {
                        itemFiltered.addAll(recipes);
                    }
                }
            }
        }

        Set<AdvancedAlloyFurnaceRecipe> moldFiltered = null;
        if (hasMold) {
            moldFiltered = new HashSet<>();
            List<AdvancedAlloyFurnaceRecipe> moldRecipes = moldIndex.get(mold.getItem());
            if (moldRecipes != null) {
                moldFiltered.addAll(moldRecipes);
            }
        }

        if (hasItems && itemFiltered != null && !itemFiltered.isEmpty()) {
            candidateSet.addAll(itemFiltered);
        }

        if (hasMold && moldFiltered != null && !moldFiltered.isEmpty()) {
            if (!candidateSet.isEmpty()) {
                candidateSet.retainAll(moldFiltered);
            } else {
                candidateSet.addAll(moldFiltered);
            }
        }

        if (hasFluids && !hasFluidInputRecipes.isEmpty()) {
            if (!candidateSet.isEmpty()) {
                candidateSet.retainAll(hasFluidInputRecipes);
            } else {
                candidateSet.addAll(hasFluidInputRecipes);
            }
        }

        // 兜底：无候选时加入无模具配方，有模具时也加入匹配模具的配方
        if (candidateSet.isEmpty()) {
            if (hasMold && moldFiltered != null && !moldFiltered.isEmpty()) {
                candidateSet.addAll(moldFiltered);
            } else if (hasItems && itemFiltered != null && !itemFiltered.isEmpty()) {
                candidateSet.addAll(itemFiltered);
            } else if (hasFluids) {
                candidateSet.addAll(hasFluidInputRecipes);
            }
            candidateSet.addAll(noMoldRecipes);
        }

        return new ArrayList<>(candidateSet);
    }

    /**
     * 按评分从候选列表中选出最佳配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe selectBestByScore(List<AdvancedAlloyFurnaceRecipe> candidates,
                                                          List<ItemStack> inputs,
                                                          List<FluidStack> fluidInputs,
                                                          @Nullable ItemStack mold) {
        List<ScoredRecipe> scored = new ArrayList<>();

        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            int score = calculateMatchScore(recipe, inputs, fluidInputs, mold);
            if (score > 0) {
                scored.add(new ScoredRecipe(recipe, score));
            }
        }

        if (scored.isEmpty()) {
            return null;
        }

        scored.sort(Comparator.<ScoredRecipe>comparingInt(s -> s.score).reversed()
                .thenComparing(s -> isConvertedRecipe(s.recipe))
                .thenComparing(s -> s.recipe.inputs().size(), Comparator.reverseOrder())
                .thenComparing(s -> s.recipe.inputFluids().size(), Comparator.reverseOrder())
                .thenComparing(s -> !s.recipe.mold().isEmpty(), Comparator.reverseOrder()));

        return scored.get(0).recipe;
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
                                     @Nullable ItemStack mold) {
        boolean matchesItems = matchesRecipe(recipe, inputs);
        boolean matchesFluids = matchesFluids(recipe, fluidInputs);
        boolean matchesMold = matchesMold(recipe, mold);

        boolean recipeHasItems = !recipe.inputs().isEmpty();
        boolean recipeHasFluids = !recipe.inputFluids().isEmpty();
        boolean hasMold = mold != null && !mold.isEmpty();

        // 物品：配方要求物品则必须匹配
        if (recipeHasItems && !matchesItems) return 0;
        // 流体：配方要求流体则必须匹配
        if (recipeHasFluids && !matchesFluids) return 0;
        // 模具：用户提供了模具则必须匹配
        if (hasMold && !matchesMold) return 0;

        if (recipeHasItems && recipeHasFluids) {
            return matchesMold ? SCORE_ITEM_FLUID_MOLD : SCORE_ITEM_FLUID;
        }
        if (recipeHasItems) {
            return matchesMold ? SCORE_ITEM_MOLD : SCORE_ITEM;
        }
        if (recipeHasFluids) {
            return matchesMold ? SCORE_FLUID_MOLD : SCORE_FLUID;
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

    /**
     * 通过适配器按评分查找
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> AdvancedAlloyFurnaceRecipe findAdaptedRecipeByScore(
            Level level, List<ItemStack> inputs, List<FluidStack> fluidInputs, @Nullable ItemStack mold) {
        List<ScoredRecipe> scoredCandidates = new ArrayList<>();

        for (IRecipeAdapter<?> adapter : adapters) {
            RecipeHolder<T> holder;

            if (mold != null && !mold.isEmpty()) {
                holder = ((IRecipeAdapter<T>) adapter).findMatchingRecipe(level, inputs, mold);
            } else {
                holder = ((IRecipeAdapter<T>) adapter).findMatchingRecipe(level, inputs);
            }

            if (holder != null) {
                List<AdvancedAlloyFurnaceRecipe> convertedRecipes = ((IRecipeAdapter<T>) adapter).convertAll(holder, level);
                for (AdvancedAlloyFurnaceRecipe recipe : convertedRecipes) {
                    int score = calculateMatchScore(recipe, inputs, fluidInputs, mold);
                    if (score > 0) {
                        scoredCandidates.add(new ScoredRecipe(recipe, score, adapter.getPriority()));
                    }
                }
            }
        }

        if (scoredCandidates.isEmpty()) {
            return null;
        }

        scoredCandidates.sort(Comparator.<ScoredRecipe>comparingInt(s -> s.score).reversed()
                .thenComparingInt(s -> s.adapterPriority).reversed()
                .thenComparing(s -> isConvertedRecipe(s.recipe))
                .thenComparing(s -> s.recipe.inputs().size(), Comparator.reverseOrder())
                .thenComparing(s -> s.recipe.inputFluids().size(), Comparator.reverseOrder()));

        return scoredCandidates.get(0).recipe;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends Recipe<?>> AdvancedAlloyFurnaceRecipe findAdaptedRecipeDirectly(Level level, List<ItemStack> inputs) {
        return findRecipe(level, inputs, List.of(), null);
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
        if (recipeCache.size() >= CACHE_CLEAN_THRESHOLD) {
            cleanCache();
        }
        recipeCache.put(key, recipe);
    }

    private void cacheNegativeResult(RecipeCacheKey key) {
        if (negativeCache.size() >= CACHE_CLEAN_THRESHOLD) {
            cleanCache();
        }
        negativeCache.put(key, Boolean.TRUE);
    }

    private void cleanCache() {
        int keepSize = MAX_CACHE_SIZE / 2;

        if (recipeCache.size() > keepSize) {
            List<RecipeCacheKey> keys = new ArrayList<>(recipeCache.keySet());
            keys.subList(0, keys.size() - keepSize).forEach(recipeCache::remove);
        }

        if (negativeCache.size() > keepSize) {
            List<RecipeCacheKey> keys = new ArrayList<>(negativeCache.keySet());
            keys.subList(0, keys.size() - keepSize).forEach(negativeCache::remove);
        }
    }

    public void clearCache() {
        recipeCache.clear();
        negativeCache.clear();
        indexBuilt = false;
    }

    public int getCacheSize() {
        return recipeCache.size() + negativeCache.size();
    }

    public String getIndexStats() {
        return String.format(
                "Indexed Recipes: %d, Input Items: %d, Mold Types: %d, No-Mold Recipes: %d, Fluid Recipes: %d",
                indexedRecipes.size(),
                inputItemIndex.size(),
                moldIndex.size(),
                noMoldRecipes.size(),
                hasFluidInputRecipes.size()
        );
    }

    // ========== 内部类 ==========

    private record RecipeCacheKey(List<ItemStack> inputs, @Nullable List<FluidStack> fluidInputs,
                                  @Nullable ItemStack mold) {

        RecipeCacheKey(List<ItemStack> inputs, @Nullable ItemStack mold) {
            this(inputs, null, mold);
        }

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

            return true;
        }

        @Override
        public int hashCode() {
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

            return result;
        }
    }

    private record ScoredRecipe(AdvancedAlloyFurnaceRecipe recipe, int score, int adapterPriority) {
        ScoredRecipe(AdvancedAlloyFurnaceRecipe recipe, int score) {
            this(recipe, score, 0);
        }
    }
}
