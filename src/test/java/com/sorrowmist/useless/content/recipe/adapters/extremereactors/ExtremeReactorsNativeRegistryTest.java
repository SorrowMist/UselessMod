package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.zerono.mods.extremereactors.api.reactor.ReactantMappingsRegistry;
import it.zerono.mods.extremereactors.api.reactor.ReactantType;
import it.zerono.mods.extremereactors.api.reactor.ReactantsRegistry;
import it.zerono.mods.extremereactors.api.reactor.Reaction;
import it.zerono.mods.extremereactors.api.reactor.ReactionsRegistry;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sorrowmist.useless.content.recipe.adapters.extremereactors.ExtremeReactorsNativeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtremeReactorsNativeRegistryTest {
    private static final String FUEL = "useless_er_native_fuel";
    private static final String WASTE = "useless_er_native_waste";
    private final List<TagKey<Item>> solidMappings = new ArrayList<>();
    private final List<TagKey<Fluid>> fluidMappings = new ArrayList<>();
    private Map<TagKey<Item>, List<Holder<Item>>> itemTags;
    private Map<TagKey<Fluid>, List<Holder<Fluid>>> fluidTags;

    @BeforeAll static void bootstrap() throws Exception {
        ExtremeReactorsNativeFixtures.initialize();
    }

    @BeforeEach void saveTags() {
        itemTags = tags(BuiltInRegistries.ITEM);
        fluidTags = tags(BuiltInRegistries.FLUID);
    }

    @AfterEach void removeOnlyFixtureRegistryEntriesAndRestoreTags() {
        asNativeMod(() -> {
            ReactionsRegistry.remove(FUEL);
            solidMappings.forEach(ReactantMappingsRegistry::removeSolid);
            fluidMappings.forEach(ReactantMappingsRegistry::removeFluid);
            ReactantsRegistry.remove(FUEL);
            ReactantsRegistry.remove(WASTE);
        });
        BuiltInRegistries.ITEM.bindTags(itemTags);
        BuiltInRegistries.FLUID.bindTags(fluidTags);
        refreshTags();
    }

    @Test void realRegistryReactionUsesExactNativeReactantAndWasteYieldsAndNativeSyntheticId() {
        registerReaction();
        var fuelTag = solid(FUEL, 1000, "fuel", Items.IRON_INGOT);
        var wasteTag = solid(WASTE, 600, "waste", Items.GOLD_INGOT);
        var adapter = new ExtremeReactorsRecipeAdapter();
        var holders = generated(adapter);
        assertEquals(1, holders.size());
        var holder = holders.get(0);
        var converted = adapter.convertAll(holder, null).get(0);
        assertEquals(holder.id(), holder.value().getId()); // real ZeroCore ModRecipe(ResourceLocation)
        assertEquals(holder.id(), converted.id());
        assertEquals(3L, converted.inputs().get(0).count());
        assertTrue(converted.inputs().get(0).ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(5, converted.outputs().get(0).getCount());
        assertTrue(converted.outputs().get(0).is(Items.GOLD_INGOT));

        var nativeFuel = ReactantsRegistry.get(FUEL).orElseThrow();
        var nativeWaste = ReactantsRegistry.get(WASTE).orElseThrow();
        var reaction = ReactionsRegistry.get(nativeFuel).orElseThrow();
        var inputMapping = ReactantMappingsRegistry.getFromSolid(new ItemStack(Items.IRON_INGOT)).orElseThrow();
        var outputMapping = ReactantMappingsRegistry.getToSolid(nativeWaste).orElseThrow().get(0);
        assertEquals(fuelTag, inputMapping.getSource());
        assertEquals(wasteTag, outputMapping.getProduct());
        int fuelUnits = inputMapping.getProductAmount(3);
        assertEquals(3000, fuelUnits);
        assertEquals(5, outputMapping.getProductAmount(reaction.getProductAmount(fuelUnits)));
        assertEquals(2000L, converted.energy());
        assertEquals(20, converted.processTime());
        assertEquals(AlloyFurnaceMode.NORMAL, converted.mode());
        assertTrue(converted.catalyst().isEmpty());
        assertTrue(converted.mold().test(mold("basic_reactorcontroller")));
        assertTrue(converted.mold().test(mold("reinforced_reactorcontroller")));
        assertFalse(converted.mold().test(mold("reprocessorcontroller")));
    }

    @Test void nativeSolidAndFluidMappingsGenerateAllFourPhysicalPathsWithoutInventingRatios() {
        registerReaction();
        solid(FUEL, 1000, "fuel", Items.IRON_INGOT);
        solid(WASTE, 500, "waste", Items.GOLD_INGOT);
        fluid(FUEL, "fluid_fuel", Fluids.WATER);
        fluid(WASTE, "fluid_waste", Fluids.LAVA);
        var adapter = new ExtremeReactorsRecipeAdapter();
        List<AdvancedAlloyFurnaceRecipe> recipes = generated(adapter).stream()
                .map(holder -> adapter.convertAll(holder, null).get(0)).toList();
        assertEquals(4, recipes.size());
        assertPath(recipes, false, false, 1L, 2);
        assertPath(recipes, false, true, 1L, 1000);
        assertPath(recipes, true, false, 500L, 1);
        assertPath(recipes, true, true, 1L, 1);
    }

    @Test void firstCallBeforeRegistrationOrTagsDoesNotPoisonLaterLoadsAndWasteTagReloads() {
        var adapter = new ExtremeReactorsRecipeAdapter();
        assertTrue(generated(adapter).isEmpty());
        registerReaction();
        TagKey<Item> input = TagKey.create(Registries.ITEM, id("late_fuel"));
        TagKey<Item> output = TagKey.create(Registries.ITEM, id("late_waste"));
        solidMappings.addAll(List.of(input, output));
        asNativeMod(() -> {
            ReactantMappingsRegistry.registerSolid(FUEL, 1000, input);
            ReactantMappingsRegistry.registerSolid(WASTE, 1000, output);
        });
        assertTrue(generated(adapter).isEmpty());
        bind(BuiltInRegistries.ITEM, input, Items.IRON_INGOT);
        assertTrue(generated(adapter).isEmpty());
        bind(BuiltInRegistries.ITEM, output, Items.GOLD_INGOT);
        assertEquals(1, generated(adapter).size());
        assertTrue(convert(adapter, generated(adapter).get(0)).outputs().get(0).is(Items.GOLD_INGOT));
        bind(BuiltInRegistries.ITEM, output, Items.DIAMOND);
        assertTrue(convert(adapter, generated(adapter).get(0)).outputs().get(0).is(Items.DIAMOND));
        asNativeMod(() -> ReactionsRegistry.remove(FUEL));
        assertTrue(generated(adapter).isEmpty());
        asNativeMod(() -> ReactionsRegistry.register(FUEL, WASTE, Reaction.STANDARD_REACTIVITY, Reaction.STANDARD_FISSIONRATE));
        assertEquals(1, generated(adapter).size());
        asNativeMod(() -> ReactantMappingsRegistry.removeSolid(output));
        assertTrue(generated(adapter).isEmpty());
        asNativeMod(() -> ReactantMappingsRegistry.registerSolid(WASTE, 2000, output));
        assertTrue(generated(adapter).isEmpty()); // the native output cache also awaits its tag event
        refreshTags();
        assertEquals(2L, convert(adapter, generated(adapter).get(0)).inputs().get(0).count());
    }

    @Test void sortedNativeMappingIdsAreStableAcrossRegistrationOrder() {
        registerReaction();
        var firstFuel = solid(FUEL, 1000, "a_fuel", Items.IRON_INGOT);
        var secondFuel = solid(FUEL, 9000, "z_fuel", Items.IRON_BLOCK);
        var firstWaste = solid(WASTE, 500, "a_waste", Items.GOLD_INGOT);
        var secondWaste = solid(WASTE, 4500, "z_waste", Items.GOLD_BLOCK);
        var adapter = new ExtremeReactorsRecipeAdapter();
        Map<ResourceLocation, String> before = fingerprint(adapter);
        assertEquals(4, before.size());
        asNativeMod(() -> {
            for (var tag : solidMappings) ReactantMappingsRegistry.removeSolid(tag);
            ReactantMappingsRegistry.registerSolid(WASTE, 4500, secondWaste);
            ReactantMappingsRegistry.registerSolid(WASTE, 500, firstWaste);
            ReactantMappingsRegistry.registerSolid(FUEL, 9000, secondFuel);
            ReactantMappingsRegistry.registerSolid(FUEL, 1000, firstFuel);
        });
        refreshTags();
        assertEquals(before, fingerprint(adapter));
    }

    @Test void zeroNativeMappingsAndIntOverflowBatchesAreExplicitlyRejected() {
        registerReaction();
        var input = solid(FUEL, 0, "invalid_fuel", Items.IRON_INGOT);
        var output = solid(WASTE, 1000, "invalid_waste", Items.GOLD_INGOT);
        var adapter = new ExtremeReactorsRecipeAdapter();
        assertTrue(generated(adapter).isEmpty());
        asNativeMod(() -> {
            ReactantMappingsRegistry.removeSolid(input);
            ReactantMappingsRegistry.removeSolid(output);
            ReactantMappingsRegistry.registerSolid(FUEL, Integer.MAX_VALUE, input);
            ReactantMappingsRegistry.registerSolid(WASTE, Integer.MAX_VALUE - 1, output);
        });
        refreshTags();
        assertTrue(generated(adapter).isEmpty());
    }

    @Test void overlappingTagsMustAgreeWithNativeForwardLookupRatherThanGrantASecondYield() {
        registerReaction();
        solid(FUEL, 1000, "native_first_fuel", Items.IRON_INGOT);
        solid(FUEL, 2000, "ambiguous_second_fuel", Items.IRON_INGOT);
        solid(WASTE, 600, "waste", Items.GOLD_INGOT);
        var adapter = new ExtremeReactorsRecipeAdapter();
        var holders = generated(adapter);
        assertEquals(1, holders.size());
        var converted = convert(adapter, holders.get(0));
        assertEquals(3L, converted.inputs().get(0).count());
        assertEquals(5, converted.outputs().get(0).getCount());
    }

    @Test void inactiveNativeReactionsAreNotStaticWasteFactories() {
        registerReaction();
        solid(FUEL, 1000, "fuel", Items.IRON_INGOT);
        solid(WASTE, 1000, "waste", Items.GOLD_INGOT);
        asNativeMod(() -> ReactionsRegistry.register(FUEL, WASTE, Reaction.STANDARD_REACTIVITY, 0.0F));
        assertTrue(generated(new ExtremeReactorsRecipeAdapter()).isEmpty());
    }

    private static void registerReaction() {
        asNativeMod(() -> {
            ReactantsRegistry.registerFuel(FUEL, 0xffffff, 1.0F, 1.0F, 1.0F, "test.fuel");
            ReactantsRegistry.register(WASTE, ReactantType.Waste, 0xffffff, "test.waste");
            ReactionsRegistry.register(FUEL, WASTE, Reaction.STANDARD_REACTIVITY, Reaction.STANDARD_FISSIONRATE);
        });
        assertTrue(ReactantsRegistry.contains(FUEL));
        assertTrue(ReactionsRegistry.get(ReactantsRegistry.get(FUEL).orElseThrow()).isPresent());
    }

    private TagKey<Item> solid(String reactant, int amount, String path, Item item) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id(path));
        solidMappings.add(tag);
        asNativeMod(() -> ReactantMappingsRegistry.registerSolid(reactant, amount, tag));
        bind(BuiltInRegistries.ITEM, tag, item);
        return tag;
    }

    private TagKey<Fluid> fluid(String reactant, String path, Fluid fluid) {
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id(path));
        fluidMappings.add(tag);
        asNativeMod(() -> ReactantMappingsRegistry.registerFluid(reactant, tag));
        bind(BuiltInRegistries.FLUID, tag, fluid);
        return tag;
    }

    private static List<RecipeHolder<ModRecipe>> generated(ExtremeReactorsRecipeAdapter adapter) {
        return adapter.getGeneratedRecipes(null).stream()
                .filter(holder -> holder.id().getPath().startsWith("reactor/" + FUEL + "_to_"))
                .toList();
    }

    private static AdvancedAlloyFurnaceRecipe convert(ExtremeReactorsRecipeAdapter adapter, RecipeHolder<ModRecipe> holder) {
        return adapter.convertAll(holder, null).get(0);
    }

    private static Map<ResourceLocation, String> fingerprint(ExtremeReactorsRecipeAdapter adapter) {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        for (var holder : generated(adapter)) {
            var recipe = convert(adapter, holder);
            var input = recipe.inputs().get(0);
            var output = recipe.outputs().get(0);
            result.put(holder.id(), input.ingredient().toJson() + ":" + input.count() + "->"
                    + BuiltInRegistries.ITEM.getKey(output.getItem()) + ":" + output.getCount());
        }
        return result;
    }

    private static void assertPath(List<AdvancedAlloyFurnaceRecipe> recipes, boolean fluidInput,
                                   boolean fluidOutput, long inputAmount, int outputAmount) {
        var recipe = recipes.stream().filter(value -> !value.inputFluids().isEmpty() == fluidInput
                && !value.outputFluids().isEmpty() == fluidOutput).findFirst().orElseThrow();
        assertEquals(inputAmount, fluidInput ? recipe.inputFluids().get(0).amount() : recipe.inputs().get(0).count());
        assertEquals(outputAmount, fluidOutput ? recipe.outputFluids().get(0).getAmount() : recipe.outputs().get(0).getCount());
        if (fluidInput) assertTrue(recipe.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.WATER, 1)));
    }
}
