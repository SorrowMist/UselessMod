package com.sorrowmist.useless.content.recipe.adapters.delight.crabbersdelight;

import alabaster.crabbersdelight.common.item.ChumItem;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Forge 1.20.1 regression tests for the Crabbers Delight crab-trap conversion.
 *
 * <p>The native trap is a block entity, not a recipe: it rolls
 * {@code crabbersdelight:gameplay/crab_trap_loot/<namespace>/<path>} with weighted alternatives and
 * consumes one bait item per produced drop stack. These tests pin the properties the converter has
 * to keep: weighted probabilities, the bait cost, chum-bucket durability and the refusal of
 * everything a static recipe cannot represent.</p>
 */
class CrabTrapRecipeAdapterTest {
    /** Created after {@code Bootstrap.bootStrap()}: {@code Registries} must not load earlier. */
    private static TagKey<Item> baitTag;
    private static Item trap;
    private static ChumItem chum;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        baitTag = TagKey.create(Registries.ITEM, CrabTrapRecipeAdapter.CRAB_TRAP_BAIT_TAG);
        trap = InventoryTestBootstrap.registerItem(CrabTrapRecipeAdapter.CRAB_TRAP_ID,
                () -> new Item(new Item.Properties()));
        chum = InventoryTestBootstrap.registerItem("crabbers_chum",
                () -> new ChumItem(new Item.Properties().durability(48)));
        // The real tag ships with the mod; tests bind a minimal one. Every tag the registry already
        // had is preserved so other test classes are unaffected.
        bindBaitTag(Items.AIR, Items.COD);
    }

    @Test
    void weightedAlternativesBecomeTheProbabilisticBatchThatTheNativeTrapRolls() {
        // 8/3/4 out of 15 is the shape of the shipped cod table: one of the three entries is picked
        // per catch, never all of them at once.
        AdvancedAlloyFurnaceRecipe recipe = convert(Items.COD, table(
                item("minecraft:cod", 8), item("minecraft:stick", 3), item("minecraft:kelp", 4)));

        assertNotNull(recipe);
        assertEquals(new ResourceLocation("crabbersdelight",
                "crab_trap_minecraft_cod_converted"), recipe.getId());
        // 15 catches is the smallest batch in which 8/15, 3/15 and 4/15 are whole item counts.
        assertEquals(15L, single(recipe).count());
        assertTrue(single(recipe).ingredient().test(new ItemStack(Items.COD)));
        assertFalse(single(recipe).ingredient().test(new ItemStack(Items.STICK)));
        assertEquals(8, count(recipe, Items.COD));
        assertEquals(3, count(recipe, Items.STICK));
        assertEquals(4, count(recipe, Items.KELP));
        assertEquals(15_000L, recipe.inputFluids().get(0).amount());
        assertEquals(2_000L * 15L, recipe.energy());
        assertEquals(20 * 15, recipe.processTime());
        assertEquals(1, recipe.molds().size());
        assertTrue(recipe.molds().get(0).test(new ItemStack(trap)));
    }

    @Test
    void airBaitUsesTheAirTableWithoutAnItemInput() {
        Level level = levelWithTables(Map.of(
                "crabbersdelight:loot_tables/gameplay/crab_trap_loot/minecraft/air.json",
                tableJson(item("minecraft:cod", 1), item("minecraft:kelp", 4))));

        List<RecipeHolder<CrabTrapSyntheticRecipe>> generated =
                new CrabTrapRecipeAdapter().getGeneratedRecipes(level);

        assertEquals(1, generated.size());
        AdvancedAlloyFurnaceRecipe air = generated.get(0).value().convertedRecipe();
        assertEquals(new ResourceLocation("crabbersdelight",
                "crab_trap_minecraft_air_converted"), air.getId());
        assertTrue(air.inputs().isEmpty());
        assertEquals(1, count(air, Items.COD));
        assertEquals(4, count(air, Items.KELP));
        assertEquals(5_000L, air.inputFluids().get(0).amount());
    }

    @Test
    void tableIdNeedsThe1201LootTableDirectoryAndTheBaitNamespace() {
        // The 1.21 directory spelling is not where 1.20.1 data packs live, so nothing is read.
        Level wrongDirectory = levelWithTables(Map.of(
                "crabbersdelight:loot_table/gameplay/crab_trap_loot/minecraft/cod.json",
                tableJson(item("minecraft:cod", 1))));
        assertTrue(new CrabTrapRecipeAdapter().getGeneratedRecipes(wrongDirectory).isEmpty());

        // The bait namespace is part of the path: .../crab_trap_loot/<namespace>/<path>.
        Level correct = levelWithTables(Map.of(
                "crabbersdelight:loot_tables/gameplay/crab_trap_loot/minecraft/cod.json",
                tableJson(item("minecraft:cod", 1))));
        List<RecipeHolder<CrabTrapSyntheticRecipe>> generated =
                new CrabTrapRecipeAdapter().getGeneratedRecipes(correct);
        assertEquals(1, generated.size(), "only cod has a loot table; air must not borrow it");
        assertTrue(generated.stream().anyMatch(holder -> holder.id().getPath()
                .equals("crab_trap_minecraft_cod_converted")));
    }

    @Test
    void lookupRequiresTheTrapMoldTheBaitAndTheWater() {
        Level level = levelWithTables(Map.of(
                "crabbersdelight:loot_tables/gameplay/crab_trap_loot/minecraft/air.json",
                tableJson(item("minecraft:cod", 1), item("minecraft:kelp", 4)),
                "crabbersdelight:loot_tables/gameplay/crab_trap_loot/minecraft/cod.json",
                tableJson(item("minecraft:cod", 8), item("minecraft:stick", 3))));
        CrabTrapRecipeAdapter adapter = new CrabTrapRecipeAdapter();
        Map<FluidStack, Long> water = Map.of(new FluidStack(Fluids.WATER, 11_000), 11_000L);
        ItemStack mold = new ItemStack(trap);

        List<RecipeHolder<CrabTrapSyntheticRecipe>> withBait = adapter.findMatchingRecipes(
                level, Map.of(Ingredient.of(Items.COD), 11L), water, Map.of(),
                mold, List.of(new ItemStack(Items.COD, 11)));
        assertEquals(1, withBait.size());
        assertEquals("crab_trap_minecraft_cod_converted", withBait.get(0).id().getPath());

        // The air recipe needs an empty machine, so a stray item must not match it.
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), water, Map.of(), mold,
                List.of(new ItemStack(Items.DIRT))).isEmpty());

        // With no items at all only the air recipe can run.
        List<RecipeHolder<CrabTrapSyntheticRecipe>> airOnly = adapter.findMatchingRecipes(
                level, Map.of(), water, Map.of(), mold, List.of());
        assertEquals(1, airOnly.size());
        assertEquals("crab_trap_minecraft_air_converted", airOnly.get(0).id().getPath());

        // The wrong mold or too little water never matches.
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), water, Map.of(),
                new ItemStack(Items.STONE), List.of()).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(),
                mold, List.of()).isEmpty());
    }

    @Test
    void chumBaitPaysOneWholeBucketPerFortyEightDropsAndReturnsTheEmptyBucket() {
        AdvancedAlloyFurnaceRecipe recipe = convert(chum,
                BuiltInRegistries.ITEM.getKey(chum), true, table(item("minecraft:cod", 1)));

        assertNotNull(recipe);
        assertEquals(1L, single(recipe).count());
        // ChumItem is registered with Properties.durability(48); native code inserts Items.BUCKET
        // into the bait slot after the last use.
        assertEquals(48, count(recipe, Items.COD));
        assertEquals(1, count(recipe, Items.BUCKET));
        assertEquals(48_000L, recipe.inputFluids().get(0).amount());
        assertTrue(single(recipe).ingredient().test(new ItemStack(chum)));
        ItemStack worn = new ItemStack(chum);
        worn.setDamageValue(47);
        assertFalse(single(recipe).ingredient().test(worn),
                "a nearly broken chum bucket must not buy a full 48-catch batch");
    }

    @Test
    void nothingOutcomesKeepTheirWeightAndOnlyProducedDropsConsumeBait() {
        AdvancedAlloyFurnaceRecipe recipe = convert(Items.COD, table(
                item("minecraft:cod", 3), "{\"type\":\"minecraft:empty\",\"weight\":1}"));

        assertNotNull(recipe);
        // 4 catches: 3 produce one cod, 1 produces nothing, so only 3 bait items are spent.
        assertEquals(3, count(recipe, Items.COD));
        assertEquals(3L, single(recipe).count());
    }

    @Test
    void constantSetCountAndSetNbtAreKeptOnTheProduct() {
        AdvancedAlloyFurnaceRecipe recipe = convert(Items.COD, table(
                "{\"type\":\"minecraft:item\",\"weight\":1,\"name\":\"minecraft:cod\","
                        + "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":2},"
                        + "{\"function\":\"minecraft:set_nbt\","
                        + "\"tag\":\"{marker:\\\"preserved\\\"}\"}]}"));

        assertNotNull(recipe);
        ItemStack output = recipe.outputs().get(0);
        assertEquals(2, output.getCount());
        assertNotNull(output.getTag());
        assertEquals("preserved", output.getTag().getString("marker"));
    }

    @Test
    void jeiDisplayAlternativesAreNeverInventedWhenTheTableIsMissing() {
        // The shipped jei_display_results tags are stale (pufferfish lists shrimp while its loot
        // table yields clam), so a missing table has to produce no recipe at all.
        Level empty = levelWithTables(Map.of());
        assertTrue(new CrabTrapRecipeAdapter().getGeneratedRecipes(empty).isEmpty());
        assertNull(new CrabTrapRecipeAdapter().convertBait(BuiltInRegistries.ITEM,
                mockResourceManager(Map.of()), Items.COD, new ResourceLocation("minecraft", "cod")));
    }

    @Test
    void unsupportedTableShapesAreRefusedInsteadOfApproximated() {
        assertNull(convert(Items.COD, table(
                        "{\"type\":\"minecraft:tag\",\"weight\":1,\"name\":\"minecraft:fishes\","
                                + "\"expand\":false}")),
                "tag entries have no single product");
        assertNull(convert(Items.COD, table(
                        "{\"type\":\"minecraft:loot_table\",\"weight\":1,"
                                + "\"value\":\"minecraft:gameplay/fishing\"}")),
                "nested tables are resolved at runtime");
        assertNull(convert(Items.COD, table(
                        "{\"type\":\"minecraft:item\",\"weight\":1,\"name\":\"crabbersdelight:crab\"}")),
                "an unregistered item cannot be produced");
        assertNull(convert(Items.COD, table(
                        "{\"type\":\"minecraft:item\",\"weight\":1,\"name\":\"minecraft:cod\","
                                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}")),
                "world conditions change the odds");
        assertNull(convert(Items.COD, table(
                        "{\"type\":\"minecraft:item\",\"weight\":1,\"name\":\"minecraft:cod\","
                                + "\"functions\":[{\"function\":\"minecraft:enchant_randomly\"}]}")),
                "random functions change the product");
        assertNull(convert(Items.COD, json(
                        "{\"type\":\"minecraft:fishing\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":1,"
                                + "\"entries\":[" + item("minecraft:cod", 1) + "]}]}")),
                "bonus rolls are not zero");
        assertNull(convert(Items.COD, json(
                        "{\"type\":\"minecraft:fishing\",\"pools\":[{\"rolls\":"
                                + "{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":2},"
                                + "\"entries\":[" + item("minecraft:cod", 1) + "]}]}")),
                "random roll counts are not constant");
        assertNull(convert(Items.COD, json(
                        "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,"
                                + "\"entries\":[" + item("minecraft:cod", 1) + "]}]}")),
                "a non-fishing table is not the crab-trap shape");
        assertNull(convert(Items.COD, table(item("minecraft:cod", 1), item("minecraft:kelp", 1000))),
                "the common scaler must not approximate a denominator over 1000");
        assertNull(convert(Items.SHEARS, new ResourceLocation("minecraft", "shears"), true,
                table(item("minecraft:cod", 1))), "a tag cannot give arbitrary tools a 48-use chum allowance");
    }

    @Test
    void largeExactBatchesAreNotLimitedToFortyEightCatches() {
        var recipe = convert(Items.COD, table(item("minecraft:cod", 1), item("minecraft:kelp", 60)));
        assertNotNull(recipe);
        assertEquals(61L, single(recipe).count());
        assertEquals(61_000L, recipe.inputFluids().get(0).amount());
        assertEquals(60, count(recipe, Items.KELP));
    }

    @Test
    void chainedSetCountUsesTheCurrentCountAndConditionalOrOversizedProductsAreRejected() {
        var recipe = convert(Items.COD, table("{\"type\":\"minecraft:item\",\"name\":\"minecraft:cod\","
                + "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":5},"
                + "{\"function\":\"minecraft:set_count\",\"count\":3,\"add\":true}]}"));
        assertNotNull(recipe);
        assertEquals(8, count(recipe, Items.COD));
        assertEquals(1L, single(recipe).count());
        assertNull(convert(Items.COD, table("{\"type\":\"minecraft:item\",\"name\":\"minecraft:cod\","
                + "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":2,"
                + "\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}]}]}")));
        assertNull(convert(Items.COD, table("{\"type\":\"minecraft:item\",\"name\":\"minecraft:cod\","
                + "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":65}]}")),
                "native stack splitting would spend bait per split stack, not per loot entry");
    }

    @Test
    void adapterIdentityAndMoldGatingFollowTheNativeTrap() {
        CrabTrapRecipeAdapter adapter = new CrabTrapRecipeAdapter();
        assertEquals("crabbersdelight", adapter.sourceId());
        assertEquals(CrabTrapSyntheticRecipe.class, adapter.getRecipeClass());
        assertNotNull(adapter.getMoldItem());
        assertTrue(adapter.matchesMold(new ItemStack(trap)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.STONE)));
        assertFalse(adapter.matchesMold(null));

        // No level means no trap recipes; nothing is guessed.
        assertTrue(adapter.getGeneratedRecipes(null).isEmpty());
        assertTrue(adapter.findMatchingRecipes(null, Map.of(), Map.of(), Map.of(),
                new ItemStack(trap), List.of()).isEmpty());
    }

    @Test
    void convertedHolderKeepsTheSingleGeneratedRecipe() {
        AdvancedAlloyFurnaceRecipe recipe = convert(Items.COD, table(item("minecraft:cod", 1)));
        assertNotNull(recipe);
        RecipeHolder<CrabTrapSyntheticRecipe> holder = new RecipeHolder<>(recipe.getId(),
                new CrabTrapSyntheticRecipe(recipe));
        assertEquals(List.of(recipe), new CrabTrapRecipeAdapter().convertAll(holder, null));
    }

    // ---------------------------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------------------------

    private static AdvancedAlloyFurnaceRecipe convert(Item bait, JsonObject table) {
        return CrabTrapRecipeAdapter.convertLootTable(BuiltInRegistries.ITEM, bait,
                BuiltInRegistries.ITEM.getKey(bait), table);
    }

    private static AdvancedAlloyFurnaceRecipe convert(Item bait, ResourceLocation baitId,
                                                      boolean chumBait, JsonObject table) {
        return CrabTrapRecipeAdapter.convertLootTable(
                BuiltInRegistries.ITEM, bait, baitId, chumBait, table);
    }

    private static CountedIngredient single(AdvancedAlloyFurnaceRecipe recipe) {
        assertEquals(1, recipe.inputs().size(), "expected exactly one bait input");
        return recipe.inputs().get(0);
    }

    private static int count(AdvancedAlloyFurnaceRecipe recipe, Item item) {
        int total = 0;
        for (ItemStack output : recipe.outputs()) {
            if (output.is(item)) {
                total += output.getCount();
            }
        }
        return total;
    }

    private static JsonObject json(String value) {
        return GsonHelper.parse(value);
    }

    private static JsonObject tableJson(String... entries) {
        return json("{\"type\":\"minecraft:fishing\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":0,"
                + "\"entries\":[" + String.join(",", entries) + "]}]}");
    }

    private static JsonObject table(String... entries) {
        return tableJson(entries);
    }

    private static String item(String name, int weight) {
        return "{\"type\":\"minecraft:item\",\"weight\":" + weight + ",\"name\":\"" + name + "\"}";
    }

    private static Level levelWithTables(Map<String, JsonObject> tables) {
        ResourceManager resourceManager = mockResourceManager(tables);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getResourceManager()).thenReturn(resourceManager);
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        return level;
    }

    private static ResourceManager mockResourceManager(Map<String, JsonObject> tables) {
        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.getResource(any(ResourceLocation.class))).thenReturn(Optional.empty());
        tables.forEach((id, contents) -> when(resourceManager.getResource(new ResourceLocation(id)))
                .thenReturn(Optional.of(resource(contents.toString()))));
        return resourceManager;
    }

    private static Resource resource(String contents) {
        return new Resource(mock(PackResources.class),
                () -> new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    private static void bindBaitTag(Item... baits) {
        MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        Map<TagKey<Item>, List<Holder<Item>>> tags = new LinkedHashMap<>();
        registry.getTagNames().forEach(key -> registry.getTag(key)
                .ifPresent(named -> tags.put(key, named.stream().toList())));
        List<Holder<Item>> holders = new ArrayList<>();
        for (Item bait : baits) {
            holders.add(registry.wrapAsHolder(bait));
        }
        tags.put(baitTag, List.copyOf(holders));
        registry.bindTags(tags);
    }
}
