package com.sorrowmist.useless.content.recipe.adapters.botanypots;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.darkhax.botanypots.common.api.data.itemdrops.ItemDropProvider;
import net.darkhax.botanypots.common.api.data.recipes.crop.Crop;
import net.darkhax.botanypots.common.impl.block.BotanyPotBlock;
import net.darkhax.botanypots.common.impl.data.itemdrops.BlockDrops;
import net.darkhax.botanypots.common.impl.data.itemdrops.LootTableDrops;
import net.darkhax.botanypots.common.impl.data.itemdrops.SimpleDropProvider;
import net.darkhax.botanypots.common.impl.data.recipe.crop.BasicCrop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把植物盆（Botany Pots）的作物生长配方转换为合金炉配方。
 *
 * <p>植物盆的作物配方本身没有物品输入，唯一的消耗是盆中的水与土壤。转换后：</p>
 * <ul>
 *     <li>流体输入为水，用量按确定性批次放大；</li>
 *     <li>不使用物品输入，作物种子仅作为模具出现；</li>
 *     <li>模具为两个：植物盆方块与对应的作物种子，由多方块结构的万能模具仓分别占用独立槽位；</li>
 *     <li>产物为该作物收获时可能掉落的全部物品，随机概率经
 *     {@link ExpectedOutputScaler} 折算为最小确定性批次。</li>
 * </ul>
 *
 * <p>掉落条目全部静态展开，不触发实际收获逻辑，因此无需构造植物盆上下文：
 * {@code SimpleDropProvider} 直接读取条目字段；战利品表类掉落由本适配器自行读取
 * 战利品表 JSON 并解析条件、权重与数量。此处刻意不调用 BotanyPots 的
 * {@code buildDisplayItems()}：该方法依赖该模组的静态注册表引用，在配方生成时机可能已失效，
 * 抛出异常会导致整条作物被丢弃，且其返回值不含概率与数量信息。</p>
 *
 * <p>战利品表 JSON 优先经模组文件容器读取，该路径暴露 jar 内的完整路径空间，不受资源
 * 管理器的包类型分流限制，因此服务端、单人客户端与连接远程服务器的客户端均可获得
 * 完整掉落。仅当模组文件不可访问时才退回资源管理器，后者仅在服务端与单人客户端持有
 * {@code data} 视图。需要实例化实体才能枚举的 {@code botanypots:entity} 掉落无法静态
 * 展开，会被忽略。</p>
 */
public final class BotanyPotsRecipeAdapter implements IRecipeAdapter<BotanyPotsCropSyntheticRecipe> {
    /** 单次收获消耗的水量（mB）。 */
    private static final int WATER_PER_HARVEST = 1_000;
    /** 作物未声明生长时间时使用的兜底值，与植物盆默认值一致。 */
    private static final int DEFAULT_GROW_TIME = 1_200;
    /** 战利品表嵌套引用的最大展开深度，用于阻断循环引用。 */
    private static final int MAX_TABLE_DEPTH = 4;

    /** 植物盆模具的运行期缓存；方块注册表在运行期不会变化，故无需失效。 */
    private static volatile Ingredient potMoldCache;

    /**
     * 本次构建内已解析的战利品表。
     *
     * <p>多个作物可能引用同一张战利品表，逐次读取会使构建开销退化为「作物数 × 单次读取」。
     * 按线程缓存后，每张表在单次构建中至多解析一次。缓存在每次构建入口清空，
     * 因此数据重载后不会读到陈旧内容。</p>
     */
    private static final ThreadLocal<Map<ResourceLocation, JsonObject>> LOOT_TABLE_CACHE =
            ThreadLocal.withInitial(HashMap::new);

    @Override
    public String sourceId() {
        return RecipeSourceIds.BOTANY_POTS;
    }

    @Override
    public Class<BotanyPotsCropSyntheticRecipe> getRecipeClass() {
        return BotanyPotsCropSyntheticRecipe.class;
    }

