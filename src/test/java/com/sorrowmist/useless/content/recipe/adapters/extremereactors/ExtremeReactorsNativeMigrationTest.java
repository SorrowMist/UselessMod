package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerFluidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.recipe.ReprocessorRecipe;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;
import it.zerono.mods.zerocore.lib.recipe.ingredient.FluidStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.ingredient.ItemStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.result.FluidStackRecipeResult;
import it.zerono.mods.zerocore.lib.recipe.result.ItemStackRecipeResult;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.sorrowmist.useless.content.recipe.adapters.extremereactors.ExtremeReactorsNativeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ExtremeReactorsNativeMigrationTest {
    private Map<TagKey<Item>, List<Holder<Item>>> itemTags;
    private Map<TagKey<Fluid>, List<Holder<Fluid>>> fluidTags;
    private int originalEnergyPerTick;

    @BeforeAll static void bootstrap() throws Exception {
        ExtremeReactorsNativeFixtures.initialize();
    }

    @BeforeEach void saveTags() {
        itemTags = tags(BuiltInRegistries.ITEM);
        fluidTags = tags(BuiltInRegistries.FLUID);
        originalEnergyPerTick = Config.COMMON.fluidizer.energyPerRecipeTick.get();
        Config.COMMON.fluidizer.energyPerRecipeTick.set(25);
    }

    @AfterEach void restoreTagsAndConfig() {
        BuiltInRegistries.ITEM.bindTags(itemTags);
        BuiltInRegistries.FLUID.bindTags(fluidTags);
        refreshTags();
        Config.COMMON.fluidizer.energyPerRecipeTick.set(originalEnergyPerTick);
    }

    @Test void nativeReprocessorPreservesCountsStrictNbtOutputAndGameplayConstraints() throws Exception {
        ItemStack waste = new ItemStack(Items.GOLD_INGOT, 2);
        waste.getOrCreateTag().putString("isotope", "spent");
        FluidStack coolant = new FluidStack(Fluids.WATER, 750);
        coolant.getOrCreateTag().putString("grade", "pure");
        ItemStack output = new ItemStack(Items.EMERALD, 3);
        output.getOrCreateTag().putString("isotope", "recovered");
        var source = recipe(ReprocessorRecipe.class, "reprocessor", ItemStackRecipeIngredient.from(waste),
                FluidStackRecipeIngredient.from(coolant), ItemStackRecipeResult.from(output));
        var converted = convert(source);

        assertEquals(id("reprocessor_converted"), converted.id());
        assertEquals(2L, converted.inputs().get(0).count());
        assertTrue(converted.inputs().get(0).ingredient().test(waste));
        assertFalse(converted.inputs().get(0).ingredient().test(new ItemStack(Items.GOLD_INGOT, 2)));
        assertEquals(750L, converted.inputFluids().get(0).amount());
        assertTrue(converted.inputFluids().get(0).ingredient().test(coolant));
        assertFalse(converted.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.WATER, 750)));
        assertEquals(3, converted.outputs().get(0).getCount());
        output.getOrCreateTag().putString("isotope", "mutated");
        assertEquals("recovered", converted.outputs().get(0).getTag().getString("isotope"));
        assertEquals(1000L, converted.energy());
        assertEquals(200, converted.processTime());
        assertEquals(AlloyFurnaceMode.NORMAL, converted.mode());
        assertTrue(converted.catalyst().isEmpty());
        assertEquals(0, converted.catalystUses());
        assertTrue(converted.mold().test(mold("reprocessorcontroller")));
        assertFalse(converted.mold().test(mold("fluidizercontroller")));
        var decoded = roundTrip(converted);
        assertEquals(2L, decoded.inputs().get(0).count());
        assertTrue(decoded.inputs().get(0).ingredient().test(waste));
        assertFalse(decoded.inputs().get(0).ingredient().test(new ItemStack(Items.GOLD_INGOT, 2)));
        assertEquals(750L, decoded.inputFluids().get(0).amount());
        assertTrue(decoded.inputFluids().get(0).ingredient().test(coolant));
        assertFalse(decoded.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.WATER, 750)));
        assertEquals("recovered", decoded.outputs().get(0).getTag().getString("isotope"));
    }

    @Test void nativeNbtSensitiveInputsAlsoRemainExactWhenSuppliedOnlyAsAeKeys() throws Exception {
        ItemStack waste = new ItemStack(Items.GOLD_INGOT, 2);
        waste.getOrCreateTag().putString("isotope", "spent");
        FluidStack coolant = new FluidStack(Fluids.WATER, 750);
        coolant.getOrCreateTag().putString("grade", "pure");
        var source = recipe(ReprocessorRecipe.class, "ae_nbt", ItemStackRecipeIngredient.from(waste),
                FluidStackRecipeIngredient.from(coolant), ItemStackRecipeResult.from(Items.EMERALD));
        assertTrue(source.test(waste, coolant));
        var adapter = new ExtremeReactorsRecipeAdapter();
        var level = levelWith(source);
        var itemKey = AEItemKey.of(waste);
        var fluidKey = AEFluidKey.of(coolant);
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(),
                Map.of(itemKey, 2L, fluidKey, 750L), mold("reprocessorcontroller")).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(),
                Map.of(AEItemKey.of(new ItemStack(Items.GOLD_INGOT)), 2L, fluidKey, 750L),
                mold("reprocessorcontroller")).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(),
                Map.of(itemKey, 2L, AEFluidKey.of(new FluidStack(Fluids.WATER, 1)), 750L),
                mold("reprocessorcontroller")).isEmpty());
    }

    @Test void nativeSolidFluidizerUsesConfiguredEnergyAndCeilingOutputMultiplierWithoutAliasingNbt() throws Exception {
        FluidStack output = new FluidStack(Fluids.LAVA, 1501);
        output.getOrCreateTag().putString("isotope", "liquid");
        var source = recipe(FluidizerSolidRecipe.class, "solid", ItemStackRecipeIngredient.from(Items.IRON_INGOT, 3),
                FluidStackRecipeResult.from(output));
        assertEquals(2, source.getEnergyUsageMultiplier());
        assertEquals(40, source.getRecipeType().getTicks());
        Config.COMMON.fluidizer.energyPerRecipeTick.set(37);
        var converted = convert(source);
        assertEquals(3L, converted.inputs().get(0).count());
        assertEquals(37L * 2L * 40L, converted.energy());
        assertEquals(400, converted.processTime());
        assertTrue(converted.mold().test(mold("fluidizercontroller")));
        assertEquals(1501, converted.outputFluids().get(0).getAmount());
        output.getOrCreateTag().putString("isotope", "changed");
        assertEquals("liquid", converted.outputFluids().get(0).getTag().getString("isotope"));
        assertEquals(37L * 2L * 40L, convert(source).energy());
        var decoded = roundTrip(converted);
        assertEquals(1501, decoded.outputFluids().get(0).getAmount());
        assertEquals("liquid", decoded.outputFluids().get(0).getTag().getString("isotope"));
    }

    @Test void nativeSolidMixingDoesNotReuseOverlappingSupplies() throws Exception {
        var source = recipe(FluidizerSolidMixingRecipe.class, "solid_mixing",
                ItemStackRecipeIngredient.from(Items.IRON_INGOT, 2),
                ItemStackRecipeIngredient.from(Items.IRON_INGOT, 2), fluidResult(2000));
        var converted = convert(source);
        assertEquals(2, converted.inputs().size());
        assertFalse(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.IRON_INGOT, 3)), 1L));
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(), List.of(new ItemStack(Items.IRON_INGOT, 4)), 1L));
        assertEquals(25L * 2L * 80L, converted.energy());
        assertEquals(800, converted.processTime());
    }

    @Test void nativeUniformItemAlternativesRetainStrictNbtAndLiveTagsInsteadOfDisplayStacks() throws Exception {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id("alternative_items"));
        bind(BuiltInRegistries.ITEM, tag, Items.COPPER_INGOT);
        ItemStack tagged = new ItemStack(Items.GOLD_INGOT);
        tagged.getOrCreateTag().putString("grade", "pure");
        var ingredient = ItemStackRecipeIngredient.from(
                ItemStackRecipeIngredient.from(tagged, 4), ItemStackRecipeIngredient.from(tag, 4));
        var source = recipe(FluidizerSolidRecipe.class, "item_alternatives", ingredient, fluidResult(1000));
        var converted = convert(source).inputs().get(0);
        assertEquals(4L, converted.count());
        assertTrue(converted.ingredient().test(tagged));
        assertFalse(converted.ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertTrue(converted.ingredient().test(new ItemStack(Items.COPPER_INGOT)));
        bind(BuiltInRegistries.ITEM, tag, Items.DIAMOND);
        // Vanilla Ingredient checks Forge's invalidation counter in getStackingIds, not test.
        // Exercise that native refresh path rather than expecting a custom live-tag predicate.
        converted.ingredient().getStackingIds();
        assertFalse(converted.ingredient().test(new ItemStack(Items.COPPER_INGOT)));
        assertTrue(converted.ingredient().test(new ItemStack(Items.DIAMOND)));
        assertEquals(ingredient.isCompatible(tagged), converted.ingredient().test(tagged));
    }

    @Test void nativeFluidTagsSurviveEarlyEmptyDisplayCachesReloadsAndWireSerialization() throws Exception {
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id("coolants"));
        bind(BuiltInRegistries.FLUID, tag);
        var ingredient = FluidStackRecipeIngredient.from(tag, 700);
        assertTrue(ingredient.getMatchingElements().isEmpty()); // deliberately poison the native display cache
        var source = recipe(FluidizerFluidMixingRecipe.class, "fluid_tags", ingredient,
                FluidStackRecipeIngredient.from(Fluids.WATER, 300), fluidResult(1000));
        var converted = convert(source);
        assertEquals(700L, converted.inputFluids().get(0).amount());
        var live = converted.inputFluids().get(0).ingredient();
        bind(BuiltInRegistries.FLUID, tag, Fluids.WATER);
        assertTrue(live.test(new FluidStack(Fluids.WATER, 700)));
        bind(BuiltInRegistries.FLUID, tag, Fluids.LAVA);
        assertTrue(ingredient.getMatchingElements().isEmpty());
        assertTrue(live.test(new FluidStack(Fluids.LAVA, 700)));
        assertFalse(live.test(new FluidStack(Fluids.WATER, 700)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SizedFluidIngredient.STREAM_CODEC.encode(buffer, new SizedFluidIngredient(live, 700));
            var decoded = SizedFluidIngredient.STREAM_CODEC.decode(buffer);
            assertEquals(700, decoded.amount());
            assertTrue(decoded.ingredient().test(new FluidStack(Fluids.LAVA, 700)));
            bind(BuiltInRegistries.FLUID, tag, Fluids.WATER);
            assertFalse(decoded.ingredient().test(new FluidStack(Fluids.LAVA, 700)));
            assertTrue(decoded.ingredient().test(new FluidStack(Fluids.WATER, 700)));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void nativeFluidAlternativesRetainExactNbtIncludingAbsenceOfNbt() throws Exception {
        FluidStack tagged = new FluidStack(Fluids.WATER, 500);
        tagged.getOrCreateTag().putString("grade", "pure");
        var ingredient = FluidStackRecipeIngredient.from(FluidStackRecipeIngredient.from(tagged),
                FluidStackRecipeIngredient.from(Fluids.LAVA, 500));
        var source = recipe(FluidizerFluidMixingRecipe.class, "fluid_alternatives", ingredient,
                FluidStackRecipeIngredient.from(Fluids.WATER, 250), fluidResult(750));
        var converted = convert(source).inputFluids().get(0);
        assertEquals(500L, converted.amount());
        for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
            for (boolean nbt : List.of(false, true)) {
                FluidStack stack = new FluidStack(fluid, 500);
                if (nbt) stack.getOrCreateTag().putString("grade", "pure");
                assertEquals(ingredient.isCompatible(stack), converted.ingredient().test(stack));
            }
        }
        var exactUntagged = convert(source).inputFluids().get(1).ingredient();
        assertTrue(exactUntagged.test(new FluidStack(Fluids.WATER, 250)));
        assertFalse(exactUntagged.test(tagged));
    }

    @Test void nativeUnrepresentableOrEmptyInputsAndOutputsAreRejectedRatherThanMadeFree() throws Exception {
        var itemAlternatives = ItemStackRecipeIngredient.from(ItemStackRecipeIngredient.from(Items.IRON_INGOT, 1),
                ItemStackRecipeIngredient.from(Items.GOLD_INGOT, 5));
        var fluidAlternatives = FluidStackRecipeIngredient.from(FluidStackRecipeIngredient.from(Fluids.WATER, 1),
                FluidStackRecipeIngredient.from(Fluids.LAVA, 5));
        var adapter = new ExtremeReactorsRecipeAdapter();
        for (ModRecipe source : List.of(
                recipe(FluidizerSolidRecipe.class, "variable_items", itemAlternatives, fluidResult(1000)),
                recipe(FluidizerFluidMixingRecipe.class, "variable_fluids", fluidAlternatives,
                        FluidStackRecipeIngredient.from(Fluids.WATER, 250), fluidResult(1000)),
                recipe(FluidizerSolidRecipe.class, "empty_item", ItemStackRecipeIngredient.from(Ingredient.EMPTY), fluidResult(1000)),
                recipe(FluidizerSolidRecipe.class, "zero_item", ItemStackRecipeIngredient.from(Items.IRON_INGOT, 0), fluidResult(1000)),
                recipe(FluidizerSolidRecipe.class, "empty_output", ItemStackRecipeIngredient.from(Items.IRON_INGOT),
                        FluidStackRecipeResult.from(FluidStack.EMPTY)),
                recipe(ModRecipe.class, "unknown_native_recipe"))) {
            assertTrue(adapter.convertAll(RecipeHolder.of(source), null).isEmpty(), source.getId().toString());
        }
        Config.COMMON.fluidizer.energyPerRecipeTick.set(0);
        assertTrue(adapter.convertAll(RecipeHolder.of(recipe(FluidizerSolidRecipe.class, "invalid_energy",
                ItemStackRecipeIngredient.from(Items.IRON_INGOT), fluidResult(1000))), null).isEmpty());
    }

    @Test void nativeItemLookupKeepsLongAeMergedSuppliesEvenWhenActualStacksArePresent() throws Exception {
        var source = recipe(FluidizerSolidMixingRecipe.class, "long_items",
                ItemStackRecipeIngredient.from(Items.IRON_INGOT, Integer.MAX_VALUE),
                ItemStackRecipeIngredient.from(Items.IRON_INGOT, Integer.MAX_VALUE), fluidResult(1000));
        var level = levelWith(source);
        var adapter = new ExtremeReactorsRecipeAdapter();
        var key = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        Ingredient exact = DataComponentIngredient.of(true, key.toStack(1));
        long total = 2L * Integer.MAX_VALUE;
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(exact, total), Map.of(), Map.of(key, total - 1L),
                mold("fluidizercontroller"), List.of(new ItemStack(Items.IRON_INGOT))).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(exact, total - 1L), Map.of(), Map.of(key, total - 2L),
                mold("fluidizercontroller"), List.of(new ItemStack(Items.IRON_INGOT))).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(exact, total), Map.of(), Map.of(key, total),
                mold("reprocessorcontroller")).isEmpty());
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(key, total - 1L),
                mold("fluidizercontroller"), List.of(new ItemStack(Items.IRON_INGOT))).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(key, total - 2L),
                mold("fluidizercontroller"), List.of(new ItemStack(Items.IRON_INGOT))).isEmpty());
    }

    @Test void nativeFluidLookupKeepsLongAeMergedAmountsWithoutClampingOrCountingKeysTwice() throws Exception {
        var source = recipe(FluidizerFluidMixingRecipe.class, "long_fluids",
                FluidStackRecipeIngredient.from(Fluids.WATER, Integer.MAX_VALUE),
                FluidStackRecipeIngredient.from(Fluids.WATER, Integer.MAX_VALUE), fluidResult(1000));
        var adapter = new ExtremeReactorsRecipeAdapter();
        var level = levelWith(source);
        FluidStack representative = new FluidStack(Fluids.WATER, 1);
        var key = AEFluidKey.of(representative);
        long total = 2L * Integer.MAX_VALUE;
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(representative, total),
                Map.of(key, total), mold("fluidizercontroller")).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(representative, total - 1L),
                Map.of(key, total - 1L), mold("fluidizercontroller")).isEmpty());
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(key, total),
                mold("fluidizercontroller")).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(key, total - 1L),
                mold("fluidizercontroller")).isEmpty());
    }

    @Test void lookupSeesReplacementNativeRecipeListsAndRetainsRuntimeNamespace() throws Exception {
        var first = recipe(ReprocessorRecipe.class, "runtime_first", ItemStackRecipeIngredient.from(Items.IRON_INGOT),
                FluidStackRecipeIngredient.from(Fluids.WATER, 250), ItemStackRecipeResult.from(Items.GOLD_INGOT));
        var second = recipe(ReprocessorRecipe.class, "runtime_second", ItemStackRecipeIngredient.from(Items.IRON_INGOT),
                FluidStackRecipeIngredient.from(Fluids.WATER, 250), ItemStackRecipeResult.from(Items.EMERALD));
        var level = levelWith(first);
        var adapter = new ExtremeReactorsRecipeAdapter();
        var items = Map.of(Ingredient.of(Items.IRON_INGOT), 1L);
        var fluids = Map.of(new FluidStack(Fluids.WATER, 1), 250L);
        assertEquals(first.getId(), adapter.findMatchingRecipes(level, items, fluids, mold("reprocessorcontroller")).get(0).id());
        when(level.getRecipeManager().getRecipes()).thenReturn(List.of(second));
        var matches = adapter.findMatchingRecipes(level, items, fluids, mold("reprocessorcontroller"));
        assertEquals(1, matches.size());
        assertEquals(second.getId(), matches.get(0).id());
        assertEquals(id("runtime_second_converted"), adapter.convertAll(matches.get(0), level).get(0).id());
    }

    private static AdvancedAlloyFurnaceRecipe roundTrip(AdvancedAlloyFurnaceRecipe recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AdvancedAlloyFurnaceRecipe.STREAM_CODEC.encode(buffer, recipe);
            var decoded = AdvancedAlloyFurnaceRecipe.STREAM_CODEC.decode(buffer);
            assertEquals(recipe.id(), decoded.id());
            assertEquals(recipe.energy(), decoded.energy());
            assertEquals(recipe.processTime(), decoded.processTime());
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static FluidStackRecipeResult fluidResult(int amount) {
        return FluidStackRecipeResult.from(new FluidStack(Fluids.LAVA, amount));
    }

    private static AdvancedAlloyFurnaceRecipe convert(ModRecipe source) {
        var recipes = new ExtremeReactorsRecipeAdapter().convertAll(RecipeHolder.of(source), null);
        assertEquals(1, recipes.size(), source.getId().toString());
        return recipes.get(0);
    }
}
