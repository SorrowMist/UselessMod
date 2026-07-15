package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.recipe.AlloyFurnaceRecipeCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.TOTAL_SLOTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnaceInputPortTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void calculatorAndConsumptionUseTheSameOverlappingAllocation() {
        ItemStackHandler items = new ItemStackHandler(TOTAL_SLOTS);
        items.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 2));
        items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 2));
        items.setStackInSlot(2, new ItemStack(Items.DIRT, 64));

        FluidTank[] inputTanks = tanks(1000);
        inputTanks[0].setFluid(water(500));
        inputTanks[1].setFluid(water(700));
        FluidTank[] outputTanks = tanks(1000);

        AdvancedAlloyFurnaceRecipe recipe = recipe(
                List.of(
                        new CountedIngredient(Ingredient.of(Items.OAK_LOG, Items.COBBLESTONE), 1),
                        new CountedIngredient(Ingredient.of(Items.OAK_LOG), 1)
                ),
                List.of(water(600))
        );
        AlloyFurnaceRecipeCalculator calculator = new AlloyFurnaceRecipeCalculator(
                items, inputTanks, outputTanks, null);

        assertTrue(calculator.canProcessRecipe(recipe));
        assertTrue(calculator.canConsumeRecipeInputs(recipe, 2));
        assertFalse(calculator.canConsumeRecipeInputs(recipe, 3));
        assertEquals(2, calculator.calculateMaterialParallel(recipe));

        assertTrue(FurnaceInputPort.consumeRecipeInputs(
                recipe, 2, items, INPUT_SLOTS_START, INPUT_SLOTS_COUNT, inputTanks, FLUID_TANK_COUNT));
        assertTrue(items.getStackInSlot(0).isEmpty());
        assertTrue(items.getStackInSlot(1).isEmpty());
        assertEquals(64, items.getStackInSlot(2).getCount());
        assertTrue(inputTanks[0].isEmpty());
        assertTrue(inputTanks[1].isEmpty());
    }

    @Test
    void failedAllocationDoesNotConsumeAnyItemsOrFluids() {
        ItemStackHandler items = new ItemStackHandler(TOTAL_SLOTS);
        items.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 1));
        items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 1));
        FluidTank[] inputTanks = tanks(1000);
        inputTanks[0].setFluid(water(500));

        AdvancedAlloyFurnaceRecipe recipe = recipe(
                List.of(
                        new CountedIngredient(Ingredient.of(Items.OAK_LOG, Items.COBBLESTONE), 1),
                        new CountedIngredient(Ingredient.of(Items.OAK_LOG), 1)
                ),
                List.of(water(400), water(300))
        );

        assertFalse(FurnaceInputPort.consumeRecipeInputs(
                recipe, 1, items, INPUT_SLOTS_START, INPUT_SLOTS_COUNT, inputTanks, FLUID_TANK_COUNT));
        assertEquals(1, items.getStackInSlot(0).getCount());
        assertEquals(1, items.getStackInSlot(1).getCount());
        assertEquals(500, inputTanks[0].getFluidAmount());
    }

    private static AdvancedAlloyFurnaceRecipe recipe(List<CountedIngredient> inputs,
                                                       List<FluidStack> fluids) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "input_port"),
                inputs,
                fluids,
                List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)),
                List.of(),
                List.of(),
                2000,
                200,
                Ingredient.EMPTY,
                0,
                Ingredient.EMPTY,
                AlloyFurnaceMode.NORMAL
        );
    }

    private static FluidTank[] tanks(int capacity) {
        FluidTank[] tanks = new FluidTank[FLUID_TANK_COUNT];
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] = new FluidTank(capacity);
        }
        return tanks;
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }
}