    /**
     * 模具由具体作物决定，无法在注册阶段固定，因此以动态模具方式登记。
     */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<RecipeHolder<BotanyPotsCropSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<Crop>> crops;
        try {
            crops = level.getRecipeManager().getAllRecipesFor(Crop.TYPE.get());
        }
        catch (RuntimeException exception) {
            // 植物盆未安装或配方类型未注册时静默跳过，不影响其余来源的转换。
            return List.of();
        }
        if (crops.isEmpty()) {
            return List.of();
        }

        ResourceManager resourceManager = resourceManager(level);
        // 单次构建内复用已解析的战利品表：多个作物可能引用同一张表。
        LOOT_TABLE_CACHE.get().clear();
        List<RecipeHolder<BotanyPotsCropSyntheticRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<Crop> crop : crops) {
            AdvancedAlloyFurnaceRecipe converted = convertCrop(crop, resourceManager);
            if (converted != null) {
                recipes.add(new RecipeHolder<>(converted.id(),
                        new BotanyPotsCropSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BotanyPotsCropSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    /**
     * 转换单个作物配方。
     *
     * @param holder 作物配方持有者
     * @param resourceManager 资源管理器，作为模组文件不可用时的退路
     * @return 转换后的合金炉配方，无法转换时返回 {@code null}
     */
    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertCrop(
            RecipeHolder<Crop> holder, @Nullable ResourceManager resourceManager) {
        if (holder == null || holder.value() == null) {
            return null;
        }
        // 只有 BasicCrop 及其子类暴露种子、生长时间与掉落列表；其余自定义作物无法静态描述。
        if (!(holder.value() instanceof BasicCrop crop)) {
            return null;
        }

        BasicCrop.Properties properties = crop.getBasicProperties();
        if (properties == null) {
            return null;
        }

        Ingredient seed = properties.input();
        if (seed == null || seed.isEmpty()) {
            return null;
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs =
                collectDrops(properties.drops(), resourceManager);
        if (weightedOutputs.isEmpty()) {
            return null;
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(weightedOutputs);
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            return null;
        }
        ExpectedOutputScaler.ScaledOutputs scaledOutputs = scaled.get();
        int operations = scaledOutputs.operations();

        List<Ingredient> molds = buildMolds(seed);
        if (molds.isEmpty()) {
            return null;
        }

        long waterAmount = (long) WATER_PER_HARVEST * operations;
        long energy = (long) AdapterUtils.DEFAULT_ENERGY * operations;
        int growTime = properties.growTime() > 0 ? properties.growTime() : DEFAULT_GROW_TIME;

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.<CountedIngredient>of(),
                List.of(new LongSizedFluidIngredient(
                        FluidIngredient.single(Fluids.WATER), waterAmount)),
                List.of(),
                scaledOutputs.outputs(),
                List.of(),
                List.of(),
                energy,
                AdapterUtils.safeInt(growTime),
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    /**
     * 构建配方模具：第一个为植物盆，第二个为对应作物种子。
     *
     * @param seed 作物种子
     * @return 模具列表，植物盆缺失时返回空列表
     */
    private static List<Ingredient> buildMolds(Ingredient seed) {
        Ingredient potMold = potMold();
        if (potMold.isEmpty()) {
            return List.of();
        }
        return List.of(potMold, seed);
    }

    /**
     * 收集全部植物盆方块作为单一模具材料。
     *
     * <p>此处直接遍历方块注册表而不是读取物品标签，避免标签在配方生成时机尚未完成绑定
     * 而导致模具为空、配方整体失效。</p>
     *
     * <p>方块注册表在运行期不会变化，因此结果缓存于静态字段：若每个作物都重新遍历一次，
     * 全部作物的转换将退化为「作物数 × 方块注册表大小」的复杂度。</p>
     *
     * @return 覆盖全部植物盆的模具 Ingredient
     */
    private static Ingredient potMold() {
        Ingredient cached = potMoldCache;
        if (cached != null) {
            return cached;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof BotanyPotBlock potBlock) {
                Item item = potBlock.asItem();
                if (item != Items.AIR) {
                    stacks.add(item.getDefaultInstance());
                }
            }
        }
        Ingredient built = stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.of(stacks.stream());
        potMoldCache = built;
        return built;
    }

    /**
     * 静态展开作物掉落条目，概率与数量原样保留，交给缩放器折算为确定性批次。
     *
     * @param providers 作物的掉落提供者列表
     * @param resourceManager 资源管理器，用于读取战利品表
     * @return 带概率的掉落条目列表
     */
    private static List<ExpectedOutputScaler.WeightedItemOutput> collectDrops(
            List<ItemDropProvider> providers, @Nullable ResourceManager resourceManager) {
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }

        List<ExpectedOutputScaler.WeightedItemOutput> outputs = new ArrayList<>();
        for (ItemDropProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            if (provider instanceof SimpleDropProvider simple) {
                appendSimpleDrops(simple, outputs);
            }
            else if (provider instanceof LootTableDrops lootTable) {
                appendLootTableDrops(lootTable, resourceManager, outputs);
            }
            // botanypots:entity 需要实例化实体才能枚举掉落，无法静态展开，此处忽略。
        }
        return outputs;
    }

    private static void appendSimpleDrops(
            SimpleDropProvider provider,
            List<ExpectedOutputScaler.WeightedItemOutput> outputs) {
        for (SimpleDropProvider.SimpleDrop drop : provider.drops()) {
            if (drop == null || drop.drop() == null || drop.drop().isEmpty()) {
                continue;
            }
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    drop.drop().copyWithCount(1), 1, 1, drop.chance()));
        }
    }

    /**
     * 展开战利品表类掉落。
     *
     * <p>战利品表无法读取或条目无法静态判定时，退回方块自身的掉落物（作物种子或方块物品），
     * 避免整条作物因掉落缺失而被丢弃。</p>
     *
     * @param provider 战利品表掉落提供者
     * @param resourceManager 资源管理器
     * @param outputs 掉落条目收集目标
     */
    private static void appendLootTableDrops(
            LootTableDrops provider,
            @Nullable ResourceManager resourceManager,
            List<ExpectedOutputScaler.WeightedItemOutput> outputs) {
        List<ExpectedOutputScaler.WeightedItemOutput> resolved = new ArrayList<>();
        ResourceLocation tableId = provider.getTableId();
        if (tableId != null) {
            JsonObject table = readLootTable(resourceManager, tableId);
            if (table != null) {
                appendTable(table, resourceManager, resolved, 0);
            }
        }

        if (!resolved.isEmpty()) {
            outputs.addAll(resolved);
            return;
        }

        ItemStack fallback = fallbackDrop(provider);
        if (!fallback.isEmpty()) {
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    fallback.copyWithCount(1), 1, 1, 1.0D));
        }
    }

    /**
     * 读取掉落提供者的兜底掉落物。
     *
     * @param provider 掉落提供者
     * @return 兜底掉落物，不可用时返回空栈
     */
    private static ItemStack fallbackDrop(LootTableDrops provider) {
        if (!(provider instanceof BlockDrops blockDrops)) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack fallback = blockDrops.findFallbackItem();
            return fallback == null ? ItemStack.EMPTY : fallback;
        }
        catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 解析战利品表 JSON 的全部门池。
     *
     * @param table 战利品表 JSON
     * @param resourceManager 资源管理器，用于解析嵌套引用
     * @param outputs 掉落条目收集目标
     * @param depth 当前嵌套深度
     */
    private static void appendTable(
            JsonObject table, ResourceManager resourceManager,
            List<ExpectedOutputScaler.WeightedItemOutput> outputs, int depth) {
        if (depth > MAX_TABLE_DEPTH || table == null) {
            return;
        }
        JsonElement poolsElement = table.get("pools");
        if (poolsElement == null || !poolsElement.isJsonArray()) {
            return;
        }

        for (JsonElement poolElement : poolsElement.getAsJsonArray()) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            appendPool(poolElement.getAsJsonObject(), resourceManager, outputs, depth);
        }
    }

    /**
     * 解析单个门池。
     *
     * <p>抽取次数只接受固定值，附加抽取次数必须为零：随机抽取次数与附加抽取无法折算为
     * 确定性的批次产出。数量范围按抽取次数放大，概率按池内权重归一化。</p>
     *
     * @param pool 门池 JSON
     * @param resourceManager 资源管理器
     * @param outputs 掉落条目收集目标
     * @param depth 当前嵌套深度
     */
    private static void appendPool(
            JsonObject pool, ResourceManager resourceManager,
            List<ExpectedOutputScaler.WeightedItemOutput> outputs, int depth) {
        int rolls = fixedCount(pool.get("rolls"), 1);
        if (rolls <= 0) {
            return;
        }
        if (!isZero(pool.get("bonus_rolls"))) {
            return;
        }
        JsonElement entriesElement = pool.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            return;
        }
        JsonArray entries = entriesElement.getAsJsonArray();

        List<EntryDrop> candidates = new ArrayList<>();
        for (JsonElement entryElement : entries) {
            if (entryElement.isJsonObject()) {
                appendEntry(entryElement.getAsJsonObject(), resourceManager, candidates, depth);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        double totalWeight = 0.0D;
        for (EntryDrop candidate : candidates) {
            totalWeight += candidate.weight();
        }
        if (totalWeight <= 0.0D) {
            return;
        }

        for (EntryDrop candidate : candidates) {
            double chance = candidate.chance() * candidate.weight() / totalWeight;
            if (chance <= 0.0D) {
                continue;
            }
            outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    candidate.stack().copyWithCount(1),
                    candidate.min() * rolls,
                    candidate.max() * rolls,
                    Math.min(1.0D, chance)));
        }
    }

    /**
     * 递归展开单个掉落条目。
     *
     * <p>仅接受可静态判定的条目类型：物品、物品标签与战利品表引用；
     * 条件只接受随机概率类条件，函数只接受数量设置。其余情形会改变产出物品或数量，
     * 无法静态描述，因此整体跳过该条目。</p>
     *
     * @param entry 条目 JSON
     * @param resourceManager 资源管理器
     * @param candidates 条目收集目标
     * @param depth 当前嵌套深度
     */
    private static void appendEntry(
            JsonObject entry, ResourceManager resourceManager,
            List<EntryDrop> candidates, int depth) {
        double conditionChance = conditionChance(entry);
        if (conditionChance <= 0.0D) {
            return;
        }

        int[] countRange = countRange(entry);
        if (countRange == null) {
            return;
        }

        double weight = entryWeight(entry);
        String type = stringValue(entry.get("type"));

        if ("minecraft:item".equals(type)) {
            Item item = registeredItem(ResourceLocation.tryParse(stringValue(entry.get("name"))));
            if (item != null && item != Items.AIR) {
                candidates.add(new EntryDrop(item.getDefaultInstance(),
                        countRange[0], countRange[1], conditionChance, weight));
            }
            return;
        }

        if ("minecraft:tag".equals(type)) {
            ResourceLocation tagId = ResourceLocation.tryParse(stringValue(entry.get("name")));
            if (tagId == null) {
                return;
            }
            List<Item> tagItems = tagItems(TagKey.create(Registries.ITEM, tagId));
            if (tagItems.isEmpty()) {
                return;
            }
            // 标签内各物品等概率命中，单件概率按条目数均分。
            double perItemChance = conditionChance / tagItems.size();
            for (Item item : tagItems) {
                candidates.add(new EntryDrop(item.getDefaultInstance(),
                        countRange[0], countRange[1], perItemChance, weight));
            }
            return;
        }

        if ("minecraft:loot_table".equals(type)) {
            ResourceLocation nestedId = ResourceLocation.tryParse(nestedTableId(entry));
            if (nestedId == null) {
                return;
            }
            JsonObject nested = readLootTable(resourceManager, nestedId);
            if (nested == null) {
                return;
            }
            List<ExpectedOutputScaler.WeightedItemOutput> nestedOutputs = new ArrayList<>();
            appendTable(nested, resourceManager, nestedOutputs, depth + 1);
            for (ExpectedOutputScaler.WeightedItemOutput output : nestedOutputs) {
                candidates.add(new EntryDrop(output.stack(), output.min(), output.max(),
                        output.chance() * conditionChance, weight));
            }
            return;
        }

        if ("minecraft:empty".equals(type)) {
            return;
        }

        appendComposite(entry, type, resourceManager, candidates, depth,
                conditionChance, countRange, weight);
    }

    /**
     * 展开组合类条目。
     *
     * <p>{@code minecraft:alternatives} 只会命中其中一个子条目，子条目概率按数量均分；
     * {@code minecraft:group} 与 {@code minecraft:sequence} 的子条目全部生效，概率保持不变。</p>
     *
     * @param entry 条目 JSON
     * @param type 条目类型 id
     * @param resourceManager 资源管理器
     * @param candidates 条目收集目标
     * @param depth 当前嵌套深度
     * @param conditionChance 条目自身的触发概率
     * @param countRange 数量范围
     * @param weight 条目权重
     */
    private static void appendComposite(
            JsonObject entry, String type, ResourceManager resourceManager,
            List<EntryDrop> candidates, int depth, double conditionChance,
            int[] countRange, double weight) {
        boolean alternatives = "minecraft:alternatives".equals(type);
        boolean group = "minecraft:group".equals(type);
        boolean sequence = "minecraft:sequence".equals(type);
        if (!alternatives && !group && !sequence) {
            return;
        }

        JsonElement childrenElement = entry.get("children");
        if (childrenElement == null || !childrenElement.isJsonArray()) {
            return;
        }
        JsonArray children = childrenElement.getAsJsonArray();
        if (children.isEmpty()) {
            return;
        }

        List<EntryDrop> childDrops = new ArrayList<>();
        for (JsonElement childElement : children) {
            if (childElement.isJsonObject()) {
                appendEntry(childElement.getAsJsonObject(), resourceManager, childDrops, depth + 1);
            }
        }
        if (childDrops.isEmpty()) {
            return;
        }

        double divisor = alternatives ? childDrops.size() : 1.0D;
        for (EntryDrop child : childDrops) {
            candidates.add(new EntryDrop(child.stack(),
                    child.min() * countRange[0], child.max() * countRange[1],
                    child.chance() * conditionChance / divisor, child.weight() * weight));
        }
    }

    /**
     * 读取条目自身的触发概率。
     *
     * <p>只接受随机概率条件；出现其它条件时无法静态判定是否命中，返回零以跳过该条目。</p>
     *
     * @param entry 条目 JSON
     * @return 触发概率，不可静态判定时返回零
     */
    private static double conditionChance(JsonObject entry) {
        JsonElement conditionsElement = entry.get("conditions");
        if (conditionsElement == null) {
            return 1.0D;
        }
        if (!conditionsElement.isJsonArray()) {
            return 0.0D;
        }

        double chance = 1.0D;
        for (JsonElement conditionElement : conditionsElement.getAsJsonArray()) {
            if (!conditionElement.isJsonObject()) {
                return 0.0D;
            }
            JsonObject condition = conditionElement.getAsJsonObject();
            String type = stringValue(condition.get("condition"));
            if (!"minecraft:random_chance".equals(type)
                    && !"minecraft:random_chance_with_looting".equals(type)) {
                return 0.0D;
            }
            double value = numberValue(condition.get("chance"), -1.0D);
            if (value < 0.0D || value > 1.0D) {
                return 0.0D;
            }
            chance *= value;
        }
        return chance;
    }

    /**
     * 读取条目的产出数量范围。
     *
     * <p>只接受数量设置函数；出现其它函数时产出物品或数量会被改写，无法静态描述，返回
     * {@code null}。</p>
     *
     * @param entry 条目 JSON
     * @return 数量范围数组，不可静态判定时返回 {@code null}
     */
    @Nullable
    private static int[] countRange(JsonObject entry) {
        JsonElement functionsElement = entry.get("functions");
        if (functionsElement == null) {
            return new int[] {1, 1};
        }
        if (!functionsElement.isJsonArray()) {
            return null;
        }

        int[] range = new int[] {1, 1};
        for (JsonElement functionElement : functionsElement.getAsJsonArray()) {
            if (!functionElement.isJsonObject()) {
                return null;
            }
            JsonObject function = functionElement.getAsJsonObject();
            if (!"minecraft:set_count".equals(stringValue(function.get("function")))) {
                return null;
            }
            int[] count = fixedRange(function.get("count"));
            if (count == null) {
                return null;
            }
            range = count;
        }
        return range;
    }

    /**
     * 读取条目权重。
     *
     * @param entry 条目 JSON
     * @return 条目权重，缺失或非法时退回 1
     */
    private static double entryWeight(JsonObject entry) {
        double weight = numberValue(entry.get("weight"), 1.0D);
        return Double.isFinite(weight) && weight > 0.0D ? weight : 1.0D;
    }

    /**
     * 读取固定数量或固定区间。
     *
     * @param element 数量 JSON
     * @return 数量范围数组，非固定值时返回 {@code null}
     */
    @Nullable
    private static int[] fixedRange(@Nullable JsonElement element) {
        if (element == null) {
            return new int[] {1, 1};
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int value = integralValue(element.getAsDouble());
            return value < 0 ? null : new int[] {value, value};
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        String type = stringValue(object.get("type"));
        if ("minecraft:constant".equals(type)) {
            int value = integralValue(numberValue(object.get("value"), -1.0D));
            return value < 0 ? null : new int[] {value, value};
        }
        if ("minecraft:uniform".equals(type)) {
            int min = integralValue(numberValue(object.get("min"), -1.0D));
            int max = integralValue(numberValue(object.get("max"), -1.0D));
            return min < 0 || max < min ? null : new int[] {min, max};
        }
        return null;
    }

    /**
     * 读取固定抽取次数。
     *
     * @param element 抽取次数 JSON
     * @param fallback 缺失时的默认值
     * @return 抽取次数，非固定值时返回 -1
     */
    private static int fixedCount(@Nullable JsonElement element, int fallback) {
        if (element == null) {
            return fallback;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return integralValue(element.getAsDouble());
        }
        if (!element.isJsonObject()) {
            return -1;
        }

        JsonObject object = element.getAsJsonObject();
        if (!"minecraft:uniform".equals(stringValue(object.get("type")))) {
            return -1;
        }
        double min = numberValue(object.get("min"), -1.0D);
        double max = numberValue(object.get("max"), -1.0D);
        return min < 0.0D || min != max ? -1 : integralValue(min);
    }

    /**
     * 读取嵌套战利品表引用 id。
     *
     * @param entry 条目 JSON
     * @return 战利品表 id 字符串，缺失时返回空串
     */
    private static String nestedTableId(JsonObject entry) {
        String value = stringValue(entry.get("value"));
        return value.isEmpty() ? stringValue(entry.get("name")) : value;
    }

    /**
     * 展开物品标签内的全部物品。
     *
     * @param tag 物品标签
     * @return 标签内的物品列表
     */
    private static List<Item> tagItems(TagKey<Item> tag) {
        List<Item> items = new ArrayList<>();
        try {
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                Item item = holder.value();
                if (item != null && item != Items.AIR) {
                    items.add(item);
                }
            }
        }
        catch (RuntimeException exception) {
            // 标签未完成绑定时退回空列表，该条目被跳过而不影响其余掉落。
            return List.of();
        }
        return items;
    }

    /**
     * 读取战利品表 JSON。
     *
     * <p>优先经模组文件容器直接读取 jar 内的 {@code data/} 条目。该路径不受资源管理器
     * 的包类型分流限制，因此客户端在连接远程服务器时同样可用；仅当模组文件不可访问时
     * 才退回资源管理器，后者只在服务端与单人客户端持有 {@code data} 视图。</p>
     *
     * @param resourceManager 资源管理器，作为模组文件不可用时的退路
     * @param tableId 战利品表 id
     * @return 战利品表 JSON，读取失败时返回 {@code null}
     */
    @Nullable
    private static JsonObject readLootTable(
            @Nullable ResourceManager resourceManager, @Nullable ResourceLocation tableId) {
        if (tableId == null) {
            return null;
        }

        Map<ResourceLocation, JsonObject> cache = LOOT_TABLE_CACHE.get();
        if (cache.containsKey(tableId)) {
            return cache.get(tableId);
        }

        JsonObject resolved = readLootTableFromModFile(tableId);
        if (resolved == null) {
            resolved = readLootTableFromClassLoader(tableId);
        }
        if (resolved == null) {
            resolved = readLootTableFromResourceManager(resourceManager, tableId);
        }

        // 读取失败同样入缓存，避免同一缺失表在后续作物上重复尝试三种入口。
        cache.put(tableId, resolved);
        return resolved;
    }

    /**
     * 经类加载器读取战利品表。
     *
     * <p>模组文件容器不覆盖原版命名空间（{@code minecraft}），而原版战利品表位于客户端
     * 附加资源包内。类加载器可见该包的完整路径空间，因此这里作为模组文件之后的第二顺位。</p>
     *
     * @param tableId 战利品表 id
     * @return 战利品表 JSON，读取失败时返回 {@code null}
     */
    @Nullable
    private static JsonObject readLootTableFromClassLoader(ResourceLocation tableId) {
        String resourcePath = "data/" + tableId.getNamespace()
                + "/loot_table/" + tableId.getPath() + ".json";
        ClassLoader classLoader = BotanyPotsRecipeAdapter.class.getClassLoader();
        if (classLoader == null) {
            return null;
        }

        try (var stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return GsonHelper.parse(reader);
            }
        }
        catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * 经模组文件容器读取战利品表。
     *
     * <p>模组文件容器暴露 jar 内的完整路径空间，不区分 {@code assets} 与 {@code data}，
     * 因此战利品表在客户端侧同样可读——这消除了「服务端按完整掉落执行、客户端只显示
     * 退化掉落」的不一致。</p>
     *
     * @param tableId 战利品表 id，其命名空间即目标模组 id
     * @return 战利品表 JSON，模组文件不可用时返回 {@code null}
     */
    @Nullable
    private static JsonObject readLootTableFromModFile(ResourceLocation tableId) {
        try {
            IModFileInfo modFileInfo = ModList.get().getModFileById(tableId.getNamespace());
            if (modFileInfo == null) {
                return null;
            }
            Path path = modFileInfo.getFile().findResource(
                    "data", tableId.getNamespace(), "loot_table", tableId.getPath() + ".json");
            if (path == null || !Files.isRegularFile(path)) {
                return null;
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return GsonHelper.parse(reader);
            }
        }
        catch (IOException | RuntimeException exception) {
            // 模组文件布局异常时退回资源管理器，不使整条作物失效。
            return null;
        }
    }

    /**
     * 经资源管理器读取战利品表。
     *
     * @param resourceManager 资源管理器
     * @param tableId 战利品表 id
     * @return 战利品表 JSON，读取失败时返回 {@code null}
     */
    @Nullable
    private static JsonObject readLootTableFromResourceManager(
            @Nullable ResourceManager resourceManager, ResourceLocation tableId) {
        if (resourceManager == null) {
            return null;
        }

        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                tableId.getNamespace(), "loot_table/" + tableId.getPath() + ".json");
        Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            return null;
        }

        try (var reader = resource.get().openAsReader()) {
            return GsonHelper.parse(reader);
        }
        catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * 获取资源管理器，作为模组文件不可用时的退路。
     *
     * <p>服务端直接返回其资源管理器；单人客户端可读取集成服务器的数据资源；
     * 远程客户端无服务端实例，返回 {@code null}。该情况下战利品表仍由模组文件容器
     * 读取，因此本方法返回 {@code null} 不再导致掉落缺失。</p>
     *
     * @param level 当前等级
     * @return 资源管理器，不可用时返回 {@code null}
     */
    @Nullable
    private static ResourceManager resourceManager(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getResourceManager();
        }

        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object integratedServer = minecraftClass.getMethod("getSingleplayerServer")
                    .invoke(minecraft);
            if (integratedServer instanceof MinecraftServer minecraftServer) {
                return minecraftServer.getResourceManager();
            }
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
        }
        return null;
    }

    @Nullable
    private static Item registeredItem(@Nullable ResourceLocation id) {
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static boolean isZero(@Nullable JsonElement element) {
        return element == null || (element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                && element.getAsDouble() == 0.0D);
    }

    private static int integralValue(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE
                && Math.rint(value) == value ? (int) value : -1;
    }

    private static double numberValue(@Nullable JsonElement element, double fallback) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : fallback;
    }

    private static String stringValue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    /** 战利品表条目展开后的中间结果。 */
    private record EntryDrop(ItemStack stack, int min, int max, double chance, double weight) {
    }
}
