package com.sorrowmist.useless.content.recipe.adapters.lychee;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.lychee.block_crushing.BlockCrushingRecipe;
import snownee.lychee.core.LycheeContext;
import snownee.lychee.core.Reference;
import snownee.lychee.core.contextual.And;
import snownee.lychee.core.contextual.Chance;
import snownee.lychee.core.contextual.ContextualCondition;
import snownee.lychee.core.contextual.FallDistance;
import snownee.lychee.core.contextual.IsWeather;
import snownee.lychee.core.contextual.Location;
import snownee.lychee.core.contextual.Not;
import snownee.lychee.core.contextual.Or;
import snownee.lychee.core.contextual.Time;
import snownee.lychee.core.post.AnvilDamageChance;
import snownee.lychee.core.post.Break;
import snownee.lychee.core.post.Delay;
import snownee.lychee.core.post.DropItem;
import snownee.lychee.core.post.DropXp;
import snownee.lychee.core.post.Execute;
import snownee.lychee.core.post.If;
import snownee.lychee.core.post.PlaceBlock;
import snownee.lychee.core.post.PostAction;
import snownee.lychee.core.post.PostActionType;
import snownee.lychee.core.post.RandomSelect;
import snownee.lychee.core.post.input.DamageItem;
import snownee.lychee.core.post.input.NBTPatch;
import snownee.lychee.core.post.input.PreventDefault;
import snownee.lychee.core.post.input.SetItem;
import snownee.lychee.core.recipe.ILycheeRecipe;
import snownee.lychee.core.recipe.LycheeRecipe;
import snownee.lychee.util.json.JsonPatch;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Genuine 5.2.1 recipe/action constructors; no mocked Lychee recipe API or viewer interpretation.
 * Protected serializer fields are set reflectively to avoid initializing RecipeTypes,
 * PostActionTypes and the loot-parameter registries without a Forge launcher/mixin transformer.
 */
