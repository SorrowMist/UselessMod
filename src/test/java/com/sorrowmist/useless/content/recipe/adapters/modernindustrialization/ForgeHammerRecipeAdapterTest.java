package com.sorrowmist.useless.content.recipe.adapters.modernindustrialization;

import aztech.modern_industrialization.blocks.forgehammer.ForgeHammerRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeHammerRecipeAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void convertsForgeHammerRecipeWithDefaultProcessingParameters() {
        ForgeHammerRecipe source = new ForgeHammerRecipe(
                Ingredient.of(Items.IRON_INGOT),
                2,
                new ItemStack(Items.IRON_NUGGET, 3),
                0);

        AdvancedAlloyFurnaceRecipe converted = new ForgeHammerRecipeAdapter()
                .convertAll(holder("modern_industrialization", "plate", source), null)
                .getFirst();

        assertEquals(2L, converted.inputs().getFirst().count());
        assertEquals(3, converted.outputs().getFirst().getCount());
        assertEquals(2_000L, converted.energy());
        assertEquals(20, converted.processTime());
        assertEquals(1, converted.molds().size());
    }

    @Test
    void addsForgeToolTagAsNonConsumableMoldWhenRecipeDamagesTool() {
        ForgeHammerRecipe source = new ForgeHammerRecipe(
                Ingredient.of(Items.IRON_INGOT),
                1,
                new ItemStack(Items.IRON_NUGGET),
                20);

        AdvancedAlloyFurnaceRecipe converted = new ForgeHammerRecipeAdapter()
                .convertAll(holder("modern_industrialization", "plate_with_tool", source), null)
                .getFirst();

        assertEquals(2, converted.molds().size());
        assertTrue(converted.molds().getFirst().test(new ItemStack(item("forge_hammer"))));
        Ingredient.Value value = converted.molds().get(1).getValues()[0];
        assertTrue(value instanceof Ingredient.TagValue);
        assertEquals(ResourceLocation.fromNamespaceAndPath(
                "modern_industrialization", "forge_hammer_tools"),
                ((Ingredient.TagValue) value).tag().location());
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("modern_industrialization", path));
    }

    private static RecipeHolder<ForgeHammerRecipe> holder(
            String namespace, String path, ForgeHammerRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(namespace, path), recipe);
    }
}
