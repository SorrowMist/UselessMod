package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.content.recipe.adapters.SmeltingRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
 * 提供高效的配方查找和缓存机制
 */
public class AlloyFurnaceRecipeManager {
    private static AlloyFurnaceRecipeManager INSTANCE;

    // 配方适配器列表（按优先级排序）
    private final List<IRecipeAdapter<?>> adapters = new ArrayList<>();

    // 配方缓存
    private final Map<RecipeCacheKey, AdvancedAlloyFurnaceRecipe> recipeCache = new HashMap<>();
    private final Map<RecipeCacheKey, Boolean> negativeCache = new HashMap<>();

    // 缓存最大大小
    private static final int MAX_CACHE_SIZE = 1000;

    // 缓存清理阈值
    private static final int CACHE_CLEAN_THRESHOLD = 800;

    // ========== 预构建索引 ==========

    // 输入物品 -> 配方列表 索引（用于快速查找）
    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> inputItemIndex = new ConcurrentHashMap<>();

    // 模具物品 -> 配方列表 索引（用于按模具类型快速查找）
    private final Map<Item, List<AdvancedAlloyFurnaceRecipe>> moldIndex = new ConcurrentHashMap<>();

    // 空模具（无模具要求）的配方列表（使用 CopyOnWriteArrayList 保证线程安全）
    private final List<AdvancedAlloyFurnaceRecipe> noMoldRecipes = new CopyOnWriteArrayList<>();

    // 所有已索引的配方（去重，使用 ConcurrentHashMap 的 keySet 保证线程安全）
    private final Set<AdvancedAlloyFurnaceRecipe> indexedRecipes = ConcurrentHashMap.newKeySet();

    // 索引是否已构建
    private boolean indexBuilt = false;

