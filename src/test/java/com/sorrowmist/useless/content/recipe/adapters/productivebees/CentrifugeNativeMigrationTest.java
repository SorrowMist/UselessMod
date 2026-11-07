package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.productivebees.ProductiveBeesCentrifugeIngredient;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import cy.jdkdigital.productivebees.common.recipe.TagOutputRecipe;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual PB 12.6.0 constructors/output resolution/matches; only the level and manager are mocked. */
class CentrifugeNativeMigrationTest {
    private static Item centrifuge;
    private static Item preferredOutput;
    private static Item geneBottle;
    private static Item honeyTreat;
    private Map<String, Integer> previousPreference;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ProductiveBeesCentrifugeIngredient.register();
        centrifuge = InventoryTestBootstrap.registerItem(new ResourceLocation("productivebees", "centrifuge"),
                () -> new Item(new Item.Properties()));
        preferredOutput = InventoryTestBootstrap.registerItem("pb_preferred_output", () -> new Item(new Item.Properties()));
        geneBottle = InventoryTestBootstrap.registerItem(new ResourceLocation("productivebees", "gene_bottle"),
                () -> new Item(new Item.Properties()));
        honeyTreat = InventoryTestBootstrap.registerItem(new ResourceLocation("productivebees", "honey_treat"),
                () -> new Item(new Item.Properties()));
    }

    @BeforeEach
    void setNativePreference() {
        previousPreference = TagOutputRecipe.modPreference;
        // Avoid fabricating FML's mod list/config context. This is the native preference cache,
        // not a mock of getRecipeOutputs or calculatedItemOutput.
        TagOutputRecipe.modPreference = new LinkedHashMap<>(Map.of("minecraft", 2, "useless_mod_test", 0));
    }

    @AfterEach
    void restoreNativePreference() {
        TagOutputRecipe.modPreference = previousPreference;
    }

    @Test
    void nativeConstructorKeepsNbtRealRangeCountFluidTimeAndIndependentCopies() {
        ItemStack input = tagged(Items.HONEYCOMB, 17, "required");
        ItemStack template = tagged(Items.DIAMOND, 23, "result");
        Ingredient predicate = StrictNBTIngredient.of(input);
        CentrifugeRecipe source = source("nbt", predicate,
                Map.of(StrictNBTIngredient.of(template), tuple(3, 3, 100)), Pair.of("minecraft:water", 250), 45);
        AdvancedAlloyFurnaceRecipe converted = convert(source);

        assertEquals(AdapterUtils.convertedId(source.id), converted.id());
        assertEquals(1L, converted.inputs().get(0).count(), "native completion consumes one, not the display count");
        assertTrue(converted.inputs().get(0).consumed());
        assertEquals(3, converted.outputs().get(0).getCount(), "the native roll replaces the template's count of 23");
        assertEquals("result", converted.outputs().get(0).getTag().getString("payload"));
        assertEquals(Fluids.WATER, converted.outputFluids().get(0).getFluid());
        assertEquals(250, converted.outputFluids().get(0).getAmount());
        assertEquals(45, converted.processTime());
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertTrue(converted.mold().test(new ItemStack(centrifuge)));
        assertFalse(converted.mold().test(new ItemStack(Items.HONEYCOMB)));
        assertTrue(source.getResultItem(null).isEmpty(), "native display result is deliberately not the actual outputs");

        Ingredient actual = converted.inputs().get(0).ingredient();
        ItemStack wrong = input.copy(); wrong.getOrCreateTag().putString("payload", "wrong");
        ItemStack extra = input.copy(); extra.getOrCreateTag().putBoolean("extra", true);
        for (ItemStack supply : List.of(input, input.copyWithCount(1), wrong, extra, new ItemStack(Items.HONEYCOMB))) {
            assertEquals(source.matches(container(supply), null), actual.test(supply));
        }
        converted.outputs().get(0).getTag().putString("payload", "mutated output");
        assertEquals("result", template.getTag().getString("payload"));
        assertEquals("result", source.getRecipeOutputs().keySet().iterator().next().getTag().getString("payload"));
        actual.getItems()[0].getTag().putString("payload", "mutated display");
        assertTrue(actual.test(input));
        assertEquals("required", input.getTag().getString("payload"));
    }

    @Test
    void nativeStackabilityMustNotBeLostWhenVanillaIngredientIgnoresNbt() {
        Ingredient vanilla = Ingredient.of(Items.HONEYCOMB);
        CentrifugeRecipe source = simple("stackability", vanilla, 100);
        Ingredient converted = convert(source).inputs().get(0).ingredient();
        ItemStack tagged = tagged(Items.HONEYCOMB, 1, "not allowed by the native machine");
        assertTrue(vanilla.test(tagged));
        assertFalse(source.matches(container(tagged), null));
        assertFalse(converted.test(tagged));
        assertTrue(converted.test(new ItemStack(Items.HONEYCOMB)));
    }

    @Test
    void nativeRangesAndProbabilitiesShareOneBatchAndKeepDistinctOutputNbt() {
        Map<Ingredient, IntArrayTag> outputs = new LinkedHashMap<>();
        outputs.put(StrictNBTIngredient.of(tagged(Items.DIAMOND, 27, "first")), tuple(1, 2, 49));
        outputs.put(StrictNBTIngredient.of(tagged(Items.DIAMOND, 19, "second")), tuple(2, 4, 24));
        var source = source("batch", Ingredient.of(Items.HONEYCOMB), outputs, Pair.of("minecraft:water", 125), 17);
        var converted = convert(source);
        assertEquals(4L, converted.inputs().get(0).count());
        assertEquals(2, converted.outputs().size(), "different NBT must not be merged");
        assertEquals(List.of(3, 3), converted.outputs().stream().map(ItemStack::getCount).toList());
        assertEquals(List.of("first", "second"), converted.outputs().stream()
                .map(stack -> stack.getTag().getString("payload")).toList());
        assertEquals(500, converted.outputFluids().get(0).getAmount());
        assertEquals(68, converted.processTime());
        assertEquals(4L * AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.HONEYCOMB, 3)), 1L));
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.HONEYCOMB, 4)), 1L));
    }

    @Test
    void inclusiveNativeChanceZeroIsOnePercentAndNinetyNineIsAlreadyGuaranteed() {
        for (int chance : List.of(0, 1, 24, 49, 50, 98, 99, 100)) {
            var converted = convert(simple("chance_" + chance, Ingredient.of(Items.HONEYCOMB), chance));
            long successes = java.util.stream.IntStream.range(0, 100).filter(roll -> roll <= chance).count();
            long divisor = AdapterUtils.gcd(successes, 100);
            assertEquals(100 / divisor, converted.inputs().get(0).count(), "native <= chance: " + chance);
            assertEquals(successes / divisor, converted.outputs().get(0).getCount());
            assertEquals(converted.inputs().get(0).count() * 11, converted.processTime());
        }
    }

    @Test
    void outputSelectionUsesNativeModPreferenceRatherThanFirstDisplayStack() {
        ItemStack preferred = tagged(preferredOutput, 31, "native preference");
        Ingredient alternatives = Ingredient.of(new ItemStack(Items.GOLD_INGOT), preferred);
        var source = source("preference", Ingredient.of(Items.HONEYCOMB),
                Map.of(alternatives, tuple(2, 2, 100)), null, 11);
        var converted = convert(source);
        assertEquals(preferredOutput, converted.outputs().get(0).getItem());
        assertEquals(2, converted.outputs().get(0).getCount());
        assertEquals("native preference", converted.outputs().get(0).getTag().getString("payload"));
    }

    @Test
    void nativeNullableFluidPairSupportsFluidOnlyAndItemOnlyButNeverAnEmptyRecipe() {
        var fluidOnly = source("fluid_only", Ingredient.of(Items.HONEYCOMB), Map.of(), Pair.of("minecraft:water", 333), 21);
        var converted = convert(fluidOnly);
        assertTrue(converted.outputs().isEmpty());
        assertEquals(333, converted.outputFluids().get(0).getAmount());
        assertEquals(1, converted.inputs().get(0).count());
        assertTrue(convert(simple("item_only", Ingredient.of(Items.HONEYCOMB), 100)).outputFluids().isEmpty());
        assertRejected(source("empty", Ingredient.of(Items.HONEYCOMB), Map.of(), null, 21));
    }

    @Test
    void malformedAndUnresolvedRowsRejectTheWholeRecipeEvenWhenOtherOutputsAreValid() {
        List<IntArrayTag> invalid = java.util.Arrays.asList(null, tuple(1, 1), tuple(1, 1, 100, 7),
                tuple(-1, 1, 100), tuple(2, 1, 100), tuple(1, 1, -1), tuple(1, 1, 101),
                tuple(1, 65, 100), tuple(1, 16_777_217, 100));
        for (IntArrayTag bad : invalid) {
            Map<Ingredient, IntArrayTag> outputs = new LinkedHashMap<>();
            outputs.put(Ingredient.of(Items.DIAMOND), tuple(1, 1, 100));
            outputs.put(Ingredient.of(Items.GOLD_INGOT), bad);
            assertRejected(source("bad_row", Ingredient.of(Items.HONEYCOMB), outputs, Pair.of("minecraft:water", 125), 10));
        }
        assertRejected(source("missing_outputs", Ingredient.of(Items.HONEYCOMB), null, null, 10));
        assertRejected(source("unresolved_output", Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.EMPTY, tuple(1, 1, 100)), Pair.of("minecraft:water", 125), 10));
        assertRejected(source("no_yield", Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.of(Items.DIAMOND), tuple(0, 0, 100)), null, 10));
    }

    @Test
    void nativeSlotCapacityFluidResolutionAndTimeOverflowAreNotIgnored() {
        Map<Ingredient, IntArrayTag> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) tooMany.put(Ingredient.of(Items.DIAMOND), tuple(1, 1, 100));
        assertRejected(source("slots", Ingredient.of(Items.HONEYCOMB), tooMany, null, 10));
        assertRejected(source("unstackable", Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.of(Items.DIAMOND_SWORD), tuple(2, 2, 100)), null, 10));
        for (Pair<String, Integer> bad : List.of(Pair.of("minecraft:water", 0), Pair.of("minecraft:water", -1),
                Pair.of("minecraft:water", 10_001), Pair.of("test:missing_fluid", 125), Pair.of("", 125))) {
            assertRejected(source("fluid", Ingredient.of(Items.HONEYCOMB),
                    Map.of(Ingredient.of(Items.DIAMOND), tuple(1, 1, 100)), bad, 10));
        }
        assertRejected(source("time", Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.of(Items.DIAMOND), tuple(1, 1, 0)), null, Integer.MAX_VALUE));
    }

    @Test
    void unknownCountSensitivePredicatesAndNativeGeneTreatBranchesAreNotFlattened() {
        Ingredient countSensitive = new Ingredient(Stream.of(new Ingredient.ItemValue(new ItemStack(Items.HONEYCOMB)))) {
            @Override public boolean test(ItemStack stack) { return stack != null && stack.is(Items.HONEYCOMB) && stack.getCount() >= 12; }
        };
        assertRejected(simple("count_sensitive", countSensitive, 100));
        assertRejected(simple("empty_input", Ingredient.EMPTY, 100));
        assertRejected(simple("null_input", null, 100));
        assertRejected(simple("gene_bottle", Ingredient.of(geneBottle), 100));
        assertRejected(simple("honey_treat", Ingredient.of(honeyTreat), 100));
    }

    @Test
    void customRecipeWorldInventoryAndDynamicAssemblyOverridesAreRejectedBeforeInvocation() {
        CentrifugeRecipe dynamic = new CentrifugeRecipe(id("dynamic"), Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.of(Items.DIAMOND), tuple(1, 1, 100)), null, 11) {
            @Override public boolean matches(Container container, Level level) { throw new AssertionError("native world predicate"); }
            @Override public Map<ItemStack, IntArrayTag> getRecipeOutputs() { throw new AssertionError("dynamic inventory output"); }
            @Override public int getProcessingTime() { throw new AssertionError("dynamic time"); }
        };
        assertRejected(dynamic);
        var adapter = new CentrifugeRecipeAdapter();
        assertTrue(adapter.findMatchingRecipes(level(dynamic), Map.of(Ingredient.of(Items.HONEYCOMB), 999L),
                Map.of(), new ItemStack(centrifuge)).isEmpty());
    }

    @Test
    void lookupUsesTheSameConversionValidationNbtBatchAndLiveManagerAfterReload() {
        ItemStack input = tagged(Items.HONEYCOMB, 1, "lookup");
        var good = simple("lookup", StrictNBTIngredient.of(input), 49);
        var bad = source("lookup_bad", Ingredient.of(Items.HONEYCOMB),
                Map.of(Ingredient.of(Items.DIAMOND), tuple(2, 1, 100)), null, 11);
        Level level = level(bad, good);
        var adapter = new CentrifugeRecipeAdapter();
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(input), 1L), Map.of(), new ItemStack(centrifuge)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(Items.HONEYCOMB), 2L), Map.of(), new ItemStack(centrifuge)).isEmpty());
        assertEquals(List.of(new RecipeHolder<>(good.id, good)), adapter.findMatchingRecipes(level,
                Map.of(Ingredient.of(input), 2L), Map.of(), new ItemStack(centrifuge)));
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(input), 2L), Map.of(), new ItemStack(Items.BRICK)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(input), 2L), Map.of(), null).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, null, Map.of(), new ItemStack(centrifuge)).isEmpty());
        RecipeManager manager = level.getRecipeManager();
        when(manager.getRecipes()).thenReturn(List.of(bad));
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(input), 2L), Map.of(), new ItemStack(centrifuge)).isEmpty());
    }

    @Test
    void concreteAndAeLookupKeepNbtAndLongAmountsWithoutDoubleCountingMergedInputs() {
        ItemStack input = tagged(Items.HONEYCOMB, 1, "ae");
        var source = simple("ae_lookup", StrictNBTIngredient.of(input), 49);
        var adapter = new CentrifugeRecipeAdapter();
        Level level = level(source);
        Map<AEKey, Long> one = Map.of(AEItemKey.of(input), 1L);
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(), one,
                new ItemStack(centrifuge), List.of(input)).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(input), 2L), Map.of(), one,
                new ItemStack(centrifuge), List.of()).isEmpty(), "the same AE supply must not be counted twice");
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), one,
                new ItemStack(centrifuge), List.of(new ItemStack(Items.HONEYCOMB))).isEmpty());
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(AEItemKey.of(input), Long.MAX_VALUE),
                new ItemStack(centrifuge), List.of()).size());
    }

    private static AdvancedAlloyFurnaceRecipe convert(CentrifugeRecipe source) {
        var converted = new CentrifugeRecipeAdapter().convertAll(new RecipeHolder<>(source.id, source), null);
        assertEquals(1, converted.size(), source.id.toString());
        return converted.get(0);
    }

    private static void assertRejected(CentrifugeRecipe source) {
        var adapter = new CentrifugeRecipeAdapter();
        var holder = new RecipeHolder<>(source.id, source);
        assertTrue(adapter.convertAll(holder, null).isEmpty(), source.id.toString());
        assertTrue(adapter.convertAll(holder, null, List.of(new ItemStack(Items.HONEYCOMB, 64))).isEmpty());
        assertNull(adapter.convert(holder, null));
    }

    private static CentrifugeRecipe simple(String path, Ingredient input, int chance) {
        return source(path, input, Map.of(Ingredient.of(Items.DIAMOND), tuple(1, 1, chance)), null, 11);
    }

    private static CentrifugeRecipe source(String path, Ingredient input, Map<Ingredient, IntArrayTag> outputs,
                                           Pair<String, Integer> fluid, int time) {
        return new CentrifugeRecipe(id(path), input, outputs, fluid, time);
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("test", "pb_" + path); }
    private static IntArrayTag tuple(int... values) { return new IntArrayTag(values); }
    private static ItemStack tagged(Item item, int count, String payload) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("payload", payload);
        return stack;
    }
    private static SimpleContainer container(ItemStack input) { return new SimpleContainer(ItemStack.EMPTY, input); }
    private static Level level(Recipe<?>... recipes) {
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of(recipes));
        return level;
    }
}
