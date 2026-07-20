package com.sorrowmist.useless.content.recipe.adapters;

import com.blakebr0.cucumber.crafting.ingredient.IngredientWithCount;
import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.CombinationRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.CompressorRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapelessEnderCrafterRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapelessFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapelessTableRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCombinationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingCompressorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingEnderCrafterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingFluxCrafterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingTableRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.powah.EnergizingRecipeAdapter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import owmii.powah.block.Blcks;
import owmii.powah.block.energizing.EnergizingRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRecipeAdaptersTest {
    @Test
    void convertsPowahLongEnergyAndRepeatedComponents() {
        ItemStack output = named(new ItemStack(Items.DIAMOND, 3), "powah-output");
        EnergizingRecipe source = new EnergizingRecipe(
                output,
                5_000_000_000L,
                List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT))
        );

        AdvancedAlloyFurnaceRecipe converted = new EnergizingRecipeAdapter()
                .convertAll(holder("powah", "long_energy", source), null).getFirst();

        assertEquals(5_000_000_000L, converted.energy());
        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(3, converted.outputs().getFirst().getCount());
        assertEquals("powah-output",
                converted.outputs().getFirst().get(DataComponents.CUSTOM_NAME).getString());
        assertTrue(converted.mold().test(new ItemStack(Blcks.ENERGIZING_ORB.get())));
        assertFalse(new EnergizingRecipeAdapter().matchesMold(new ItemStack(Items.STICK)));
    }

    @Test
    void rejectsPowahRecipesThatDoNotFitAnOrb() {
        List<Ingredient> tooManyInputs = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> Ingredient.of(Items.IRON_INGOT))
                .toList();
        EnergizingRecipe source = new EnergizingRecipe(new ItemStack(Items.DIAMOND), 1L, tooManyInputs);
        assertTrue(new EnergizingRecipeAdapter()
                .convertAll(holder("powah", "too_many_inputs", source), null).isEmpty());
    }

    @Test
    void mapsTableTierToOnlyTheMatchingNormalTable() {
        ShapelessTableRecipe source = new ShapelessTableRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)),
                named(new ItemStack(Items.GOLD_INGOT, 2), "table-output"),
                3
        );

        ExtendedCraftingTableRecipeAdapter adapter = new ExtendedCraftingTableRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter
                .convertAll(holder("extendedcrafting", "elite", source), null).getFirst();

        assertTrue(converted.mold().test(new ItemStack(ModBlocks.ELITE_TABLE.get())));
        assertFalse(converted.mold().test(new ItemStack(ModBlocks.ULTIMATE_TABLE.get())));
        assertFalse(adapter.matchesMold(new ItemStack(ModBlocks.ELITE_AUTO_TABLE.get())));
        assertEquals(2, converted.outputs().getFirst().getCount());
        assertEquals("table-output", converted.outputs().getFirst()
                .get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    void rejectsInvalidTableTier() {
        ShapelessTableRecipe source = new ShapelessTableRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)),
                new ItemStack(Items.GOLD_INGOT),
                5
        );
        assertTrue(new ExtendedCraftingTableRecipeAdapter()
                .convertAll(holder("extendedcrafting", "bad_tier", source), null).isEmpty());
    }

    @Test
    void expandsAllNormalTableGridSizes() {
        int[] sizes = {9, 25, 49, 81};
        ItemStack[] molds = {
                new ItemStack(ModBlocks.BASIC_TABLE.get()),
                new ItemStack(ModBlocks.ADVANCED_TABLE.get()),
                new ItemStack(ModBlocks.ELITE_TABLE.get()),
                new ItemStack(ModBlocks.ULTIMATE_TABLE.get())
        };
        for (int tier = 1; tier <= 4; tier++) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int slot = 0; slot < sizes[tier - 1]; slot++) {
                ingredients.add(Ingredient.of(Items.IRON_INGOT));
            }
            ShapelessTableRecipe source = new ShapelessTableRecipe(
                    ingredients, new ItemStack(Items.DIAMOND), tier);
            AdvancedAlloyFurnaceRecipe converted = new ExtendedCraftingTableRecipeAdapter()
                    .convertAll(holder("extendedcrafting", "table_" + tier, source), null).getFirst();

            assertEquals(sizes[tier - 1], converted.inputs().getFirst().count());
            assertTrue(converted.mold().test(molds[tier - 1]));
        }
    }

    @Test
    void expandsCompressorCountAndReturnsCatalyst() {
        ItemStack namedIron = named(new ItemStack(Items.IRON_INGOT), "compressor-input");
        IngredientWithCount counted = new IngredientWithCount(
                Ingredient.of(namedIron).getValues()[0], 64);
        CompressorRecipe source = new CompressorRecipe(
                NonNullList.of(IngredientWithCount.EMPTY, counted),
                new ItemStack(Items.DIAMOND),
                Ingredient.of(new ItemStack(Items.BUCKET)),
                100,
                30
        );

        RecipeHolder<ICompressorRecipe> compressorHolder = holder(
                "extendedcrafting", "compressor", source);
        AdvancedAlloyFurnaceRecipe converted = new ExtendedCraftingCompressorRecipeAdapter()
                .convertAll(compressorHolder, null).getFirst();

        assertEquals(64L, converted.inputs().stream()
                .filter(input -> input.ingredient().test(namedIron.copyWithCount(64)))
                .findFirst().orElseThrow().count());
        assertFalse(converted.inputs().stream()
                .anyMatch(input -> input.ingredient().test(new ItemStack(Items.IRON_INGOT, 64))));
        assertEquals(1L, converted.inputs().stream()
                .filter(input -> input.ingredient().test(new ItemStack(Items.BUCKET)))
                .findFirst().orElseThrow().count());
        assertTrue(converted.outputs().stream().anyMatch(stack -> stack.is(Items.BUCKET)));
        assertEquals(4, converted.processTime());
        Map<Ingredient, Long> requirements = new java.util.LinkedHashMap<>();
        converted.inputs().forEach(input -> requirements.put(input.ingredient(), input.count()));
        assertTrue(AdapterUtils.matchesRequired(
                AdapterUtils.mergeInputs(List.of(namedIron.copyWithCount(64), new ItemStack(Items.BUCKET))),
                requirements));
    }

    @Test
    void convertsCombinationEnderAndFluxPowerAndTime() {
        CombinationRecipe combination = new CombinationRecipe(
                Ingredient.of(Items.DIAMOND),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.GOLD_INGOT)),
                new ItemStack(Items.NETHER_STAR),
                100,
                30
        );
        AdvancedAlloyFurnaceRecipe combinationResult = new ExtendedCraftingCombinationRecipeAdapter()
                .convertAll(holder("extendedcrafting", "combination", combination), null).getFirst();
        assertEquals(2L, combinationResult.inputs().stream()
                .filter(input -> input.ingredient().test(new ItemStack(Items.GOLD_INGOT)))
                .findFirst().orElseThrow().count());
        assertEquals(100L, combinationResult.energy());
        assertEquals(4, combinationResult.processTime());

        ShapelessEnderCrafterRecipe ender = new ShapelessEnderCrafterRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.ENDER_PEARL)),
                new ItemStack(Items.ENDER_EYE),
                3
        );
        AdvancedAlloyFurnaceRecipe enderResult = new ExtendedCraftingEnderCrafterRecipeAdapter()
                .convertAll(holder("extendedcrafting", "ender", ender), null).getFirst();
        assertEquals(60, enderResult.processTime());

        ShapelessFluxCrafterRecipe flux = new ShapelessFluxCrafterRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.REDSTONE)),
                new ItemStack(Items.REDSTONE_BLOCK),
                101,
                30
        );
        AdvancedAlloyFurnaceRecipe fluxResult = new ExtendedCraftingFluxCrafterRecipeAdapter()
                .convertAll(holder("extendedcrafting", "flux", flux), null).getFirst();
        assertEquals(101L, fluxResult.energy());
        assertEquals(4, fluxResult.processTime());
    }

    @Test
    void skipsPoweredRecipesWithInvalidRate() {
        CompressorRecipe source = new CompressorRecipe(
                NonNullList.of(IngredientWithCount.EMPTY,
                        new IngredientWithCount(Ingredient.of(Items.IRON_INGOT).getValues()[0], 2)),
                new ItemStack(Items.DIAMOND),
                Ingredient.of(Items.BUCKET),
                1,
                0
        );
        assertTrue(new ExtendedCraftingCompressorRecipeAdapter()
                .convertAll(holder("extendedcrafting", "bad_rate", source), null).isEmpty());
    }

    @Test
    void preservesDeterministicRemaindersAndRejectsAmbiguousOnes() {
        ShapelessTableRecipe deterministic = new ShapelessTableRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.WATER_BUCKET)),
                new ItemStack(Items.DIAMOND),
                1
        );
        AdvancedAlloyFurnaceRecipe converted = new ExtendedCraftingTableRecipeAdapter()
                .convertAll(holder("extendedcrafting", "container", deterministic), null).getFirst();
        assertTrue(converted.outputs().stream().anyMatch(stack -> stack.is(Items.BUCKET)));

        ShapelessTableRecipe ambiguous = new ShapelessTableRecipe(
                NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(Items.WATER_BUCKET, Items.IRON_INGOT)),
                new ItemStack(Items.DIAMOND),
                1
        );
        assertTrue(new ExtendedCraftingTableRecipeAdapter()
                .convertAll(holder("extendedcrafting", "ambiguous_container", ambiguous), null).isEmpty());
    }

    @Test
    void ignoresCombinationCenterRemainderButKeepsPedestalRemainder() {
        CombinationRecipe source = new CombinationRecipe(
                Ingredient.of(Items.WATER_BUCKET),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.WATER_BUCKET)),
                new ItemStack(Items.DIAMOND),
                10,
                10
        );
        AdvancedAlloyFurnaceRecipe converted = new ExtendedCraftingCombinationRecipeAdapter()
                .convertAll(holder("extendedcrafting", "center_remainder", source), null).getFirst();

        assertEquals(1, converted.outputs().stream()
                .filter(stack -> stack.is(Items.BUCKET))
                .mapToInt(ItemStack::getCount)
                .sum());
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(
            String namespace, String path, T recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(namespace, path), recipe);
    }
}
