package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.sammy.malum.common.item.impetus.ImpetusItem;
import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
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

class SpiritFocusingRecipeAdapterTest {
    @BeforeAll
    static void bootstrap() {
        MalumNativeTestSupport.initialize();
    }

    @Test
    void focusingKeepsTheReusableImpetusAsANonConsumedInputAndConsumesSpirits() {
        ImpetusItem impetus = MalumNativeTestSupport.impetus();
        ItemStack output = tagged(Items.AMETHYST_SHARD, 8, "focus-output");
        SpiritFocusingRecipe source = new SpiritFocusingRecipe(
                id("amethyst_shard"),
                300,
                0,
                Ingredient.of(impetus),
                output,
                List.of(MalumNativeTestSupport.spirit(2), MalumNativeTestSupport.spirit(2)));

        try (MockedStatic<MalumAdapterUtils> stubs = malumCrucible()) {
            AdvancedAlloyFurnaceRecipe converted = new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("amethyst_shard"), source), null)
                    .get(0);

            // Equivalent strict-NBT rows need not be merged into one Ingredient instance.
            CountedIngredient impetusInput = converted.inputs().get(0);
            assertFalse(impetusInput.consumed());
            assertEquals(1L, impetusInput.count());
            assertTrue(impetusInput.ingredient().test(new ItemStack(impetus)));
            assertEquals(4L, required(converted, MalumNativeTestSupport.spiritStack(1)));
            assertTrue(converted.inputs().stream()
                    .filter(input -> input != impetusInput)
                    .allMatch(CountedIngredient::consumed));
            assertEquals(300, converted.processTime());
            assertEquals(8, converted.outputs().get(0).getCount());
            assertEquals("focus-output", converted.outputs().get(0).getTag().getString("marker"));
            assertTrue(converted.molds().get(0).test(new ItemStack(Items.BLAST_FURNACE)));
        }
    }

    @Test
    void invalidFocusingDataIsRejected() {
        ImpetusItem impetus = MalumNativeTestSupport.impetus();
        try (MockedStatic<MalumAdapterUtils> stubs = malumCrucible()) {
            SpiritFocusingRecipe wearsTool = new SpiritFocusingRecipe(
                    id("wears_tool"), 300, 1, Ingredient.of(impetus), new ItemStack(Items.AMETHYST_SHARD),
                    List.of(MalumNativeTestSupport.spirit(1)));
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("wears_tool"), wearsTool), null).isEmpty());

            SpiritFocusingRecipe zeroTime = new SpiritFocusingRecipe(
                    id("zero_time"), 0, 0, Ingredient.of(impetus), new ItemStack(Items.AMETHYST_SHARD),
                    List.of(MalumNativeTestSupport.spirit(1)));
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("zero_time"), zeroTime), null).isEmpty());

            SpiritFocusingRecipe emptyInput = new SpiritFocusingRecipe(
                    id("empty_input"), 300, 0, Ingredient.EMPTY, new ItemStack(Items.AMETHYST_SHARD),
                    List.of(MalumNativeTestSupport.spirit(1)));
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_input"), emptyInput), null).isEmpty());

            SpiritFocusingRecipe emptyOutput = new SpiritFocusingRecipe(
                    id("empty_output"), 300, 0, Ingredient.of(impetus), ItemStack.EMPTY,
                    List.of(MalumNativeTestSupport.spirit(1)));
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_output"), emptyOutput), null).isEmpty());

            SpiritFocusingRecipe zeroSpirit = new SpiritFocusingRecipe(
                    id("zero_spirit"), 300, 0, Ingredient.of(impetus), new ItemStack(Items.AMETHYST_SHARD),
                    List.of(MalumNativeTestSupport.spirit(0)));
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("zero_spirit"), zeroSpirit), null).isEmpty());

            SpiritFocusingRecipe noSpirits = new SpiritFocusingRecipe(
                    id("no_spirits"), 300, 0, Ingredient.of(impetus), new ItemStack(Items.AMETHYST_SHARD),
                    List.of());
            assertTrue(new SpiritFocusingRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("no_spirits"), noSpirits), null).isEmpty());
        }
    }

    private static MockedStatic<MalumAdapterUtils> malumCrucible() {
        MockedStatic<MalumAdapterUtils> stubs =
                Mockito.mockStatic(MalumAdapterUtils.class, Mockito.CALLS_REAL_METHODS);
        stubs.when(() -> MalumAdapterUtils.item("spirit_crucible"))
                .thenReturn(new ItemStack(Items.BLAST_FURNACE));
        return stubs;
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        long total = 0L;
        for (CountedIngredient input : recipe.inputs()) {
            if (input.ingredient().test(stack)) {
                total += input.count();
            }
        }
        return total;
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
