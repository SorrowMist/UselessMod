package com.sorrowmist.useless.content.recipe;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedAlloyFurnaceRecipeMultiMoldTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyMoldAndNewMoldsDecodeToTheCanonicalList() {
        JsonObject legacy = baseJson();
        legacy.add("mold", ingredientJson(Items.IRON_INGOT));
        AdvancedAlloyFurnaceRecipe oldRecipe = parse(legacy);
        assertEquals(1, oldRecipe.molds().size());
        assertTrue(oldRecipe.mold().test(new ItemStack(Items.IRON_INGOT)));

        JsonObject multiple = baseJson();
        multiple.add("molds", com.google.gson.JsonParser.parseString(
                "[{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"minecraft:gold_ingot\"}]").getAsJsonArray());
        AdvancedAlloyFurnaceRecipe multiRecipe = parse(multiple);
        assertEquals(2, multiRecipe.molds().size());
    }

    @Test
    void emptyMoldEntriesAreIgnoredAndConflictingFieldsAreRejected() {
        JsonObject empty = baseJson();
        empty.add("molds", com.google.gson.JsonParser.parseString(
                "[[],{\"item\":\"minecraft:iron_ingot\"},[]]").getAsJsonArray());
        assertEquals(1, parse(empty).molds().size());

        JsonObject conflict = baseJson();
        conflict.add("mold", ingredientJson(Items.IRON_INGOT));
        conflict.add("molds", com.google.gson.JsonParser.parseString(
                "[{\"item\":\"minecraft:gold_ingot\"}]").getAsJsonArray());
        assertThrows(RuntimeException.class, () -> parse(conflict));
    }

    @Test
    void singleMoldEncodingUsesLegacyFieldAndMultiMoldUsesListField() {
        JsonObject single = AdvancedAlloyFurnaceRecipe.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, recipe(List.of(Ingredient.of(Items.IRON_INGOT))))
                .getOrThrow().getAsJsonObject();
        assertTrue(single.has("mold"));
        assertFalse(single.has("molds"));

        JsonObject multiple = AdvancedAlloyFurnaceRecipe.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, recipe(List.of(
                        Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_INGOT))))
                .getOrThrow().getAsJsonObject();
        assertTrue(multiple.has("molds"));
        assertFalse(multiple.has("mold"));
    }

    @Test
    void moldMatchingUsesDistinctSlotsAndHandlesOverlappingCandidates() {
        Ingredient broad = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        Ingredient exactGold = Ingredient.of(Items.GOLD_INGOT);

        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                List.of(broad, exactGold),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))));
        assertFalse(OmniversalMoldHubBlockEntity.matchesMolds(
                List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT)),
                List.of(new ItemStack(Items.IRON_INGOT, 2))));
        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT)),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT))));
        assertTrue(OmniversalMoldHubBlockEntity.matchesMolds(
                List.of(Ingredient.of(Items.IRON_INGOT)),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))));
    }

    @Test
    void ordinaryFurnaceSelectionRejectsMultiMoldRecipes() {
        AdvancedAlloyFurnaceRecipe recipe = recipe(List.of(
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_INGOT)));
        assertNull(AlloyFurnaceRecipeManager.selectBestCandidate(
                List.of(recipe), List.of(new ItemStack(Items.IRON_INGOT)), List.of(), List.of(),
                new ItemStack(Items.IRON_INGOT), List.of(), 1L));
    }

    @Test
    void multiMoldFingerprintIsOrderIndependentButKeepsDuplicates() {
        String first = AlloyFurnaceRecipeFingerprint.create(recipe(List.of(
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_INGOT))), RegistryAccess.EMPTY);
        String reordered = AlloyFurnaceRecipeFingerprint.create(recipe(List.of(
                Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.IRON_INGOT))), RegistryAccess.EMPTY);
        String repeated = AlloyFurnaceRecipeFingerprint.create(recipe(List.of(
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT))), RegistryAccess.EMPTY);
        assertEquals(first, reordered);
        assertNotEquals(first, repeated);
    }

    private static AdvancedAlloyFurnaceRecipe parse(JsonObject json) {
        return AdvancedAlloyFurnaceRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonObject baseJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "useless_mod_test:multi_mold");
        json.add("ingredients", com.google.gson.JsonParser.parseString(
                "[{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"}}]").getAsJsonArray());
        json.add("outputs", com.google.gson.JsonParser.parseString(
                "[{\"id\":\"minecraft:gold_ingot\"}]").getAsJsonArray());
        return json;
    }

    private static com.google.gson.JsonElement ingredientJson(net.minecraft.world.item.Item item) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
        return ingredient;
    }

    private static AdvancedAlloyFurnaceRecipe recipe(List<Ingredient> molds) {
        return new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", "multi_mold"),
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1L)), List.of(), List.of(),
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(), List.of(),
                100L, 20, Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL);
    }
}
