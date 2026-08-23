package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ExtremeReactorsRecipeAdapterTest {
    private static final ExtremeReactorsRecipeAdapter ADAPTER = new ExtremeReactorsRecipeAdapter();
    private static Unsafe unsafe;

    @BeforeAll
    static void initializeTestRuntime() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);

        Method register = Class.forName(
                "it.zerono.mods.extremereactors.gamecontent.ReactorGameData")
                .getDeclaredMethod("register");
        register.setAccessible(true);
        register.invoke(null);
    }

    @Test
    void convertsFluidizerRecipeFamiliesWithSourceCosts() {
        FluidStack output = new FluidStack(fluid("yellorium"), 2_000);
        FluidizerSolidRecipe solid = new FluidizerSolidRecipe(
                ItemStackRecipeIngredient.from(Ingredient.of(Items.IRON_INGOT), 2),
                FluidStackRecipeResult.from(output));
        FluidizerSolidMixingRecipe solidMixing = new FluidizerSolidMixingRecipe(
                ItemStackRecipeIngredient.from(Ingredient.of(Items.IRON_INGOT), 2),
                ItemStackRecipeIngredient.from(Ingredient.of(Items.GOLD_INGOT), 1),
                FluidStackRecipeResult.from(output));
        FluidizerFluidMixingRecipe fluidMixing = new FluidizerFluidMixingRecipe(
                FluidStackRecipeIngredient.from(new FluidStack(fluid("yellorium"), 1_000)),
                FluidStackRecipeIngredient.from(new FluidStack(fluid("cyanite"), 500)),
                FluidStackRecipeResult.from(output));

        AdvancedAlloyFurnaceRecipe solidResult = convert("solid", solid);
        AdvancedAlloyFurnaceRecipe solidMixingResult = convert("solid_mixing", solidMixing);
        AdvancedAlloyFurnaceRecipe fluidMixingResult = convert("fluid_mixing", fluidMixing);

        assertEquals(2L, solidResult.inputs().getFirst().count());
        assertEquals(2_000, solidResult.outputFluids().getFirst().getAmount());
        assertEquals(400, solidResult.processTime());
        assertEquals((long) Config.COMMON.fluidizer.energyPerRecipeTick.get() * 2 * 40,
                solidResult.energy());
        assertTrue(solidResult.mold().test(new ItemStack(item("fluidizercontroller"))));

        assertEquals(2, solidMixingResult.inputs().size());
        assertEquals(800, solidMixingResult.processTime());
        assertEquals((long) Config.COMMON.fluidizer.energyPerRecipeTick.get() * 2 * 80,
                solidMixingResult.energy());

        assertEquals(2, fluidMixingResult.inputFluids().size());
        assertEquals(800, fluidMixingResult.processTime());
        assertEquals((long) Config.COMMON.fluidizer.energyPerRecipeTick.get() * 2 * 80,
                fluidMixingResult.energy());
    }

    @Test
    void convertsReprocessorRecipeWithItsFixedProcessingCost() {
        ReprocessorRecipe source = new ReprocessorRecipe(
                ItemStackRecipeIngredient.from(Ingredient.of(Items.IRON_INGOT), 2),
                FluidStackRecipeIngredient.from(new FluidStack(Fluids.WATER, 1_000)),
                ItemStackRecipeResult.from(new ItemStack(Items.GOLD_INGOT, 3)));

        AdvancedAlloyFurnaceRecipe result = convert("reprocessor", source);

        assertEquals(2L, result.inputs().getFirst().count());
        assertEquals(1_000, result.inputFluids().getFirst().amount());
        assertEquals(3, result.outputs().getFirst().getCount());
        assertEquals(200, result.processTime());
        assertEquals(1_000L, result.energy());
        assertTrue(result.mold().test(new ItemStack(item("reprocessorcontroller"))));
    }

    @Test
    void generatedReactorRecipesUsePhysicalInputsAndOutputs() {
        List<RecipeHolder<ModRecipe>> generated = ADAPTER.getGeneratedRecipes(null);
        assumeFalse(generated.isEmpty(),
                "The unit-test host does not execute Extreme Reactors' active-mod API dispatcher");

        List<AdvancedAlloyFurnaceRecipe> converted = generated.stream()
                .map(holder -> ADAPTER.convertAll(holder, null))
                .flatMap(List::stream)
                .toList();
        assertFalse(converted.isEmpty());
        assertTrue(converted.stream().allMatch(recipe ->
                recipe.energy() == AdapterUtils.DEFAULT_ENERGY
                        && recipe.processTime() == AdapterUtils.DEFAULT_PROCESS_TIME
                        && recipe.keyInputs().isEmpty()
                        && recipe.keyOutputs().isEmpty()));
        assertTrue(converted.stream().anyMatch(recipe ->
                !recipe.inputs().isEmpty() && !recipe.outputs().isEmpty()));
        assertTrue(converted.stream().anyMatch(recipe ->
                !recipe.inputFluids().isEmpty() && !recipe.outputFluids().isEmpty()));
        assertTrue(converted.stream().allMatch(recipe ->
                recipe.mold().test(new ItemStack(item("basic_reactorcontroller")))
                        && recipe.mold().test(new ItemStack(item("reinforced_reactorcontroller")))));
    }

    @Test
    void syntheticReactorHolderPreservesDirectPhysicalRecipe() {
        AdvancedAlloyFurnaceRecipe payload = new AdvancedAlloyFurnaceRecipe(
                id("reactor/test"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(), List.of(), List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), List.of(),
                AdapterUtils.DEFAULT_ENERGY, AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0,
                List.of(Ingredient.of(
                        item("basic_reactorcontroller").getDefaultInstance(),
                        item("reinforced_reactorcontroller").getDefaultInstance())),
                AlloyFurnaceMode.NORMAL);

        List<AdvancedAlloyFurnaceRecipe> converted = ADAPTER.convertAll(
                holder("synthetic", new ExtremeReactorsSyntheticRecipe(payload)), null);
        assertEquals(List.of(payload), converted);
        assertTrue(converted.getFirst().inputs().getFirst().ingredient().test(
                new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.getFirst().outputs().getFirst().is(Items.GOLD_INGOT));
    }

    @Test
    void runtimeLookupMatchesStandardRecipesAndRejectsWrongInputsOrMolds() {
        FluidizerSolidRecipe source = new FluidizerSolidRecipe(
                ItemStackRecipeIngredient.from(Ingredient.of(Items.IRON_INGOT), 2),
                FluidStackRecipeResult.from(new FluidStack(fluid("yellorium"), 1_000)));
        RecipeHolder<ModRecipe> holder = holder("runtime", source);
        RecipeManager recipeManager = new RecipeManager(null);
        recipeManager.replaceRecipes(new ArrayList<>(List.of(holder)));
        TestServerLevel.recipeManager = recipeManager;

        List<RecipeHolder<ModRecipe>> matches = ADAPTER.findMatchingRecipes(
                testLevel(),
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.IRON_INGOT, 2))),
                Map.of(), Map.of(), new ItemStack(item("fluidizercontroller")), List.of());
        assertEquals(List.of(holder.id()), matches.stream().map(RecipeHolder::id).toList());

        assertTrue(ADAPTER.findMatchingRecipes(
                testLevel(),
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.GOLD_INGOT, 2))),
                Map.of(), Map.of(), new ItemStack(item("fluidizercontroller")), List.of()).isEmpty());
        assertTrue(ADAPTER.findMatchingRecipes(
                testLevel(),
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.IRON_INGOT, 2))),
                Map.of(), Map.of(), new ItemStack(Items.STICK), List.of()).isEmpty());
    }

    private static AdvancedAlloyFurnaceRecipe convert(String path, ModRecipe source) {
        return ADAPTER.convertAll(holder(path, source), null).getFirst();
    }

    private static RecipeHolder<ModRecipe> holder(String path, ModRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("bigreactors_test", path), recipe);
    }

    private static Item item(String path) {
        Item item = BuiltInRegistries.ITEM.getOptional(id(path)).orElse(null);
        assertNotNull(item, "Extreme Reactors item is not registered: " + path);
        return item;
    }

    private static Fluid fluid(String path) {
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id(path)).orElse(null);
        assertNotNull(fluid, "Extreme Reactors fluid is not registered: " + path);
        return fluid;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("bigreactors", path);
    }

    private static Level testLevel() {
        try {
            return (Level) unsafe.allocateInstance(TestServerLevel.class);
        } catch (InstantiationException exception) {
            throw new AssertionError("Could not allocate the test level", exception);
        }
    }

    private static final class TestServerLevel extends net.minecraft.server.level.ServerLevel {
        private static RecipeManager recipeManager = new RecipeManager(null);

        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }

        @Override
        public RecipeManager getRecipeManager() {
            return recipeManager;
        }
    }
}
