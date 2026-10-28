package com.sorrowmist.useless.content.recipe.adapters.ae.advancedae;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedAeBatchRecipeResourceTest {
    private static final Path DIR =
            Path.of("src/main/resources/data/useless_mod/recipes/machine/smelter/ae2/advacedae");
    private static final String REACTION_CHAMBER = "advanced_ae:reaction_chamber";
    private static final String INSCRIBER = "ae2:inscriber";

    @Test void everyAdvancedAeBatchRecipeKeepsItsConditionalShellAndNativeMold() throws Exception {
        Map<String, String> molds = Map.of(
                "quantum_alloy", REACTION_CHAMBER,
                "quantum_alloy_plate", REACTION_CHAMBER,
                "quantum_infusion_source", REACTION_CHAMBER,
                "shattered_singularity", REACTION_CHAMBER,
                "quantum_infused_dust", INSCRIBER,
                "quantum_processor", INSCRIBER);
        try (var files = Files.list(DIR)) {
            assertEquals(molds.keySet(), files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .collect(Collectors.toSet()));
        }
        for (var entry : molds.entrySet()) {
            JsonObject mold = recipe(entry.getKey()).getAsJsonObject("mold");
            assertNotNull(mold, entry.getKey());
            assertEquals(entry.getValue(), mold.get("item").getAsString(), entry.getKey());
        }
    }

    private static JsonObject recipe(String name) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(DIR.resolve(name + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals("forge:conditional", root.get("type").getAsString(), name);
        JsonArray recipes = root.getAsJsonArray("recipes");
        assertNotNull(recipes, name);
        assertEquals(1, recipes.size(), name);
        JsonObject entry = recipes.get(0).getAsJsonObject();
        JsonObject recipe = entry.getAsJsonObject("recipe");
        assertNotNull(recipe, name);
        assertEquals("useless_mod:advanced_alloy_furnace", recipe.get("type").getAsString(), name);
        conditions(name, entry, recipe);
        return recipe;
    }

    private static void conditions(String name, JsonObject entry, JsonObject recipe) {
        JsonArray conditions = entry.getAsJsonArray("conditions");
        assertNotNull(conditions, name);
        JsonArray outputs = recipe.getAsJsonArray("outputs");
        assertNotNull(outputs, name);
        Set<String> types = new HashSet<>();
        for (JsonElement element : conditions) {
            JsonObject condition = element.getAsJsonObject();
            String type = condition.get("type").getAsString();
            assertTrue(types.add(type), name);
            if (type.equals("forge:mod_loaded")) {
                assertEquals("advanced_ae", condition.get("modid").getAsString(), name);
            } else if (type.equals("forge:item_exists")) {
                assertFalse(outputs.isEmpty(), name);
                assertEquals(outputs.get(0).getAsJsonObject().get("item").getAsString(),
                        condition.get("item").getAsString(), name);
            } else {
                fail("Unexpected condition " + type + " in " + name);
            }
        }
        Set<String> expected = outputs.isEmpty()
                ? Set.of("forge:mod_loaded")
                : Set.of("forge:mod_loaded", "forge:item_exists");
        assertEquals(expected, types, name);
    }
}
