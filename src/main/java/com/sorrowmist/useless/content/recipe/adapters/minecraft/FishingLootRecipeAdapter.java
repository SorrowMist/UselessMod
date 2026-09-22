package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 把原版钓鱼战利品表（minecraft:gameplay/fishing）转换成确定性的合金炉配方。
 *
 * <p>原版战利品表使用权重随机、条件与函数，而合金炉配方必须是无随机、无世界状态的静态数据，
 * 因此这里只收集「普通物品条目」，忽略带条件或带函数的条目：</p>
 * <ul>
 *     <li>附魔书（{@code minecraft:enchanted_book} + {@code enchant_randomly}）被排除；</li>
 *     <li>装备（有耐久值的工具/武器/护甲、可穿戴物品）被排除；</li>
 *     <li>带 {@code conditions} 的条目被排除，保证客户端与服务端生成完全一致的配方集合。</li>
 * </ul>
 *
 * <p>整张表按顶层 pool 拆分：每个 pool 生成一个配方，产物为该 pool 全部可转换条目的合集，
 * 消耗水，并以钓鱼竿作为模具。配方 id 只由产物内容决定，不依赖集合迭代顺序。</p>
 */
public final class FishingLootRecipeAdapter implements IRecipeAdapter<FishingLootSyntheticRecipe> {
    /** 原版钓鱼战利品表：data/minecraft/loot_table/gameplay/fishing.json。 */
    private static final ResourceLocation FISHING_TABLE_ID =
            ResourceLocation.withDefaultNamespace("gameplay/fishing");
    private static final int WATER_PER_OUTPUT = 1_000;
    /** 战利品表可以引用子表，限制递归深度避免异常数据导致栈溢出。 */
    private static final int MAX_TABLE_DEPTH = 8;

    @Override
    public String sourceId() {
        return RecipeSourceIds.MINECRAFT_FISHING;
    }

