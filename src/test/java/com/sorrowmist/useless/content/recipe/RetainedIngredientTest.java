package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetainedIngredientTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void overlappingRetainedMoldMustBeReservedBeforeConsumingOtherInputs() {
        var requirements = List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1L),
                new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1L, false));
        var inputs = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT, 2));
        assertEquals(2, ItemIngredientAllocator.maxOperations(requirements, inputs));
        var allocation = ItemIngredientAllocator.allocate(requirements, inputs, 2L);
        assertNotNull(allocation);
        assertEquals(0L, allocation.consumedFromInput(0));
        assertEquals(2L, allocation.consumedFromInput(1));
        assertNull(ItemIngredientAllocator.allocate(requirements, List.of(new ItemStack(Items.IRON_INGOT)), 1L));
    }

    @Test void exactAndSingleIngredientPathsAlsoReserveWithoutConsuming() {
        var seed = new CountedIngredient(Ingredient.of(Items.WHEAT_SEEDS), 1L, false);
        var seeds = List.of(new ItemStack(Items.WHEAT_SEEDS));
        assertEquals(Integer.MAX_VALUE, ItemIngredientAllocator.maxOperations(List.of(seed), seeds));
        assertEquals(0L, ItemIngredientAllocator.allocate(List.of(seed), seeds, Integer.MAX_VALUE).consumedFromInput(0));
        assertEquals(0, ItemIngredientAllocator.maxOperations(List.of(seed), List.of()));
        var recipe = List.of(seed, new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 2));
        var allocation = ItemIngredientAllocator.allocate(recipe,
                List.of(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.IRON_INGOT, 6)), 3L);
        assertNotNull(allocation);
        assertEquals(0L, allocation.consumedFromInput(0));
        assertEquals(6L, allocation.consumedFromInput(1));
    }

    @Test void retainedFlagAndLongQuantitySurviveTheActualIngredientCodec() {
        var value = new CountedIngredient(Ingredient.of(Items.DIAMOND), 3_000_000_000L, false);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CountedIngredient.STREAM_CODEC.encode(buffer, value);
            var decoded = CountedIngredient.STREAM_CODEC.decode(buffer);
            assertEquals(value.count(), decoded.count());
            assertFalse(decoded.consumed());
            assertTrue(decoded.ingredient().test(new ItemStack(Items.DIAMOND)));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void aeReturnsOnlyActualUnconsumedMaterialsWithTheirNbtAndLongAmounts() {
        ItemStack tool = new ItemStack(Items.DIAMOND);
        tool.getOrCreateTag().putLong("custom", Long.MAX_VALUE);
        var key = AEItemKey.of(tool);
        var recipe = new AdvancedAlloyFurnaceRecipe(new ResourceLocation("test", "retained"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new CountedIngredient(Ingredient.of(Items.DIAMOND), 1, false)), List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), 1L, 1,
                Ingredient.EMPTY, 0, Ingredient.EMPTY, AlloyFurnaceMode.NORMAL);
        var remainder = RetainedRecipeInputs.remainders(recipe, List.of(new ItemStack(Items.IRON_INGOT, 64)),
                List.of(new GenericStack(key, 3_000_000_000L)), 64L);
        assertNotNull(remainder);
        assertEquals(List.of(new GenericStack(key, 3_000_000_000L)), remainder);
        assertEquals(tool.getTag(), ((AEItemKey) remainder.get(0).what()).toStack(1).getTag());
        assertNull(RetainedRecipeInputs.remainders(recipe, List.of(), List.of(new GenericStack(key, 1)), 1L));
    }
}
