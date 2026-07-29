package com.sorrowmist.useless.content.recipe.adapters.eco;

import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedWorkingStationRecipeAdapterTest {
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
        assertEquals(Fluids.WATER, converted.inputFluids().getFirst().getFluid());
        assertEquals(250, converted.inputFluids().getFirst().getAmount());
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
    void expandsFluidAlternativesAndSupportsFluidOnlyInputs() {
        IntegratedWorkingStationRecipe source = new IntegratedWorkingStationRecipe(
                List.of(),
                new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 125),
                new ItemStack(Items.CLAY_BALL),
                FluidStack.EMPTY,
                1000
        );

        List<AdvancedAlloyFurnaceRecipe> converted = new IntegratedWorkingStationRecipeAdapter()
                .convertAll(holder("fluid_alternatives", source), null);

        assertEquals(2, converted.size());
        assertTrue(converted.stream().allMatch(recipe -> recipe.inputFluids().size() == 1));
        assertTrue(converted.stream().allMatch(recipe -> recipe.inputFluids().getFirst().getAmount() == 125));
        assertTrue(converted.stream().anyMatch(recipe -> recipe.inputFluids().getFirst().is(Fluids.WATER)));
        assertTrue(converted.stream().anyMatch(recipe -> recipe.inputFluids().getFirst().is(Fluids.LAVA)));
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
        assertTrue(converted.getFirst().inputFluids().getFirst().is(Fluids.WATER));
        assertEquals(125, converted.getFirst().inputFluids().getFirst().getAmount());
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
}
