package com.sorrowmist.useless.content.recipe.adapters.eco;

import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedWorkingStationRecipeAdapterTest {
    private static final TagKey<Fluid> TEST_WATER_TAG = TagKey.create(
            Registries.FLUID, ResourceLocation.fromNamespaceAndPath("useless_mod_test", "eco_water"));
    private static Map<TagKey<Fluid>, List<Holder<Fluid>>> originalFluidTags;
    private static Unsafe unsafe;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalFluidTags = BuiltInRegistries.FLUID.getTags().collect(Collectors.toUnmodifiableMap(
                Pair::getFirst,
                pair -> StreamSupport.stream(pair.getSecond().spliterator(), false).toList()));
        Map<TagKey<Fluid>, List<Holder<Fluid>>> testTags = new java.util.HashMap<>(originalFluidTags);
        ResourceLocation waterId = BuiltInRegistries.FLUID.getKey(Fluids.WATER);
        testTags.put(TEST_WATER_TAG, List.of(BuiltInRegistries.FLUID.getHolderOrThrow(
                ResourceKey.create(Registries.FLUID, waterId))));
        BuiltInRegistries.FLUID.bindTags(testTags);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
    }

    @AfterAll
    static void restoreFluidTags() {
        if (originalFluidTags != null) {
            BuiltInRegistries.FLUID.bindTags(originalFluidTags);
        }
    }

    @Test
    void convertsComponentsCountsFluidsEnergyAndMold() {
        ItemStack namedInput = named(new ItemStack(Items.IRON_INGOT), "eco-input");
        var exactInput = DataComponentIngredient.of(true, namedInput.copyWithCount(1));
        ItemStack itemOutput = named(new ItemStack(Items.DIAMOND, 2), "eco-output");
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(new SizedIngredient(exactInput, 2), new SizedIngredient(exactInput, 3)),
                SizedFluidIngredient.of(Fluids.WATER, 250),
                itemOutput,
                new FluidStack(Fluids.LAVA, 500),
                2_000_000_000
        );

        IntegratedWorkingStationRecipeAdapter adapter = new IntegratedWorkingStationRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(
                holder("complete_recipe", source), null).getFirst();

        assertEquals(5L, converted.inputs().getFirst().count());
        assertTrue(converted.inputs().getFirst().ingredient().test(namedInput));
        assertFalse(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.inputFluids().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 1)));
        assertEquals(250, converted.inputFluids().getFirst().amount());
        assertEquals(2, converted.outputs().getFirst().getCount());
        assertEquals("eco-output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(Fluids.LAVA, converted.outputFluids().getFirst().getFluid());
        assertEquals(500, converted.outputFluids().getFirst().getAmount());
        assertEquals(2_000_000_000L, converted.energy());
        assertEquals(AdapterUtils.DEFAULT_PROCESS_TIME, converted.processTime());
        assertTrue(converted.mold().test(adapter.getMoldItem()));
        assertEquals(ResourceLocation.fromNamespaceAndPath("neoecoae", "integrated_working_station"),
                BuiltInRegistries.ITEM.getKey(adapter.getMoldItem().getItem()));
        assertFalse(adapter.matchesMold(new ItemStack(Items.CRAFTING_TABLE)));
    }

    @Test
    void preservesFluidAlternativesAndSupportsFluidOnlyInputs() {
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(),
                new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 125),
                new ItemStack(Items.CLAY_BALL),
                FluidStack.EMPTY,
                1000
        );

        List<AdvancedAlloyFurnaceRecipe> converted = new IntegratedWorkingStationRecipeAdapter()
                .convertAll(holder("fluid_alternatives", source), null);

        assertEquals(1, converted.size());
        assertTrue(converted.stream().allMatch(recipe -> recipe.inputFluids().size() == 1));
        assertTrue(converted.stream().allMatch(recipe -> recipe.inputFluids().getFirst().amount() == 125));
        assertTrue(converted.getFirst().inputFluids().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 1)));
        assertTrue(converted.getFirst().inputFluids().getFirst().ingredient().test(new FluidStack(Fluids.LAVA, 1)));
    }

    @Test
    void preservesFluidTagsWithoutExpandingRecipes() {
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(),
                new SizedFluidIngredient(FluidIngredient.tag(TEST_WATER_TAG), 125),
                new ItemStack(Items.CLAY_BALL),
                FluidStack.EMPTY,
                1000
        );

        List<AdvancedAlloyFurnaceRecipe> converted = new IntegratedWorkingStationRecipeAdapter()
                .convertAll(holder("water_tag", source), null);

        assertEquals(1, converted.size());
        assertTrue(converted.getFirst().inputFluids().getFirst().ingredient()
                .test(new FluidStack(Fluids.WATER, 1)));
        assertFalse(converted.getFirst().inputFluids().getFirst().ingredient()
                .test(new FluidStack(Fluids.LAVA, 1)));
    }

    @Test
    void convertsItemOnlyInputWithoutAddingFluidRequirement() {
        SizedFluidIngredient noFluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(SizedIngredient.of(Items.IRON_INGOT, 2)),
                noFluid,
                new ItemStack(Items.DIAMOND),
                FluidStack.EMPTY,
                1000
        );

        AdvancedAlloyFurnaceRecipe converted = new IntegratedWorkingStationRecipeAdapter()
                .convertAll(holder("item_only", source), null)
                .getFirst();

        assertEquals(2L, converted.inputs().getFirst().count());
        assertTrue(converted.inputFluids().isEmpty());
    }

    @Test
    void runtimeMatchingFindsItemOnlyAndFluidOnlyRecipes() throws ReflectiveOperationException {
        IntegratedWorkingStationRecipe itemOnly = new IntegratedWorkingStationRecipe(
                List.of(SizedIngredient.of(Items.IRON_INGOT, 2)),
                new SizedFluidIngredient(FluidIngredient.empty(), 1),
                new ItemStack(Items.DIAMOND),
                FluidStack.EMPTY,
                1000
        );
        IntegratedWorkingStationRecipe fluidOnly = new IntegratedWorkingStationRecipe(
                List.of(),
                SizedFluidIngredient.of(Fluids.WATER, 125),
                ItemStack.EMPTY,
                new FluidStack(Fluids.LAVA, 125),
                1000
        );
        IntegratedWorkingStationRecipeAdapter adapter = new IntegratedWorkingStationRecipeAdapter();
        Level level = levelWithRecipes(List.of(
                holder("runtime_item_only", itemOnly),
                holder("runtime_fluid_only", fluidOnly)));

        List<RecipeHolder<IntegratedWorkingStationRecipe>> itemMatches = adapter.findMatchingRecipes(
                level,
                AdapterUtils.mergeInputs(List.of(new ItemStack(Items.IRON_INGOT, 2))),
                Map.of(),
                adapter.getMoldItem());
        List<RecipeHolder<IntegratedWorkingStationRecipe>> fluidMatches = adapter.findMatchingRecipes(
                level,
                Map.of(),
                Map.of(new FluidStack(Fluids.WATER, 125), 125L),
                adapter.getMoldItem());

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("neoecoae", "runtime_item_only")),
                itemMatches.stream().map(RecipeHolder::id).toList());
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("neoecoae", "runtime_fluid_only")),
                fluidMatches.stream().map(RecipeHolder::id).toList());
    }

    @Test
    void collapsesSourceAndFlowingVariantsOfTheSameFluid() {
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(),
                new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.FLOWING_WATER), 125),
                new ItemStack(Items.CLAY_BALL),
                FluidStack.EMPTY,
                1000
        );

        List<AdvancedAlloyFurnaceRecipe> converted = new IntegratedWorkingStationRecipeAdapter()
                .convertAll(holder("water_tag", source), null);

        assertEquals(1, converted.size());
        assertTrue(converted.getFirst().inputFluids().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 1)));
        assertEquals(125, converted.getFirst().inputFluids().getFirst().amount());
    }

    @Test
    void rejectsNegativeEnergyMissingInputsAndMissingOutputs() {
        SizedFluidIngredient noFluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        IntegratedWorkingStationRecipeAdapter adapter = new IntegratedWorkingStationRecipeAdapter();

        IntegratedWorkingStationRecipe negativeEnergy = new IntegratedWorkingStationRecipe(
                List.of(SizedIngredient.of(Items.IRON_INGOT, 1)), noFluid,
                new ItemStack(Items.DIAMOND), FluidStack.EMPTY, -1);
        IntegratedWorkingStationRecipe missingInputs = new IntegratedWorkingStationRecipe(
                List.of(), noFluid, new ItemStack(Items.DIAMOND), FluidStack.EMPTY, 1);
        IntegratedWorkingStationRecipe missingOutputs = new IntegratedWorkingStationRecipe(
                List.of(SizedIngredient.of(Items.IRON_INGOT, 1)), noFluid,
                ItemStack.EMPTY, FluidStack.EMPTY, 1);

        assertTrue(adapter.convertAll(holder("negative_energy", negativeEnergy), null).isEmpty());
        assertTrue(adapter.convertAll(holder("missing_inputs", missingInputs), null).isEmpty());
        assertTrue(adapter.convertAll(holder("missing_outputs", missingOutputs), null).isEmpty());
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static RecipeHolder<IntegratedWorkingStationRecipe> holder(
            String path, IntegratedWorkingStationRecipe recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("neoecoae", path), recipe);
    }

    private static Level levelWithRecipes(
            List<RecipeHolder<IntegratedWorkingStationRecipe>> recipes)
            throws ReflectiveOperationException {
        RecipeManager recipeManager = new RecipeManager(null);
        recipeManager.replaceRecipes(new ArrayList<RecipeHolder<?>>(recipes));
        TestServerLevel.recipeManager = recipeManager;
        return (Level) unsafe.allocateInstance(TestServerLevel.class);
    }

    private static final class TestServerLevel extends ServerLevel {
        private static RecipeManager recipeManager;

        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }

        @Override
        public RecipeManager getRecipeManager() {
            return recipeManager;
        }
    }
}
