package com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft;

import com.mojang.datafixers.util.Either;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import me.desht.pneumaticcraft.api.crafting.AmadronTradeResource;
import me.desht.pneumaticcraft.api.crafting.TemperatureRange;
import me.desht.pneumaticcraft.api.crafting.recipe.AssemblyRecipe;
import me.desht.pneumaticcraft.api.crafting.recipe.ThermoPlantRecipe;
import me.desht.pneumaticcraft.common.recipes.amadron.AmadronOffer;
import me.desht.pneumaticcraft.common.recipes.machine.AssemblyRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.FluidMixerRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.HeatFrameCoolingRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.PressureChamberRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.PressureEnchantingRecipe;
import me.desht.pneumaticcraft.common.recipes.machine.RefineryRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.ThermoPlantRecipeImpl;
import me.desht.pneumaticcraft.common.item.EmptyPCBItem;
import me.desht.pneumaticcraft.common.block.entity.processing.UVLightBoxBlockEntity;
import me.desht.pneumaticcraft.common.util.playerfilter.PlayerFilter;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import me.desht.pneumaticcraft.common.registry.ModItems;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PneumaticCraftRecipeAdapterTest {
    private static Level level;

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        level = (Level) ((Unsafe) field.get(null)).allocateInstance(TestServerLevel.class);
    }

    @Test
    void convertsAssemblyRecipeWithPlatformAndProgramMolds() {
        AssemblyRecipe source = new AssemblyRecipeImpl(
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                new ItemStack(Items.DIAMOND),
                AssemblyRecipe.AssemblyProgramType.DRILL);

        AdvancedAlloyFurnaceRecipe converted = PneumaticCraftRecipeAdapter.assembly()
                .convertAll(holder("assembly", source), null).getFirst();

        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(2, converted.molds().size());
        assertTrue(converted.molds().get(0).test(new ItemStack(ModBlocks.ASSEMBLY_PLATFORM.get())));
        assertTrue(converted.molds().get(1).test(new ItemStack(ModItems.ASSEMBLY_PROGRAM_DRILL.get())));
    }

    @Test
    void convertsFluidMixerAndPressureChamberInputsAndOutputs() {
        FluidMixerRecipeImpl mixer = new FluidMixerRecipeImpl(
                fluid(Fluids.WATER, 1_000),
                fluid(Fluids.LAVA, 1_000),
                FluidStack.EMPTY,
                new ItemStack(Items.OBSIDIAN),
                0.5f,
                40);
        AdvancedAlloyFurnaceRecipe mixerResult = PneumaticCraftRecipeAdapter.fluidMixer()
                .convertAll(holder("mixer", mixer), null).getFirst();

        assertEquals(2, mixerResult.inputFluids().size());
        assertEquals(40, mixerResult.processTime());
        assertTrue(mixerResult.outputs().getFirst().is(Items.OBSIDIAN));

        PressureChamberRecipeImpl pressure = new PressureChamberRecipeImpl(
                List.of(
                        new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new SizedIngredient(Ingredient.of(Items.REDSTONE), 1)),
                2.0f,
                List.of(new ItemStack(Items.DIAMOND)));
        AdvancedAlloyFurnaceRecipe pressureResult = PneumaticCraftRecipeAdapter.pressureChamber()
                .convertAll(holder("pressure", pressure), null).getFirst();

        assertEquals(2, pressureResult.inputs().size());
        assertEquals(1, pressureResult.outputs().size());
        assertTrue(pressureResult.mold().test(new ItemStack(ModBlocks.PRESSURE_CHAMBER_VALVE.get())));
    }

    @Test
    void convertsHeatFrameAtItsMaximumBonus() {
        HeatFrameCoolingRecipeImpl source = new HeatFrameCoolingRecipeImpl(
                Either.left(Ingredient.of(Items.WATER_BUCKET)),
                300,
                new ItemStack(Items.ICE),
                1.0f,
                2.0f);

        AdvancedAlloyFurnaceRecipe converted = PneumaticCraftRecipeAdapter.heatFrameCooling()
                .convertAll(holder("heat_frame", source), null).getFirst();

        assertEquals(3, converted.outputs().getFirst().getCount());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.WATER_BUCKET)));
    }

    @Test
    void convertsRefineryAndThermoPlantFluidSemantics() {
        RefineryRecipeImpl refinery = new RefineryRecipeImpl(
                fluid(Fluids.WATER, 1_000),
                TemperatureRange.any(),
                List.of(new FluidStack(Fluids.LAVA, 400), new FluidStack(Fluids.WATER, 100)));
        AdvancedAlloyFurnaceRecipe refineryResult = PneumaticCraftRecipeAdapter.refinery()
                .convertAll(holder("refinery", refinery), null).getFirst();

        assertEquals(1, refineryResult.inputFluids().size());
        assertEquals(2, refineryResult.outputFluids().size());

        ThermoPlantRecipe.Inputs inputs = ThermoPlantRecipe.Inputs.of(
                fluid(Fluids.WATER, 250), Ingredient.of(Items.WHEAT));
        ThermoPlantRecipe.Outputs outputs = new ThermoPlantRecipe.Outputs(
                new FluidStack(Fluids.LAVA, 50), ItemStack.EMPTY);
        ThermoPlantRecipeImpl thermo = new ThermoPlantRecipeImpl(
                inputs, outputs, TemperatureRange.any(), 1.0f, 1.0f, 1.0f, false);
        AdvancedAlloyFurnaceRecipe thermoResult = PneumaticCraftRecipeAdapter.thermoPlant()
                .convertAll(holder("thermo", thermo), null).getFirst();

        assertEquals(1, thermoResult.inputs().size());
        assertEquals(1, thermoResult.inputFluids().size());
        assertEquals(50, thermoResult.outputFluids().getFirst().getAmount());
    }

    @Test
    void convertsStaticAmadronTradesInBothResourceForms() {
        AmadronOffer source = new AmadronOffer(
                ResourceLocation.fromNamespaceAndPath("pneumaticcraft", "test_trade"),
                AmadronTradeResource.of(new ItemStack(Items.IRON_INGOT, 2)),
                AmadronTradeResource.of(new FluidStack(Fluids.WATER, 100)),
                true, false, 1, 1, 10, PlayerFilter.YES, PlayerFilter.NO);

        AdvancedAlloyFurnaceRecipe converted = PneumaticCraftRecipeAdapter.amadron()
                .convertAll(holder("amadron", source), null).getFirst();

        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(100, converted.outputFluids().getFirst().getAmount());
        assertTrue(converted.mold().test(new ItemStack(ModItems.AMADRON_TABLET.get())));
    }

    @Test
    void generatesDeterministicEtchingAndUvRecipesForRegisteredPcbs() {
        EmptyPCBItem pcb = ModItems.EMPTY_PCB.get();
        ItemStack pcbStack = new ItemStack(pcb);

        List<RecipeHolder<PneumaticCraftSyntheticRecipe>> etching =
                PneumaticCraftSyntheticRecipeAdapter.etching().getGeneratedRecipes(null);
        AdvancedAlloyFurnaceRecipe etchingRecipe = etching.stream()
                .map(holder -> holder.value().convertedRecipe())
                .filter(recipe -> recipe.inputs().getFirst().ingredient().test(pcbStack))
                .findFirst().orElseThrow();
        assertEquals(1_000, etchingRecipe.inputFluids().getFirst().amount());
        assertFalse(etchingRecipe.outputs().isEmpty());

        List<RecipeHolder<PneumaticCraftSyntheticRecipe>> uv =
                PneumaticCraftSyntheticRecipeAdapter.uvLightBox().getGeneratedRecipes(null);
        AdvancedAlloyFurnaceRecipe uvRecipe = uv.stream()
                .map(holder -> holder.value().convertedRecipe())
                .filter(recipe -> recipe.inputs().getFirst().ingredient().test(pcbStack))
                .findFirst().orElseThrow();
        assertEquals(100, UVLightBoxBlockEntity.getExposureProgress(uvRecipe.outputs().getFirst()));
    }

    @Test
    void runtimeMatchingUsesTheSourceProviderAndMergedFluidQuantities() {
        FluidMixerRecipeImpl source = new FluidMixerRecipeImpl(
                fluid(Fluids.WATER, 1_000),
                fluid(Fluids.LAVA, 1_000),
                FluidStack.EMPTY,
                new ItemStack(Items.OBSIDIAN),
                0.5f,
                40);
        RecipeHolder<me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe> holder =
                holder("runtime_mixer", source);
        PneumaticCraftRecipeAdapter<me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe> adapter =
                new PneumaticCraftRecipeAdapter<>(
                        me.desht.pneumaticcraft.api.crafting.recipe.FluidMixerRecipe.class,
                        PneumaticCraftRecipeAdapter.Kind.FLUID_MIXER,
                        ignored -> List.of(holder),
                        new ItemStack(Items.FURNACE));

        Map<net.neoforged.neoforge.fluids.FluidStack, Long> fluids = AdapterUtils.mergeFluids(List.of(
                new FluidStack(Fluids.WATER, 1_000), new FluidStack(Fluids.LAVA, 1_000)));
        assertEquals(List.of(holder.id()), adapter.findMatchingRecipes(
                level, Map.of(), fluids, new ItemStack(Items.FURNACE))
                .stream().map(RecipeHolder::id).toList());
        assertTrue(adapter.findMatchingRecipes(
                level, Map.of(), AdapterUtils.mergeFluids(List.of(new FluidStack(Fluids.WATER, 999))),
                new ItemStack(Items.FURNACE)).isEmpty());
    }

    @Test
    void skipsPressureChamberRecipesWithDynamicEnchantingResults() {
        assertTrue(PneumaticCraftRecipeAdapter.pressureChamber()
                .convertAll(holder("pressure_enchanting", new PressureEnchantingRecipe(
                        CraftingBookCategory.MISC)), null)
                .isEmpty());
    }

    private static SizedFluidIngredient fluid(net.minecraft.world.level.material.Fluid fluid, int amount) {
        return new SizedFluidIngredient(FluidIngredient.single(fluid), amount);
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(
            String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("pneumaticcraft", path), recipe);
    }

    private static final class TestServerLevel extends ServerLevel {
        private TestServerLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null);
        }
    }
}
