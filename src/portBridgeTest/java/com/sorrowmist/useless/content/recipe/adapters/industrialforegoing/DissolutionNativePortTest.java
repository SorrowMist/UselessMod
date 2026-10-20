package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DissolutionNativePortTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void nativeValuesRemainSeparateRequiredSlots() {
        var inputs=DissolutionChamberRecipeAdapter.nativeInputs(new Ingredient.Value[]{
                new Ingredient.ItemValue(new ItemStack(Items.IRON_INGOT)),
                new Ingredient.ItemValue(new ItemStack(Items.GOLD_INGOT)),
                new Ingredient.ItemValue(new ItemStack(Items.IRON_INGOT))});
        var requirements=AdapterUtils.mergeIngredients(inputs);
        assertFalse(ItemIngredientAllocator.matches(requirements,List.of(new ItemStack(Items.IRON_INGOT,3)),1));
        var allocation=ItemIngredientAllocator.allocate(requirements,
                List.of(new ItemStack(Items.IRON_INGOT,2),new ItemStack(Items.GOLD_INGOT)),1);
        assertNotNull(allocation);
        assertEquals(2,allocation.consumedFromInput(0)); assertEquals(1,allocation.consumedFromInput(1));
    }
    @Test void eachValuesAlternativesStayOrWithinAnAndSlot() {
        Ingredient.Value alternative=new Ingredient.Value() {
            public Collection<ItemStack> getItems() { return List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.GOLD_INGOT)); }
            public com.google.gson.JsonObject serialize() { return new com.google.gson.JsonObject(); }
        };
        var inputs=AdapterUtils.mergeIngredients(DissolutionChamberRecipeAdapter.nativeInputs(new Ingredient.Value[]{alternative,alternative}));
        assertTrue(ItemIngredientAllocator.matches(inputs,List.of(new ItemStack(Items.GOLD_INGOT,2)),1));
        assertFalse(ItemIngredientAllocator.matches(inputs,List.of(new ItemStack(Items.GOLD_INGOT)),1));
    }
}
