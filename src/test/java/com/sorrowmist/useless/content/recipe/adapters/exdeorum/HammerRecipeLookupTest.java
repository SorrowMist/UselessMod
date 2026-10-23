package com.sorrowmist.useless.content.recipe.adapters.exdeorum;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HammerRecipeLookupTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static RecipeHolder<HammerSyntheticRecipe> recipe(String id, Ingredient mold) {
        var converted = new AdvancedAlloyFurnaceRecipe(new ResourceLocation("test", id),
                List.of(new CountedIngredient(Ingredient.of(Items.COBBLESTONE), 2L)),
                List.of(), List.of(new ItemStack(Items.GRAVEL)), List.of(),
                2000L, 200, Ingredient.EMPTY, 0, mold, AlloyFurnaceMode.NORMAL);
        return RecipeHolder.of(new HammerSyntheticRecipe(converted));
    }

    @Test void generatedJeiRecipeIsAlsoAvailableToMachineLookup() {
        var level = mock(Level.class);
        var adapter = spy(new HammerRecipeAdapter());
        var normal = recipe("normal", Ingredient.of(Items.DIAMOND_PICKAXE));
        var compressed = recipe("compressed", Ingredient.of(Items.IRON_PICKAXE));
        doReturn(true).when(adapter).matchesMold(any());
        doReturn(List.of(normal, compressed)).when(adapter).getGeneratedRecipes(level);
        var inputs = Map.of(Ingredient.of(Items.COBBLESTONE), 2L);
        var mold = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals(List.of(normal), adapter.findMatchingRecipes(level, inputs, Map.of(), Map.of(), mold));
        assertEquals(List.of(compressed), adapter.findMatchingRecipes(level, inputs, Map.of(), Map.of(),
                new ItemStack(Items.IRON_PICKAXE)));
    }

    @Test void damageAndCustomNbtDoNotPreventOrdinaryMoldMatching() {
        var level = mock(Level.class);
        var adapter = spy(new HammerRecipeAdapter());
        var normal = recipe("normal", Ingredient.of(Items.DIAMOND_PICKAXE));
        doReturn(true).when(adapter).matchesMold(any());
        doReturn(List.of(normal)).when(adapter).getGeneratedRecipes(level);
        var mold = new ItemStack(Items.DIAMOND_PICKAXE);
        mold.setDamageValue(37);
        mold.getOrCreateTag().putString("custom_marker", "keep");
        var inputs = Map.of(Ingredient.of(Items.COBBLESTONE), 2L);
        assertEquals(List.of(normal), adapter.findMatchingRecipes(level, inputs, Map.of(), Map.of(), mold,
                List.of(new ItemStack(Items.COBBLESTONE, 2))));
        assertEquals(37, mold.getDamageValue());
        assertEquals("keep", mold.getTag().getString("custom_marker"));
    }

    @Test void missingMoldOrInsufficientBatchInputsCannotMatch() {
        var level = mock(Level.class);
        var adapter = spy(new HammerRecipeAdapter());
        doReturn(true).when(adapter).matchesMold(any());
        doReturn(List.of(recipe("normal", Ingredient.of(Items.DIAMOND_PICKAXE))))
                .when(adapter).getGeneratedRecipes(level);
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(Items.COBBLESTONE), 1L),
                Map.of(), Map.of(), new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(Items.COBBLESTONE), 2L),
                Map.of(), Map.of(), ItemStack.EMPTY).isEmpty());
    }
}
