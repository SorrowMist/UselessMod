package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.jemi.JemiRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmniversalEmiSelectionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeAe2EmiPathRetainsTheExactImportedCatalogEntry() {
        var entry = mock(AlloyFurnaceRecipeCatalog.Entry.class);
        var recipe = imported("useless_mod:advanced_alloy_furnace", entry);
        assertSame(entry, OmniversalEmiSelection.recipe(recipe));
    }

    @Test void normalMachinePagesAndUnrelatedRecipesNeverSelectAnOmniversalPattern() {
        assertNull(OmniversalEmiSelection.recipe(imported("mekanism:crushing", mock(AlloyFurnaceRecipeCatalog.Entry.class))));
        assertNull(OmniversalEmiSelection.recipe(imported("useless_mod:advanced_alloy_furnace", "not a catalog entry")));
        assertNull(OmniversalEmiSelection.recipe(mock(EmiRecipe.class)));
        assertNull(OmniversalEmiSelection.recipe(null));
    }

    private static JemiRecipe<Object> imported(String categoryId, Object entry) {
        JemiRecipe<Object> recipe = mock(JemiRecipe.class);
        recipe.recipe = entry;
        var category = mock(EmiRecipeCategory.class);
        when(category.getId()).thenReturn(new ResourceLocation(categoryId));
        when(recipe.getCategory()).thenReturn(category);
        return recipe;
    }
}
