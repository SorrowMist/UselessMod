package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerOutput;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalCatalyzerRecipeAdapterTest {

    @Test
    void convertsCrystalOutputIntoBaseAndMatrixVariants() {
        CrystalCatalyzerRecipe source = recipe(
                Mode.CRYSTAL, new ItemStack(Items.AMETHYST_SHARD, 3), 1);

        var convertedRecipes = new CrystalCatalyzerRecipeAdapter()
                .convertAll(holder(source), null);
        AdvancedAlloyFurnaceRecipe converted = convertedRecipes.getFirst();
        AdvancedAlloyFurnaceRecipe matrix = convertedRecipes.get(1);

        assertEquals(3 * 256, converted.outputs().getFirst().getCount());
        assertEquals(3 * 1024, matrix.outputs().getFirst().getCount());
        assertEquals(1_000, converted.inputFluids().getFirst().getAmount());
        assertEquals(123_456L, converted.energy());
        assertEquals("test/crystal_catalyzer_converted", converted.id().getPath());
        assertEquals("test/crystal_catalyzer_converted_with_collapse_matrix", matrix.id().getPath());
        assertEquals(2, converted.molds().size());
        assertEquals(3, matrix.molds().size());
        assertTrue(converted.molds().get(0).test(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get())));
        assertTrue(converted.molds().get(1).test(new ItemStack(Items.AMETHYST_BLOCK)));
        assertTrue(matrix.molds().get(2).test(new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())));
    }

    @Test
    void convertsDustModeInsteadOfDroppingIt() {
        CrystalCatalyzerRecipe source = recipe(
                Mode.DUST, new ItemStack(Items.REDSTONE, 2), 2);

        AdvancedAlloyFurnaceRecipe converted = convert(source);

        assertEquals(2 * 256, converted.outputs().getFirst().getCount());
        assertTrue(converted.mold().test(new ItemStack(ModBlocks.CRYSTAL_CATALYZER.get())));
    }

    @Test
    void leavesMatrixOptionalWhenSourceHasNoCatalyst() {
        CrystalCatalyzerRecipe source = new CrystalCatalyzerRecipe(
                Optional.empty(),
                0,
                CrystalCatalyzerOutput.ofItem(new ItemStack(Items.REDSTONE, 1)),
                123_456,
                1,
                LightningKey.Tier.HIGH_VOLTAGE,
                Mode.DUST);

        var converted = new CrystalCatalyzerRecipeAdapter().convertAll(holder(source), null);

        assertEquals(2, converted.size());
        assertEquals(1, converted.getFirst().molds().size());
        assertEquals(1, converted.get(1).molds().size());
        assertFalse(converted.get(1).molds().stream()
                .anyMatch(mold -> mold.test(new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()))));
    }

    @Test
    void skipsAnUnresolvedTagOutput() {
        TagKey<Item> missingTag = TagKey.create(
                Registries.ITEM, ResourceLocation.fromNamespaceAndPath("test", "missing_output"));
        CrystalCatalyzerRecipe source = new CrystalCatalyzerRecipe(
                Optional.of(Ingredient.of(Items.AMETHYST_BLOCK)),
                1,
                CrystalCatalyzerOutput.ofTag(missingTag, 1),
                123_456,
                1,
                LightningKey.Tier.HIGH_VOLTAGE,
                Mode.DUST);

        assertTrue(new CrystalCatalyzerRecipeAdapter()
                .convertAll(holder(source), null).isEmpty());
    }

    private static AdvancedAlloyFurnaceRecipe convert(CrystalCatalyzerRecipe source) {
        return new CrystalCatalyzerRecipeAdapter().convertAll(holder(source), null).getFirst();
    }

    private static CrystalCatalyzerRecipe recipe(Mode mode, ItemStack output, int catalystCount) {
        return new CrystalCatalyzerRecipe(
                Optional.of(Ingredient.of(Items.AMETHYST_BLOCK)),
                catalystCount,
                CrystalCatalyzerOutput.ofItem(output),
                123_456,
                1,
                LightningKey.Tier.HIGH_VOLTAGE,
                mode);
    }

    private static RecipeHolder<CrystalCatalyzerRecipe> holder(CrystalCatalyzerRecipe recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "test/crystal_catalyzer"), recipe);
    }
}
