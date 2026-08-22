package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.nuclear.FluidNuclearComponent;
import aztech.modern_industrialization.nuclear.NuclearAbsorbable;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearAbsorptionRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsFullNuclearFuelDepletionWithOneThousandFePerNeutron() {
        Item fuelItem = item("uranium_fuel_rod");
        assertTrue(fuelItem instanceof NuclearAbsorbable);
        NuclearAbsorbable fuel = (NuclearAbsorbable) fuelItem;

        AdvancedAlloyFurnaceRecipe converted = findItemRecipe(
                new NuclearAbsorptionRecipeAdapter().getGeneratedRecipes(null), fuelItem);

        assertEquals((long) fuel.desintegrationMax * 1_000L, converted.energy());
        assertEquals(fuel.desintegrationMax * 20, converted.processTime());
        assertTrue(converted.mold().test(new ItemStack(item("nuclear_reactor"))));

        ItemStack full = fuelItem.getDefaultInstance();
        fuel.setRemainingDesintegrations(full, fuel.desintegrationMax);
        ItemStack partial = full.copy();
        fuel.setRemainingDesintegrations(partial, fuel.desintegrationMax - 1);
        assertTrue(converted.inputs().getFirst().ingredient().test(full));
        assertFalse(converted.inputs().getFirst().ingredient().test(partial));
        assertTrue(converted.outputs().getFirst().getItem() != Items.AIR);
    }

    @Test
    void convertsNuclearFluidWithExpectedConsumptionAndOutputProbability() {
        Fluid fluid = BuiltInRegistries.FLUID.get(
                ResourceLocation.fromNamespaceAndPath("modern_industrialization", "high_pressure_water"));
        FluidNuclearComponent component = FluidNuclearComponent.get(fluid);
        assertNotNull(component);
        assertTrue(component.getNeutronProductProbability() > 0.0d);

        AdvancedAlloyFurnaceRecipe converted = findFluidRecipe(
                new NuclearAbsorptionRecipeAdapter().getGeneratedRecipes(null), fluid);
        ModernIndustrializationRecipeAdapter.Rational probability =
                ModernIndustrializationRecipeAdapter.probability(
                        component.getNeutronProductProbability());
        ModernIndustrializationRecipeAdapter.Rational consumption =
                ModernIndustrializationRecipeAdapter.divide(probability, 81L);
        assertNotNull(consumption);

        long operations = consumption.denominator();
        long expectedInput = ModernIndustrializationRecipeAdapter.scaleAmount(
                1L, consumption, operations);
        long expectedOutput = expectedInput * component.getNeutronProductAmount();

        assertEquals(operations * 1_000L, converted.energy());
        assertEquals(operations * 20L, converted.processTime());
        assertEquals(expectedInput, converted.inputFluids().getFirst().amount());
        assertEquals(expectedOutput, converted.outputFluids().getFirst().getAmount());
        assertTrue(converted.mold().test(new ItemStack(item("nuclear_reactor"))));
        assertEquals(component.getNeutronProduct().getFluid(),
                converted.outputFluids().getFirst().getFluid());
    }

    private static AdvancedAlloyFurnaceRecipe findItemRecipe(
            List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> holders, Item item) {
        return holders.stream()
                .map(holder -> holder.value().convertedRecipe())
                .filter(recipe -> !recipe.inputs().isEmpty()
                        && recipe.inputs().getFirst().ingredient().test(item.getDefaultInstance()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing nuclear item absorption recipe"));
    }

    private static AdvancedAlloyFurnaceRecipe findFluidRecipe(
            List<RecipeHolder<NuclearAbsorptionSyntheticRecipe>> holders, Fluid fluid) {
        return holders.stream()
                .map(holder -> holder.value().convertedRecipe())
                .filter(recipe -> !recipe.inputFluids().isEmpty()
                        && recipe.inputFluids().getFirst().ingredient().test(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing nuclear fluid absorption recipe"));
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("modern_industrialization", path));
    }
}
