package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.api.recipe.AraVitaeRecipe;
import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.datacomponent.SpiritusType;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorPotionRecipe;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeTransformRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeUpgradeRecipe;
import com.breakinblocks.neovitae.common.recipe.tabulavitae.TabulaVitaeRecipe;
import com.mojang.datafixers.util.Pair;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoVitaeRecipeAdapterTest {
    @Test
    void convertsHellfireForgeDrainAndTransformInputs() {
        ForgeRecipe normal = new ForgeRecipe(
                900.0, 12.0, List.of(Ingredient.of(Items.IRON_INGOT)),
                new ItemStack(Items.GOLD_INGOT), Optional.empty());
        AdvancedAlloyFurnaceRecipe converted = new HellfireForgeRecipeAdapter()
                .convertAll(holder("forge", normal), null).getFirst();

        assertEquals(120_000L, converted.energy());
        assertEquals(20, converted.processTime());
        assertTrue(converted.inputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.outputs().getFirst().is(Items.GOLD_INGOT));

        ForgeTransformRecipe transform = new ForgeTransformRecipe(
                0.0, 2.0, List.of(Ingredient.of(Items.IRON_INGOT)),
                Ingredient.of(Items.DIAMOND), new ItemStack(Items.EMERALD));
        List<AdvancedAlloyFurnaceRecipe> transformed = new HellfireForgeRecipeAdapter()
                .convertAll(holder("transform", transform), null);

        assertEquals(1, transformed.size());
        assertEquals(20, transformed.getFirst().processTime());
        assertEquals(20_000L, transformed.getFirst().energy());
        assertTrue(transformed.getFirst().inputs().stream()
                .anyMatch(input -> input.ingredient().test(new ItemStack(Items.DIAMOND))));
        assertTrue(transformed.getFirst().outputs().getFirst().is(Items.EMERALD));
        assertTrue(transformed.getFirst().mold().test(new ItemStack(NVBlocks.HELLFIRE_FORGE.asItem())));
    }

    @Test
    void upgradeOnlyAcceptsDamageableTargetsAndUsesDrain() {
        ForgeUpgradeRecipe source = new ForgeUpgradeRecipe(
                1.0, 3.0, List.of(Ingredient.of(Items.IRON_INGOT)));
        assertTrue(HellfireForgeRecipeAdapter.isUpgradeTarget(new ItemStack(Items.IRON_SWORD)));
        assertFalse(HellfireForgeRecipeAdapter.isUpgradeTarget(new ItemStack(Items.DIAMOND)));

        ItemStack target = new ItemStack(Items.IRON_SWORD);
        List<AdvancedAlloyFurnaceRecipe> converted = new HellfireForgeRecipeAdapter()
                .convertAll(holder("upgrade", source), null,
                        List.of(new ItemStack(Items.IRON_INGOT), target));

        assertEquals(1, converted.size());
        assertEquals(30_000L, converted.getFirst().energy());
        assertEquals(20, converted.getFirst().processTime());
        assertTrue(converted.getFirst().inputs().stream()
                .anyMatch(input -> input.ingredient().test(target)));
        assertTrue(converted.getFirst().outputs().getFirst().is(Items.IRON_SWORD));
    }

    @Test
    void tabulaKeepsTicksAndSyphonAsEnergy() {
        TabulaVitaeRecipe source = new TabulaVitaeRecipe(
                List.of(Ingredient.of(Items.IRON_INGOT)),
                new ItemStack(Items.GOLD_INGOT), 4, 37, 99);
        AdvancedAlloyFurnaceRecipe converted = new TabulaVitaeRecipeAdapter()
                .convertAll(holder("tabula", source), null).getFirst();

        assertEquals(40_000L, converted.energy());
        assertEquals(37, converted.processTime());
        assertTrue(converted.outputs().getFirst().is(Items.GOLD_INGOT));
        assertTrue(converted.mold().test(new ItemStack(NVBlocks.TABULA_VITAE.asItem())));
        assertEquals(2_000L, NeoVitaeAdapterUtils.energyFor(0.0));
    }

    @Test
    void athanorKeepsFluidOutputsAndToolAsReusableMold() {
        SizedFluidIngredient inputFluid = new SizedFluidIngredient(
                FluidIngredient.single(new FluidStack(Fluids.WATER, 250)), 250);
        AthanorRecipe source = new AthanorRecipe(
                Ingredient.of(Items.FLINT),
                List.of(Ingredient.of(Items.IRON_INGOT)),
                List.of(new ItemStack(Items.GOLD_INGOT)),
                List.of(Pair.of(new ItemStack(Items.DIAMOND), 0.01)),
                Optional.of(inputFluid), Optional.of(new FluidStack(Fluids.LAVA, 125)),
                Map.of(SpiritusType.RAW, 2.5));

        AdvancedAlloyFurnaceRecipe converted = new AthanorRecipeAdapter()
                .convertAll(holder("athanor", source), null).getFirst();

        assertEquals(25_000L, converted.energy());
        assertEquals(20, converted.processTime());
        assertEquals(1, converted.inputs().size());
        assertEquals(2, converted.outputs().size());
        assertTrue(converted.outputs().getFirst().is(Items.GOLD_INGOT));
        assertEquals(1, converted.outputFluids().size());
        assertEquals(Fluids.LAVA, converted.outputFluids().getFirst().getFluid());
        assertEquals(2, converted.molds().size());
        assertTrue(converted.molds().get(1).test(new ItemStack(Items.FLINT)));
    }

    @Test
    void athanorPotionCopiesEffectsToGuaranteedAndChanceOutputs() {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(), Optional.empty(),
                List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200))));
        Ingredient potionInput = DataComponentIngredient.of(true, potion);
        AthanorPotionRecipe source = new AthanorPotionRecipe(
                Ingredient.of(Items.FLINT), potionInput,
                List.of(new ItemStack(Items.POTION)),
                List.of(Pair.of(new ItemStack(Items.SPLASH_POTION), 0.0)),
                Optional.empty(), Optional.empty());

        List<AdvancedAlloyFurnaceRecipe> converted = new AthanorRecipeAdapter()
                .convertAll(holder("potion", source), null, List.of(potion));

        assertEquals(1, converted.size());
        assertEquals(2, converted.getFirst().outputs().size());
        assertTrue(converted.getFirst().outputs().stream().allMatch(output ->
                output.get(DataComponents.POTION_CONTENTS) != null
                        && output.get(DataComponents.POTION_CONTENTS).hasEffects()));
    }

    @Test
    void araCopiesInputComponentsAndUsesBloodProcessingTime() {
        AraVitaeRecipe source = new com.breakinblocks.neovitae.common.recipe.aravitae.AraVitaeRecipe(
                Ingredient.of(Items.PAPER), new ItemStack(Items.BOOK),
                99, 101, 5, 3, true);
        ItemStack input = new ItemStack(Items.PAPER);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("copied"));

        AdvancedAlloyFurnaceRecipe converted = new AraVitaeRecipeAdapter()
                .convertAll(holder("ara", source), null, List.of(input)).getFirst();

        assertEquals(1_010_000L, converted.energy());
        assertEquals(21, converted.processTime());
        assertEquals("copied", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(1, converted.inputs().size());
        assertTrue(converted.inputs().getFirst().ingredient().test(input));
        assertTrue(converted.mold().test(new ItemStack(NVBlocks.ARA_VITAE.asItem())));
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>>
    RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("neovitae_test", path), recipe);
    }
}
