package com.sorrowmist.useless.content.recipe.adapters.delight.crabbersdelight;

import alabaster.crabbersdelight.common.item.ChumItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Converts Crabbers Delight's data-driven crab-trap loot into deterministic alloy-furnace batches.
 *
 * <p>Native behaviour (crabbersdelight-1.20.1-1.2.3,
 * {@code CrabTrapBlockEntity#serverTick} and
 * {@code CrabTrapItemHandler#addItemsAndShrinkBait}):</p>
 * <ul>
 *     <li>The trap block must be waterlogged and the 3x3 layer around it flooded
 *     ({@code Config.REQUIRE_SURROUNDING_WATER}, default {@code true}). The alloy furnace
 *     represents that world requirement as a water input and the trap item as a non-consumed
 *     mold; the machine cannot check the placement of a block.</li>
 *     <li>Every catch resolves the loot table
 *     {@code crabbersdelight:gameplay/crab_trap_loot/<bait namespace>/<bait path>}, built from the
 *     registry name of the bait stack, and calls it with luck 0. The shipped tables use
 *     {@code rolls=1} plus weighted entries, so exactly one weighted entry is picked per
 *     catch - the table is an <em>alternative</em> list, never a list of guaranteed products.</li>
 *     <li>Non-chum bait loses one item per produced drop stack, chum buckets lose one durability
 *     point per produced drop stack and turn into an empty bucket after 48 uses
 *     ({@code ChumItem} is registered with {@code Properties.durability(48)}).</li>
 *     <li>An empty bait slot behaves like {@code minecraft:air} because that item is part of
 *     {@code crabbersdelight:crab_trap_bait}; a bait whose table does not exist resolves to
 *     {@code LootTable.EMPTY}, which produces nothing and consumes no bait.</li>
 * </ul>
 *
 * <p>The alloy furnace is deterministic, so each bait becomes one batch recipe whose size is the
 * smallest number of catches that turns every weighted probability into a whole item count (for
 * example 20 catches for the air table's 1/1/1/1/4/4/4/4 weights). The same batch also pays the
 * bait cost reported by the native handler, and chum batches are rounded up to a whole number of
 * 48-use buckets.</p>
 *
 * <p>The {@code jei_display_results} item tags are deliberately not used: they are a display hint
 * that is stale for several baits (pufferfish lists shrimp while its loot table yields clam, and
 * tropical fish the other way round), so converting them produced recipes for items the trap never
 * yields. Tables that need world conditions, unresolved entry types or functions that change the
 * product are refused instead of being approximated.</p>
 */
public final class CrabTrapRecipeAdapter implements IRecipeAdapter<CrabTrapSyntheticRecipe> {
    static final String MOD_ID = "crabbersdelight";
    static final ResourceLocation CRAB_TRAP_ID = new ResourceLocation(MOD_ID, "crab_trap");
    static final ResourceLocation CRAB_TRAP_BAIT_TAG =
            new ResourceLocation(MOD_ID, "crab_trap_bait");
    static final ResourceLocation CREATURE_CHUMS_TAG =
            new ResourceLocation(MOD_ID, "creature_chums");

    /**
     * Native table id {@code crabbersdelight:gameplay/crab_trap_loot/<namespace>/<path>}: the file
     * lives under {@code data/crabbersdelight/loot_tables/gameplay/crab_trap_loot/<namespace>/},
     * so the bait namespace is part of the path.
     */
    private static final String LOOT_TABLE_PREFIX = "gameplay/crab_trap_loot/";
    /** 1.20.1 data packs store tables under {@code loot_tables}; 1.21 renamed it to {@code loot_table}. */
    private static final String LOOT_TABLE_DIRECTORY = "loot_tables/";

    static final long WATER_PER_OPERATION = 1_000L;
    /** {@code ChumItem} durability: one chum bucket covers 48 produced drops. */
    static final int CHUM_DURABILITY = 48;
    /** The recipe's tick count must remain exact in its native int field. */
    static final int MAX_BATCH_OPERATIONS = Integer.MAX_VALUE / AdapterUtils.DEFAULT_PROCESS_TIME;

    @Override
    public String sourceId() {
        return RecipeSourceIds.CRABBERS_DELIGHT;
    }

    @Override
    public Class<CrabTrapSyntheticRecipe> getRecipeClass() {
        return CrabTrapSyntheticRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        Item trap = registeredItem(BuiltInRegistries.ITEM, CRAB_TRAP_ID);
        return trap == null ? null : trap.getDefaultInstance();
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        Item trap = registeredItem(BuiltInRegistries.ITEM, CRAB_TRAP_ID);
        return trap != null && mold != null && !mold.isEmpty() && mold.is(trap);
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        ResourceManager resourceManager = resourceManager(level);
        if (resourceManager == null) {
            // A remote client cannot read loot-table JSON. Emitting nothing is the only honest
            // answer: the synced JEI display tags are stale and would invent products.
            return List.of();
        }

        // Item tags are rebound on BuiltInRegistries.ITEM on the client when the server sends its
        // tag packet. ClientLevel.registryAccess() may still expose the pre-sync view here, so the
        // bait list is read from the item registry that the trap itself uses.
        Registry<Item> itemRegistry = BuiltInRegistries.ITEM;

        List<RecipeHolder<CrabTrapSyntheticRecipe>> recipes = new ArrayList<>();
        for (Item bait : baitItems(itemRegistry)) {
            ResourceLocation baitId = itemRegistry.getKey(bait);
            if (baitId == null || baitId.getPath().isBlank()) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted =
                    convertBait(itemRegistry, resourceManager, bait, baitId);
            if (converted != null) {
                recipes.add(new RecipeHolder<>(converted.id(),
                        new CrabTrapSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CrabTrapSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<CrabTrapSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrabTrapSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value() == null
                    ? null : holder.value().convertedRecipe();
            if (recipe == null || !recipe.keyInputs().isEmpty()) {
                continue;
            }
            // The baited recipes consume bait, the air recipe needs an otherwise empty machine,
            // and every recipe consumes water.
            if (DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, actualInputs)
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** Reads one bait's table and converts it, or returns {@code null} when it cannot be expressed. */
    @Nullable
    static AdvancedAlloyFurnaceRecipe convertBait(Registry<Item> itemRegistry,
                                                  ResourceManager resourceManager,
                                                  Item bait, ResourceLocation baitId) {
        JsonObject table = readLootTable(resourceManager, lootTableId(baitId));
        if (table == null) {
            // The native trap resolves a missing table to LootTable.EMPTY: no drop and no bait
            // loss. Inventing a recipe here would fabricate products.
            return null;
        }
        return convertLootTable(itemRegistry, bait, baitId,
                isCreatureChum(itemRegistry, bait), table);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convertLootTable(
            Registry<Item> itemRegistry, Item bait, ResourceLocation baitId, JsonObject table) {
        return convertLootTable(itemRegistry, bait, baitId, false, table);
    }

    /**
     * Converts one crab-trap loot table.
     *
     * @param chumBait whether the bait is a {@code crabbersdelight:creature_chums} entry, i.e. a
     *                 durability tool that only breaks after {@link #CHUM_DURABILITY} uses
     */
    @Nullable
    static AdvancedAlloyFurnaceRecipe convertLootTable(Registry<Item> itemRegistry, Item bait,
                                                       ResourceLocation baitId, boolean chumBait,
                                                       JsonObject table) {
        if (itemRegistry == null || bait == null || baitId == null || table == null
                || !isSupportedTable(table)) {
            return null;
        }

        if (chumBait && (bait.getClass() != ChumItem.class
                || new ItemStack(bait).getMaxDamage() != CHUM_DURABILITY)) {
            return null;
        }

        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        long dropNumerator = 0L;
        long dropDenominator = 1L;

        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            JsonObject pool = asObject(poolElement);
            if (pool == null || !hasNoEntries(pool, "conditions")
                    || !hasNoEntries(pool, "functions")
                    || !isZero(pool.get("bonus_rolls"))) {
                return null;
            }

            int rolls = fixedInteger(pool.get("rolls"), 1);
            if (rolls < 0) {
                return null;
            }
            if (rolls == 0) {
                continue;
            }

            JsonArray entries = asArray(pool.get("entries"));
            if (entries == null || entries.isEmpty()) {
                return null;
            }

            List<PoolEntry> poolEntries = new ArrayList<>(entries.size());
            long totalWeight = 0L;
            long itemWeight = 0L;
            for (JsonElement entryElement : entries) {
                JsonObject entry = asObject(entryElement);
                // Conditions select which entries may roll (biome, open water, ...). The machine has
                // no world state, so a conditional entry cannot be converted without changing the
                // odds.
                if (entry == null || !hasNoEntries(entry, "conditions")) {
                    return null;
                }
                int weight = fixedInteger(entry.get("weight"), 1);
                if (weight <= 0) {
                    return null;
                }

                String type = stringValue(entry.get("type"));
                ItemStack stack;
                if ("minecraft:empty".equals(type)) {
                    // A "nothing" outcome: it still takes weight away from the item entries.
                    stack = null;
                } else if ("minecraft:item".equals(type)) {
                    stack = itemStack(itemRegistry, entry);
                    if (stack == null) {
                        return null;
                    }
                    if (stack.isEmpty()) {
                        // "minecraft:air" produces an empty stack that the native handler skips.
                        stack = null;
                    }
                } else {
                    // Tags, nested tables, alternatives, dynamic entity drops: the product set
                    // depends on data that is not resolvable from this JSON.
                    return null;
                }

                poolEntries.add(new PoolEntry(weight, stack));
                totalWeight += weight;
                if (stack != null) {
                    itemWeight += weight;
                }
            }
            if (totalWeight <= 0L) {
                return null;
            }

            if (itemWeight > 0L) {
                // One entry is selected per roll, and the native handler shrinks the bait once per
                // produced drop stack - never per roll and never per produced item. So the bait
                // cost follows the item weight, not the roll count.
                long addNumerator;
                try {
                    addNumerator = Math.multiplyExact(itemWeight, (long) rolls);
                } catch (ArithmeticException overflow) {
                    return null;
                }
                Fraction next = addFraction(dropNumerator, dropDenominator,
                        addNumerator, totalWeight);
                if (next == null) {
                    return null;
                }
                dropNumerator = next.numerator();
                dropDenominator = next.denominator();
            }

            for (PoolEntry entry : poolEntries) {
                if (entry.stack() == null) {
                    continue;
                }
                // The shared scaler represents exact probability fractions up to denominator
                // 1000. Do not silently approximate a data pack's larger weighted denominator.
                if (totalWeight / AdapterUtils.gcd(entry.weight(), totalWeight) > 1000L) {
                    return null;
                }
                OptionalInt stackCount = ExpectedOutputScaler.multiplyToInt(
                        entry.stack().getCount(), rolls);
                if (stackCount.isEmpty() || stackCount.getAsInt() <= 0) {
                    return null;
                }
                // Every roll of this pool independently multiplies the stack by `rolls`; the scaler
                // averages min/max, so a constant range reproduces the expectations exactly.
                int count = stackCount.getAsInt();
                weighted.add(new ExpectedOutputScaler.WeightedItemOutput(
                        entry.stack(), count, count,
                        (double) entry.weight() / (double) totalWeight));
            }
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaledOptional =
                ExpectedOutputScaler.scale(weighted);
        if (scaledOptional.isEmpty()) {
            return null;
        }
        ExpectedOutputScaler.ScaledOutputs scaled = scaledOptional.get();
        if (scaled.outputs().isEmpty() || dropNumerator <= 0L) {
            return null;
        }

        // Smallest batch that turns every probability into a whole item count.
        long rawOperations = scaled.operations();
        if (rawOperations <= 0L) {
            return null;
        }

        // Bait is consumed per produced drop stack, which can be fractional per catch when a pool
        // contains "nothing" outcomes; grow the batch until that cost is an exact whole number.
        long scaledUses;
        try {
            scaledUses = Math.multiplyExact(rawOperations, dropNumerator);
        } catch (ArithmeticException overflow) {
            return null;
        }
        long divisor = AdapterUtils.gcd(dropDenominator, scaledUses);
        if (divisor <= 0L) {
            return null;
        }
        long operations;
        try {
            operations = Math.multiplyExact(rawOperations, dropDenominator / divisor);
        } catch (ArithmeticException overflow) {
            return null;
        }
        long uses;
        try {
            long totalDrops = Math.multiplyExact(operations, dropNumerator);
            if (totalDrops % dropDenominator != 0L) {
                return null;
            }
            uses = totalDrops / dropDenominator;
        } catch (ArithmeticException overflow) {
            return null;
        }
        if (uses <= 0L) {
            return null;
        }

        long baitCount;
        if (chumBait) {
            // One chum bucket survives CHUM_DURABILITY drops, so the batch has to be a whole number
            // of buckets; otherwise a partially worn bucket would buy a full bucket's worth.
            long bucketFactor = CHUM_DURABILITY / AdapterUtils.gcd(uses, CHUM_DURABILITY);
            try {
                operations = Math.multiplyExact(operations, bucketFactor);
                uses = Math.multiplyExact(uses, bucketFactor);
            } catch (ArithmeticException overflow) {
                return null;
            }
            baitCount = uses / CHUM_DURABILITY;
        } else {
            baitCount = uses;
        }

        if (operations <= 0L || operations > MAX_BATCH_OPERATIONS || baitCount <= 0L) {
            return null;
        }
        long itemFactor = operations / rawOperations;
        if (itemFactor <= 0L || itemFactor > Integer.MAX_VALUE) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>(scaled.outputs().size() + 1);
        for (ItemStack output : scaled.outputs()) {
            OptionalInt count = ExpectedOutputScaler.multiplyToInt(
                    output.getCount(), (int) itemFactor);
            if (count.isEmpty() || count.getAsInt() <= 0) {
                return null;
            }
            ItemStack stack = output.copy();
            stack.setCount(count.getAsInt());
            outputs.add(stack);
        }
        if (chumBait) {
            // CrabTrapItemHandler#addItemsAndShrinkBait inserts Items.BUCKET after the 48th use.
            outputs.add(new ItemStack(Items.BUCKET, (int) baitCount));
        }

        Item trap = registeredItem(itemRegistry, CRAB_TRAP_ID);
        if (trap == null) {
            return null;
        }

        List<CountedIngredient> inputs = bait == Items.AIR
                ? List.of()
                : List.of(new CountedIngredient(baitIngredient(bait, chumBait), baitCount));
        return new AdvancedAlloyFurnaceRecipe(
                recipeId(baitId),
                inputs,
                List.of(new LongSizedFluidIngredient(FluidIngredient.single(Fluids.WATER),
                        WATER_PER_OPERATION * operations)),
                List.of(),
                List.copyOf(outputs),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY * operations,
                AdapterUtils.safeInt((long) AdapterUtils.DEFAULT_PROCESS_TIME * operations),
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(trap)),
                AlloyFurnaceMode.NORMAL);
    }

    private static Ingredient baitIngredient(Item bait, boolean chumBait) {
        // A chum bucket wears out instead of being consumed. Only an unused bucket may pay for a
        // full 48-catch batch; a nearly broken bucket must not buy the same amount.
        return chumBait
                ? DelightRecipeAdapterUtils.exactStack(new ItemStack(bait))
                : Ingredient.of(bait);
    }

    private static List<Item> baitItems(Registry<Item> itemRegistry) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, CRAB_TRAP_BAIT_TAG);
        // A tag may list the same item twice (directly and through a nested tag).
        LinkedHashSet<Item> items = new LinkedHashSet<>();
        // The source tag contains AIR for the trap's no-bait mode. Keep it so that mode is
        // represented by a water-only converted recipe.
        for (Holder<Item> holder : itemRegistry.getTagOrEmpty(tag)) {
            Item item = holder.value();
            if (item != null) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static boolean isCreatureChum(Registry<Item> itemRegistry, Item bait) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, CREATURE_CHUMS_TAG);
        for (Holder<Item> holder : itemRegistry.getTagOrEmpty(tag)) {
            if (holder.value() == bait) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves one {@code minecraft:item} entry, applying only functions that keep the product
     * deterministic.
     *
     * @return the stack, or {@code null} when the item is unknown or a function would change the
     *         product in a way a static recipe cannot reproduce
     */
    @Nullable
    private static ItemStack itemStack(Registry<Item> itemRegistry, JsonObject entry) {
        ResourceLocation itemId = ResourceLocation.tryParse(stringValue(entry.get("name")));
        Item item = itemId == null ? null : registeredItem(itemRegistry, itemId);
        if (item == null) {
            return null;
        }

        int count = 1;
        CompoundTag tag = null;

        JsonArray functions = asArray(entry.get("functions"));
        if (functions != null) {
            for (JsonElement functionElement : functions) {
                JsonObject function = asObject(functionElement);
                if (function == null || !hasNoEntries(function, "conditions")) {
                    return null;
                }
                String name = stringValue(function.get("function"));
                if ("minecraft:set_count".equals(name)) {
                    Integer fixed = constantCount(function.get("count"));
                    if (fixed == null) {
                        return null;
                    }
                    try {
                        // add=true increments the existing count (one) instead of replacing it.
                        count = booleanValue(function.get("add"))
                                ? Math.addExact(count, fixed) : fixed;
                    } catch (ArithmeticException overflow) {
                        return null;
                    }
                } else if ("minecraft:set_nbt".equals(name)) {
                    String snbt = stringValue(function.get("tag"));
                    try {
                        CompoundTag parsed = TagParser.parseTag(snbt);
                        if (tag == null) {
                            tag = parsed;
                        } else {
                            tag.merge(parsed);
                        }
                    } catch (CommandSyntaxException | RuntimeException exception) {
                        return null;
                    }
                } else {
                    // enchant_randomly, set_damage, set_name, set_potion, ... all change the
                    // product; a static recipe would lose them.
                    return null;
                }
            }
        }

        ItemStack stack = new ItemStack(item, Math.max(0, count));
        if (tag != null && !tag.isEmpty()) {
            stack.setTag(tag);
        }
        // LootTable splits oversized products into several stacks. The trap spends bait per
        // inserted stack, not per item entry; that path cannot use the one-stack cost above.
        return stack.getCount() > stack.getMaxStackSize() ? null : stack;
    }

    /** Reads {@code set_count.count}; only constant values are deterministic. */
    @Nullable
    private static Integer constantCount(@Nullable JsonElement countElement) {
        if (countElement == null || countElement.isJsonNull()) {
            return null;
        }
        if (countElement.isJsonPrimitive() && countElement.getAsJsonPrimitive().isNumber()) {
            int fixed = fixedInteger(countElement, -1);
            return fixed < 0 ? null : fixed;
        }
        JsonObject count = asObject(countElement);
        if (count == null || !"minecraft:constant".equals(stringValue(count.get("type")))) {
            return null;
        }
        int fixed = fixedInteger(count.get("value"), -1);
        return fixed < 0 ? null : fixed;
    }

    @Nullable
    private static Item registeredItem(Registry<Item> itemRegistry, ResourceLocation id) {
        return itemRegistry.getOptional(id).orElse(null);
    }

    @Nullable
    private static ResourceManager resourceManager(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getResourceManager();
        }

        // A remote client has no server data resources. Keep client-only classes behind a
        // separate holder, and use mapped calls so reobfuscation preserves the integrated path.
        return net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT
                ? ClientResources.resourceManager() : null;
    }

    private static final class ClientResources {
        @Nullable static ResourceManager resourceManager() {
            MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
            return server == null ? null : server.getResourceManager();
        }
    }

    private static ResourceLocation lootTableId(ResourceLocation baitId) {
        return new ResourceLocation(MOD_ID,
                LOOT_TABLE_PREFIX + baitId.getNamespace() + "/" + baitId.getPath());
    }

    @Nullable
    private static JsonObject readLootTable(ResourceManager resourceManager,
                                            ResourceLocation tableId) {
        ResourceLocation resourceId = new ResourceLocation(tableId.getNamespace(),
                LOOT_TABLE_DIRECTORY + tableId.getPath() + ".json");
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

    private static boolean isSupportedTable(JsonObject table) {
        return "minecraft:fishing".equals(stringValue(table.get("type")))
                && hasNoEntries(table, "functions")
                && table.has("pools")
                && table.get("pools").isJsonArray()
                && !table.getAsJsonArray("pools").isEmpty();
    }

    @Nullable
    private static JsonObject asObject(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray asArray(@Nullable JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static boolean hasNoEntries(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || (value.isJsonArray() && value.getAsJsonArray().isEmpty());
    }

    private static boolean isZero(@Nullable JsonElement element) {
        return element == null || (element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                && element.getAsDouble() == 0.0D);
    }

    private static boolean booleanValue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static int fixedInteger(@Nullable JsonElement element, int defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE
                && Math.rint(value) == value ? (int) value : -1;
    }

    private static String stringValue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    private static ResourceLocation recipeId(ResourceLocation baitId) {
        String path = baitId.getNamespace() + "_" + baitId.getPath().replace('/', '_');
        return new ResourceLocation(RecipeSourceIds.CRABBERS_DELIGHT,
                "crab_trap_" + path + "_converted");
    }

    /** Exact expected number of produced drop stacks per catch. */
    private static Fraction addFraction(long leftNumerator, long leftDenominator,
                                        long rightNumerator, long rightDenominator) {
        if (leftDenominator <= 0L || rightDenominator <= 0L) {
            return null;
        }
        try {
            long numerator = Math.addExact(Math.multiplyExact(leftNumerator, rightDenominator),
                    Math.multiplyExact(rightNumerator, leftDenominator));
            long denominator = Math.multiplyExact(leftDenominator, rightDenominator);
            long divisor = AdapterUtils.gcd(numerator, denominator);
            if (divisor <= 0L) {
                return null;
            }
            return new Fraction(numerator / divisor, denominator / divisor);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private record PoolEntry(int weight, @Nullable ItemStack stack) {
    }

    private record Fraction(long numerator, long denominator) {
    }
}
