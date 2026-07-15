package com.sorrowmist.useless.content.recipe;

import cy.jdkdigital.productivebees.common.crafting.ingredient.ComponentIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedOutputScalerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void scalesGuaranteedCombAndFivePercentByproduct() {
        ExpectedOutputScaler.ScaledOutputs scaled = scale(
                weighted(new ItemStack(Items.HONEYCOMB), 1, 1, 1.0),
                weighted(new ItemStack(Items.WHEAT_SEEDS), 1, 1, 0.05)
        );

        assertEquals(20, scaled.operations());
        assertEquals(20, findCount(scaled.outputs(), Items.HONEYCOMB.getDefaultInstance()));
        assertEquals(1, findCount(scaled.outputs(), Items.WHEAT_SEEDS.getDefaultInstance()));
    }

    @Test
    void usesUniformMeanForChanceAndAmountRange() {
        ExpectedOutputScaler.ScaledOutputs scaled = scale(
                weighted(new ItemStack(Items.IRON_INGOT), 1, 2, 0.1)
        );

        assertEquals(20, scaled.operations());
        assertEquals(3, scaled.outputs().getFirst().getCount());
    }

    @Test
    void usesLeastCommonMultipleAcrossOutputs() {
        ExpectedOutputScaler.ScaledOutputs scaled = scale(
                weighted(new ItemStack(Items.IRON_INGOT), 1, 1, 0.5),
                weighted(new ItemStack(Items.GOLD_INGOT), 1, 1, 1.0 / 3.0)
        );

        assertEquals(6, scaled.operations());
        assertEquals(3, findCount(scaled.outputs(), Items.IRON_INGOT.getDefaultInstance()));
        assertEquals(2, findCount(scaled.outputs(), Items.GOLD_INGOT.getDefaultInstance()));
    }

    @Test
    void mergesOnlyComponentIdenticalOutputs() {
        ItemStack alphaA = namedPaper("alpha");
        ItemStack alphaB = namedPaper("alpha");
        ItemStack beta = namedPaper("beta");

        ExpectedOutputScaler.ScaledOutputs scaled = scale(
                weighted(alphaA, 1, 1, 1.0),
                weighted(alphaB, 1, 1, 1.0),
                weighted(beta, 1, 1, 1.0)
        );

        assertEquals(1, scaled.operations());
        assertEquals(2, scaled.outputs().size());
        assertEquals(2, findCount(scaled.outputs(), alphaA));
        assertEquals(1, findCount(scaled.outputs(), beta));
    }

    @Test
    void rejectsInvalidRangesAndUnrepresentableBatchSizes() {
        assertTrue(ExpectedOutputScaler.scale(List.of(
                weighted(new ItemStack(Items.DIAMOND), 2, 1, 1.0)
        )).isEmpty());

        Optional<ExpectedOutputScaler.ScaledOutputs> overflowing = ExpectedOutputScaler.scale(List.of(
                weighted(new ItemStack(Items.DIAMOND), 1, 1, 1.0 / 997.0),
                weighted(new ItemStack(Items.EMERALD), 1, 1, 1.0 / 991.0),
                weighted(new ItemStack(Items.GOLD_INGOT), 1, 1, 1.0 / 983.0),
                weighted(new ItemStack(Items.IRON_INGOT), 1, 1, 1.0 / 977.0)
        ));
        assertTrue(overflowing.isEmpty());
        assertTrue(ExpectedOutputScaler.multiplyToInt(Integer.MAX_VALUE, 2).isEmpty());
    }

    @Test
    void configurableSpawnEggIngredientDistinguishesBeeTypeComponents() {
        ItemStack ironEgg = configuredEgg("productivebees:iron");
        ItemStack diamondEgg = configuredEgg("productivebees:diamond");
        Ingredient ironMold = ComponentIngredient.of(ironEgg);

        assertTrue(ironMold.test(ironEgg));
        assertFalse(ironMold.test(diamondEgg));

        ItemStack namedIronEgg = ironEgg.copy();
        namedIronEgg.set(DataComponents.CUSTOM_NAME, Component.literal("kept as a mold"));
        assertTrue(ironMold.test(namedIronEgg));
    }

    @Test
    void fixedSpawnEggIngredientMatchesByItem() {
        ItemStack fixedEgg = new ItemStack(Items.BEE_SPAWN_EGG);
        Ingredient fixedMold = ComponentIngredient.of(fixedEgg);

        ItemStack namedEgg = fixedEgg.copy();
        namedEgg.set(DataComponents.CUSTOM_NAME, Component.literal("fixed bee"));
        assertTrue(fixedMold.test(namedEgg));
        assertFalse(fixedMold.test(new ItemStack(Items.CREEPER_SPAWN_EGG)));
    }

    private static ExpectedOutputScaler.ScaledOutputs scale(
            ExpectedOutputScaler.WeightedItemOutput... outputs) {
        return ExpectedOutputScaler.scale(List.of(outputs)).orElseThrow();
    }

    private static ExpectedOutputScaler.WeightedItemOutput weighted(
            ItemStack stack, int min, int max, double chance) {
        return new ExpectedOutputScaler.WeightedItemOutput(stack, min, max, chance);
    }

    private static ItemStack namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack configuredEgg(String beeType) {
        ItemStack stack = new ItemStack(Items.BEE_SPAWN_EGG);
        CompoundTag entityData = new CompoundTag();
        entityData.putString("type", beeType);
        entityData.putString("id", "productivebees:configurable_bee");
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));
        return stack;
    }

    private static int findCount(List<ItemStack> outputs, ItemStack expected) {
        return outputs.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
