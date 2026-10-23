package com.sorrowmist.useless.content.recipe.adapters;

import cofh.lib.util.crafting.IngredientWithCount;
import cofh.thermal.core.util.recipes.machine.InsolatorRecipe;
import cofh.thermal.core.util.recipes.machine.PressRecipe;
import cofh.thermal.core.util.recipes.machine.SmelterRecipe;
import com.google.gson.JsonParser;
import com.rekindled.embers.recipe.IAlchemyRecipe;
import com.rekindled.embers.recipe.IMeltingRecipe;
import com.rekindled.embers.recipe.IMixingRecipe;
import com.rekindled.embers.recipe.IStampingRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.adapters.bloodmagic.BloodMagicRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.embers.EmbersRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.thermal.ThermalRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.thermal.NativeThermalIngredients;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wayoftime.bloodmagic.recipe.RecipeAlchemyTable;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.recipe.RecipeTartaricForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchRecipeAdapterTest {
    private static final ResourceLocation ID = new ResourceLocation("test", "batch");
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static <T extends BatchRecipeAdapter<?>> T adapter(T value) {
        T spy = spy(value);
        doReturn(new ItemStack(Items.FURNACE)).when(spy).getMoldItem();
        return spy;
    }

    @Test void absentNativeMoldNeverBecomesAWildcard() {
        var value = spy(new EmbersRecipeAdapter<>(IMixingRecipe.class, "missing", "test_", false));
        doReturn(ItemStack.EMPTY).when(value).getMoldItem();
        assertFalse(value.matchesMold(new ItemStack(Items.FURNACE)));
        assertFalse(value.matchesMold(ItemStack.EMPTY));
        assertTrue(value.findMatchingRecipes(mock(Level.class), Map.of(), Map.of(), Map.of(),
                new ItemStack(Items.FURNACE), List.of()).isEmpty());
    }

    @Test void badExternalRecipeCannotCrashMachineLookup() {
        var value = adapter(new EmbersRecipeAdapter<>(IMixingRecipe.class, "mixer_centrifuge", "test_", false));
        var source = mock(IMixingRecipe.class);
        when(source.getId()).thenReturn(ID);
        when(source.getDisplayInputFluids()).thenThrow(new IllegalArgumentException("broken external recipe"));
        var level = mock(Level.class);
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of(source));
        assertTrue(value.findMatchingRecipes(level, Map.of(), Map.of(), Map.of(),
                new ItemStack(Items.FURNACE), List.of()).isEmpty());
    }

    @Test void cofhCountedNbtIngredientKeepsItsCountAndPredicate() {
        ItemStack required = new ItemStack(Items.DIAMOND);
        required.getOrCreateTag().putInt("grade", 9);
        var input = new IngredientWithCount(StrictNBTIngredient.of(required), 3);
        var converted = NativeThermalIngredients.items(List.of(input), 64L).get(0);
        assertEquals(192L, converted.count());
        assertTrue(converted.ingredient().test(required));
        assertFalse(converted.ingredient().test(new ItemStack(Items.DIAMOND)));
    }

    @Test void fluidAlternativesKeepTheirDifferentAmountsAndLiveTags() {
        var json = JsonParser.parseString("[{\"fluid\":\"minecraft:water\",\"amount\":5},"
                + "{\"fluid_tag\":\"forge:lava\",\"amount\":10}]");
        var alternatives = BatchRecipeAdapter.fluids(List.of(json), 64L);
        assertEquals(2, alternatives.size());
        assertEquals(320L, alternatives.get(0).get(0).amount());
        assertEquals(640L, alternatives.get(1).get(0).amount());
        assertTrue(alternatives.get(1).get(0).ingredient().toJson().toString().contains("forge:lava"));
        assertThrows(IllegalArgumentException.class, () -> BatchRecipeAdapter.fluids(
                List.of(JsonParser.parseString("{\"fluid\":\"minecraft:water\",\"amount\":0}")), 1L));
    }

    @Test void nativeThermalInsolatorRetainsSeedsWhilePressRetainsOnlyItsMold() {
        var seed = new InsolatorRecipe(ID, 1000, 0.0F, List.of(Ingredient.of(Items.WHEAT_SEEDS)),
                List.of(cofh.lib.common.fluid.FluidIngredient.of(new FluidStack(Fluids.WATER, 500))),
                List.of(new ItemStack(Items.WHEAT)), List.of(1.5F), List.of());
        var insolator = adapter(new ThermalRecipeAdapter<>(InsolatorRecipe.class, "machine_insolator",
                "thermal_insolator_", AlloyFurnaceMode.INSOLATOR));
        var converted = insolator.convertAll(RecipeHolder.of(seed), null).get(0);
        assertFalse(converted.inputs().get(0).consumed());
        assertEquals(1L, converted.inputs().get(0).count());
        assertEquals(500L, converted.inputFluids().get(0).amount());
        assertEquals(2, converted.outputs().get(0).getCount());
        var press = new PressRecipe(ID, 1000, 0.0F,
                List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.DIAMOND)), List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(1.0F), List.of());
        var pressing = adapter(new ThermalRecipeAdapter<>(PressRecipe.class, "machine_press",
                "thermal_press_", AlloyFurnaceMode.PRESS)).convertAll(RecipeHolder.of(press), null).get(0);
        assertEquals(64L, pressing.inputs().get(0).count());
        assertTrue(pressing.inputs().get(0).consumed());
        assertEquals(1L, pressing.inputs().get(1).count());
        assertFalse(pressing.inputs().get(1).consumed());
        var smelter = new SmelterRecipe(ID, 100, 0.0F, List.of(Ingredient.of(Items.IRON_INGOT)),
                List.of(), List.of(new ItemStack(Items.GOLD_INGOT)), List.of(1.0F), List.of());
        var smelting = adapter(new ThermalRecipeAdapter<>(SmelterRecipe.class, "machine_smelter",
                "thermal_smelter_", AlloyFurnaceMode.NORMAL)).convertAll(RecipeHolder.of(smelter), null).get(0);
        assertEquals(6400L, smelting.energy());
        assertEquals(64, smelting.outputs().get(0).getCount());
    }

    @Test void allThreeNativeBloodMagicFamiliesProduceTheirBatchAndPreserveOutputNbt() {
        var output = new ItemStack(Items.DIAMOND);
        output.getOrCreateTag().putString("kept", "foreign");
        var inputs = List.of(Ingredient.of(Items.IRON_INGOT));
        var altar = new RecipeBloodAltar(ID, inputs.get(0), output, 1, 1000, 10, 10);
        var forge = new RecipeTartaricForge(ID, inputs, output, 1.0D, 1.0D);
        var table = new RecipeAlchemyTable(ID, inputs, output, 100, 20, 1);
        var a = adapter(new BloodMagicRecipeAdapter<>(RecipeBloodAltar.class, "altar", "bm_altar_"));
        var f = adapter(new BloodMagicRecipeAdapter<>(RecipeTartaricForge.class, "soulforge", "bm_soulforge_"));
        var t = adapter(new BloodMagicRecipeAdapter<>(RecipeAlchemyTable.class, "alchemytable", "bm_alchemytable_"));
        for (var recipe : List.of(a.convertAll(RecipeHolder.of(altar), null).get(0),
                f.convertAll(RecipeHolder.of(forge), null).get(0), t.convertAll(RecipeHolder.of(table), null).get(0))) {
            assertEquals(64L, recipe.inputs().get(0).count());
            assertEquals(64, recipe.outputs().get(0).getCount());
            assertEquals(output.getTag(), recipe.outputs().get(0).getTag());
        }
    }

    @Test void nativeEmbersUsesPublicRecipesAndKeepsAllRequiredMixingFluids() {
        var mixing = mock(IMixingRecipe.class);
        when(mixing.getDisplayInputFluids()).thenReturn(new ArrayList<>(List.of(
                com.rekindled.embers.recipe.FluidIngredient.of(Fluids.WATER, 5),
                com.rekindled.embers.recipe.FluidIngredient.of(Fluids.LAVA, 10))));
        when(mixing.getDisplayOutput()).thenReturn(new FluidStack(Fluids.WATER, 15));
        var recipe = adapter(new EmbersRecipeAdapter<>(IMixingRecipe.class, "mixer", "embers_mixing_", false))
                .convertAll(new RecipeHolder<>(ID, mixing), null).get(0);
        assertEquals(2, recipe.inputFluids().size());
        assertEquals(320L, recipe.inputFluids().get(0).amount());
        assertEquals(640L, recipe.inputFluids().get(1).amount());
        assertEquals(960, recipe.outputFluids().get(0).getAmount());
        var melting = mock(IMeltingRecipe.class);
        when(melting.getDisplayInput()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(melting.getDisplayOutput()).thenReturn(new FluidStack(Fluids.WATER, 5));
        when(melting.getBonus()).thenReturn(new FluidStack(Fluids.LAVA, 1));
        assertTrue(adapter(new EmbersRecipeAdapter<>(IMeltingRecipe.class, "melter", "embers_melter_", false))
                .convertAll(new RecipeHolder<>(ID, melting), null).isEmpty());
        assertEquals(2, adapter(new EmbersRecipeAdapter<>(IMeltingRecipe.class, "geologic_separator", "embers_geo_separator_", true))
                .convertAll(new RecipeHolder<>(ID, melting), null).get(0).outputFluids().size());
        var alchemy = mock(IAlchemyRecipe.class);
        when(alchemy.getCenterInput()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(alchemy.getInputs()).thenReturn(List.of(Ingredient.of(Items.GOLD_INGOT)));
        when(alchemy.getResultItem()).thenReturn(new ItemStack(Items.DIAMOND));
        assertEquals(2, adapter(new EmbersRecipeAdapter<>(IAlchemyRecipe.class, "alchemy_tablet", "embers_alchemy_", false))
                .convertAll(new RecipeHolder<>(ID, alchemy), null).get(0).inputs().size());
        var stamp = mock(IStampingRecipe.class);
        when(stamp.getDisplayInput()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(stamp.getDisplayInputFluid()).thenReturn(com.rekindled.embers.recipe.FluidIngredient.of(Fluids.WATER, 5));
        when(stamp.getResultItem()).thenReturn(new ItemStack(Items.DIAMOND));
        assertEquals(320L, adapter(new EmbersRecipeAdapter<>(IStampingRecipe.class, "stamper", "embers_stamping_", false))
                .convertAll(new RecipeHolder<>(ID, stamp), null).get(0).inputFluids().get(0).amount());
    }
}
