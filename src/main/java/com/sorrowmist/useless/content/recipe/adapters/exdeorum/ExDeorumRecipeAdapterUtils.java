package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import thedarkcolour.exdeorum.loot.SummationGenerator;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.compat.CompatUtil;
import thedarkcolour.exdeorum.registry.EItems;
import thedarkcolour.exdeorum.tag.EItemTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Ex Deorum 桶与坩埚配方转换的共用判定与换算。
 *
 * <p>桶类配方以 {@code exdeorum:barrels} 物品标签判定模具，坩埚类配方没有对应标签，改由
 * Ex Deorum 公开的 {@link CompatUtil} 枚举当前可用的坩埚物品。两类判定结果都在首次使用时
 * 缓存，避免每次配方查找都重复遍历材料注册表。</p>
 */
public final class ExDeorumRecipeAdapterUtils {
    /** 与 Ex Deorum {@code BarrelBlockEntity.MAX_CAPACITY} 一致的堆肥充满量。 */
    public static final int COMPOST_FULL_VOLUME = 1000;
    /** 流体类配方按整桶体积换算。 */
    public static final int BUCKET_VOLUME = 1000;
    /** 处理时间与能量的换算基准：200 tick 对应 {@link AdapterUtils#DEFAULT_ENERGY}。 */
    private static final int BASE_PROCESS_TIME = 200;

    @Nullable
    private static volatile Set<Item> lavaCrucibleItems;
    @Nullable
    private static volatile Set<Item> waterCrucibleItems;
    @Nullable
    private static volatile Set<Item> sieveItems;
    @Nullable
    private static volatile Set<Item> compressedSieveItems;

    private ExDeorumRecipeAdapterUtils() {
    }

    /** 判定模具是否为任意材质的 Ex Deorum 桶，涵盖石桶与各类木桶。 */
    public static boolean isBarrel(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(EItemTags.BARRELS);
    }

    public static boolean isLavaCrucible(@Nullable ItemStack mold) {
        return containsItem(lavaCrucibleItems(), mold);
    }

    public static boolean isWaterCrucible(@Nullable ItemStack mold) {
        return containsItem(waterCrucibleItems(), mold);
    }

    /** 桶配方的模具字段，直接使用 Ex Deorum 的物品标签以保留标签语义。 */
    public static List<Ingredient> barrelMolds() {
        return List.of(Ingredient.of(EItemTags.BARRELS));
    }

    public static List<Ingredient> lavaCrucibleMolds() {
        return moldsOf(lavaCrucibleItems());
    }

    public static List<Ingredient> waterCrucibleMolds() {
        return moldsOf(waterCrucibleItems());
    }

    /**
     * 按操作次数换算处理时间。
     *
     * <p>Ex Deorum 的桶与坩埚配方本身不声明耗时，其进度由设备按 tick 推进，因此这里以
     * {@link #BASE_PROCESS_TIME} 作为单次操作的基准耗时，操作次数放大后即为总耗时。</p>
     *
     * @param operations 操作次数，小于 1 时按 1 计
     * @return 处理时间（tick）
     */
    public static int processTimeFor(int operations) {
        return AdapterUtils.safeInt((long) BASE_PROCESS_TIME * Math.max(1, operations));
    }

    /** 按处理时间换算能量，保持与其它适配器一致的能量标度。 */
    public static long energyFor(int processTime) {
        long time = Math.max(1, processTime);
        return Math.max(1L, (long) AdapterUtils.safeInt(time * AdapterUtils.DEFAULT_ENERGY / BASE_PROCESS_TIME));
    }

