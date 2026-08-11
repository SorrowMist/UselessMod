package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MekanismChemicalRecipeSupportTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void scalesChemicalAndFluidAmountsForItemFreeRecipes() {
        GenericStack chemicalInput = generic(Items.IRON_INGOT, 2L);
        GenericStack chemicalOutput = generic(Items.GOLD_INGOT, 3L);
        AdvancedAlloyFurnaceRecipe recipe = MekanismChemicalRecipeSupport.recipe(
                id("item_free"), List.of(), List.of(water(4)), List.of(chemicalInput),
                List.of(), List.of(water(5)), List.of(chemicalOutput), 12_345L, 200, null);

        assertEquals(4_000, recipe.inputFluids().getFirst().amount());
        assertEquals(2_000, recipe.keyInputs().getFirst().amount());
        assertEquals(5_000, recipe.outputFluids().getFirst().getAmount());
        assertEquals(3_000, recipe.keyOutputs().getFirst().amount());
        assertEquals(12_345L, recipe.energy());
        assertEquals(200, recipe.processTime());
    }

    @Test
    void leavesItemBearingRecipesUnchanged() {
        GenericStack chemicalInput = generic(Items.IRON_INGOT, 2L);
        GenericStack chemicalOutput = generic(Items.GOLD_INGOT, 3L);
        AdvancedAlloyFurnaceRecipe recipe = MekanismChemicalRecipeSupport.recipe(
                id("item_bearing"),
                List.of(new CountedIngredient(Ingredient.of(Items.DIAMOND), 7L)),
                List.of(water(4)), List.of(chemicalInput),
                List.of(new ItemStack(Items.EMERALD, 6)), List.of(water(5)),
                List.of(chemicalOutput), 12_345L, 37, null);

        assertEquals(4, recipe.inputFluids().getFirst().amount());
        assertEquals(2L, recipe.keyInputs().getFirst().amount());
        assertEquals(5, recipe.outputFluids().getFirst().getAmount());
        assertEquals(3L, recipe.keyOutputs().getFirst().amount());
        assertEquals(6, recipe.outputs().getFirst().getCount());
        assertEquals(12_345L, recipe.energy());
        assertEquals(37, recipe.processTime());
    }

    @Test
    void saturatesLongChemicalAndIntFluidAmounts() {
        GenericStack chemical = generic(Items.IRON_INGOT, Long.MAX_VALUE);
        FluidStack fluid = water(Integer.MAX_VALUE);
        AdvancedAlloyFurnaceRecipe recipe = MekanismChemicalRecipeSupport.recipe(
                id("overflow"), List.of(), List.of(fluid), List.of(chemical),
                List.of(), List.of(fluid), List.of(chemical), 1L, 1, null);

        assertEquals(Long.MAX_VALUE, recipe.keyInputs().getFirst().amount());
        assertEquals(Long.MAX_VALUE, recipe.keyOutputs().getFirst().amount());
        assertEquals(Integer.MAX_VALUE, recipe.inputFluids().getFirst().amount());
        assertEquals(Integer.MAX_VALUE, recipe.outputFluids().getFirst().getAmount());
    }

    @Test
    void treatsFluidIngredientRepresentationsAsAlternativesAndAllowsTankSplitting() {
        FluidStackIngredient required = IngredientCreatorAccess.fluid().from(
                FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 1_000);

        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(
                java.util.Map.of(water(1_000), 1_000L), required));
        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(
                java.util.Map.of(lava(1_000), 1_000L), required));
        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(
                java.util.Map.of(water(500), 500L, lava(500), 500L), required));
    }

    @Test
    void combinesSplitAmountsOfTheSameFluidOnly() {
        FluidStackIngredient required = IngredientCreatorAccess.fluid().from(
                FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 1_000);
        var splitWater = AdapterUtils.mergeFluids(java.util.List.of(water(400), water(600)));

        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(splitWater, required));
    }

    @Test
    void handlesLargeAvailableFluidAmountsWithoutOverflow() {
        FluidStackIngredient required = IngredientCreatorAccess.fluid().from(
                FluidIngredient.of(Fluids.WATER), Integer.MAX_VALUE);

        assertTrue(MekanismChemicalRecipeSupport.matchesFluid(
                java.util.Map.of(water(Integer.MAX_VALUE), Long.MAX_VALUE), required));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("useless_mod_test", path);
    }

    private static GenericStack generic(net.minecraft.world.item.Item item, long amount) {
        AEItemKey key = Objects.requireNonNull(AEItemKey.of(item));
        return new GenericStack(key, amount);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack lava(int amount) {
        return new FluidStack(Fluids.LAVA, amount);
    }
}
