package com.sorrowmist.useless.content.recipe.adapters.malum;

import appeng.api.stacks.GenericStack;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternProfile;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import team.lodestar.lodestone.systems.recipe.IngredientWithCount;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritInfusionRecipeAdapterTest {
    @BeforeAll
    static void bootstrap() {
        MalumNativeTestSupport.initialize();
    }

    @Test
    void staticConversionKeepsExactPrimaryExtraAndSpiritCountsAndOutputNbt() {
        ItemStack output = tagged(Items.EMERALD, 3, "base-output");
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                id("accelerating_inlay"),
                new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 4),
                true,
                output,
                List.of(MalumNativeTestSupport.spirit(2), MalumNativeTestSupport.spirit(3)),
                List.of(new IngredientWithCount(Ingredient.of(Items.DIAMOND), 2)));

        try (MockedStatic<MalumAdapterUtils> stubs = malumItems()) {
            AdvancedAlloyFurnaceRecipe converted = new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("accelerating_inlay"), source), null)
                    .get(0);

            assertEquals(4L, required(converted, new ItemStack(Items.GOLD_INGOT)));
            assertEquals(2L, required(converted, new ItemStack(Items.DIAMOND)));
            assertEquals(5L, required(converted, MalumNativeTestSupport.spiritStack(1)));
            assertEquals(300, converted.processTime());
            assertEquals(3, converted.outputs().get(0).getCount());
            assertEquals("base-output", converted.outputs().get(0).getTag().getString("marker"));
            assertTrue(converted.molds().get(0).test(stubMold("spirit_altar")));
        }
        assertEquals(3, output.getCount(), "the adapter must not mutate the native output");
    }

    @Test
    void runtimeConversionCopiesTheConcretePrimaryInputTagExactly() {
        ItemStack output = tagged(Items.EMERALD, 3, "base-output");
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                id("dynamic_infusion"),
                new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 4),
                true,
                output,
                List.of(MalumNativeTestSupport.spirit(2)),
                List.of(new IngredientWithCount(Ingredient.of(Items.DIAMOND), 2)));

        ItemStack primary = tagged(Items.GOLD_INGOT, 4, "input-tag");
        primary.getOrCreateTag().putInt("Damage", 7);
        try (MockedStatic<MalumAdapterUtils> stubs = malumItems()) {
            AdvancedAlloyFurnaceRecipe converted = new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("dynamic_infusion"), source), null, List.of(primary))
                    .get(0);

            CountedIngredient primaryInput = converted.inputs().get(0);
            assertTrue(primaryInput.ingredient().test(primary));
            assertFalse(primaryInput.ingredient().test(new ItemStack(Items.GOLD_INGOT, 4)));
            assertEquals(4L, primaryInput.count());
            assertEquals(primary.getTag(), converted.outputs().get(0).getTag());
            assertEquals(3, converted.outputs().get(0).getCount());
            assertNotNull(converted.id());
        }
    }

    @Test
    void disabledInputNbtCopyRetainsTheStaticOutputTag() {
        ItemStack output = tagged(Items.EMERALD, 1, "base-output");
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                id("static_infusion"),
                new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 1),
                false,
                output,
                List.of(MalumNativeTestSupport.spirit(1)),
                List.of());
        ItemStack primary = tagged(Items.GOLD_INGOT, 1, "ignored-input");

        try (MockedStatic<MalumAdapterUtils> stubs = malumItems()) {
            AdvancedAlloyFurnaceRecipe converted = new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("static_infusion"), source), null, List.of(primary))
                    .get(0);
            assertEquals("base-output", converted.outputs().get(0).getTag().getString("marker"));
        }
    }

    @Test
    void dynamicPatternProfileMarksTheExactPrimaryAndResultSlots() {
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                id("pattern_infusion"),
                new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 4),
                true,
                new ItemStack(Items.EMERALD, 3),
                List.of(MalumNativeTestSupport.spirit(2)),
                List.of(new IngredientWithCount(Ingredient.of(Items.DIAMOND), 2)));

        ItemStack primary = tagged(Items.GOLD_INGOT, 4, "pattern-input");
        ItemStack result = tagged(Items.EMERALD, 3, "pattern-input");
        PatternStackView pattern = PatternStackView.fromLegacy(
                List.of(primary, new ItemStack(Items.DIAMOND, 2), MalumNativeTestSupport.spiritStack(2)),
                List.of(result));

        Optional<DynamicPatternProfile> profile = SpiritInfusionRecipeAdapter.findDynamicPatternProfile(
                List.of(new RecipeHolder<>(id("pattern_infusion"), source)),
                List.of(primary, new ItemStack(Items.DIAMOND, 2), MalumNativeTestSupport.spiritStack(2)),
                List.of(result));

        assertTrue(profile.isPresent());
        assertEquals(java.util.Set.of(0), profile.get().idOnlyInputSlots());
        assertEquals(java.util.Set.of(0), profile.get().idOnlyOutputSlots());
        assertTrue(pattern.outputs().size() == 1);
        assertTrue(pattern.outputs().get(0).what() instanceof appeng.api.stacks.AEItemKey);
    }

    @Test
    void invalidNativeDataIsRejectedInsteadOfRelaxed() {
        try (MockedStatic<MalumAdapterUtils> stubs = malumItems()) {
            SpiritInfusionRecipe emptyInput = new SpiritInfusionRecipe(
                    id("empty_input"),
                    new IngredientWithCount(Ingredient.EMPTY, 1),
                    true,
                    new ItemStack(Items.EMERALD),
                    List.of(MalumNativeTestSupport.spirit(1)),
                    List.of());
            assertTrue(new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_input"), emptyInput), null).isEmpty());

            SpiritInfusionRecipe emptyOutput = new SpiritInfusionRecipe(
                    id("empty_output"),
                    new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 1),
                    true,
                    ItemStack.EMPTY,
                    List.of(MalumNativeTestSupport.spirit(1)),
                    List.of());
            assertTrue(new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_output"), emptyOutput), null).isEmpty());

            SpiritInfusionRecipe emptyExtra = new SpiritInfusionRecipe(
                    id("empty_extra"),
                    new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 1),
                    true,
                    new ItemStack(Items.EMERALD),
                    List.of(MalumNativeTestSupport.spirit(1)),
                    List.of(new IngredientWithCount(Ingredient.EMPTY, 1)));
            assertTrue(new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("empty_extra"), emptyExtra), null).isEmpty());

            SpiritInfusionRecipe zeroSpirit = new SpiritInfusionRecipe(
                    id("zero_spirit"),
                    new IngredientWithCount(Ingredient.of(Items.GOLD_INGOT), 1),
                    true,
                    new ItemStack(Items.EMERALD),
                    List.of(MalumNativeTestSupport.spirit(0)),
                    List.of());
            assertTrue(new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("zero_spirit"), zeroSpirit), null).isEmpty());
        }
    }

    @Test
    void strictPrimaryNbtIsPreservedWhenTheSourceIngredientUsesIt() {
        ItemStack exactInput = tagged(Items.GOLD_INGOT, 2, "exact");
        SpiritInfusionRecipe source = new SpiritInfusionRecipe(
                id("strict_input"),
                new IngredientWithCount(DataComponentIngredient.of(true, exactInput), 2),
                false,
                new ItemStack(Items.EMERALD),
                List.of(MalumNativeTestSupport.spirit(1)),
                List.of());

        try (MockedStatic<MalumAdapterUtils> stubs = malumItems()) {
            AdvancedAlloyFurnaceRecipe converted = new SpiritInfusionRecipeAdapter()
                    .convertAll(new RecipeHolder<>(id("strict_input"), source), null).get(0);
            CountedIngredient input = converted.inputs().get(0);
            assertTrue(input.ingredient().test(exactInput));
            assertFalse(input.ingredient().test(new ItemStack(Items.GOLD_INGOT, 2)));
            assertEquals(2L, input.count());
        }
    }

    private static MockedStatic<MalumAdapterUtils> malumItems() {
        MockedStatic<MalumAdapterUtils> stubs =
                Mockito.mockStatic(MalumAdapterUtils.class, Mockito.CALLS_REAL_METHODS);
        stubs.when(() -> MalumAdapterUtils.item("spirit_altar")).thenReturn(stubMold("spirit_altar"));
        return stubs;
    }

    private static ItemStack stubMold(String path) {
        return new ItemStack(Items.ENCHANTING_TABLE);
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
        return ResourceLocation.fromNamespaceAndPath("malum_test", path);
    }
}
