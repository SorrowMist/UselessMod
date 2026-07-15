package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemIngredientAllocatorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsDistinctEmptyDisplayCustomIngredientsAndMatchesEachOne() {
        Ingredient oak = emptyDisplayIngredient(Items.OAK_LOG);
        Ingredient cobblestone = emptyDisplayIngredient(Items.COBBLESTONE);

        List<CountedIngredient> merged = AdapterUtils.mergeIngredients(List.of(oak, cobblestone));
        assertEquals(2, merged.size());

        List<ItemStack> complete = List.of(stack(Items.OAK_LOG, 1), stack(Items.COBBLESTONE, 1));
        assertTrue(ItemIngredientAllocator.matches(merged, complete, 1));
        assertFalse(ItemIngredientAllocator.matches(merged, List.of(stack(Items.OAK_LOG, 2)), 1));

        Map<Ingredient, Long> required = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(required, oak, 1);
        AdapterUtils.mergeIngredient(required, cobblestone, 1);
        assertEquals(2, required.size());
        assertTrue(AdapterUtils.matchesRequired(AdapterUtils.mergeInputs(complete), required));
    }

    @Test
    void allocatesOverlappingAndRepeatedRequirementsWithoutReusingItems() {
        Ingredient oakOrCobblestone = Ingredient.of(Items.OAK_LOG, Items.COBBLESTONE);
        Ingredient oak = Ingredient.of(Items.OAK_LOG);
        Ingredient customDiamond = emptyDisplayIngredient(Items.DIAMOND);
        List<CountedIngredient> repeatedCustom = AdapterUtils.mergeIngredients(
                List.of(customDiamond, emptyDisplayIngredient(Items.DIAMOND)));
        assertEquals(1, repeatedCustom.size());
        assertEquals(2, repeatedCustom.getFirst().count());

        List<CountedIngredient> requirements = List.of(
                new CountedIngredient(oakOrCobblestone, 1),
                new CountedIngredient(oak, 1),
                repeatedCustom.getFirst()
        );

        List<ItemStack> splitInputsWithExtra = List.of(
                stack(Items.OAK_LOG, 1),
                stack(Items.COBBLESTONE, 1),
                stack(Items.DIAMOND, 1),
                stack(Items.DIAMOND, 1),
                stack(Items.DIRT, 64)
        );
        assertTrue(ItemIngredientAllocator.matches(requirements, splitInputsWithExtra, 1));
        assertFalse(ItemIngredientAllocator.matches(requirements,
                List.of(stack(Items.OAK_LOG, 1), stack(Items.DIAMOND, 2), stack(Items.DIRT, 64)), 1));

        List<CountedIngredient> twoItemRequirements = requirements.subList(0, 2);
        assertEquals(3, ItemIngredientAllocator.maxOperations(twoItemRequirements,
                List.of(stack(Items.OAK_LOG, 4), stack(Items.COBBLESTONE, 2), stack(Items.DIRT, 64))));
    }

    @Test
    void mergeInputsPreservesDifferentComponentsOfTheSameItem() {
        ItemStack alpha = namedPaper("alpha", 2);
        ItemStack beta = namedPaper("beta", 3);
        Ingredient alphaIngredient = DataComponentIngredient.of(true, alpha.copyWithCount(1));
        Ingredient betaIngredient = DataComponentIngredient.of(true, beta.copyWithCount(1));

        Map<Ingredient, Long> mergedInputs = AdapterUtils.mergeInputs(List.of(alpha, beta));
        assertEquals(2, mergedInputs.size());

        Map<Ingredient, Long> required = new LinkedHashMap<>();
        required.put(alphaIngredient, 2L);
        required.put(betaIngredient, 3L);
        assertTrue(AdapterUtils.matchesRequired(mergedInputs, required));
        assertFalse(AdapterUtils.matchesRequired(AdapterUtils.mergeInputs(List.of(namedPaper("alpha", 5))), required));
    }

    @Test
    void recipeManagerSupportsSingleAndAggregatedAeOperationsForCustomIngredients() {
        Ingredient oak = emptyDisplayIngredient(Items.OAK_LOG);
        Ingredient cobblestone = emptyDisplayIngredient(Items.COBBLESTONE);
        AdvancedAlloyFurnaceRecipe recipe = recipe(List.of(
                new CountedIngredient(oak, 1),
                new CountedIngredient(cobblestone, 1)
        ));

        assertSame(recipe, select(recipe,
                List.of(stack(Items.OAK_LOG, 1), stack(Items.COBBLESTONE, 1)), 1));
        assertSame(recipe, select(recipe,
                List.of(stack(Items.OAK_LOG, 2), stack(Items.COBBLESTONE, 2)), 2));
        assertNull(select(recipe,
                List.of(stack(Items.OAK_LOG, 2), stack(Items.COBBLESTONE, 1)), 2));
    }

    private static AdvancedAlloyFurnaceRecipe select(AdvancedAlloyFurnaceRecipe recipe,
                                                       List<ItemStack> inputs, long operations) {
        return AlloyFurnaceRecipeManager.selectBestCandidate(
                List.of(recipe), inputs, List.of(), List.of(), ItemStack.EMPTY, List.of(), operations);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(List<CountedIngredient> inputs) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "custom_ingredient"),
                inputs,
                List.of(),
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

    private static Ingredient emptyDisplayIngredient(Item item) {
        return new EmptyDisplayIngredient(item).toVanilla();
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static ItemStack namedPaper(String name, int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private record EmptyDisplayIngredient(Item item) implements ICustomIngredient {
        @Override
        public boolean test(ItemStack stack) {
            return stack.is(this.item);
        }

        @Override
        public Stream<ItemStack> getItems() {
            return Stream.empty();
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IngredientType<?> getType() {
            // Matching tests never serialize this synthetic ingredient.
            return null;
        }
    }
}
