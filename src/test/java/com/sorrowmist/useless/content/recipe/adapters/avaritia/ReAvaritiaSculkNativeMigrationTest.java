package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReAvaritiaSculkNativeMigrationTest {
    private static RegistryObject<Block> original;
    private static Item table;
    private final ReAvaritiaSculkCraftingRecipeAdapter adapter = new ReAvaritiaSculkCraftingRecipeAdapter();

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        table = InventoryTestBootstrap.registerItem(new ResourceLocation("avaritia", "sculk_crafting_table"), () -> new Item(new Item.Properties()));
        InventoryTestBootstrap.registerItem(new ResourceLocation("avaritia", "double_compressed_crafting_table"), () -> new Item(new Item.Properties()));
        // Plain JUnit has no Forge block registration event; replace only this mold handle.
        // Forge extends Rarity at launch; plain JUnit cannot create the two native custom enums.
        try (var rarities = mockStatic(net.minecraft.world.item.Rarity.class)) {
            rarities.when(() -> net.minecraft.world.item.Rarity.create(anyString(), any(net.minecraft.ChatFormatting.class)))
                    .thenReturn(net.minecraft.world.item.Rarity.EPIC);
            original = ModBlocks.sculk_crafting_table;
        }
        Block block = mock(Block.class);
        when(block.asItem()).thenReturn(table);
        @SuppressWarnings("unchecked") RegistryObject<Block> handle = mock(RegistryObject.class);
        when(handle.get()).thenReturn(block);
        ModBlocks.sculk_crafting_table = handle;
    }
    @AfterAll static void restore() { if (original != null) ModBlocks.sculk_crafting_table = original; }

    @Test void actualReleaseJsonRetainsAllNineInputsAndTheSculkMold() throws Exception {
        ShapedRecipe recipe;
        try (var stream = ModBlocks.class.getResourceAsStream("/data/avaritia/recipes/sculk_crafting_table.json")) {
            assertNotNull(stream);
            recipe = new ShapedRecipe.Serializer().fromJson(new ResourceLocation("avaritia", "sculk_crafting_table"),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        var converted = adapter.convertAll(RecipeHolder.of(recipe), null);
        assertEquals(1, converted.size());
        var result = converted.get(0);
        assertEquals(9, result.inputs().stream().mapToLong(input -> input.count()).sum());
        assertEquals(4, result.inputs().stream().filter(input -> input.ingredient().test(new ItemStack(Items.ECHO_SHARD))).mapToLong(input -> input.count()).sum());
        assertTrue(result.molds().get(0).test(new ItemStack(table)));
        assertTrue(result.outputs().get(0).is(table));
        assertTrue(adapter.getMoldItem().is(table));
    }

    @Test void exactNbtPredicateAndOutputCopiesSurviveConversionAndLiveLookup() {
        ItemStack required = new ItemStack(Items.ECHO_SHARD);
        required.getOrCreateTag().putString("binding", "exact");
        ItemStack output = new ItemStack(table, 2);
        output.getOrCreateTag().putInt("revision", 7);
        ShapedRecipe nativeRecipe = recipe(StrictNBTIngredient.of(required), output);
        var converted = adapter.convertAll(RecipeHolder.of(nativeRecipe), null).get(0);
        assertTrue(converted.inputs().get(0).ingredient().test(required));
        assertFalse(converted.inputs().get(0).ingredient().test(new ItemStack(Items.ECHO_SHARD)));
        assertEquals(2, converted.outputs().get(0).getCount());
        output.getTag().putInt("revision", 8);
        assertEquals(7, converted.outputs().get(0).getTag().getInt("revision"));
        RecipeManager manager = new RecipeManager();
        manager.replaceRecipes(List.of(nativeRecipe));
        Level level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(manager);
        assertEquals(1, adapter.findMatchingRecipes(level, Map.of(StrictNBTIngredient.of(required), 1L), Map.of(), new ItemStack(table)).size());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(Ingredient.of(Items.ECHO_SHARD), 1L), Map.of(), new ItemStack(table)).isEmpty());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(StrictNBTIngredient.of(required), 1L), Map.of(), new ItemStack(Items.CRAFTING_TABLE)).isEmpty());
        manager.replaceRecipes(List.of());
        assertTrue(adapter.findMatchingRecipes(level, Map.of(StrictNBTIngredient.of(required), 1L), Map.of(), new ItemStack(table)).isEmpty());
    }

    @Test void deterministicContainerRemaindersAreNotDiscardedAndConflictingAlternativesAreRejected() {
        var converted = adapter.convertAll(RecipeHolder.of(recipe(Ingredient.of(Items.MILK_BUCKET), new ItemStack(table))), null);
        assertEquals(1, converted.size());
        assertTrue(converted.get(0).outputs().stream().anyMatch(stack -> stack.is(Items.BUCKET) && stack.getCount() == 1));
        assertTrue(adapter.convertAll(RecipeHolder.of(recipe(Ingredient.of(Items.MILK_BUCKET, Items.STONE), new ItemStack(table))), null).isEmpty());
    }

    @Test void unknownCraftingImplementationsAndUnrelatedVanillaResultsAreNotAssumedStatic() {
        CraftingRecipe dynamic = mock(CraftingRecipe.class);
        when(dynamic.getId()).thenReturn(new ResourceLocation("useless_mod_test", "dynamic_sculk"));
        when(dynamic.getResultItem(any())).thenReturn(new ItemStack(table));
        assertTrue(adapter.convertAll(RecipeHolder.of(dynamic), null).isEmpty());
        assertTrue(adapter.convertAll(RecipeHolder.of(recipe(Ingredient.of(Items.STONE), new ItemStack(Items.DIAMOND))), null).isEmpty());
    }

    private static ShapedRecipe recipe(Ingredient ingredient, ItemStack result) {
        return new ShapedRecipe(new ResourceLocation("useless_mod_test", "sculk_recipe"), "", CraftingBookCategory.MISC,
                1, 1, NonNullList.of(Ingredient.EMPTY, ingredient), result);
    }
}
