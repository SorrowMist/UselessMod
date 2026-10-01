package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thedarkcolour.exdeorum.recipe.sieve.CompressedSieveRecipe;
import thedarkcolour.exdeorum.recipe.sieve.SieveRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

/**
 * 把 Ex Deorum 的筛子与重型筛子配方转换为合金炉配方。
 *
 * <p>筛子配方由三部分构成：被筛方块材料、产出物品及产出量提供者、以及限定可用筛网的材料。
 * 转换后模具占用两个槽位：一号槽为筛子方块，二号槽为该配方限定的筛网。普通筛子与重型筛子
 * 分别使用各自的设备材料集合，二者共用同一份配方结构（{@link CompressedSieveRecipe} 是
 * {@link SieveRecipe} 的子类），因此由本适配器一并处理。</p>
 *
 * <p>产出语义：单条筛子配方的产出量由数量提供者描述，其随机性表达在数量本身而非独立的触发
 * 概率上，例如 {@code binomial(1, p)} 表示以概率 p 产出 1 个。转换时先按 Ex Deorum 的期望值
 * 规则把提供者折算为期望产出量，再经 {@link ExpectedOutputScaler} 折算为最小确定性批次，
 * 输入数量、能耗与处理时间按同一批次倍数同步放大，避免把概率产出错误地当作必出。</p>
 *
 * <p>筛网拆分：同一条配方可声明多个可用筛网，而不同筛网对应的产出概率互不相同。转换时按
 * 「输入材料 + 具体筛网」重新分组，每个筛网生成独立配方，同一分组内多条配方的期望产出累加，
 * 与筛子实际逐个判定产出条目的行为一致。</p>
 *
 * <p>动态读取：配方来自 RecipeManager，筛网集合来自 {@code exdeorum:sieve_meshes} 物品标签，
 * 筛子与重型筛子的设备集合来自 Ex Deorum 的材料注册表，三者均在运行期解析，因此数据包或脚本
 * 新增的筛网、配方与设备材料会一并生效。</p>
 *
 * <p>已知取舍：筛子效率受筛网效率附魔影响，幸运附魔额外提供重掷机会，二者均为运行时随机
 * 行为，无法在不引入随机的前提下表达为确定性配方，故此处按未附魔的基准效率折算；标记为仅
 * 手动筛选的配方不会从机械筛产出，合金炉属机械装置，故不纳入转换范围。</p>
 *
 * <p>模具数量：筛子配方占用两个模具槽位，超出单槽配方的假设，因此仅多方块结构的万能模具仓
 * 支持此类配方；AE 网络与样板计算器路径按单模具语义处理，遇到多模具配方会跳过。</p>
 */
