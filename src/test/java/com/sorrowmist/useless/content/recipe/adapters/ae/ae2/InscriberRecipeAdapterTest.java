package com.sorrowmist.useless.content.recipe.adapters.ae.ae2;

import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InscriberRecipeAdapterTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private static final InscriberRecipeAdapter ADAPTER=new InscriberRecipeAdapter() {
        @Override public ItemStack getMoldItem() { return new ItemStack(Items.PISTON); }
    };
    private static InscriberRecipe source(InscriberProcessType mode) {
        var recipe=mock(InscriberRecipe.class);
        when(recipe.getMiddleInput()).thenReturn(Ingredient.of(Items.GOLD_INGOT));
        when(recipe.getTopOptional()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getBottomOptional()).thenReturn(Ingredient.of(Items.DIAMOND));
        when(recipe.getResultItem()).thenReturn(new ItemStack(Items.EMERALD));
        when(recipe.getProcessType()).thenReturn(mode);
        return recipe;
    }
    @Test void printingRequiresMachineAndBothPlatesWithoutConsumingThem() {
        var converted=ADAPTER.convertAll(new RecipeHolder<>(new ResourceLocation("ae2:printing_test"),source(InscriberProcessType.INSCRIBE)),null).get(0);
        assertEquals(3,converted.molds().size()); assertEquals(1,converted.inputs().size());
        assertTrue(converted.molds().get(0).test(new ItemStack(Items.PISTON)));
        assertTrue(converted.molds().get(1).test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(converted.molds().get(2).test(new ItemStack(Items.DIAMOND)));
    }
    @Test void pressConsumesAllThreeInputsButKeepsMachine() {
        var converted=ADAPTER.convertAll(new RecipeHolder<>(new ResourceLocation("ae2:pressing_test"),source(InscriberProcessType.PRESS)),null).get(0);
        assertEquals(1,converted.molds().size()); assertEquals(3,converted.inputs().size());
        assertTrue(converted.inputs().stream().allMatch(i->i.consumed()&&i.count()==1));
    }
}
