package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AlloyFurnaceRecipeManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void selectsMostSpecificOverlappingRecipeRegardlessOfCandidateOrder() {
        AdvancedAlloyFurnaceRecipe recipeA = recipe(
                "recipe_a",
                List.of(counted(Items.OAK_LOG, 2), counted(Items.COBBLESTONE, 5)),
                List.of(water(1000)),
                Items.DIAMOND,
                Ingredient.EMPTY
        );
        AdvancedAlloyFurnaceRecipe recipeB = recipe(
                "recipe_b",
                List.of(counted(Items.OAK_LOG, 1)),
                List.of(water(1000)),
                Items.GOLD_INGOT,
                Ingredient.EMPTY
        );

        List<ItemStack> fullInputs = List.of(stack(Items.OAK_LOG, 2), stack(Items.COBBLESTONE, 5));
        assertSame(recipeA, select(List.of(recipeB, recipeA), fullInputs, List.of(water(1000)), List.of(), 1));
        assertSame(recipeA, select(List.of(recipeA, recipeB), fullInputs, List.of(water(1000)), List.of(), 1));
        assertSame(recipeB, select(List.of(recipeA, recipeB), List.of(stack(Items.OAK_LOG, 1)),
                List.of(water(1000)), List.of(), 1));
    }

    @Test
    void aeExpectedOutputLocksTheIntendedRecipe() {
        AdvancedAlloyFurnaceRecipe recipeA = recipe(
                "recipe_a",
                List.of(counted(Items.OAK_LOG, 2), counted(Items.COBBLESTONE, 5)),
                List.of(water(1000)),
                Items.DIAMOND,
                Ingredient.EMPTY
        );
        AdvancedAlloyFurnaceRecipe recipeB = recipe(
                "recipe_b",
                List.of(counted(Items.OAK_LOG, 1)),
                List.of(water(1000)),
                Items.GOLD_INGOT,
                Ingredient.EMPTY
        );
        List<ItemStack> inputs = List.of(stack(Items.OAK_LOG, 2), stack(Items.COBBLESTONE, 5));

        assertSame(recipeB, select(List.of(recipeA, recipeB), inputs, List.of(water(1000)),
                List.of(genericItem(Items.GOLD_INGOT, 64)), 1));
        assertSame(recipeA, select(List.of(recipeB, recipeA), inputs, List.of(water(1000)),
                List.of(genericItem(Items.DIAMOND, 1)), 1));
    }

    @Test
    void aggregatedAeInputsAreValidatedAgainstOperationCount() {
        AdvancedAlloyFurnaceRecipe onePerOperation = recipe(
                "one_per_operation",
                List.of(counted(Items.OAK_LOG, 1)),
                List.of(water(1000)),
                Items.GOLD_INGOT,
                Ingredient.EMPTY
        );
        AdvancedAlloyFurnaceRecipe largerRecipe = recipe(
                "larger_recipe",
                List.of(counted(Items.OAK_LOG, 2)),
                List.of(water(2000)),
                Items.GOLD_INGOT,
                Ingredient.EMPTY
        );

        assertSame(onePerOperation, select(
                List.of(largerRecipe, onePerOperation),
                List.of(stack(Items.OAK_LOG, 2)),
                List.of(water(2000)),
                List.of(genericItem(Items.GOLD_INGOT, 1)),
                2
        ));
    }

    @Test
    void moldRecipeRequiresItsMoldAndOutranksGenericRecipe() {
        AdvancedAlloyFurnaceRecipe generic = recipe(
                "generic",
                List.of(counted(Items.OAK_LOG, 1)),
                List.of(water(1000)),
                Items.GOLD_INGOT,
                Ingredient.EMPTY
        );
        AdvancedAlloyFurnaceRecipe moldSpecific = recipe(
                "mold_specific",
                List.of(counted(Items.OAK_LOG, 1)),
                List.of(water(1000)),
                Items.GOLD_INGOT,
                Ingredient.of(Items.FURNACE)
        );

        List<ItemStack> inputs = List.of(stack(Items.OAK_LOG, 1));
        List<FluidStack> fluids = List.of(water(1000));
        assertSame(generic, AlloyFurnaceRecipeManager.selectBestCandidate(
                List.of(moldSpecific, generic), inputs, fluids, List.of(), ItemStack.EMPTY, List.of(), 1));
        assertSame(moldSpecific, AlloyFurnaceRecipeManager.selectBestCandidate(
                List.of(generic, moldSpecific), inputs, fluids, List.of(), new ItemStack(Items.FURNACE), List.of(), 1));
    }

    @Test
    void exactTieUsesRecipeId() {
        AdvancedAlloyFurnaceRecipe idA = recipe(
                "a_recipe", List.of(counted(Items.OAK_LOG, 1)), List.of(water(1000)),
                Items.GOLD_INGOT, Ingredient.EMPTY);
        AdvancedAlloyFurnaceRecipe idZ = recipe(
                "z_recipe", List.of(counted(Items.OAK_LOG, 1)), List.of(water(1000)),
                Items.DIAMOND, Ingredient.EMPTY);

        assertSame(idA, select(List.of(idZ, idA), List.of(stack(Items.OAK_LOG, 1)),
                List.of(water(1000)), List.of(), 1));
    }

    @Test
    void cacheFingerprintIncludesAmountsComponentsAndIsOrderIndependent() {
        List<ItemStack> ordered = List.of(stack(Items.OAK_LOG, 2), stack(Items.COBBLESTONE, 5));
        List<ItemStack> reversed = List.of(stack(Items.COBBLESTONE, 5), stack(Items.OAK_LOG, 2));

        AlloyFurnaceRecipeManager.RecipeCacheKey base = cacheKey(ordered, List.of(water(1000)));
        assertEquals(base, cacheKey(reversed, List.of(water(1000))));
        assertNotEquals(base, cacheKey(List.of(stack(Items.OAK_LOG, 1), stack(Items.COBBLESTONE, 5)),
                List.of(water(1000))));
        assertNotEquals(base, cacheKey(ordered, List.of(water(999))));
        assertNotEquals(base, AlloyFurnaceRecipeManager.RecipeCacheKey.create(
                ordered, List.of(water(1000)), List.of(), ItemStack.EMPTY,
                List.of(genericItem(Items.GOLD_INGOT, 1)), 1));
        assertNotEquals(base, AlloyFurnaceRecipeManager.RecipeCacheKey.create(
                ordered, List.of(water(1000)), List.of(), ItemStack.EMPTY, List.of(), 2));

        ItemStack namedLog = stack(Items.OAK_LOG, 2);
        namedLog.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
        assertNotEquals(base, cacheKey(List.of(namedLog, stack(Items.COBBLESTONE, 5)), List.of(water(1000))));
    }

    private static AdvancedAlloyFurnaceRecipe select(List<AdvancedAlloyFurnaceRecipe> candidates,
                                                       List<ItemStack> inputs, List<FluidStack> fluids,
                                                       List<GenericStack> expectedOutputs, long operations) {
        return AlloyFurnaceRecipeManager.selectBestCandidate(
                candidates, inputs, fluids, List.of(), ItemStack.EMPTY, expectedOutputs, operations);
    }

    private static AlloyFurnaceRecipeManager.RecipeCacheKey cacheKey(List<ItemStack> inputs,
                                                                      List<FluidStack> fluids) {
        return AlloyFurnaceRecipeManager.RecipeCacheKey.create(
                inputs, fluids, List.of(), ItemStack.EMPTY, List.of(), 1);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(String path, List<CountedIngredient> inputs,
                                                       List<FluidStack> fluids, Item output, Ingredient mold) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", path),
                inputs,
                fluids,
                List.of(),
                List.of(new ItemStack(output)),
                List.of(),
                List.of(),
                2000,
                200,
                Ingredient.EMPTY,
                0,
                mold,
                AlloyFurnaceMode.NORMAL
        );
    }

    private static CountedIngredient counted(Item item, long count) {
        return new CountedIngredient(Ingredient.of(item), count);
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static GenericStack genericItem(Item item, int count) {
        return Objects.requireNonNull(GenericStack.fromItemStack(new ItemStack(item, count)));
    }
}