    /** 枚举某一配方类的全部条目，避免依赖 Ex Deorum 的注册表静态字段。 */
    public static <T extends Recipe<?>> List<RecipeHolder<T>> allOf(
            @Nullable RecipeManager recipeManager, Class<T> recipeClass) {
        if (recipeManager == null || recipeClass == null) {
            return List.of();
        }

        List<RecipeHolder<T>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (recipeClass.isInstance(holder.value())) {
                result.add(new RecipeHolder<>(holder.id(), recipeClass.cast(holder.value())));
            }
        }
        return List.copyOf(result);
    }

    /** 把输入物品列表合并成需求映射，供基于 Ingredient 计数的匹配使用。 */
    public static Map<Ingredient, Long> requirements(List<CountedIngredient> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Map.of();
        }

        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input == null || AdapterUtils.isIngredientEmpty(input.ingredient()) || input.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return required;
    }

    /**
     * 判定物品输入是否满足配方需求。
     *
     * <p>优先使用机器中的具体物品栈，缺失时回退到合并后的 Ingredient 计数。配方不需求物品时，
     * 只有在机器同样没有物品输入的情况下才判定为匹配，避免与其它配方产生歧义。</p>
     */
    public static boolean matchesItems(List<CountedIngredient> requirements,
                                       @Nullable Map<Ingredient, Long> mergedInputs,
                                       @Nullable List<ItemStack> actualInputs) {
        boolean hasActual = hasConcreteInputs(actualInputs);
        boolean hasMerged = mergedInputs != null && !mergedInputs.isEmpty();
        if (requirements == null || requirements.isEmpty()) {
            return !hasActual && !hasMerged;
        }
        if (hasActual) {
            return ItemIngredientAllocator.matches(requirements, actualInputs, 1L);
        }
        return hasMerged && AdapterUtils.matchesRequired(mergedInputs, requirements(requirements));
    }

    /** 判定流体输入是否满足配方需求；配方不需求流体时要求机器同样为空。 */
    public static boolean matchesFluids(@Nullable List<LongSizedFluidIngredient> requirements,
                                        @Nullable Map<FluidStack, Long> mergedFluids) {
        boolean hasFluids = mergedFluids != null && !mergedFluids.isEmpty();
        if (requirements == null || requirements.isEmpty()) {
            return !hasFluids;
        }
        return hasFluids && FluidIngredientAllocator.matchesLong(requirements, mergedFluids, 1L);
    }

    private static boolean hasConcreteInputs(@Nullable List<ItemStack> inputs) {
        return inputs != null && inputs.stream().anyMatch(stack ->
                stack != null && !stack.isEmpty() && stack.getCount() > 0);
    }

    private static boolean containsItem(@Nullable Set<Item> items, @Nullable ItemStack mold) {
        return items != null && !items.isEmpty() && mold != null && !mold.isEmpty()
                && items.contains(mold.getItem());
    }

    private static List<Ingredient> moldsOf(@Nullable Set<Item> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Item item : items) {
            stacks.add(item.getDefaultInstance());
        }
        return List.of(Ingredient.of(stacks.stream()));
    }

    private static Set<Item> lavaCrucibleItems() {
        Set<Item> cached = lavaCrucibleItems;
        if (cached != null) {
            return cached;
        }
        Set<Item> collected = collectCrucibleItems(true);
        lavaCrucibleItems = collected;
        return collected;
    }

    private static Set<Item> waterCrucibleItems() {
        Set<Item> cached = waterCrucibleItems;
        if (cached != null) {
            return cached;
        }
        Set<Item> collected = collectCrucibleItems(false);
        waterCrucibleItems = collected;
        return collected;
    }

    /**
     * 枚举 Ex Deorum 当前可用的坩埚物品。
     *
     * <p>{@link CompatUtil} 的枚举会触发材料注册表的类初始化，因此仅在首次需要判定模具时调用。
     * 第三方材料缺失或注册表尚未就绪时返回空集合，由调用方按不匹配处理，不向配方查找抛出异常。</p>
     */
    private static Set<Item> collectCrucibleItems(boolean lava) {
        Set<Item> items = new HashSet<>();
        try {
            List<ItemLike> materials = lava
                    ? CompatUtil.getAvailableLavaCrucibles(true)
                    : CompatUtil.getAvailableWaterCrucibles(true);
            for (ItemLike material : materials) {
                items.add(material.asItem());
            }
        } catch (RuntimeException | LinkageError ignored) {
            return Set.of();
        }
        return Set.copyOf(items);
    }

    /**
     * 判定模具是否为钻石锤。
     *
     * <p>锤子配方统一以钻石锤作为模具，不按材质区分：同一配方的产出由锤击行为决定，
     * 与手持锤子的材质无关，故仅暴露一个确定性的模具槽位，避免同一配方因材质产生多份
     * 等价条目。</p>
     */
    public static boolean isHammer(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(EItems.DIAMOND_HAMMER.get());
    }

    /** 判定模具是否为钻石压缩锤。 */
    public static boolean isCompressedHammer(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(EItems.COMPRESSED_DIAMOND_HAMMER.get());
    }

    /** 锤子的模具材料，固定为钻石锤。 */
    public static List<Ingredient> hammerMolds() {
        return List.of(Ingredient.of(EItems.DIAMOND_HAMMER.get()));
    }

    /** 压缩锤的模具材料，固定为钻石压缩锤，与普通锤互不重叠。 */
    public static List<Ingredient> compressedHammerMolds() {
        return List.of(Ingredient.of(EItems.COMPRESSED_DIAMOND_HAMMER.get()));
    }

    /** 判定模具是否为任意材质的普通筛子。 */
    public static boolean isSieve(@Nullable ItemStack mold) {
        return containsItem(sieveItems(), mold);
    }

    /** 判定模具是否为任意材质的重型筛子。 */
    public static boolean isCompressedSieve(@Nullable ItemStack mold) {
        return containsItem(compressedSieveItems(), mold);
    }

    /**
     * 普通筛子的模具材料。
     *
     * <p>筛子没有对应的物品标签，改由 {@link CompatUtil} 枚举当前可用的筛子物品，第三方材料
     * 缺失时返回空列表，由调用方放弃转换。</p>
     */
    public static List<Ingredient> sieveMolds() {
        return moldsOf(sieveItems());
    }

    /** 重型筛子的模具材料，枚举方式与普通筛子一致。 */
    public static List<Ingredient> compressedSieveMolds() {
        return moldsOf(compressedSieveItems());
    }

    private static Set<Item> sieveItems() {
        Set<Item> cached = sieveItems;
        if (cached != null) {
            return cached;
        }
        Set<Item> collected = collectSieveItems(false);
        sieveItems = collected;
        return collected;
    }

    private static Set<Item> compressedSieveItems() {
        Set<Item> cached = compressedSieveItems;
        if (cached != null) {
            return cached;
        }
        Set<Item> collected = collectSieveItems(true);
        compressedSieveItems = collected;
        return collected;
    }

    /**
     * 枚举 Ex Deorum 当前可用的筛子物品。
     *
     * <p>普通筛子与重型筛子分别来自两套材料注册表，重型筛子额外依赖压缩方块材质，因此不共用缓存。</p>
     */
    private static Set<Item> collectSieveItems(boolean compressed) {
        Set<Item> items = new HashSet<>();
        try {
            List<ItemLike> materials = compressed
                    ? CompatUtil.getAvailableCompressedSieves(true)
                    : CompatUtil.getAvailableSieves(true, false);
            for (ItemLike material : materials) {
                items.add(material.asItem());
            }
        } catch (RuntimeException | LinkageError ignored) {
            return Set.of();
        }
        return Set.copyOf(items);
    }

    /**
     * 展开筛网材料为具体物品，供按筛网拆分模具槽使用。
     *
     * <p>筛网集合由 {@code exdeorum:sieve_meshes} 物品标签在运行期决定，数据包或脚本新增的
     * 筛网会随标签一并生效。条目在展开时去重，并过滤空物品，避免生成重复模具。</p>
     *
     * @param mesh 配方声明的筛网材料
     * @return 每个筛网对应一个单件物品栈；无法展开时返回空列表
     */
    public static List<ItemStack> meshItems(@Nullable Ingredient mesh) {
        if (mesh == null || mesh.isEmpty()) {
            return List.of();
        }

        ItemStack[] candidates;
        try {
            candidates = mesh.getItems();
        } catch (RuntimeException exception) {
            return List.of();
        }

        Set<Item> seen = new HashSet<>();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || !seen.add(candidate.getItem())) {
                continue;
            }
            // 只保留物品本身而不继承候选栈的组件：原版筛子按 mesh.getItem() 查找配方，
            // 附魔等组件由效率与幸运另行处理，不参与配方匹配。
            items.add(new ItemStack(candidate.getItem()));
        }
        return List.copyOf(items);
    }

    /**
     * 取物品的稳定标识片段，用于构造与遍历顺序无关的配方 id。
     *
     * <p>筛网与输入材料可以以标签或候选列表的形式声明，其展开顺序在客户端与服务端不保证
     * 一致，因此配方 id 只能由物品注册名一类的内容特征派生，不能使用集合下标。未注册物品
     * 回退为占位片段：该情形仅出现在注册表尚未就绪时，此时转换结果本身也不会被采纳。</p>
     *
     * @param stack 参与构成配方身份的物品栈
     * @return 由注册名拼成的稳定片段，命名空间与路径中的分隔符统一替换为下划线
     */
    public static String stableItemKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return "unregistered";
        }
        return key.getNamespace() + "_" + key.getPath().replace('/', '_');
    }

    /**
     * 取输入材料的稳定标识片段，用于构造与遍历顺序无关的配方 id。
     *
     * <p>材料以 {@link Ingredient} 声明，可以是标签或候选列表，其候选展开顺序在客户端与
     * 服务端不保证一致。此处对候选物品排序去重后再拼接，使结果只取决于材料集合本身。
     *
     * <p>候选的编码必须与 {@link AdapterUtils#areIngredientsEqual} 的判等口径一致——后者以
     * 「物品 + 组件」判定材料是否相同，因此仅取注册名不足以区分组件不同的候选，会让两条不同
     * 材料映射到同一个 id。单候选且无组件时直接使用注册名以保持可读；其余情形用集合内容的
     * SHA-256 摘要表达，避免拼接结果含资源路径不允许的分隔符或长度不可控。</p>
     *
     * @param ingredient 参与构成配方身份的输入材料
     * @param registries 注册表访问器，用于编码物品栈组件
     * @return 与候选展开顺序无关的稳定片段
     */
    public static String stableIngredientKey(
            @Nullable Ingredient ingredient, HolderLookup.Provider registries) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "empty";
        }

        ItemStack[] candidates;
        try {
            candidates = ingredient.getItems();
        } catch (RuntimeException exception) {
            return "unavailable";
        }

        Set<String> keys = new HashSet<>();
        @Nullable ItemStack single = null;
        boolean simple = true;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            keys.add(AlloyFurnaceRecipeFingerprint.safeItemStack(candidate, registries));
            if (single == null) {
                single = candidate;
            }
            if (candidate.getComponentsPatch() != null
                    && !candidate.getComponentsPatch().isEmpty()) {
                simple = false;
            }
        }
        if (keys.isEmpty() || single == null) {
            return "empty";
        }
        if (simple && keys.size() == 1) {
            return stableItemKey(single);
        }

        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.naturalOrder());
        return AdapterUtils.stableHash(String.join("|", sorted));
    }

    /**
     * 计算数量提供者的期望产出量。
     *
     * <p>换算规则与 Ex Deorum 的 {@code RecipeUtil#getExpectedValue} 一致：常量取声明值，
     * 均匀分布取区间期望，二项分布取 {@code n * p}，求和分布取各项期望之和。无法静态求值的
     * 提供者返回空值，由调用方放弃该条目。</p>
     *
     * @param provider 配方声明的产出数量提供者
     * @return 期望产出量；无法计算时为 {@link OptionalDouble#empty()}
     */
    public static OptionalDouble expectedAmount(@Nullable NumberProvider provider) {
        if (provider == null) {
            return OptionalDouble.empty();
        }
        if (provider instanceof ConstantValue constant) {
            return OptionalDouble.of(constant.value());
        }
        if (provider instanceof UniformGenerator uniform) {
            OptionalDouble min = expectedAmount(uniform.min());
            OptionalDouble max = expectedAmount(uniform.max());
            if (min.isEmpty() || max.isEmpty()) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of((min.getAsDouble() + max.getAsDouble()) / 2.0D);
        }
        if (provider instanceof BinomialDistributionGenerator binomial) {
            OptionalDouble rolls = expectedAmount(binomial.n());
            OptionalDouble chance = expectedAmount(binomial.p());
            if (rolls.isEmpty() || chance.isEmpty()) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(rolls.getAsDouble() * chance.getAsDouble());
        }
        if (provider instanceof SummationGenerator summation) {
            double total = 0.0D;
            for (NumberProvider child : summation.providers()) {
                OptionalDouble value = expectedAmount(child);
                if (value.isEmpty()) {
                    return OptionalDouble.empty();
                }
                total += value.getAsDouble();
            }
            return OptionalDouble.of(total);
        }
        // 其余提供者依赖运行时上下文（如战利品表数值），无法静态求值。
        return OptionalDouble.empty();
    }

    /**
     * 把单个产出的期望量追加到带权产出列表。
     *
     * <p>期望量被拆分为整数部分与小数部分：整数部分按确定产出表达，小数部分按单件概率产出
     * 表达。{@link ExpectedOutputScaler} 只接受 [0, 1] 区间内的概率，直接传入大于 1 的期望值
     * 会被当作概率截断而丢失产出，故必须拆分。</p>
     *
     * @param outputs        带权产出列表，原地追加
     * @param result         产出物品，数量部分由期望量决定
     * @param expectedAmount 该产出的期望数量
     */
    public static void appendExpectedOutput(List<ExpectedOutputScaler.WeightedItemOutput> outputs,
                                            ItemStack result, double expectedAmount) {
        if (outputs == null || result == null || result.isEmpty() || expectedAmount <= 0.0D) {
            return;
        }

        ItemStack single = result.copyWithCount(1);
        long whole = (long) Math.floor(expectedAmount);
        if (whole > 0 && whole <= Integer.MAX_VALUE) {
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    single.copy(), (int) whole, (int) whole, 1.0D));
        }
        double fraction = expectedAmount - whole;
        if (fraction > 1.0E-9D) {
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    single.copy(), 1, 1, fraction));
        }
    }
}