package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.content.recipe.adapters.SmeltingRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    // 单例实例
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
    }

    /**
     * 查找匹配的配方
     * <p>
     * 查找顺序：
     * 1. 缓存
     * 2. 自定义配方
     * 3. 通过适配器转换的配方
     *
     * @param level  世界
     * @param inputs 输入物品列表
     * @return 匹配的高级熔炉配方，如果没有则返回 null
     */
    @Nullable
    public AdvancedAlloyFurnaceRecipe findRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        // 生成缓存键
        RecipeCacheKey cacheKey = new RecipeCacheKey(inputs);

        // 检查缓存
        AdvancedAlloyFurnaceRecipe cachedRecipe = recipeCache.get(cacheKey);
        if (cachedRecipe != null) {
            return cachedRecipe;
        }

        // 检查负缓存（已知没有配方）
        if (negativeCache.containsKey(cacheKey)) {
            return null;
        }

        // 1. 首先查找自定义配方
        AdvancedAlloyFurnaceRecipe recipe = findCustomRecipe(level, inputs);
        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        // 2. 通过适配器查找转换配方
        recipe = findAdaptedRecipe(level, inputs);
        if (recipe != null) {
            cacheRecipe(cacheKey, recipe);
            return recipe;
        }

        // 没有找到配方，加入负缓存
        cacheNegativeResult(cacheKey);
        return null;
    }

    /**
     * 查找自定义配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe findCustomRecipe(Level level, List<ItemStack> inputs) {
        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value();
            if (matchesRecipe(recipe, inputs)) {
                return recipe;
            }
        }

        return null;
    }

    /**
     * 通过适配器查找转换配方
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends Recipe<?>> AdvancedAlloyFurnaceRecipe findAdaptedRecipe(Level level, List<ItemStack> inputs) {
        for (IRecipeAdapter<?> adapter : adapters) {
            RecipeHolder<T> holder = ((IRecipeAdapter<T>) adapter).findMatchingRecipe(level, inputs);
            if (holder != null) {
                // 使用 convertAll 获取所有转换后的配方（包括基础版和批量版）
                List<AdvancedAlloyFurnaceRecipe> convertedRecipes = ((IRecipeAdapter<T>) adapter).convertAll(holder, level);

                // 查找第一个匹配的配方
                for (AdvancedAlloyFurnaceRecipe recipe : convertedRecipes) {
                    if (matchesRecipe(recipe, inputs)) {
                        return recipe;
                    }
                }
            }
        }
        return null;
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
        // 简单的LRU策略：保留最新的50%条目
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
     * 清除所有缓存
     */
    public void clearCache() {
        recipeCache.clear();
        negativeCache.clear();
    }

    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return recipeCache.size() + negativeCache.size();
    }

    /**
     * 配方缓存键
     * 用于快速查找缓存的配方
     */
    private record RecipeCacheKey(List<ItemStack> inputs) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecipeCacheKey that = (RecipeCacheKey) o;

            if (inputs.size() != that.inputs.size()) return false;

            // 比较物品堆栈（忽略数量，只比较物品类型和NBT）
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
            for (ItemStack stack : inputs) {
                // 使用物品和组件计算哈希
                result = 31 * result + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
                // 使用 getComponentsPatch 替代 hasComponents
                var components = stack.getComponentsPatch();
                if (!components.isEmpty()) {
                    result = 31 * result + components.hashCode();
                }
            }
            return result;
        }
    }
}