public final class SieveRecipeAdapter implements IRecipeAdapter<SieveSyntheticRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.EX_DEORUM;
    }

    @Override
    public Class<SieveSyntheticRecipe> getRecipeClass() {
        return SieveSyntheticRecipe.class;
    }

    /** 模具由配方限定的筛网决定，无法在注册阶段固定，因此以动态模具方式登记。 */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return ExDeorumRecipeAdapterUtils.isSieve(mold)
                || ExDeorumRecipeAdapterUtils.isCompressedSieve(mold);
    }

    @Override
    public List<RecipeHolder<SieveSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<SieveRecipe>> sources;
        try {
            // 重型筛子配方是普通筛子配方的子类，按基类枚举即可同时覆盖两类配方。
            sources = ExDeorumRecipeAdapterUtils.allOf(level.getRecipeManager(), SieveRecipe.class);
        }
        catch (RuntimeException exception) {
            // Ex Deorum 未安装或配方类型未注册时静默跳过，不影响其余来源的转换。
            return List.of();
        }
        if (sources.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<SieveSyntheticRecipe>> recipes = new ArrayList<>();
        for (AdvancedAlloyFurnaceRecipe converted : convertSieves(sources, level.registryAccess())) {
            recipes.add(new RecipeHolder<>(converted.id(), new SieveSyntheticRecipe(converted)));
        }
        return List.copyOf(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SieveSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    /**
     * 按「输入材料 + 具体筛网」分组并转换全部筛子配方。
     *
     * @param sources    运行期枚举到的筛子配方
     * @param registries 注册表访问器，用于编码材料组件以派生配方 id
     * @return 转换后的合金炉配方列表
     */
    private static List<AdvancedAlloyFurnaceRecipe> convertSieves(
            List<RecipeHolder<SieveRecipe>> sources, HolderLookup.Provider registries) {
        List<SieveGroup> groups = new ArrayList<>();

        for (RecipeHolder<SieveRecipe> holder : sources) {
            SieveRecipe recipe = holder.value();
            if (recipe == null || recipe.byHandOnly()) {
                continue;
            }
            if (AdapterUtils.isIngredientEmpty(recipe.ingredient())
                    || recipe.result == null || recipe.result.isEmpty()) {
                continue;
            }

            OptionalDouble expected = ExDeorumRecipeAdapterUtils.expectedAmount(recipe.resultAmount());
            if (expected.isEmpty() || expected.getAsDouble() <= 0.0D) {
                continue;
            }

            // 筛网无法展开时该配方没有可用模具，放弃转换而不是产出无筛网配方。
            List<ItemStack> meshes = ExDeorumRecipeAdapterUtils.meshItems(recipe.mesh());
            if (meshes.isEmpty()) {
                continue;
            }

            boolean compressed = recipe instanceof CompressedSieveRecipe;
            for (ItemStack mesh : meshes) {
                SieveGroup group = findGroup(groups, compressed, mesh, recipe.ingredient());
                if (group == null) {
                    group = new SieveGroup(compressed, mesh, recipe.ingredient(), registries);
                    groups.add(group);
                }
                group.add(recipe.result, expected.getAsDouble());
                group.addSourceId(holder.id());
            }
        }

        // 分组顺序由源配方枚举顺序决定，而该顺序在客户端与服务端不保证一致。转换结果本身
        // 不依赖顺序，但排序后可以让配方目录的条目次序保持稳定，便于比对与缓存。
        groups.sort(Comparator.comparing(SieveGroup::sortKey));

        // 内容键相同的分组无法共存：分组判等的三个分量与内容键一一对应，因此该统计只用于
        // 识别材料以物品级键表达、无法区分组件差异的碰撞，正常结果为全部为 1。
        Map<String, Integer> pathCounts = new HashMap<>();
        for (SieveGroup group : groups) {
            pathCounts.merge(group.idPath(), 1, Integer::sum);
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (SieveGroup group : groups) {
            AdvancedAlloyFurnaceRecipe converted =
                    group.toRecipe(pathCounts.getOrDefault(group.idPath(), 1) > 1);
            if (converted != null) {
                recipes.add(converted);
            }
        }
        return List.copyOf(recipes);
    }

    /** 在既有分组中查找输入材料语义相同的同筛网分组。 */
    @Nullable
    private static SieveGroup findGroup(
            List<SieveGroup> groups, boolean compressed, ItemStack mesh, Ingredient input) {
        for (SieveGroup group : groups) {
            if (group.matches(compressed, mesh, input)) {
                return group;
            }
        }
        return null;
    }

    /** 同一「输入材料 + 具体筛网 + 设备类别」下的产出累加单元。 */
    private static final class SieveGroup {
        private final boolean compressed;
        private final ItemStack mesh;
        private final Ingredient input;
        private final String contentKey;
        private final Set<ResourceLocation> sourceIds = new TreeSet<>();
        private final List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();

        private SieveGroup(boolean compressed, ItemStack mesh, Ingredient input,
                           HolderLookup.Provider registries) {
            this.compressed = compressed;
            this.mesh = mesh;
            this.input = input;
            // 分组判等的三个分量即配方身份的全部内容来源，故 id 只由它们派生。
            this.contentKey = (compressed ? "compressed_sieve_" : "sieve_")
                    + ExDeorumRecipeAdapterUtils.stableItemKey(mesh) + "_"
                    + ExDeorumRecipeAdapterUtils.stableIngredientKey(input, registries);
        }

        /** 与配方枚举顺序无关的排序键。 */
        private String sortKey() {
            return contentKey;
        }

        /**
         * 记录合入本分组的源配方 id。
         *
         * <p>源配方 id 仅在内容键发生碰撞时用于消歧，正常路径不进入配方 id：分组合并时哪条
         * 源配方先被枚举到由遍历顺序决定，若把它作为 id 的固定组成部分，两端就会生成不同的
         * 配方 id。此处用有序集合消除记录顺序的影响。</p>
         */
        private void addSourceId(ResourceLocation id) {
            if (id != null) {
                sourceIds.add(id);
            }
        }

        /** 配方 id 的主体路径，不含命名空间。 */
        private String idPath() {
            return contentKey + "_converted";
        }

        private boolean matches(boolean compressed, ItemStack mesh, Ingredient input) {
            return this.compressed == compressed
                    && this.mesh.getItem() == mesh.getItem()
                    && AdapterUtils.areIngredientsEqual(this.input, input);
        }

        /** 累加一条配方的期望产出；期望量的拆分规则见共用方法。 */
        private void add(ItemStack result, double expectedAmount) {
            ExDeorumRecipeAdapterUtils.appendExpectedOutput(weightedOutputs, result, expectedAmount);
        }

        @Nullable
        private AdvancedAlloyFurnaceRecipe toRecipe(boolean disambiguate) {
            List<Ingredient> deviceMolds = compressed
                    ? ExDeorumRecipeAdapterUtils.compressedSieveMolds()
                    : ExDeorumRecipeAdapterUtils.sieveMolds();
            if (deviceMolds.isEmpty()) {
                // 设备材料集合为空说明材料注册表尚未就绪，此时放弃转换而不是产出无设备模具配方。
                return null;
            }

            Optional<ExpectedOutputScaler.ScaledOutputs> scaled =
                    ExpectedOutputScaler.scale(weightedOutputs);
            if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
                return null;
            }
            ExpectedOutputScaler.ScaledOutputs scaledOutputs = scaled.get();
            int operations = scaledOutputs.operations();

            // 模具按槽位排列：一号槽为筛子方块，二号槽为该配方限定的筛网。
            List<Ingredient> molds = new ArrayList<>(deviceMolds.size() + 1);
            molds.addAll(deviceMolds);
            molds.add(Ingredient.of(mesh));

            int processTime = ExDeorumRecipeAdapterUtils.processTimeFor(operations);

            // 同一源配方会按筛网拆分为多条，配方 id 必须由分组内容派生：源配方与筛网的枚举
            // 顺序在客户端与服务端不保证一致，若改用集合下标或「首条源配方 id」，两端会把
            // 同一个 id 指向不同的配方，导致 JEI 侧选中的配方在服务端目录中无法解析。
            String path = idPath();
            if (disambiguate) {
                path = path + "_" + AdapterUtils.stableHash(
                        String.join("|", sourceIds.stream().map(ResourceLocation::toString).toList()));
            }
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    RecipeSourceIds.EX_DEORUM, path);

            return new AdvancedAlloyFurnaceRecipe(
                    recipeId,
                    List.of(new CountedIngredient(input, Math.max(1L, operations))),
                    List.of(),
                    List.of(),
                    scaledOutputs.outputs(),
                    List.of(),
                    List.of(),
                    ExDeorumRecipeAdapterUtils.energyFor(processTime),
                    processTime,
                    Ingredient.EMPTY,
                    0,
                    molds,
                    AlloyFurnaceMode.NORMAL);
        }
    }
}