    /**
     * 获取单例实例
     */
    public static AlloyFurnaceRecipeManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AlloyFurnaceRecipeManager();
        }
        return INSTANCE;
    }

    /**
     * 私有构造函数，初始化默认适配器
     */
    private AlloyFurnaceRecipeManager() {
        // 注册默认适配器
        registerAdapter(new SmeltingRecipeAdapter());
    }

    /**
     * 注册配方适配器
     *
     * @param adapter 配方适配器
     */
    public void registerAdapter(IRecipeAdapter<?> adapter) {
        adapters.add(adapter);
        // 按优先级排序（高优先级在前）
        adapters.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        // 索引需要重建
        indexBuilt = false;
    }

    /**
     * 构建配方索引
     * <p>
     * 在配方数据加载完成后调用，预构建所有索引以加速查找
     * 只能在服务器端调用，避免并发修改异常
     *
     * @param level 世界
     */
    public void buildIndex(Level level) {
        if (level == null || level.isClientSide()) return;

        // 清空旧索引
        clearIndex();

        // 索引自定义配方
        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            indexRecipe(holder.value());
        }

        indexBuilt = true;
    }

    /**
     * 清空所有索引
     */
    private void clearIndex() {
        inputItemIndex.clear();
        moldIndex.clear();
        noMoldRecipes.clear();
        indexedRecipes.clear();
        indexBuilt = false;
    }

    /**
     * 索引单个配方
     */
    private void indexRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null || indexedRecipes.contains(recipe)) {
            return;
        }
        indexedRecipes.add(recipe);

        // 1. 索引输入物品
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

        // 2. 索引模具
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
    }

    /**
     * 查找匹配的配方
     * <p>
     * 查找顺序：
     * 1. 缓存
     * 2. 基于索引的快速查找
     * 3. 回退到遍历查找（如果索引未构建）
     *
     * @param level  世界
     * @param inputs 输入物品列表
     * @param mold   当前模具（可为空）
     * @return 匹配的高级熔炉配方，如果没有则返回 null
     */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        // 确保索引已构建（只在服务器端构建，客户端等待服务器同步数据）
        if (!indexBuilt && !level.isClientSide()) {
            buildIndex(level);
        }

        // 生成缓存键
        RecipeCacheKey cacheKey = new RecipeCacheKey(inputs, mold);

        // 检查缓存
        AdvancedAlloyFurnaceRecipe cachedRecipe = recipeCache.get(cacheKey);
        if (cachedRecipe != null) {
            return cachedRecipe;
        }

        // 检查负缓存
        if (negativeCache.containsKey(cacheKey)) {
            return null;
        }

        // 使用索引查找
        AdvancedAlloyFurnaceRecipe recipe = findRecipeByIndex(inputs, mold);

        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        // 3. 通过适配器查找转换配方（回退方案）
        recipe = findAdaptedRecipe(level, inputs, mold);
        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        // 没有找到配方，加入负缓存
        cacheNegativeResult(cacheKey);
        return null;
    }

    /**
     * 基于索引查找配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findRecipeByIndex(List<ItemStack> inputs, @Nullable ItemStack mold) {
        // 获取候选配方列表
        List<AdvancedAlloyFurnaceRecipe> candidates = getCandidateRecipes(inputs, mold);

        // 按优先级排序候选配方：
        // 1. 原生自定义配方（非转换配方）优先于转换配方
        // 2. 输入要求更多的配方优先（更具体的配方优先）
        // 3. 有模具要求的配方优先
        candidates.sort((a, b) -> {
            // 首先，原生配方优先于转换配方
            boolean aIsConverted = isConvertedRecipe(a);
            boolean bIsConverted = isConvertedRecipe(b);
            if (aIsConverted != bIsConverted) {
                return aIsConverted ? 1 : -1; // 原生配方在前
            }

            // 其次，输入要求更多的配方优先（更具体）
            int aInputs = a.inputs().size();
            int bInputs = b.inputs().size();
            if (aInputs != bInputs) {
                return Integer.compare(bInputs, aInputs); // 输入多的在前
            }

            // 最后，有模具要求的优先
            boolean aHasMold = !a.mold().isEmpty();
            boolean bHasMold = !b.mold().isEmpty();
            if (aHasMold != bHasMold) {
                return aHasMold ? -1 : 1; // 有模具的在前
            }

            return 0;
        });

        // 精确匹配
        for (AdvancedAlloyFurnaceRecipe recipe : candidates) {
            if (matchesRecipe(recipe, inputs) && matchesMold(recipe, mold)) {
                return recipe;
            }
        }

        return null;
    }

    /**
     * 检查配方是否是适配器转换的配方
     */
    private boolean isConvertedRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        return recipe.id().getPath().endsWith("_converted");
    }

    /**
     * 通过适配器查找转换配方
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> AdvancedAlloyFurnaceRecipe findAdaptedRecipe(Level level, List<ItemStack> inputs, @Nullable ItemStack mold) {
        for (IRecipeAdapter<?> adapter : adapters) {
            RecipeHolder<T> holder = ((IRecipeAdapter<T>) adapter).findMatchingRecipe(level, inputs);
            if (holder != null) {
                // 使用 convertAll 获取所有转换后的配方（包括基础版和批量版）
                List<AdvancedAlloyFurnaceRecipe> convertedRecipes = ((IRecipeAdapter<T>) adapter).convertAll(holder, level);

                // 查找第一个匹配的配方
                for (AdvancedAlloyFurnaceRecipe recipe : convertedRecipes) {
                    if (matchesRecipe(recipe, inputs) && matchesMold(recipe, mold)) {
                        return recipe;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取候选配方列表（基于索引快速筛选）
     * <p>
     * 返回的列表按优先级排序：
     * 1. 有模具要求的配方（如果提供了模具）
     * 2. 无模具要求的配方（仅当没有提供模具时，或没有匹配模具的配方时）
     */
    private List<AdvancedAlloyFurnaceRecipe> getCandidateRecipes(List<ItemStack> inputs, @Nullable ItemStack mold) {
        // 使用 LinkedHashSet 保持插入顺序，同时去重
        Set<AdvancedAlloyFurnaceRecipe> moldRequiredRecipes = new LinkedHashSet<>();
        Set<AdvancedAlloyFurnaceRecipe> noMoldRequiredRecipes = new LinkedHashSet<>();

        // 1. 基于模具筛选
        if (mold == null || mold.isEmpty()) {
            // 无模具时，只考虑无模具要求的配方
            noMoldRequiredRecipes.addAll(noMoldRecipes);
        } else {
            // 有模具时，优先考虑匹配该模具的配方
            List<AdvancedAlloyFurnaceRecipe> moldRecipes = moldIndex.get(mold.getItem());
            if (moldRecipes != null) {
                moldRequiredRecipes.addAll(moldRecipes);
            }
            // 只有当没有匹配该模具的配方时，才考虑无模具要求的配方
            if (moldRequiredRecipes.isEmpty()) {
                noMoldRequiredRecipes.addAll(noMoldRecipes);
            }
        }

        // 2. 基于输入物品进一步筛选
        Set<AdvancedAlloyFurnaceRecipe> inputFiltered = new HashSet<>();
        for (ItemStack input : inputs) {
            if (!input.isEmpty()) {
                List<AdvancedAlloyFurnaceRecipe> recipes = inputItemIndex.get(input.getItem());
                if (recipes != null) {
                    inputFiltered.addAll(recipes);
                }
            }
        }

        // 如果基于输入找到了配方，与模具筛选结果取交集
        if (!inputFiltered.isEmpty()) {
            moldRequiredRecipes.retainAll(inputFiltered);
            noMoldRequiredRecipes.retainAll(inputFiltered);
        }

        // 3. 合并结果：有模具要求的配方优先，无模具要求的配方在后
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        result.addAll(moldRequiredRecipes);
        // 只有当没有匹配模具的配方时，才添加无模具要求的配方
        if (moldRequiredRecipes.isEmpty()) {
            result.addAll(noMoldRequiredRecipes);
        }

        return result;
    }

    /**
     * 检查模具是否匹配配方要求
     */
    private boolean matchesMold(AdvancedAlloyFurnaceRecipe recipe, @Nullable ItemStack mold) {
        Ingredient requiredMold = recipe.mold();

        // 配方不需要模具
        if (requiredMold == null || requiredMold.isEmpty()) {
            return true; // 任何模具都可以（包括无模具）
        }

        // 配方需要模具，但当前没有模具
        if (mold == null || mold.isEmpty()) {
            return false;
        }

        // 检查模具是否匹配
        return requiredMold.test(mold);
    }

    /**
     * 检查输入物品是否匹配配方
     */
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

    /**
     * 缓存配方结果
     */
    private void cacheRecipe(RecipeCacheKey key, AdvancedAlloyFurnaceRecipe recipe) {
        if (recipeCache.size() >= CACHE_CLEAN_THRESHOLD) {
            cleanCache();
        }
        recipeCache.put(key, recipe);
    }

    /**
     * 缓存负结果（没有配方）
     */
    private void cacheNegativeResult(RecipeCacheKey key) {
        if (negativeCache.size() >= CACHE_CLEAN_THRESHOLD) {
            cleanCache();
        }
        negativeCache.put(key, Boolean.TRUE);
    }

    /**
     * 清理缓存
     */
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

    /**
     * 清除缓存并标记索引需要重建
     * 在配方数据重载时调用，标记索引需要重建
     * 注意：此方法应在服务器端调用
     */
    public void clearCache() {
        recipeCache.clear();
        negativeCache.clear();
        // 标记索引需要重建，下次服务器端查找时会自动重建
        indexBuilt = false;
    }

    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return recipeCache.size() + negativeCache.size();
    }

    /**
     * 获取索引统计信息（用于调试）
     */
    public String getIndexStats() {
        return String.format(
                "Indexed Recipes: %d, Input Items: %d, Mold Types: %d, No-Mold Recipes: %d",
                indexedRecipes.size(),
                inputItemIndex.size(),
                moldIndex.size(),
                noMoldRecipes.size()
        );
    }

    // ========== 内部类 ==========

    /**
     * 配方缓存键
     */
    private record RecipeCacheKey(List<ItemStack> inputs, @Nullable ItemStack mold) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecipeCacheKey that = (RecipeCacheKey) o;

            // 比较模具
            if (!Objects.equals(mold, that.mold)) {
                if (mold == null || that.mold == null) return false;
                if (!ItemStack.isSameItemSameComponents(mold, that.mold)) return false;
            }

            // 比较输入物品
            if (inputs.size() != that.inputs.size()) return false;

            for (int i = 0; i < inputs.size(); i++) {
                ItemStack a = inputs.get(i);
                ItemStack b = that.inputs.get(i);

                if (!ItemStack.isSameItemSameComponents(a, b)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = 1;

            // 模具哈希
            if (mold != null && !mold.isEmpty()) {
                result = 31 * result + mold.getItem().hashCode();
                var components = mold.getComponentsPatch();
                if (!components.isEmpty()) {
                    result = 31 * result + components.hashCode();
                }
            }

            // 输入物品哈希
            for (ItemStack stack : inputs) {
                result = 31 * result + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
                var components = stack.getComponentsPatch();
                if (!components.isEmpty()) {
                    result = 31 * result + components.hashCode();
                }
            }
            return result;
        }
    }
}
