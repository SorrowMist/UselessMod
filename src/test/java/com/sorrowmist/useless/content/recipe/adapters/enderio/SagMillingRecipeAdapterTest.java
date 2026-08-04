package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagMillingRecipeAdapterTest {
    private static final SagMillingRecipeAdapter ADAPTER = new SagMillingRecipeAdapter();

    @Test
    void noneIgnoresGrindingBallMultipliersAndScalesChanceOutputs() {
        SagMillingRecipe source = recipe(
                SagMillingRecipe.BonusType.NONE,
                output(Items.DIAMOND, 2, 1.0F),
                output(Items.GOLD_INGOT, 1, 0.5F));

        AdvancedAlloyFurnaceRecipe converted = convert("none", source, 9.0, 9.0);

        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(4, count(converted.outputs(), Items.DIAMOND));
        assertEquals(1, count(converted.outputs(), Items.GOLD_INGOT));
        assertEquals(4_800L, converted.energy());
        assertEquals(40, converted.processTime());
    }

    @Test
    void multiplyOutputScalesGuaranteedAndChanceOutputsTogether() {
        SagMillingRecipe source = recipe(
                SagMillingRecipe.BonusType.MULTIPLY_OUTPUT,
                output(Items.DIAMOND, 2, 1.0F),
                output(Items.GOLD_INGOT, 1, 0.25F));

        AdvancedAlloyFurnaceRecipe converted = convert("multiply", source, 1.5, 99.0);

        assertEquals(8L, converted.inputs().getFirst().count());
        assertEquals(24, count(converted.outputs(), Items.DIAMOND));
        assertEquals(3, count(converted.outputs(), Items.GOLD_INGOT));
        assertEquals(19_200L, converted.energy());
        assertEquals(160, converted.processTime());
    }

    @Test
    void chanceOnlyLeavesGuaranteedOutputUnchangedAndCapsBonusChance() {
        SagMillingRecipe source = recipe(
                SagMillingRecipe.BonusType.CHANCE_ONLY,
                output(Items.DIAMOND, 3, 1.0F),
                output(Items.GOLD_INGOT, 1, 0.8F));

        AdvancedAlloyFurnaceRecipe converted = convert("chance_only", source, 9.0, 2.4);

        assertEquals(1L, converted.inputs().getFirst().count());
        assertEquals(3, count(converted.outputs(), Items.DIAMOND));
        assertEquals(1, count(converted.outputs(), Items.GOLD_INGOT));
        assertEquals(2_400L, converted.energy());
        assertEquals(20, converted.processTime());
    }

    @Test
    void componentIdenticalOutputsMergeButComponentsArePreserved() {
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("named diamond"));
        SagMillingRecipe source = new SagMillingRecipe(
                Ingredient.of(Items.IRON_ORE),
                List.of(
                        SagMillingRecipe.OutputItem.of(namedDiamond, 1.0F, false),
                        SagMillingRecipe.OutputItem.of(namedDiamond.copy(), 0.5F, false)),
                2_400,
                SagMillingRecipe.BonusType.NONE);

        AdvancedAlloyFurnaceRecipe converted = convert("components", source, 1.0, 1.0);

        assertEquals(1, converted.outputs().size());
        assertEquals(3, converted.outputs().getFirst().getCount());
        assertEquals("named diamond", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void convertedRecipeRequiresTheCompleteDeterministicBatch() {
        SagMillingRecipe source = recipe(
                SagMillingRecipe.BonusType.MULTIPLY_OUTPUT,
                output(Items.DIAMOND, 1, 0.25F));
        AdvancedAlloyFurnaceRecipe converted = convert("batch", source, 1.5, 1.0);
        ItemStack sevenInputs = new ItemStack(Items.IRON_ORE, 7);
        ItemStack eightInputs = new ItemStack(Items.IRON_ORE, 8);

        assertFalse(ItemIngredientAllocator.matches(
                converted.inputs(), List.of(sevenInputs), 1));
        assertTrue(ItemIngredientAllocator.matches(
                converted.inputs(), List.of(eightInputs), 1));
    }

    @Test
    void convertedRecipeUsesSagMillMoldAndStableId() {
        SagMillingRecipe source = recipe(
                SagMillingRecipe.BonusType.NONE,
                output(Items.DIAMOND, 1, 1.0F));

        AdvancedAlloyFurnaceRecipe converted = convert("mold", source, 1.0, 1.0);

        assertEquals(ResourceLocation.fromNamespaceAndPath(
                "enderio_test", "mold_converted"), converted.id());
        assertTrue(converted.mold().test(new ItemStack(EIOBlocks.SAG_MILL.get())));
    }

    private static AdvancedAlloyFurnaceRecipe convert(
            String path, SagMillingRecipe source, double outputMultiplier, double bonusMultiplier) {
        return ADAPTER.convertAll(
                holder(path, source),
                null,
                new SagMillingRecipeAdapter.GrindingBallMultipliers(outputMultiplier, bonusMultiplier))
                .getFirst();
    }

    private static SagMillingRecipe recipe(
            SagMillingRecipe.BonusType bonusType,
            SagMillingRecipe.OutputItem... outputs) {
        return new SagMillingRecipe(
                Ingredient.of(Items.IRON_ORE),
                List.of(outputs),
                2_400,
                bonusType);
    }

    private static SagMillingRecipe.OutputItem output(Item item, int count, float chance) {
        return SagMillingRecipe.OutputItem.of(item, count, chance, false);
    }

    private static RecipeHolder<SagMillingRecipe> holder(String path, SagMillingRecipe recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("enderio_test", path), recipe);
    }

    private static int count(List<ItemStack> outputs, Item item) {
        return outputs.stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
