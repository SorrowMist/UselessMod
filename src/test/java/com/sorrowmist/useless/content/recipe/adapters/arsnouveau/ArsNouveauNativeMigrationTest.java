package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.hollingsworth.arsnouveau.api.enchanting_apparatus.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArsNouveauNativeMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void apparatusUsesNativeFieldsAndKeepsLongEnergyCountAndOutputNbt() {
        var id = new ResourceLocation("test", "apparatus");
        var output = new ItemStack(Items.EMERALD, 3);
        output.getOrCreateTag().putString("result", "exact");
        var source = new EnchantingApparatusRecipe(id, List.of(Ingredient.of(Items.GOLD_INGOT)),
                Ingredient.of(Items.IRON_INGOT), output, 600_000_000, false);
        var adapter = new EnchantingApparatusRecipeAdapter() {
            @Override public ItemStack getMoldItem() { return new ItemStack(Items.BRICK); }
        };
        var converted = adapter.convertAll(new RecipeHolder<>(id, source), null).get(0);
        assertEquals(6_000_000_000L, converted.energy());
        assertEquals(2L, converted.inputs().stream().mapToLong(row -> row.count()).sum());
        assertEquals(3, converted.outputs().get(0).getCount());
        assertEquals("exact", converted.outputs().get(0).getTag().getString("result"));
        converted.outputs().get(0).getTag().putString("result", "changed");
        assertEquals("exact", output.getTag().getString("result"));
    }

    @Test void apparatusDoesNotFlattenNativeReagentNbtTransfer() {
        var id = new ResourceLocation("test", "apparatus_transfer");
        var source = new EnchantingApparatusRecipe(id, List.of(Ingredient.of(Items.GOLD_INGOT)),
                Ingredient.of(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_SWORD), 1000, true);
        assertTrue(new EnchantingApparatusRecipeAdapter().convertAll(new RecipeHolder<>(id, source), null).isEmpty());
    }

    @Test void imbuementRetainsExactOutputMoldNbtAndDoesNotOverflowSourceCost() {
        var id = new ResourceLocation("test", "imbuement");
        var output = new ItemStack(Items.EMERALD, 2);
        output.getOrCreateTag().putString("type", "required");
        var source = new ImbuementRecipe(id, Ingredient.of(Items.IRON_INGOT), output, 600_000_000, List.of());
        var adapter = new ImbuementRecipeAdapter();
        var converted = adapter.convertAll(new RecipeHolder<>(id, source), null).get(0);
        assertEquals(6_000_000_000L, converted.energy());
        assertEquals(2, converted.outputs().get(0).getCount());
        assertTrue(converted.mold().test(output.copyWithCount(1)));
        assertFalse(converted.mold().test(new ItemStack(Items.EMERALD)));
        var wrongNbt = output.copyWithCount(1); wrongNbt.getTag().putString("type", "different");
        assertFalse(converted.mold().test(wrongNbt));
        var dynamic = new ImbuementRecipe(id, Ingredient.of(Items.IRON_INGOT), output, 1000, List.of()) {};
        assertTrue(adapter.convertAll(new RecipeHolder<>(id, dynamic), null).isEmpty());
    }
}
