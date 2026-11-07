package com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import me.desht.pneumaticcraft.api.crafting.ingredient.FluidIngredient;
import me.desht.pneumaticcraft.api.crafting.ingredient.StackedIngredient;
import me.desht.pneumaticcraft.api.crafting.recipe.*;
import me.desht.pneumaticcraft.common.recipes.machine.HeatFrameCoolingRecipeImpl;
import me.desht.pneumaticcraft.common.recipes.machine.PressureChamberRecipeImpl;
import me.desht.pneumaticcraft.common.block.entity.UVLightBoxBlockEntity;
import me.desht.pneumaticcraft.common.item.EmptyPCBItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.sorrowmist.useless.content.recipe.AdapterGuards;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PneumaticCraftNativeMigrationTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "pnc_native");
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @BeforeEach @AfterEach void resetGuards() { AdapterGuards.clearFailures(); }

    @Test void pressureChamberSplitsNativeStackedCountsWithoutKeepingTheCountSensitivePredicate() {
        var input = StackedIngredient.fromItems(12, Items.IRON_INGOT);
        var output = new ItemStack(Items.GOLD_INGOT, 3); output.getOrCreateTag().putString("batch", "preserved");
        var source = new PressureChamberRecipeImpl(ID, List.of(input), 2.0F, output);
        var converted = adapter(PressureChamberRecipe.class, PneumaticCraftRecipeAdapter.Kind.PRESSURE_CHAMBER)
                .convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertEquals(12, converted.inputs().get(0).count());
        assertTrue(converted.inputs().get(0).ingredient().test(new ItemStack(Items.IRON_INGOT, 1)));
        assertFalse(converted.inputs().get(0).ingredient().test(new ItemStack(Items.DIAMOND)));
        assertEquals(3, converted.outputs().get(0).getCount());
        output.getOrCreateTag().putString("batch", "mutated");
        assertEquals("preserved", converted.outputs().get(0).getTag().getString("batch"));
    }

    @Test void pressureEnchantingAndUnknownInventoryDependentOutputsAreNotFlattened() {
        assertTrue(adapter(PressureChamberRecipe.class, PneumaticCraftRecipeAdapter.Kind.PRESSURE_CHAMBER)
                .convertAll(new RecipeHolder<>(ID, mock(PressureChamberRecipe.class)), null).isEmpty());
    }

    @Test void fluidMixerPreservesBothNativeFluidAlternativesPartialNbtAndExactAmount() {
        CompoundTag nbt = new CompoundTag(); nbt.putString("grade", "pure");
        for (boolean fuzzy : new boolean[]{false, true}) {
            var nativeInput = FluidIngredient.of(750, nbt, fuzzy, Fluids.WATER, Fluids.LAVA);
            var source = mock(FluidMixerRecipe.class);
            when(source.getInput1()).thenReturn(nativeInput);
            when(source.getInput2()).thenReturn(FluidIngredient.EMPTY);
            when(source.getOutputFluid()).thenReturn(FluidStack.EMPTY);
            when(source.getOutputItem()).thenReturn(new ItemStack(Items.DIAMOND, 2));
            when(source.getProcessingTime()).thenReturn(87);
            var converted = adapter(FluidMixerRecipe.class, PneumaticCraftRecipeAdapter.Kind.FLUID_MIXER)
                    .convertAll(new RecipeHolder<>(ID, source), null).get(0);
            assertEquals(87, converted.processTime());
            assertEquals(750, converted.inputFluids().get(0).amount());
            for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) for (String grade : List.of("pure", "dirty")) {
                var stack = new FluidStack(fluid, 1000); stack.getOrCreateTag().putString("grade", grade);
                stack.getOrCreateTag().putInt("additional", 1);
                assertEquals(nativeInput.testFluid(stack), converted.inputFluids().get(0).ingredient().test(stack));
            }
        }
    }

    @Test void unsupportedNativeFluidRowDoesNotDisableLookupOfTheNextRecipe() {
        var bad = mock(FluidMixerRecipe.class);
        when(bad.getInput1()).thenReturn(FluidIngredient.ofFluidStream(java.util.stream.Stream.of(
                FluidIngredient.of(10, Fluids.WATER))));
        var good = mock(FluidMixerRecipe.class);
        when(good.getInput1()).thenReturn(FluidIngredient.of(10, Fluids.WATER));
        when(good.getInput2()).thenReturn(FluidIngredient.EMPTY);
        when(good.getOutputFluid()).thenReturn(FluidStack.EMPTY);
        when(good.getOutputItem()).thenReturn(new ItemStack(Items.GOLD_INGOT));
        var goodHolder = new RecipeHolder<>(new ResourceLocation("test", "pnc_good"), good);
        var adapter = new PneumaticCraftRecipeAdapter<>(FluidMixerRecipe.class,
                PneumaticCraftRecipeAdapter.Kind.FLUID_MIXER,
                level -> List.of(new RecipeHolder<>(ID, bad), goodHolder), new ItemStack(Items.STONE));
        var matches = adapter.findMatchingRecipes(mock(net.minecraft.world.level.Level.class), java.util.Map.of(),
                java.util.Map.of(new FluidStack(Fluids.WATER, 1000), 1000L), new ItemStack(Items.STONE));
        assertEquals(List.of(goodHolder), matches);
        assertFalse(AdapterGuards.isDisabled(adapter, "matchRecipe"));
        assertEquals(1, AdapterGuards.skippedItems(adapter, "matchRecipe"));
    }

    @Test void deterministicHeatFrameRetainsOutputCountAndNbtInsteadOfReplacingCountWithOne() {
        var output = new ItemStack(Items.GOLD_INGOT, 7); output.getOrCreateTag().putInt("quality", 4);
        var source = new HeatFrameCoolingRecipeImpl(ID, Ingredient.of(Items.IRON_INGOT), 273, output);
        var converted = adapter(HeatFrameCoolingRecipe.class, PneumaticCraftRecipeAdapter.Kind.HEAT_FRAME_COOLING)
                .convertAll(new RecipeHolder<>(ID, source), null).get(0);
        assertEquals(7, converted.outputs().get(0).getCount());
        assertEquals(4, converted.outputs().get(0).getTag().getInt("quality"));
    }

    @Test void probabilisticCoolingDoesNotBecomeAGuaranteedMaximumYieldRecipe() {
        var source = new HeatFrameCoolingRecipeImpl(ID, Ingredient.of(Items.IRON_INGOT), 273,
                new ItemStack(Items.GOLD_INGOT), 0.05F, 1.5F);
        assertTrue(adapter(HeatFrameCoolingRecipe.class, PneumaticCraftRecipeAdapter.Kind.HEAT_FRAME_COOLING)
                .convertAll(new RecipeHolder<>(ID, source), null).isEmpty());
    }

    @Test void playerAmadronOffersAreNotConvertedToUnlimitedTrades() {
        var source = mock(AmadronRecipe.class);
        assertTrue(adapter(AmadronRecipe.class, PneumaticCraftRecipeAdapter.Kind.AMADRON)
                .convertAll(new RecipeHolder<>(ID, source), null).isEmpty());
    }

    @Test void etchingRequiresExactFullExposureRatherThanDiscardingTheFailureProbability() throws Exception {
        var adapter = spy(PneumaticCraftSyntheticRecipeAdapter.etching());
        doReturn(new ItemStack(Items.STONE)).when(adapter).getMoldItem();
        var pcb = mock(EmptyPCBItem.class);
        when(pcb.getSuccessItem()).thenReturn(new ItemStack(Items.DIAMOND));
        var method = PneumaticCraftSyntheticRecipeAdapter.class.getDeclaredMethod("etchingRecipe", EmptyPCBItem.class, Item.class);
        method.setAccessible(true);
        try (var nativePcb = mockStatic(EmptyPCBItem.class)) {
            nativePcb.when(EmptyPCBItem::getEtchingFluid).thenReturn(new FluidStack(Fluids.WATER, 100));
            var recipe = (AdvancedAlloyFurnaceRecipe) method.invoke(adapter, pcb, Items.PAPER);
            Ingredient input = recipe.inputs().get(0).ingredient();
            assertFalse(input.test(new ItemStack(Items.PAPER)));
            assertTrue(input.test(UVLightBoxBlockEntity.setExposureProgress(new ItemStack(Items.PAPER), 100)));
            assertFalse(input.test(UVLightBoxBlockEntity.setExposureProgress(new ItemStack(Items.PAPER), 50)));
            assertEquals(100, recipe.inputFluids().get(0).amount());
        }
    }

    private static <T extends PneumaticCraftRecipe> PneumaticCraftRecipeAdapter<T> adapter(
            Class<T> type, PneumaticCraftRecipeAdapter.Kind kind) {
        return new PneumaticCraftRecipeAdapter<>(type, kind, level -> List.of(), new ItemStack(Items.STONE));
    }
}
