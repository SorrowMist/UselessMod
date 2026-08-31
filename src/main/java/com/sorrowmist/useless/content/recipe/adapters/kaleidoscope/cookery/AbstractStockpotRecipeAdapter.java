package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractStockpotRecipeAdapter<T extends net.minecraft.world.item.crafting.Recipe<?>>
        extends AbstractCookeryRecipeAdapter<T> {
    private static final ResourceLocation STOCKPOT_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "stockpot");
    private static final ResourceLocation LID_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "stockpot_lid");

    @Override
    protected final ResourceLocation moldId() {
        return STOCKPOT_ID;
    }

    @Override
    protected final List<Ingredient> moldIngredients() {
        List<Ingredient> molds = new ArrayList<>(super.moldIngredients());
        Item lid = BuiltInRegistries.ITEM.getOptional(LID_ID).orElse(null);
        if (lid != null) {
            molds.add(Ingredient.of(lid));
        }
        return List.copyOf(molds);
    }

    protected final AdvancedAlloyFurnaceRecipe createStockpotRecipe(
            ResourceLocation recipeId, List<Ingredient> ingredients, ResourceLocation soupBase,
            ItemStack output, int processTime, Ingredient carrier) {
        return super.createStockpotRecipe(
                recipeId, ingredients, soupBase, output, processTime, carrier);
    }
}
