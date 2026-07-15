package com.sorrowmist.useless.content.recipe.adapters.draconicevolution;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.crafting.FusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.api.crafting.StackIngredient;
import com.brandon3055.draconicevolution.init.DEContent;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraconicFusionRecipeAdapterTest {

    @Test
    void convertsLongEnergyTieredMoldAndReturnedIngredient() {
        FusionRecipe source = new FusionRecipe(
                new ItemStack(Items.DIAMOND),
                StackIngredient.of(4, Items.DIRT),
                5_000_000_000L,
                TechLevel.DRACONIC,
                List.of(
                        new FusionRecipe.FusionIngredient(Ingredient.of(Items.IRON_INGOT), true),
                        new FusionRecipe.FusionIngredient(Ingredient.of(Items.STICK), false)
                )
        );
        RecipeHolder<IFusionRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("kubejs", "long_fusion"), source);

        AdvancedAlloyFurnaceRecipe converted = new DraconicFusionRecipeAdapter()
                .convertAll(holder, null)
                .getFirst();

        assertEquals(5_000_000_000L, converted.energy());
        assertEquals(List.of(4L, 1L, 1L), converted.inputs().stream().map(input -> input.count()).toList());
        assertEquals(1, countOutput(converted, new ItemStack(Items.DIAMOND)));
        assertEquals(1, countOutput(converted, new ItemStack(Items.STICK)));

        assertFalse(converted.mold().test(new ItemStack(DEContent.BASIC_CRAFTING_INJECTOR.get())));
        assertFalse(converted.mold().test(new ItemStack(DEContent.WYVERN_CRAFTING_INJECTOR.get())));
        assertTrue(converted.mold().test(new ItemStack(DEContent.AWAKENED_CRAFTING_INJECTOR.get())));
        assertTrue(converted.mold().test(new ItemStack(DEContent.CHAOTIC_CRAFTING_INJECTOR.get())));
    }

    private static int countOutput(AdvancedAlloyFurnaceRecipe recipe, ItemStack expected) {
        return recipe.outputs().stream()
                .filter(output -> ItemStack.isSameItemSameComponents(output, expected))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