class LycheeBlockCrushingNativeMigrationTest {
    private static Item inputItem;
    private static Item outputItem;
    private final LycheeBlockCrushingRecipeAdapter adapter = new LycheeBlockCrushingRecipeAdapter();

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        inputItem = InventoryTestBootstrap.registerItem("lychee_native_input", () -> new Item(new Item.Properties()));
        outputItem = InventoryTestBootstrap.registerItem(new ResourceLocation("useless_mod_test", "lychee_native_output"),
                () -> new Item(new Item.Properties()));
    }

    @Test
    void nativeApiAndFixedWorkstationStayOnTheExistingBlockCrushingAdapter() {
        assertEquals(BlockCrushingRecipe.class, adapter.getRecipeClass());
        assertEquals(RecipeSourceIds.LYCHEE, adapter.sourceId());
        assertEquals(Items.ANVIL, adapter.getMoldItem().getItem());
        assertTrue(adapter.matchesMold(new ItemStack(Items.ANVIL)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.CHIPPED_ANVIL)));
        assertFalse(adapter.matchesMold(new ItemStack(Items.DAMAGED_ANVIL)));
        assertFalse(adapter.matchesMold(null));
        assertEquals("snownee.lychee.block_crushing.BlockCrushingRecipe", adapter.getRecipeClass().getName());
    }

    @Test
    void fixedDropsRetainCountsSeparateNbtAndDetachedCopiesOfTheNativeStack() {
        ItemStack required = tagged(inputItem, 1, "required");
        Ingredient strict = StrictNBTIngredient.of(required);
        ItemStack first = tagged(outputItem, 3, "first");
        ItemStack more = tagged(outputItem, 2, "first");
        ItemStack second = tagged(outputItem, 7, "second");
        BlockCrushingRecipe source = recipe(List.of(strict, strict),
                new DropItem(first), new DropItem(more), new DropItem(second));

        AdvancedAlloyFurnaceRecipe converted = convert(source);
        assertSame(strict, converted.inputs().get(0).ingredient());
        assertSame(strict, converted.inputs().get(1).ingredient());
        assertTrue(converted.inputs().stream().allMatch(i -> i.count() == 1L && i.consumed()));
        assertFalse(strict.test(new ItemStack(inputItem)));
        assertTrue(converted.inputFluids().isEmpty());
        assertTrue(converted.keyInputs().isEmpty());
        assertTrue(converted.catalyst().isEmpty());
        assertEquals(0, converted.catalystUses());
        assertTrue(converted.mold().test(new ItemStack(Items.ANVIL)));
        assertEquals(2, converted.outputs().size());
        assertEquals(5, output(converted, "first").getCount());
        assertEquals(7, output(converted, "second").getCount());
        assertEquals(AdapterUtils.DEFAULT_ENERGY, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME, converted.processTime());

        first.getOrCreateTag().putString("marker", "caller changed");
        assertEquals("first", output(converted, "first").getTag().getString("marker"));
        output(converted, "second").getOrCreateTag().putString("marker", "result changed");
        assertEquals("second", second.getTag().getString("marker"));
        assertEquals(3, first.getCount());
        assertEquals(7, second.getCount());
    }

    @Test
    void independentActionChancesScaleAllInputSlotsDeterministicOutputsEnergyAndTimeTogether() {
        DropItem rolled = drop("rolled", 1);
        rolled.withCondition(new Chance(0.5f));
        rolled.withCondition(new Chance(0.5f));
        DropItem never = drop("never", 11);
        never.withCondition(new Chance(0));
        Ingredient same = Ingredient.of(inputItem);
        BlockCrushingRecipe source = recipe(List.of(same, same, Ingredient.of(Items.DIAMOND)),
                drop("fixed", 3), rolled, never);
        AdvancedAlloyFurnaceRecipe converted = convert(source);

        assertEquals(3, converted.inputs().size());
        assertTrue(converted.inputs().stream().allMatch(i -> i.count() == 4L && i.consumed()));
        assertEquals(12L, converted.inputs().stream().mapToLong(i -> i.count()).sum());
        assertEquals(12, output(converted, "fixed").getCount());
        assertEquals(1, output(converted, "rolled").getCount());
        assertEquals(2, converted.outputs().size());
        assertEquals(AdapterUtils.DEFAULT_ENERGY * 4L, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME * 4, converted.processTime());
        assertEquals(1, rolled.stack.getCount());
        assertEquals(3, source.getIngredients().size());
    }

    @Test
    void ingredientEntriesAreUnitCostsNotDisplayStackCountsOrInventedSizedIngredients() {
        Ingredient displayedCount = Ingredient.of(new ItemStack(inputItem, 17));
        AdvancedAlloyFurnaceRecipe converted = convert(recipe(List.of(displayedCount), drop("unit", 2)));
        assertSame(displayedCount, converted.inputs().get(0).ingredient());
        assertEquals(1L, converted.inputs().get(0).count());
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(inputItem)), 1));
    }

    @Test
    void recipeChanceGatesAreNotLossyActionRollsBecauseTheyRunBeforeConsumption() {
        for (float chance : List.of(0f, 0.5f)) {
            BlockCrushingRecipe source = simple();
            source.withCondition(new Chance(chance));
            assertRejected(source);
        }
        BlockCrushingRecipe certain = simple();
        certain.withCondition(new Chance(1));
        certain.withCondition(new Chance(1));
        assertEquals(1L, convert(certain).inputs().get(0).count());
    }

    @Test
    void everyNonChanceRecipeAndActionConditionIncludingNestedBooleanConditionsIsRejected() {
        And and = new And();
        and.withCondition(new Chance(0.5f));
        and.withCondition(IsWeather.RAIN);
        Or or = new Or();
        or.withCondition(new Chance(1));
        or.withCondition(IsWeather.THUNDER);
        Location location = new Location((LocationCheck) LocationCheck.checkLocation(
                LocationPredicate.Builder.location().setDimension(Level.NETHER)).build());
        List<ContextualCondition> conditions = List.of(
                IsWeather.RAIN, new FallDistance(MinMaxBounds.Doubles.atLeast(3)),
                new Time(MinMaxBounds.Ints.between(0, 1000), 24000L), location,
                and, or, new Not(new Chance(0.5f)));
        for (ContextualCondition condition : conditions) {
            BlockCrushingRecipe gated = simple();
            gated.withCondition(condition);
            assertRejected(gated);

            DropItem action = drop("conditional", 1);
            action.withCondition(new Chance(0));
            action.withCondition(condition); // A zero roll must not hide the unsupported condition.
            assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("fixed", 1), action));
        }
    }

    @Test
    void invalidChancesAreRejectedRatherThanClampedToCertainOrAbsent() {
        for (float value : List.of(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.1f, 1.1f)) {
            DropItem action = drop("invalid", 1);
            action.withCondition(new Chance(value));
            assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("fixed", 1), action));
            BlockCrushingRecipe source = simple();
            source.withCondition(new Chance(value));
            assertRejected(source);
        }
    }

    @Test
    void onlyExplicitSingleExecutionIsAcceptedInsteadOfSilentlyCollapsingRepeatLimits() {
        assertFalse(convertAll(simple()).isEmpty());
        for (MinMaxBounds.Ints repeats : List.of(MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.exactly(0),
                MinMaxBounds.Ints.exactly(2), MinMaxBounds.Ints.between(1, 3),
                MinMaxBounds.Ints.atLeast(1), MinMaxBounds.Ints.atMost(1))) {
            BlockCrushingRecipe source = simple();
            set(LycheeRecipe.class, source, "maxRepeats", repeats);
            assertRejected(source);
        }
        BlockCrushingRecipe defaultRecipe = new BlockCrushingRecipe(id());
        assertTrue(defaultRecipe.getMaxRepeats().isAny(), "5.2.1 defaults to unbounded, not one");
        defaultRecipe.getIngredients().add(Ingredient.of(inputItem));
        defaultRecipe.addPostAction(drop("default", 1));
        set(BlockCrushingRecipe.class, defaultRecipe, "fallingBlock", anvil());
        assertRejected(defaultRecipe);
    }

    @Test
    void preventionConsumptionDamageCompoundAndWorldActionsRejectTheWholeRecipe() {
        PostAction[] choices = {drop("choice_one", 1), drop("choice_two", 1)};
        List<PostAction> unsupported = List.of(
                new PreventDefault(), new DamageItem(1, Reference.DEFAULT),
                new SetItem(tagged(outputItem, 2, "replacement"), Reference.DEFAULT),
                new AnvilDamageChance(0.25f), new Delay(0.5f), new Break(),
                new DropXp(5), new Execute("say not a drop", true, false),
                new PlaceBlock(BlockPredicate.Builder.block().of(Blocks.DIAMOND_BLOCK).build(), BlockPos.ZERO),
                new If(choices, new PostAction[]{drop("failure", 1)}),
                new RandomSelect(choices, new int[]{1, 1}, 2, 0, MinMaxBounds.Ints.exactly(1)));
        for (PostAction action : unsupported) {
            assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("before", 1), action, drop("after", 1)));
            action.withCondition(new Chance(0));
            assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("fixed", 1), action));
        }
    }

    @Test
    void dynamicNbtPatchAndDropPointersDoNotBecomeStaticViewerStacks() {
        DropItem pointer = drop("viewer_only", 4);
        pointer.path = "/post/0";
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), pointer));
        NBTPatch patch = new NBTPatch(JsonPatch.parse(JsonParser.parseString("""
                {"op":"copy","from":"/item_in/0/tag","path":"/post/0/tag"}
                """).getAsJsonObject()));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("viewer_only", 4), patch));
    }

    @Test
    void unknownActionsAndNativeSubclassesCannotSmuggleInViewerOutputsOrChangedEffects() {
        PostAction displayOnly = new PostAction() {
            @Override public PostActionType<?> getType() { return null; }
            @Override protected void apply(ILycheeRecipe<?> recipe, LycheeContext context, int times) {
                throw new AssertionError("conversion must not run actions");
            }
            @Override public List<ItemStack> getItemOutputs() {
                throw new AssertionError("viewer outputs must never be consulted");
            }
        };
        DropItem dynamicDrop = new DropItem(tagged(outputItem, 5, "display_only")) {
            @Override public void doApply(ILycheeRecipe<?> recipe, LycheeContext context, int times) {
                throw new AssertionError("unknown subclass effects");
            }
        };
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("fixed", 1), displayOnly));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), dynamicDrop));
        BlockCrushingRecipe subclass = new BlockCrushingRecipe(id()) { };
        configure(subclass, List.of(Ingredient.of(inputItem)), drop("subclass", 1));
        assertRejected(subclass);
    }

    @Test
    void fallingAndLandingRestrictionsAreCheckedByConversionNotJustByAViewerHelper() {
        for (String json : List.of("{\"blocks\":[\"minecraft:sand\"]}",
                "{\"blocks\":[\"minecraft:anvil\"],\"state\":{\"facing\":\"north\"}}",
                "{\"blocks\":[\"minecraft:anvil\"],\"nbt\":\"{}\"}")) {
            BlockCrushingRecipe source = simple();
            set(BlockCrushingRecipe.class, source, "fallingBlock", predicate(json));
            assertRejected(source);
        }
        for (String json : List.of("{\"blocks\":[\"minecraft:obsidian\"]}",
                "{\"tag\":\"minecraft:anvil\"}", "{\"state\":{\"facing\":\"north\"}}", "{\"nbt\":\"{}\"}")) {
            BlockCrushingRecipe source = simple();
            set(BlockCrushingRecipe.class, source, "landingBlock", predicate(json));
            assertRejected(source);
        }
        BlockCrushingRecipe nbtExists = simple();
        set(BlockCrushingRecipe.class, nbtExists, "fallingBlock", new BlockPredicate(null, null,
                StatePropertiesPredicate.ANY, new NbtPredicate(null)));
        assertRejected(nbtExists);
        BlockCrushingRecipe any = simple();
        set(BlockCrushingRecipe.class, any, "fallingBlock", BlockPredicate.ANY);
        assertFalse(convertAll(any).isEmpty());
    }

    @Test
    void ghostEmptyMalformedAndOverflowingRecipesAreNotPartiallyConverted() {
        BlockCrushingRecipe ghost = simple();
        ghost.ghost = true;
        assertRejected(ghost);
        assertRejected(recipe(List.of(), drop("free", 1)));
        assertRejected(recipe(List.of(Ingredient.of(inputItem), Ingredient.EMPTY), drop("partial", 1)));
        assertRejected(recipe(java.util.Collections.nCopies(28, Ingredient.of(inputItem)), drop("too_many", 1)));
        assertRejected(recipe(List.of(Ingredient.of(inputItem))));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), new DropItem(ItemStack.EMPTY)));
        DropItem never = drop("never", 1);
        never.withCondition(new Chance(0));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), never));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("overflow", Integer.MAX_VALUE), drop("overflow", 1)));
        DropItem half = drop("half", 1);
        half.withCondition(new Chance(0.5f));
        assertRejected(recipe(List.of(Ingredient.of(inputItem)), drop("huge", Integer.MAX_VALUE), half));
        assertTrue(adapter.convertAll(null, null).isEmpty());

        BlockCrushingRecipe hidden = simple();
        hidden.hideInRecipeViewer = true; // Viewer visibility alone does not change execution.
        assertFalse(convertAll(hidden).isEmpty());
    }

    @Test
    void matchingUsesTheScaledCostsWithoutDoubleSpendingOverlapsOrFlatteningNbtAndAeKeys() {
        ItemStack tagged = tagged(inputItem, 1, "required");
        Ingredient strict = StrictNBTIngredient.of(tagged);
        Ingredient broad = Ingredient.of(inputItem);
        DropItem half = drop("half", 1);
        half.withCondition(new Chance(0.5f));
        AdvancedAlloyFurnaceRecipe converted = convert(recipe(List.of(strict, broad), half));
        assertEquals(2L, converted.inputs().get(0).count());
        ItemStack taggedTwo = tagged.copyWithCount(2);
        ItemStack plainTwo = new ItemStack(inputItem, 2);
        assertFalse(matches(converted, List.of(taggedTwo)));
        assertFalse(matches(converted, List.of(new ItemStack(inputItem, 4))));
        assertTrue(matches(converted, List.of(taggedTwo, plainTwo)));
        assertTrue(LycheeBlockCrushingRecipeAdapter.matchesInputs(converted, Map.of(), List.of(plainTwo),
                List.of(new GenericStack(AEItemKey.of(tagged), 2L))));
        assertFalse(LycheeBlockCrushingRecipeAdapter.matchesInputs(converted, Map.of(), List.of(plainTwo),
                List.of(new GenericStack(AEItemKey.of(new ItemStack(inputItem)), 2L))));
        Map<Ingredient, Long> exactSupply = new IdentityHashMap<>();
        exactSupply.put(strict, 2L);
        assertFalse(LycheeBlockCrushingRecipeAdapter.matchesInputs(converted, exactSupply, null, List.of()));
        exactSupply.put(broad, 2L);
        assertTrue(LycheeBlockCrushingRecipeAdapter.matchesInputs(converted, exactSupply, null, List.of()));
        assertEquals("required", taggedTwo.getTag().getString("marker"));
        assertEquals(2, taggedTwo.getCount());
        assertEquals(2, plainTwo.getCount());
    }

    @Test
    void invalidLookupContextsReturnBeforeTouchingLycheeRegistrationStatics() {
        assertTrue(adapter.findMatchingRecipes(null, Map.of(), Map.of(), new ItemStack(Items.ANVIL)).isEmpty());
        Level level = mock(Level.class);
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), new ItemStack(Items.STONE)).isEmpty());
        verifyNoInteractions(level);
    }

    private static boolean matches(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputs) {
        return LycheeBlockCrushingRecipeAdapter.matchesInputs(recipe, Map.of(), inputs, List.of());
    }

    private BlockCrushingRecipe simple() {
        return recipe(List.of(Ingredient.of(inputItem)), drop("simple", 1));
    }

    private static BlockCrushingRecipe recipe(List<Ingredient> inputs, PostAction... actions) {
        BlockCrushingRecipe recipe = new BlockCrushingRecipe(id());
        configure(recipe, inputs, actions);
        return recipe;
    }

    private static void configure(BlockCrushingRecipe recipe, List<Ingredient> inputs, PostAction... actions) {
        recipe.getIngredients().addAll(inputs);
        for (PostAction action : actions) recipe.addPostAction(action);
        set(BlockCrushingRecipe.class, recipe, "fallingBlock", anvil());
        set(LycheeRecipe.class, recipe, "maxRepeats", MinMaxBounds.Ints.exactly(1));
    }

    private static void set(Class<?> owner, Object target, String fieldName, Object value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Pinned native serializer field changed: " + fieldName, exception);
        }
    }

    private static BlockPredicate anvil() {
        return BlockPredicate.Builder.block().of(Blocks.ANVIL).build();
    }

    private static BlockPredicate predicate(String json) {
        return BlockPredicate.fromJson(JsonParser.parseString(json));
    }

    private static DropItem drop(String marker, int count) {
        return new DropItem(tagged(outputItem, count, marker));
    }

    private static ItemStack tagged(Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", marker);
        return stack;
    }

    private static ItemStack output(AdvancedAlloyFurnaceRecipe recipe, String marker) {
        return recipe.outputs().stream().filter(stack -> stack.hasTag()
                && marker.equals(stack.getTag().getString("marker"))).findFirst().orElseThrow();
    }

    private static ResourceLocation id() {
        return new ResourceLocation("useless_mod_test", "lychee_native_crushing");
    }

    private List<AdvancedAlloyFurnaceRecipe> convertAll(BlockCrushingRecipe source) {
        return adapter.convertAll(new RecipeHolder<>(id(), source), null);
    }

    private AdvancedAlloyFurnaceRecipe convert(BlockCrushingRecipe source) {
        List<AdvancedAlloyFurnaceRecipe> result = convertAll(source);
        assertEquals(1, result.size());
        return result.get(0);
    }

    private void assertRejected(BlockCrushingRecipe source) {
        assertTrue(convertAll(source).isEmpty());
    }
}