    @Override
    public Class<FishingLootSyntheticRecipe> getRecipeClass() {
        return FishingLootSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.FISHING_ROD);
    }

    @Override
    public List<RecipeHolder<FishingLootSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        ResourceManager resourceManager = resourceManager(level);
        if (resourceManager == null) {
            // 远程客户端读不到战利品表资源，此时不生成任何配方，避免客户端与服务端不一致。
            return List.of();
        }

        // 物品标签在客户端由服务端标签包重绑，这里只用注册表本身，不依赖标签。
        Registry<Item> itemRegistry = BuiltInRegistries.ITEM;
        List<RecipeHolder<FishingLootSyntheticRecipe>> recipes = new ArrayList<>();
        Set<ResourceLocation> usedIds = new HashSet<>();

        // 原版自己的钓鱼表。
        generateFromTable(itemRegistry, resourceManager, FISHING_TABLE_ID, "vanilla_",
                recipes, usedIds);
        // 其他模组放在自己命名空间下的钓鱼表。它们多数通过运行时事件注入原版表，
        // 那种注入读不到，但这些表本身是数据包 JSON，可以静态读取。
        generateFromExternalTables(itemRegistry, resourceManager, recipes, usedIds);

        return List.copyOf(recipes);
    }

    /**
     * 读取一张钓鱼表，按子表分组后生成配方。
     *
     * @param idPrefix 配方 id 前缀，用于区分不同模组里同名的子表分组（例如都叫 treasure）
     */
    private static void generateFromTable(Registry<Item> itemRegistry,
                                          ResourceManager resourceManager,
                                          ResourceLocation tableId,
                                          String idPrefix,
                                          List<RecipeHolder<FishingLootSyntheticRecipe>> recipes,
                                          Set<ResourceLocation> usedIds) {
        JsonObject table = readLootTable(resourceManager, tableId);
        if (table == null) {
            return;
        }

        JsonArray pools = asArray(table.get("pools"));
        if (pools == null) {
            return;
        }

        for (JsonElement poolElement : pools) {
            JsonObject pool = poolElement != null && poolElement.isJsonObject()
                    ? poolElement.getAsJsonObject() : null;
            if (pool == null) {
                continue;
            }

            // 原版表用顶层 pool 包住 fish/junk/treasure 三个子表，必须按子表拆分，
            // 否则鱼类、杂物与宝藏会被合并成一个产物多达二十余种的配方。
            Map<String, List<ItemStack>> grouped = new LinkedHashMap<>();
            // 直接写在顶层 pool 里的物品归入 misc，子表引用会各自带上自己的分组名。
            collectGroupedOutputs(itemRegistry, resourceManager, pool.get("entries"),
                    grouped, 0, "misc");
            appendGroupedRecipes(grouped, idPrefix, recipes, usedIds);
        }
    }

    /** 把分组结果逐个转成配方并追加到结果列表。 */
    private static void appendGroupedRecipes(Map<String, List<ItemStack>> grouped,
                                             String idPrefix,
                                             List<RecipeHolder<FishingLootSyntheticRecipe>> recipes,
                                             Set<ResourceLocation> usedIds) {
        for (Map.Entry<String, List<ItemStack>> group : grouped.entrySet()) {
            List<ItemStack> outputs = group.getValue();
            if (outputs.isEmpty()) {
                continue;
            }

            ResourceLocation recipeId = nextRecipeId(idPrefix + group.getKey(), outputs, usedIds);
            if (recipeId == null) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertOutputs(recipeId, outputs);
            if (converted != null) {
                recipes.add(new RecipeHolder<>(converted.id(),
                        new FishingLootSyntheticRecipe(converted)));
            }
        }
    }

    /**
     * 读取其他模组放在自己命名空间下的钓鱼表。
     *
     * <p>不少模组通过 {@code LootTableLoadEvent} 在运行时把产物注入原版钓鱼表，那种注入只发生在
     * 逻辑服务端、也没有对应的 JSON，静态读取永远看不到。但这些模组通常会把表本身以数据包 JSON
     * 的形式发布（例如 {@code aquaculture:gameplay/fishing/fish}），这部分是可以静态读取的，
     * 因此这里把它们一并纳入转换。</p>
     *
     * <p>只取「根表」：被同一命名空间下其他钓鱼表引用过的表属于子表，根表递归时已经读到，
     * 再单独生成一次只会得到重复配方。</p>
     */
    private static void generateFromExternalTables(Registry<Item> itemRegistry,
                                                   ResourceManager resourceManager,
                                                   List<RecipeHolder<FishingLootSyntheticRecipe>> recipes,
                                                   Set<ResourceLocation> usedIds) {
        Map<ResourceLocation, JsonObject> candidates = new LinkedHashMap<>();
        try {
            Map<ResourceLocation, Resource> found = resourceManager.listResources(
                    "loot_table/gameplay/fishing",
                    id -> !id.getNamespace().equals("minecraft")
                            && id.getPath().endsWith(".json"));
            for (ResourceLocation resourceId : found.keySet()) {
                ResourceLocation tableId = toTableId(resourceId);
                if (tableId == null) {
                    continue;
                }
                JsonObject table = readLootTable(resourceManager, tableId);
                if (table != null) {
                    candidates.put(tableId, table);
                }
            }
        } catch (RuntimeException ignored) {
            return;
        }

        if (candidates.isEmpty()) {
            return;
        }

        // 先扫出所有被引用的子表，剩下的才是根表。
        Set<ResourceLocation> referenced = new HashSet<>();
        for (JsonObject table : candidates.values()) {
            collectReferencedTables(table, referenced);
        }

        for (ResourceLocation tableId : candidates.keySet()) {
            if (referenced.contains(tableId)) {
                continue;
            }
            generateFromTable(itemRegistry, resourceManager, tableId,
                    tableId.getNamespace() + "_", recipes, usedIds);
        }
    }

    /** 把 {@code aquaculture:loot_table/gameplay/fishing/fish.json} 还原成表 id。 */
    @Nullable
    private static ResourceLocation toTableId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = "loot_table/";
        String suffix = ".json";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        return ResourceLocation.tryParse(resourceId.getNamespace() + ":"
                + path.substring(prefix.length(), path.length() - suffix.length()));
    }

    /** 递归收集一张表里引用的全部子表 id（含嵌套在条目、函数里的引用）。 */
    private static void collectReferencedTables(@Nullable JsonElement element,
                                                Set<ResourceLocation> referenced) {
        if (element == null) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectReferencedTables(child, referenced);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if ("minecraft:loot_table".equals(stringValue(object.get("type")))) {
            ResourceLocation childId = ResourceLocation.tryParse(
                    firstNonBlank(stringValue(object.get("value")),
                            stringValue(object.get("name"))));
            if (childId != null) {
                referenced.add(childId);
            }
        }
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            collectReferencedTables(field.getValue(), referenced);
        }
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<FishingLootSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<FishingLootSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<FishingLootSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<FishingLootSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<FishingLootSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe == null || !recipe.keyInputs().isEmpty()) {
                continue;
            }

            // 钓鱼转换配方没有物品输入，只消耗水；若机器里放了物品则不匹配。
            if (recipe.inputs().isEmpty() && hasConcreteInputs(actualInputs)) {
                continue;
            }
            if (recipe.inputs().isEmpty()
                    && mergedInputs != null && !mergedInputs.isEmpty()) {
                continue;
            }

            boolean itemsMatch = recipe.inputs().isEmpty()
                    || ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L);
            boolean fluidsMatch = FluidIngredientAllocator
                    .matchesLong(recipe.inputFluids(), mergedFluids == null ? Map.of() : mergedFluids, 1L);
            if (itemsMatch && fluidsMatch) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /**
     * 递归收集战利品表条目中的可转换产物，并按「来源子表」分组。
     *
     * <p>原版钓鱼表把鱼类（fish）、杂物（junk）、宝藏（treasure）拆成三张子表，
     * 分组键取子表路径的最后一段，这样每个类别各生成一个配方。若条目直接写在顶层，
     * 则归入传入的 {@code groupKey}。</p>
     */
    private static void collectGroupedOutputs(Registry<Item> itemRegistry,
                                              ResourceManager resourceManager,
                                              @Nullable JsonElement entriesElement,
                                              Map<String, List<ItemStack>> grouped,
                                              int depth, String groupKey) {
        if (depth > MAX_TABLE_DEPTH) {
            return;
        }
        JsonArray entries = asArray(entriesElement);
        if (entries == null) {
            return;
        }

        for (JsonElement entryElement : entries) {
            JsonObject entry = entryElement != null && entryElement.isJsonObject()
                    ? entryElement.getAsJsonObject() : null;
            if (entry == null) {
                continue;
            }

            String type = stringValue(entry.get("type"));
            switch (type) {
                case "minecraft:empty" -> {
                    // 空条目表示该次抽取没有产物，跳过。
                }
                case "minecraft:item" -> {
                    // 条目上的 conditions 只决定「这次能不能抽到它」（例如原版宝藏要求开阔水域、
                    // 其他模组要求特定生物群系），不影响产物本身是什么，因此忽略以保证配方的
                    // 产物集合完整。合金炉配方本身无随机、无世界状态，忽略这些门槛不会引入不确定性。
                    //
                    // functions 则不同：它会改变产物内容（附魔、药水、随机数量），
                    // 只有「固定数量」能安全转换，其余一律跳过。
                    Integer fixedCount = fixedCountOrNull(entry.get("functions"));
                    if (fixedCount == null) {
                        continue;
                    }
                    ResourceLocation itemId = ResourceLocation.tryParse(stringValue(entry.get("name")));
                    Item item = itemId == null ? null : itemRegistry.getOptional(itemId).orElse(null);
                    if (item == null || item == Items.AIR) {
                        continue;
                    }
                    ItemStack stack = item.getDefaultInstance();
                    if (isEquipment(stack)) {
                        continue;
                    }
                    if (fixedCount > 1) {
                        stack.setCount(Math.min(fixedCount, stack.getMaxStackSize()));
                    }
                    grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(stack);
                }
                case "minecraft:loot_table" -> {
                    // 原版钓鱼表用子表拆分鱼类/杂物/宝藏，递归读取子表的 pools。
                    // 子表引用上的 conditions 只决定「是否抽取该子表」——例如原版宝藏要求
                    // 必须在开阔水域——它不影响子表内产物的确定性，因此这里忽略条件继续递归，
                    // 否则原版宝藏会被整个丢掉。
                    ResourceLocation childId = ResourceLocation.tryParse(
                            firstNonBlank(stringValue(entry.get("value")),
                                    stringValue(entry.get("name"))));
                    if (childId == null) {
                        continue;
                    }
                    JsonObject childTable = readLootTable(resourceManager, childId);
                    if (childTable == null || !hasNoEntries(childTable, "conditions")) {
                        continue;
                    }
                    JsonArray childPools = asArray(childTable.get("pools"));
                    if (childPools == null) {
                        continue;
                    }
                    String childGroup = groupName(childId);
                    for (JsonElement childPoolElement : childPools) {
                        JsonObject childPool = childPoolElement != null && childPoolElement.isJsonObject()
                                ? childPoolElement.getAsJsonObject() : null;
                        if (childPool == null || !hasNoEntries(childPool, "conditions")) {
                            continue;
                        }
                        collectGroupedOutputs(itemRegistry, resourceManager, childPool.get("entries"),
                                grouped, depth + 1, childGroup);
                    }
                }
                default -> {
                    // 标签、序列化实体等条目无法静态确定产物，跳过。
                }
            }
        }
    }

    /** 取子表路径的最后一段作为分组名，例如 gameplay/fishing/fish -> fish。 */
    private static String groupName(ResourceLocation tableId) {
        String path = tableId.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? "fishing" : name;
    }

    /** 判断物品是否属于「装备」，钓鱼兼容明确不转换这些产物。 */
    private static boolean isEquipment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        // 附魔书虽然不可损坏，但属于明确排除项。
        if (item == Items.ENCHANTED_BOOK) {
            return true;
        }
        // 有耐久值的物品覆盖工具、武器、护甲、钓鱼竿、盾牌、剪刀等全部装备。
        if (stack.isDamageableItem()) {
            return true;
        }
        return item instanceof Equipable;
    }

    /**
     * 从条目的 {@code functions} 里取出「固定产物数量」，无法确定时返回 null。
     *
     * <p>函数会改变产物内容：附魔、药水、随机数量、写入容器内容等都无法映射成合金炉的
     * 确定性产物，只要出现这类函数就整条跳过。唯一可以安全转换的是固定数量的
     * {@code minecraft:set_count}（常量形式），它只是把产物数量从 1 改成 N。</p>
     *
     * @return 产物数量；无函数时为 1；含无法转换的函数或数量非正时返回 null
     */
    @Nullable
    private static Integer fixedCountOrNull(@Nullable JsonElement functionsElement) {
        JsonArray functions = asArray(functionsElement);
        if (functions == null) {
            // 没有 functions，产物就是默认的 1 个。
            return 1;
        }

        int count = 1;
        for (JsonElement functionElement : functions) {
            JsonObject function = functionElement != null && functionElement.isJsonObject()
                    ? functionElement.getAsJsonObject() : null;
            if (function == null) {
                continue;
            }
            String name = stringValue(function.get("function"));
            if (!"minecraft:set_count".equals(name)) {
                // 附魔、药水、耐久、随机数量等函数都会改变产物内容，无法静态确定。
                return null;
            }
            Integer fixed = constantCountOrNull(function.get("count"));
            if (fixed == null) {
                // 均匀分布等随机数量同样无法确定。
                return null;
            }
            count = fixed;
        }
        return count > 0 ? count : null;
    }

    /** 读取 {@code set_count} 的数量字段，只接受常量形式。 */
    @Nullable
    private static Integer constantCountOrNull(@Nullable JsonElement countElement) {
        if (countElement == null || countElement.isJsonNull()) {
            return null;
        }
        if (countElement.isJsonPrimitive() && countElement.getAsJsonPrimitive().isNumber()) {
            return countElement.getAsInt();
        }
        if (!countElement.isJsonObject()) {
            return null;
        }
        JsonObject count = countElement.getAsJsonObject();
        // 形如 {"type":"minecraft:constant","value":10}，只有 constant 是确定的。
        if (!"minecraft:constant".equals(stringValue(count.get("type")))) {
            return null;
        }
        JsonElement value = count.get("value");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return value.getAsInt();
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertOutputs(ResourceLocation recipeId,
                                                             List<ItemStack> outputs) {
        List<ItemStack> normalizedOutputs = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (ItemStack output : outputs) {
            if (output == null || output.isEmpty() || output.getCount() <= 0) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(output.getItem());
            if (id == null || !seen.add(id)) {
                continue;
            }
            normalizedOutputs.add(output.copy());
        }
        if (normalizedOutputs.isEmpty()) {
            return null;
        }

        int operations = normalizedOutputs.size();
        long waterAmount = saturatingMultiply(operations, WATER_PER_OUTPUT);
        long energy = saturatingMultiply(AdapterUtils.DEFAULT_ENERGY, operations);
        int processTime = AdapterUtils.safeInt(
                saturatingMultiply(AdapterUtils.DEFAULT_PROCESS_TIME, operations));

        return new AdvancedAlloyFurnaceRecipe(
                recipeId,
                List.of(),
                List.of(new LongSizedFluidIngredient(FluidIngredient.single(Fluids.WATER), waterAmount)),
                List.of(),
                normalizedOutputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(Items.FISHING_ROD)),
                AlloyFurnaceMode.NORMAL);
    }

    /**
     * 生成与内容绑定的稳定配方 id。
     *
     * <p>id 取自排序后首个产物的注册名，绝不使用集合下标或身份哈希，否则万象样板会在重建索引后失效。</p>
     */
    @Nullable
    private static ResourceLocation nextRecipeId(String groupKey, List<ItemStack> outputs,
                                                 Set<ResourceLocation> usedIds) {
        ResourceLocation anchor = null;
        for (ItemStack output : outputs) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(output.getItem());
            if (id == null) {
                continue;
            }
            if (anchor == null || id.compareTo(anchor) < 0) {
                anchor = id;
            }
        }
        if (anchor == null) {
            return null;
        }

        String basePath = "fishing_" + sanitize(groupKey) + "_"
                + anchor.getNamespace() + "_" + anchor.getPath().replace('/', '_');
        ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(
                RecipeSourceIds.MINECRAFT_FISHING, basePath + "_converted");
        int suffix = 2;
        while (!usedIds.add(candidate)) {
            candidate = ResourceLocation.fromNamespaceAndPath(
                    RecipeSourceIds.MINECRAFT_FISHING, basePath + "_converted_" + suffix);
            suffix++;
        }
        return candidate;
    }

    /** 把分组名净化为合法的资源路径片段。 */
    private static String sanitize(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return "fishing";
        }
        StringBuilder builder = new StringBuilder(groupKey.length());
        for (char c : groupKey.toCharArray()) {
            // 只保留资源路径允许的字符，其余一律替换为下划线。
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/';
            builder.append(allowed ? c : '_');
        }
        return builder.toString();
    }

    @Nullable
    private static JsonObject readLootTable(ResourceManager resourceManager,
                                            ResourceLocation tableId) {
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                tableId.getNamespace(), "loot_table/" + tableId.getPath() + ".json");
        Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            return null;
        }

        try (var reader = resource.get().openAsReader()) {
            return GsonHelper.parse(reader);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceManager resourceManager(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getResourceManager();
        }

        // 单机客户端可以读取集成服务端的数据资源；远程客户端读不到，因此直接放弃生成。
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object integratedServer = minecraftClass.getMethod("getSingleplayerServer")
                    .invoke(minecraft);
            if (integratedServer instanceof MinecraftServer minecraftServer) {
                return minecraftServer.getResourceManager();
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
        }
        return null;
    }

    @Nullable
    private static JsonArray asArray(@Nullable JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static boolean hasNoEntries(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || (value.isJsonArray() && value.getAsJsonArray().isEmpty());
    }

    private static String stringValue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static boolean hasConcreteInputs(@Nullable List<ItemStack> inputs) {
        if (inputs == null) {
            return false;
        }
        return inputs.stream().anyMatch(stack -> stack != null && !stack.isEmpty()
                && stack.getCount() > 0);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    /** 保留给未来扩展：把合并输入映射成需求映射时的工具方法。 */
    static Map<Ingredient, Long> mergeRequirements(List<CountedIngredient> requirements) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient requirement : requirements) {
            AdapterUtils.mergeIngredient(required, requirement.ingredient(), requirement.count());
        }
        return required;
    }
}