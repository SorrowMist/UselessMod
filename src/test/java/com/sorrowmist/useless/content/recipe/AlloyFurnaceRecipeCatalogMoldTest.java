package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceRecipeCatalogMoldTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsMoldsDeclaredByExternalRecipes() {
        AdvancedAlloyFurnaceRecipe recipe = recipe(Ingredient.of(Items.FURNACE));

        assertTrue(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.FURNACE), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.CHEST), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                ItemStack.EMPTY, List.of(recipe)));
    }

    @Test
    void preservesComponentSensitiveMoldMatching() {
        ItemStack required = namedPaper("required");
        Ingredient componentMold = DataComponentIngredient.of(true, required.copy());
        AdvancedAlloyFurnaceRecipe recipe = recipe(componentMold);

        assertTrue(AlloyFurnaceRecipeCatalog.isKnownMold(required.copy(), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                namedPaper("different"), List.of(recipe)));
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.PAPER), List.of(recipe)));
    }

    @Test
    void ignoresRecipesWithoutMolds() {
        assertFalse(AlloyFurnaceRecipeCatalog.isKnownMold(
                new ItemStack(Items.FURNACE), List.of(recipe(Ingredient.EMPTY))));
    }

    private static ItemStack namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static AdvancedAlloyFurnaceRecipe recipe(Ingredient mold) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "mold"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)),
                List.of(),
                List.of(),
                100L,
                20,
                Ingredient.EMPTY,
                0,
                mold,
                AlloyFurnaceMode.NORMAL);
    }
}
