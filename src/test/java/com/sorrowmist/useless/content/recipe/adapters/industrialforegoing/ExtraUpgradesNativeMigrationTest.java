package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.config.machine.InfuserConfig;
import net.yxiao233.ifeu.common.config.machine.PrecisionCraftingTableConfig;
import net.yxiao233.ifeu.common.recipe.InfuserRecipe;
import net.yxiao233.ifeu.common.recipe.PrecisionShapedRecipe;
import net.yxiao233.ifeu.common.recipe.PrecisionShapelessRecipe;
import net.yxiao233.ifeu.common.recipe.ShapedRecipe;
import net.yxiao233.ifeu.common.recipe.ShapelessRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Constructors and predicates from the published IFEU 1.20.1-2.5.000, not mock recipes. */
class ExtraUpgradesNativeMigrationTest {
    private static Item air;
    private static Item dynamicOutput;
    private int infuserPower;
    private int precisionPower;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        for (String name : List.of("infuser", "precision_crafting_table", "fluid_crafting_table")) {
            InventoryTestBootstrap.registerItem(id(name), () -> new Item(new Item.Properties()));
        }
        air = InventoryTestBootstrap.registerItem(id("air"), () -> new Item(new Item.Properties()));
        dynamicOutput = InventoryTestBootstrap.registerItem("ifeu_dynamic_crafted_output", () -> new Item(new Item.Properties()) {
            @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {
                stack.getOrCreateTag().putString("world_dependent", "callback");
            }
        });
    }

    @BeforeEach void saveConfig() {
        infuserPower = InfuserConfig.powerPerTick;
        precisionPower = PrecisionCraftingTableConfig.powerPerTick;
    }

    @AfterEach void restoreConfig() {
        InfuserConfig.powerPerTick = infuserPower;
        PrecisionCraftingTableConfig.powerPerTick = precisionPower;
        InfuserRecipe.RECIPES.removeIf(recipe -> recipe.getId().getPath().startsWith("migration_test/"));
        PrecisionShapedRecipe.RECIPES.removeIf(recipe -> recipe.getId().getPath().startsWith("migration_test/"));
        PrecisionShapelessRecipe.RECIPES.removeIf(recipe -> recipe.getId().getPath().startsWith("migration_test/"));
        ShapedRecipe.RECIPES.removeIf(recipe -> recipe.getId().getPath().startsWith("migration_test/"));
        ShapelessRecipe.RECIPES.removeIf(recipe -> recipe.getId().getPath().startsWith("migration_test/"));
    }

    @Test void nativeInfuserKeepsCountsLongEnergyExactFluidAndOutputNbt() {
        InfuserConfig.powerPerTick = 2_000_000_000;
        ItemStack input = marked(Items.IRON_INGOT, 5);
        ItemStack output = marked(Items.DIAMOND, 3);
        FluidStack fluid = new FluidStack(Fluids.WATER, 750);
        fluid.getOrCreateTag().putString("grade", "pure");
        var source = new InfuserRecipe(testId("infuser"), input, fluid, 3, output);
        var converted = new InfuserRecipeAdapter().convertAll(RecipeHolder.of(source), null).get(0);
        assertEquals(5L, converted.inputs().get(0).count());
        assertTrue(converted.inputs().get(0).ingredient().test(new ItemStack(Items.IRON_INGOT)),
                "native Infuser matches item identity, not item NBT");
        assertEquals(6_000_000_000L, converted.energy());
        assertEquals(3, converted.processTime());
        assertEquals(750L, converted.inputFluids().get(0).amount());
        assertTrue(converted.inputFluids().get(0).ingredient().test(fluid));
        assertFalse(converted.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.WATER, 750)));
        assertTrue(converted.mold().test(new InfuserRecipeAdapter().getMoldItem()));
        assertFalse(converted.mold().test(new PrecisionShapedRecipeAdapter().getMoldItem()));
        output.getOrCreateTag().putString("marker", "changed");
        assertEquals(3, converted.outputs().get(0).getCount());
        assertEquals("native", converted.outputs().get(0).getTag().getString("marker"));
        var decoded = roundTrip(converted);
        assertEquals(6_000_000_000L, decoded.energy());
        assertTrue(decoded.inputFluids().get(0).ingredient().test(fluid));
        assertEquals("native", decoded.outputs().get(0).getTag().getString("marker"));
    }

    @Test void untaggedNativeFluidDoesNotAcceptAdditionalNbt() {
        var source = new InfuserRecipe(testId("untagged"), new ItemStack(Items.IRON_INGOT),
                new FluidStack(Fluids.WATER, 100), 2, new ItemStack(Items.DIAMOND));
        var predicate = new InfuserRecipeAdapter().convertAll(RecipeHolder.of(source), null).get(0)
                .inputFluids().get(0).ingredient();
        FluidStack tagged = new FluidStack(Fluids.WATER, 100);
        tagged.getOrCreateTag().putString("grade", "different");
        assertTrue(predicate.test(new FluidStack(Fluids.WATER, 100)));
        assertFalse(predicate.test(tagged));
    }

    @Test void generatedBucketsUseTheFluidsCanonicalBucketAndDoNotAppendNativeDatagenRecipes() {
        InventoryTestBootstrap.registerItem("ifeu_alternate_water_bucket", () ->
                new BucketItem(Fluids.WATER, new Item.Properties()));
        Level level = levelWith();
        var adapter = new InfuserRecipeAdapter();
        int before = InfuserRecipe.RECIPES.size();
        var generated = adapter.getGeneratedRecipes(level);
        assertFalse(generated.isEmpty());
        var water = generated.stream().filter(holder -> holder.value().inputFluid.getFluid() == Fluids.WATER).findFirst().orElseThrow();
        assertTrue(water.value().output.is(Items.WATER_BUCKET));
        assertEquals(1000, water.value().inputFluid.getAmount());
        assertEquals(200, water.value().processingTime);
        assertTrue(generated.stream().noneMatch(holder -> holder.value().output.is(Items.COD_BUCKET)
                || holder.value().output.is(Items.MILK_BUCKET)));
        adapter.getGeneratedRecipes(level);
        assertEquals(before, InfuserRecipe.RECIPES.size());
        assertEquals(water.id(), water.value().getId());
    }

    @Test void declaredInfuserRecipesKeepPriorityOverGeneratedBucketFallbacks() {
        var declared = new InfuserRecipe(testId("declared"), new ItemStack(Items.BUCKET),
                new FluidStack(Fluids.WATER, 2000), 4, new ItemStack(Items.DIAMOND));
        var adapter = new InfuserRecipeAdapter();
        assertTrue(adapter.getGeneratedRecipes(levelWith(declared)).stream()
                .noneMatch(holder -> holder.value().inputFluid.getFluid() == Fluids.WATER));
        var unknown = new InfuserRecipe(testId("unknown")) {};
        assertTrue(adapter.getGeneratedRecipes(levelWith(unknown)).isEmpty());
        assertTrue(adapter.convertAll(RecipeHolder.of(unknown), null).isEmpty());
    }

    @Test void precisionShapedConsumesOnePerOccupiedSlotNotDisplayCountOrToolDurability() {
        PrecisionCraftingTableConfig.powerPerTick = 70_000_000;
        Ingredient.Value[] inputs = shaped(item(marked(Items.IRON_INGOT, 32)), item(new ItemStack(Items.IRON_INGOT, 17)),
                item(new ItemStack(Items.IRON_PICKAXE)));
        var source = new PrecisionShapedRecipe(testId("precision_shaped"), inputs, marked(Items.DIAMOND, 2), 1.0f);
        var converted = new PrecisionShapedRecipeAdapter().convertAll(RecipeHolder.of(source), null).get(0);
        assertEquals(7_000_000_000L, converted.energy());
        assertEquals(100, converted.processTime());
        assertEquals(3L, converted.inputs().stream().mapToLong(input -> input.count()).sum());
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(),
                List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.IRON_PICKAXE)), 1L));
        assertEquals(1, converted.outputs().size(), "native table consumes tools and returns no container/durability remainder");
        assertEquals(2, converted.outputs().get(0).getCount());
        assertTrue(converted.catalyst().isEmpty());
    }

    @Test void fractionalPrecisionRollsAreNotTurnedIntoGuaranteedOutput() {
        var adapter = new PrecisionShapedRecipeAdapter();
        for (float chance : new float[]{0.0f, 0.5f, Float.NaN, Float.POSITIVE_INFINITY}) {
            var source = new PrecisionShapedRecipe(testId("chance"), shaped(item(new ItemStack(Items.IRON_INGOT))),
                    new ItemStack(Items.DIAMOND), chance);
            assertTrue(adapter.convertAll(RecipeHolder.of(source), null).isEmpty());
        }
        var clamped = new PrecisionShapedRecipe(testId("clamped"), shaped(item(new ItemStack(Items.IRON_INGOT))),
                new ItemStack(Items.DIAMOND), 2.0f);
        assertEquals(1, adapter.convertAll(RecipeHolder.of(clamped), null).size(), "native onFinish clamps chances above one");
    }

    @Test void fluidTablesKeepNativeZeroPowerOneTickAndExactNbtOnBothRecipeShapes() {
        FluidStack fluid = new FluidStack(Fluids.LAVA, 125);
        fluid.getOrCreateTag().putString("grade", "exact");
        var shaped = new ShapedRecipe(testId("fluid_shaped"), shaped(item(new ItemStack(Items.GOLD_INGOT))), fluid, marked(Items.EMERALD, 2));
        var shapeless = new ShapelessRecipe(testId("fluid_shapeless"), new Ingredient.Value[]{item(new ItemStack(Items.GOLD_INGOT))}, fluid, marked(Items.EMERALD, 2));
        for (var converted : List.of(
                new FluidShapedRecipeAdapter().convertAll(RecipeHolder.of(shaped), null).get(0),
                new FluidShapelessRecipeAdapter().convertAll(RecipeHolder.of(shapeless), null).get(0))) {
            assertEquals(0L, converted.energy());
            assertEquals(1, converted.processTime());
            assertEquals(125L, converted.inputFluids().get(0).amount());
            assertTrue(converted.inputFluids().get(0).ingredient().test(fluid));
            assertFalse(converted.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.LAVA, 125)));
            assertEquals("native", converted.outputs().get(0).getTag().getString("marker"));
            assertEquals(2, converted.outputs().get(0).getCount());
        }
    }

    @Test void repeatedShapelessRequirementsCannotReuseASingleSupply() {
        var source = new PrecisionShapelessRecipe(testId("repeated"), new Ingredient.Value[]{
                item(new ItemStack(Items.IRON_INGOT)), item(new ItemStack(Items.IRON_INGOT))}, new ItemStack(Items.DIAMOND), 1.0f);
        @SuppressWarnings("unchecked") InventoryComponent<net.yxiao233.ifeu.common.block.entity.PrecisionCraftingTableEntity> inventory = mock(InventoryComponent.class);
        when(inventory.getSlots()).thenReturn(2);
        when(inventory.getStackInSlot(0)).thenReturn(marked(Items.IRON_INGOT, 64));
        when(inventory.getStackInSlot(1)).thenReturn(ItemStack.EMPTY);
        assertFalse(source.matches(inventory), "native shapeless matching requires two occupied slots, not a display count");
        when(inventory.getStackInSlot(1)).thenReturn(new ItemStack(Items.IRON_INGOT));
        assertTrue(source.matches(inventory), "native item identity matching deliberately ignores NBT");
        var converted = new PrecisionShapelessRecipeAdapter().convertAll(RecipeHolder.of(source), null).get(0);
        assertEquals(2L, converted.inputs().get(0).count());
        assertFalse(ExtraUpgradesCraftingRecipeAdapterSupport.matches(converted, Map.of(Ingredient.of(Items.IRON_INGOT), 1L), Map.of()));
        assertTrue(ExtraUpgradesCraftingRecipeAdapterSupport.matches(converted, Map.of(Ingredient.of(Items.IRON_INGOT), 2L), Map.of()));
    }

    @Test void nativeGreedyShapelessAlternativesAreNotBroadenedIntoGlobalMatching() {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, testId("iron_or_gold"));
        var registry = BuiltInRegistries.ITEM;
        Map<TagKey<Item>, List<net.minecraft.core.Holder<Item>>> original = new java.util.LinkedHashMap<>();
        registry.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        Map<TagKey<Item>, List<net.minecraft.core.Holder<Item>>> updated = new java.util.LinkedHashMap<>(original);
        updated.put(tag, List.of(registry.wrapAsHolder(Items.IRON_INGOT), registry.wrapAsHolder(Items.GOLD_INGOT)));
        registry.bindTags(updated);
        try {
            var source = new PrecisionShapelessRecipe(testId("greedy"), new Ingredient.Value[]{new Ingredient.TagValue(tag),
                    item(new ItemStack(Items.IRON_INGOT))}, new ItemStack(Items.DIAMOND), 1.0f);
            assertTrue(new PrecisionShapelessRecipeAdapter().convertAll(RecipeHolder.of(source), null).isEmpty());
        } finally {
            original.putIfAbsent(tag, List.of());
            registry.bindTags(original);
            Ingredient.invalidateAll();
        }
    }

    @Test void unknownRecipesValuesAndCraftingCallbacksAreNotFrozenIntoStaticRecipes() {
        var dynamic = new PrecisionShapelessRecipe(testId("custom_recipe")) {};
        dynamic.inputs = new Ingredient.Value[]{item(new ItemStack(Items.IRON_INGOT))};
        dynamic.output = new ItemStack(Items.DIAMOND);
        dynamic.chance = 1.0f;
        assertTrue(new PrecisionShapelessRecipeAdapter().convertAll(RecipeHolder.of(dynamic), null).isEmpty());
        var callback = new PrecisionShapelessRecipe(testId("callback"));
        callback.inputs = dynamic.inputs;
        callback.chance = 1.0f;
        callback.output = new ItemStack(dynamicOutput);
        assertTrue(new PrecisionShapelessRecipeAdapter().convertAll(RecipeHolder.of(callback), null).isEmpty());
        assertFalse(callback.output.hasTag(), "catalog conversion must not execute onCraftedBy");
        var customValue = mock(Ingredient.Value.class);
        callback.output = new ItemStack(Items.DIAMOND);
        callback.inputs = new Ingredient.Value[]{customValue};
        assertTrue(new PrecisionShapelessRecipeAdapter().convertAll(RecipeHolder.of(callback), null).isEmpty());
        verifyNoInteractions(customValue);
    }

    @Test void lookupReadsOnlyTheLiveManagerAndNotTheNativeDatagenList() {
        var source = new PrecisionShapelessRecipe(testId("live"), new Ingredient.Value[]{item(new ItemStack(Items.IRON_INGOT))},
                new ItemStack(Items.DIAMOND), 1.0f);
        var adapter = new PrecisionShapelessRecipeAdapter();
        Level level = levelWith();
        Map<Ingredient, Long> supplied = Map.of(Ingredient.of(Items.IRON_INGOT), 1L);
        assertTrue(adapter.findMatchingRecipes(level, supplied, Map.of(), adapter.getMoldItem()).isEmpty());
        when(level.getRecipeManager().getRecipes()).thenReturn(List.of(source));
        assertEquals(1, adapter.findMatchingRecipes(level, supplied, Map.of(), adapter.getMoldItem()).size());
        when(level.getRecipeManager().getRecipes()).thenReturn(List.of());
        assertTrue(adapter.findMatchingRecipes(level, supplied, Map.of(), adapter.getMoldItem()).isEmpty());
    }

    @Test void nativeShapedRequiresNineEntriesIncludingTheRegisteredHoleMarker() {
        var source = new PrecisionShapedRecipe(testId("short_shape"), new Ingredient.Value[]{item(new ItemStack(Items.IRON_INGOT))},
                new ItemStack(Items.DIAMOND), 1.0f);
        assertEquals(1, source.inputs.length);
        assertTrue(new PrecisionShapedRecipeAdapter().convertAll(RecipeHolder.of(source), null).isEmpty());
        source.inputs = shaped(item(new ItemStack(Items.IRON_INGOT)));
        assertEquals(1, new PrecisionShapedRecipeAdapter().convertAll(RecipeHolder.of(source), null).size());
    }

    private static Ingredient.ItemValue item(ItemStack stack) { return new Ingredient.ItemValue(stack); }
    private static Ingredient.Value[] shaped(Ingredient.Value... occupied) {
        Ingredient.Value[] values = new Ingredient.Value[9];
        Arrays.fill(values, item(new ItemStack(air)));
        System.arraycopy(occupied, 0, values, 0, occupied.length);
        return values;
    }
    private static ItemStack marked(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", "native");
        return stack;
    }
    private static ResourceLocation id(String name) { return new ResourceLocation("ifeu", name); }
    private static ResourceLocation testId(String name) { return id("migration_test/" + name); }
    private static Level levelWith(net.minecraft.world.item.crafting.Recipe<?>... recipes) {
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of(recipes));
        return level;
    }
    private static AdvancedAlloyFurnaceRecipe roundTrip(AdvancedAlloyFurnaceRecipe recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AdvancedAlloyFurnaceRecipe.STREAM_CODEC.encode(buffer, recipe);
            return AdvancedAlloyFurnaceRecipe.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }
}
