package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.crafting.recipe.AwakeningRecipe;
import com.blakebr0.mysticalagriculture.crafting.recipe.InfusionRecipe;
import com.blakebr0.mysticalagriculture.registry.CropRegistry;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Forge 1.20.1 native-shape tests for Mystical Agriculture 7.0.24.
 *
 * <p>These tests exercise the real 1.20.1 {@link InfusionRecipe} and {@link AwakeningRecipe}
 * constructors. The important native detail is that both expose a nine-slot ingredient view and
 * keep the altar input at slot 0; awakening essences are real stacks with counts and NBT.</p>
 */
class MysticalAgriculture7024NativeForge1201AdapterTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void infusionUsesAllNineSlotsOnceAndPreservesNbtPredicates() {
        ItemStack taggedPedestal = tagged(Items.GOLD_INGOT, 1, "pedestal");
        NonNullList<Ingredient> inputs = NonNullList.withSize(9, Ingredient.EMPTY);
        inputs.set(0, Ingredient.of(Items.DIAMOND_SWORD));
        inputs.set(1, DataComponentIngredient.of(true, taggedPedestal));
        for (int slot = 2; slot < 9; slot++) {
            inputs.set(slot, Ingredient.of(Items.STICK));
        }

        InfusionRecipe source = new InfusionRecipe(
                id("infusion"),
                inputs,
                tagged(Items.NETHERITE_SWORD, 1, "infusion-output"),
                false);

        AdvancedAlloyFurnaceRecipe converted = new InfusionRecipeAdapter()
                .convertAll(holder("infusion", source), null).get(0);

        assertEquals(1L, required(converted, new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals(1L, required(converted, taggedPedestal));
        assertEquals(0L, required(converted, new ItemStack(Items.GOLD_INGOT)));
        assertEquals(7L, required(converted, new ItemStack(Items.STICK)));
        assertEquals(2_000L, converted.energy());
        // Eight pedestal slots, not the altar input at slot 0.
        assertEquals(20 + 8 * 15, converted.processTime());
        assertEquals("infusion-output", tag(converted.outputs().get(0)).getString("marker"));
    }

    @Test
    void awakeningReadsEvenPedestalSlotsAndExactEssenceCounts() {
        NonNullList<Ingredient> inputs = NonNullList.withSize(5, Ingredient.EMPTY);
        inputs.set(0, Ingredient.of(Items.NETHERITE_SWORD));
        inputs.set(1, Ingredient.of(Items.GOLD_BLOCK));
        inputs.set(2, Ingredient.of(Items.GOLD_BLOCK));
        inputs.set(3, Ingredient.of(Items.DIAMOND));
        inputs.set(4, Ingredient.of(Items.EMERALD));

        ItemStack taggedEssence = tagged(Items.GUNPOWDER, 40, "air-essence");
        ItemStack plainEssence = new ItemStack(Items.GUNPOWDER, 40);
        NonNullList<ItemStack> essences = NonNullList.withSize(4, ItemStack.EMPTY);
        essences.set(0, taggedEssence);
        essences.set(1, plainEssence);
        essences.set(2, new ItemStack(Items.REDSTONE, 40));
        essences.set(3, new ItemStack(Items.LAPIS_LAZULI, 40));

        AwakeningRecipe source = new AwakeningRecipe(
                id("awakening"),
                inputs,
                essences,
                tagged(Items.NETHERITE_CHESTPLATE, 1, "awakening-output"),
                false);

        AdvancedAlloyFurnaceRecipe converted = new AwakeningRecipeAdapter()
                .convertAll(holder("awakening", source), null).get(0);

        assertEquals(1L, required(converted, new ItemStack(Items.NETHERITE_SWORD)));
        assertEquals(2L, required(converted, new ItemStack(Items.GOLD_BLOCK)));
        assertEquals(1L, required(converted, new ItemStack(Items.DIAMOND)));
        assertEquals(1L, required(converted, new ItemStack(Items.EMERALD)));
        // Exact count and NBT, with no extra one-item phantom from the interleaved ingredient view.
        assertEquals(40L, required(converted, taggedEssence));
        assertEquals(40L, required(converted, plainEssence));
        assertEquals(40L, required(converted, new ItemStack(Items.REDSTONE)));
        assertEquals(40L, required(converted, new ItemStack(Items.LAPIS_LAZULI)));
        assertEquals(5_000L, converted.energy());
        // Four pedestal slots plus four essence slots.
        assertEquals(20 + (4 + 4) * 15, converted.processTime());
        assertEquals("awakening-output", tag(converted.outputs().get(0)).getString("marker"));

        Ingredient taggedIngredient = converted.inputs().stream()
                .map(CountedIngredient::ingredient)
                .filter(ingredient -> ingredient.test(taggedEssence))
                .findFirst()
                .orElseThrow();
        assertNotEquals(ingredientIdentity(taggedIngredient), ingredientIdentity(
                Ingredient.of(Items.GUNPOWDER)), "NBT-bearing essence must not become a plain item");
        assertTrue(taggedIngredient.test(taggedEssence));
    }

    @Test
    void seedEssenceSyntheticRecipeKeepsTwoEssenceAndOneMillibucketWater() {
        Crop crop = mock(Crop.class);
        when(crop.getName()).thenReturn("test_crop");
        when(crop.getSeedsItem()).thenReturn((ItemNameBlockItem) Items.WHEAT_SEEDS);
        when(crop.getEssenceItem()).thenReturn(Items.SUGAR);

        CropRegistry registry = mock(CropRegistry.class);
        when(registry.getCrops()).thenReturn(List.of(crop));

        try (MockedStatic<CropRegistry> stubs = Mockito.mockStatic(CropRegistry.class)) {
            stubs.when(CropRegistry::getInstance).thenReturn(registry);

            SeedEssenceRecipeAdapter adapter = new SeedEssenceRecipeAdapter();
            List<AdvancedAlloyFurnaceRecipe> recipes = adapter.getAllRecipes();

            assertEquals(1, recipes.size());
            AdvancedAlloyFurnaceRecipe recipe = recipes.get(0);
            assertEquals(1L, recipe.inputFluids().get(0).amount());
            assertEquals(2, recipe.outputs().get(0).getCount());
            assertEquals(Items.SUGAR, recipe.outputs().get(0).getItem());
            assertTrue(recipe.molds().get(0).test(new ItemStack(Items.WHEAT_SEEDS)));
        }
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

    private static ItemStack tagged(Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("marker", marker);
        return stack;
    }

    private static CompoundTag tag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        assertTrue(tag != null, "expected preserved NBT on " + stack);
        return tag;
    }

    private static String ingredientIdentity(Ingredient ingredient) {
        return ingredient.getClass().getName() + "|" + ingredient.toJson();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "test/" + path);
    }

    private static <T extends Recipe<?>> RecipeHolder<T> holder(String path, T recipe) {
        return new RecipeHolder<>(id(path), recipe);
    }
}
