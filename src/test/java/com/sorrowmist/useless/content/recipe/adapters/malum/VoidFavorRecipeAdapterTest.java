package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.sammy.malum.common.recipe.FavorOfTheVoidRecipe;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidFavorRecipeAdapterTest {
    @BeforeAll
    static void bootstrap() {
        MalumNativeTestSupport.initialize();
    }

    @Test
    void favorOfTheVoidConvertsOneItemAndKeepsTheNativeOutputNbt() {
        ItemStack output = tagged(Items.AMETHYST_SHARD, 2, "void-output");
        FavorOfTheVoidRecipe source = new FavorOfTheVoidRecipe(
                id("auric_embers"), Ingredient.of(Items.BLAZE_POWDER), output);

        try (MockedStatic<MalumAdapterUtils> stubs = malumConduit()) {
            AdvancedAlloyFurnaceRecipe converted = new VoidFavorRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("auric_embers"), source), null)
                    .get(0);

            assertEquals(1, converted.inputs().size());
            CountedIngredient input = converted.inputs().get(0);
            assertEquals(1L, input.count());
            assertTrue(input.consumed());
            assertTrue(input.ingredient().test(new ItemStack(Items.BLAZE_POWDER)));
            assertEquals(2, converted.outputs().get(0).getCount());
            assertEquals("void-output", converted.outputs().get(0).getTag().getString("marker"));
            assertEquals(80, converted.processTime());
            assertTrue(converted.molds().get(0).test(new ItemStack(Items.DROPPER)));
        }
    }

    @Test
    void strictNativeInputNbtIsNotDropped() {
        ItemStack exactInput = tagged(Items.BLAZE_POWDER, 1, "exact-input");
        FavorOfTheVoidRecipe source = new FavorOfTheVoidRecipe(
                id("strict_favor"),
                DataComponentIngredient.of(true, exactInput),
                new ItemStack(Items.AMETHYST_SHARD));

        try (MockedStatic<MalumAdapterUtils> stubs = malumConduit()) {
            AdvancedAlloyFurnaceRecipe converted = new VoidFavorRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("strict_favor"), source), null)
                    .get(0);
            CountedIngredient input = converted.inputs().get(0);
            assertTrue(input.ingredient().test(exactInput));
            assertFalse(input.ingredient().test(new ItemStack(Items.BLAZE_POWDER)));
        }
    }

    @Test
    void invalidFavorDataIsRejected() {
        try (MockedStatic<MalumAdapterUtils> stubs = malumConduit()) {
            FavorOfTheVoidRecipe emptyInput = new FavorOfTheVoidRecipe(
                    id("empty_input"), Ingredient.EMPTY, new ItemStack(Items.AMETHYST_SHARD));
            assertTrue(new VoidFavorRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_input"), emptyInput), null).isEmpty());

            FavorOfTheVoidRecipe emptyOutput = new FavorOfTheVoidRecipe(
                    id("empty_output"), Ingredient.of(Items.BLAZE_POWDER), ItemStack.EMPTY);
            assertTrue(new VoidFavorRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_output"), emptyOutput), null).isEmpty());
        }
    }

    private static MockedStatic<MalumAdapterUtils> malumConduit() {
        MockedStatic<MalumAdapterUtils> stubs =
                Mockito.mockStatic(MalumAdapterUtils.class, Mockito.CALLS_REAL_METHODS);
        stubs.when(() -> MalumAdapterUtils.item("void_conduit"))
                .thenReturn(new ItemStack(Items.DROPPER));
        return stubs;
    }

    private static ItemStack tagged(net.minecraft.world.item.Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", marker);
        return stack;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("malum_test", path);
    }
}
