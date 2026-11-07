package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturesAuraNativeMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void offeringUsesTheNativeInputStartItemAndOutputFieldsWithoutDroppingNbt() {
        var output = new ItemStack(Items.EMERALD, 2); output.getOrCreateTag().putString("variant", "preserved");
        var id = new ResourceLocation("test", "offering");
        var source = new OfferingRecipe(id, Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.DIAMOND), output);
        var converted = new OfferingRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).get(0);
        assertEquals(16, converted.inputs().stream().filter(input -> input.ingredient().test(new ItemStack(Items.GOLD_INGOT))).findFirst().orElseThrow().count());
        assertEquals(1, converted.inputs().stream().filter(input -> input.ingredient().test(new ItemStack(Items.DIAMOND))).findFirst().orElseThrow().count());
        assertEquals(32, converted.outputs().get(0).getCount());
        output.getOrCreateTag().putString("variant", "mutated");
        assertEquals("preserved", converted.outputs().get(0).getTag().getString("variant"));
    }

    @Test void emptyNativeOfferingInputsAreNotInventedAsFreeRecipes() {
        var id = new ResourceLocation("test", "invalid_offering");
        var source = new OfferingRecipe(id, Ingredient.EMPTY, Ingredient.of(Items.DIAMOND), new ItemStack(Items.EMERALD));
        assertTrue(new OfferingRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).isEmpty());
    }
}
