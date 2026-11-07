package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerOutput;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionIngredient;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationIngredient;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.BorrowedFluidInput;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AELightningTechNativeMigrationTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        for (String item : List.of("lightning_simulation_room", "lightning_assembly_chamber",
                "overload_processing_factory", "firmament_conversion_core", "crystal_catalyzer",
                "lightning_collapse_matrix")) {
            InventoryTestBootstrap.registerItem(new ResourceLocation("ae2lt", item),
                    () -> new Item(new Item.Properties()));
        }
    }

    @Test void simulationAndAssemblyKeepNativeLongEnergyAndExactNbt() {
        var id = id("simulation");
        var input = marked(Items.GOLD_INGOT, 1);
        var predicate = DataComponentIngredient.of(true, input);
        var inputs = List.of(new LightningSimulationIngredient(predicate, 7));
        var output = marked(Items.DIAMOND, 3);
        var simulation = new LightningSimulationRecipe(id, 0, inputs, output,
                6_000_000_000L, 13, LightningKey.Tier.EXTREME_HIGH_VOLTAGE);
        var converted = new LightningSimulationRecipeAdapter().convertAll(new RecipeHolder<>(id, simulation), null).get(0);
        assertEquals(6_000_000_000L, converted.energy());
        assertSame(predicate, converted.inputs().get(0).ingredient());
        assertEquals(7L, converted.inputs().get(0).count());
        assertFalse(converted.inputs().get(0).ingredient().test(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(13L, converted.keyInputs().get(0).amount());
        assertSame(LightningKey.EXTREME_HIGH_VOLTAGE, converted.keyInputs().get(0).what());
        assertEquals(3, converted.outputs().get(0).getCount());
        assertEquals(output.getTag(), converted.outputs().get(0).getTag());
        converted.outputs().get(0).getTag().putString("marker", "changed");
        assertEquals("exact", output.getTag().getString("marker"));
        var assembly = new LightningAssemblyRecipe(id("assembly"), 0, inputs, output,
                7_000_000_000L, 19, LightningKey.Tier.HIGH_VOLTAGE);
        var assembled = new LightningAssemblyRecipeAdapter().convertAll(
                new RecipeHolder<>(id("assembly"), assembly), null).get(0);
        assertEquals(7_000_000_000L, assembled.energy());
        assertEquals(19L, assembled.keyInputs().get(0).amount());
        assertSame(LightningKey.HIGH_VOLTAGE, assembled.keyInputs().get(0).what());
    }

    @Test void overloadKeepsNativeFluidNbtAndEnergyWithoutIntFallback() {
        var id = id("overload");
        var fluid = new FluidStack(Fluids.WATER, 250);
        fluid.getOrCreateTag().putString("grade", "exact");
        var outputFluid = new FluidStack(Fluids.LAVA, 50);
        outputFluid.getOrCreateTag().putString("result", "preserved");
        var source = new OverloadProcessingRecipe(id, 0,
                List.of(new OverloadProcessingIngredient(Ingredient.of(Items.IRON_INGOT), 5)),
                fluid, List.of(marked(Items.DIAMOND, 2)), outputFluid,
                8_000_000_000L, 11, LightningKey.Tier.EXTREME_HIGH_VOLTAGE);
        var converted = new OverloadProcessingRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).get(0);
        assertEquals(8_000_000_000L, converted.energy());
        assertEquals(250L, converted.inputFluids().get(0).amount());
        assertTrue(converted.inputFluids().get(0).ingredient().test(fluid));
        assertFalse(converted.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.WATER, 250)));
        assertEquals(outputFluid.getTag(), converted.outputFluids().get(0).getTag());
        var borrowed = OverloadProcessingRecipe.borrowed(id("borrowed"), List.of(),
                new BorrowedFluidInput(fluid, stack -> stack.getFluid() == Fluids.LAVA),
                List.of(new ItemStack(Items.DIAMOND)), FluidStack.EMPTY, 5000L);
        assertEquals(250, borrowed.inputFluidAmount());
        assertTrue(borrowed.fluidInput().isEmpty());
        assertTrue(new OverloadProcessingRecipeAdapter().convertAll(
                new RecipeHolder<>(id("borrowed"), borrowed), null).isEmpty(),
                "a display sample cannot replace the borrowed recipe's live fluid predicate");
    }

    @Test void firmamentTimeEnergyMultiplicationRemainsLong() {
        var source = new FirmamentConversionRecipe(0,
                List.of(new FirmamentConversionIngredient(Ingredient.of(Items.GOLD_INGOT), 5)),
                List.of(marked(Items.DIAMOND, 2), marked(Items.EMERALD, 3)), 600_000_000);
        var converted = new FirmamentConversionRecipeAdapter().convertAll(
                new RecipeHolder<>(id("firmament"), source), null).get(0);
        assertEquals(12_000_000_000L, converted.energy());
        assertEquals(600_000_000, converted.processTime());
        assertEquals(2, converted.outputs().size());
        assertEquals("exact", converted.outputs().get(1).getTag().getString("marker"));
    }

    @Test void catalyzerUsesOneNativeCycleAndRequiresTheMatrixEvenWithoutACatalyst() {
        var output = marked(Items.DIAMOND, 3);
        var fluid = new FluidStack(Fluids.LAVA, 250);
        fluid.getOrCreateTag().putString("grade", "exact");
        var source = new CrystalCatalyzerRecipe(id("catalyzer"), Optional.empty(), 0,
                CrystalCatalyzerOutput.ofItem(output), 5000, 7, LightningKey.Tier.HIGH_VOLTAGE,
                Mode.CRYSTAL, fluid);
        var recipes = new CrystalCatalyzerRecipeAdapter().convertAll(
                new RecipeHolder<>(id("catalyzer"), source), null);
        assertEquals(2, recipes.size());
        var base = recipes.get(0);
        var matrix = recipes.get(1);
        assertEquals(3, base.outputs().get(0).getCount());
        assertEquals(24, matrix.outputs().get(0).getCount());
        assertEquals(output.getTag(), matrix.outputs().get(0).getTag());
        assertEquals(7L, base.keyInputs().get(0).amount());
        assertEquals(7L, matrix.keyInputs().get(0).amount());
        assertEquals(5000L, base.energy());
        assertEquals(250L, base.inputFluids().get(0).amount());
        assertTrue(base.inputFluids().get(0).ingredient().test(fluid));
        assertFalse(base.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.LAVA, 250)));
        assertEquals(1, base.molds().size());
        assertEquals(2, matrix.molds().size());
        var matrixItem = new ItemStack(BuiltInRegistries.ITEM.get(id("lightning_collapse_matrix")));
        assertTrue(matrix.molds().get(1).test(matrixItem));
        var taggedWater = new FluidStack(Fluids.WATER, 1000);
        taggedWater.getOrCreateTag().putString("unrequested", "nbt");
        assertFalse(AELightningIngredientHelper.exactFluidInput(new FluidStack(Fluids.WATER, 1000))
                .ingredient().test(taggedWater), "native isFluidEqual rejects unexpected NBT too");
    }

    @Test void catalyzerDoesNotInventNonConsumedCountsOrDynamicHarvestOutputs() {
        var adapter = new CrystalCatalyzerRecipeAdapter();
        var countTwo = new CrystalCatalyzerRecipe(id("count_two"), Optional.of(Ingredient.of(Items.GOLD_INGOT)),
                2, new ItemStack(Items.DIAMOND), 5000);
        assertTrue(adapter.convertAll(new RecipeHolder<>(id("count_two"), countTwo), null).isEmpty());
        var dynamicOutput = new com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalHarvestOutput(
                new ResourceLocation("minecraft", "diamond_ore"), 1);
        var dynamic = new CrystalCatalyzerRecipe(id("dynamic"), Optional.empty(), 0,
                dynamicOutput, 5000, 1, LightningKey.Tier.HIGH_VOLTAGE, Mode.CRYSTAL);
        assertTrue(adapter.convertAll(new RecipeHolder<>(id("dynamic"), dynamic), null).isEmpty());
    }

    @Test void lookupNeverClaimsAnUnconsumedVoltageSubstitution() {
        Map<AEKey, Long> highOnly = Map.of(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE);
        assertFalse(AELightningIngredientHelper.matchesLightning(highOnly, LightningKey.Tier.EXTREME_HIGH_VOLTAGE, 1));
        assertTrue(AELightningIngredientHelper.matchesLightning(highOnly, LightningKey.Tier.HIGH_VOLTAGE, Long.MAX_VALUE));
        assertTrue(AELightningIngredientHelper.matchesLightning(Map.of(), LightningKey.Tier.HIGH_VOLTAGE, 0));
        assertFalse(AELightningIngredientHelper.matchesLightning(Map.of(), LightningKey.Tier.HIGH_VOLTAGE, -1));
        assertEquals(Long.MAX_VALUE, AELightningIngredientHelper.lightningInputs(
                LightningKey.Tier.HIGH_VOLTAGE, Long.MAX_VALUE).get(0).amount());
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("ae2lt", path); }
    private static ItemStack marked(Item item, int count) {
        var stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", "exact");
        return stack;
    }
}
