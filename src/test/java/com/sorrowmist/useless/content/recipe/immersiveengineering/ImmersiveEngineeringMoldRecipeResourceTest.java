package com.sorrowmist.useless.content.recipe.immersiveengineering;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmersiveEngineeringMoldRecipeResourceTest {
    private static final String RECIPE_ROOT =
            "/data/useless_mod/recipe/immersiveengineering/mold/";
    private static final String INGOT = "useless_mod:useless_ingot_tier_1";
    private static final String GLASS = "useless_mod:useless_glass_tier_1";
    private static final List<String> PATTERN = List.of("IGI", "GCG", "IGI");
    private static final Map<String, String> RECIPES = new LinkedHashMap<>();

    static {
        RECIPES.put("alloy_smelter", "immersiveengineering:alloybrick");
        RECIPES.put("arc_furnace", "immersiveengineering:heavy_engineering");
        RECIPES.put("blast_furnace", "immersiveengineering:blastbrick");
        RECIPES.put("bottling_machine", "immersiveengineering:conveyor_basic");
        RECIPES.put("coke_oven", "immersiveengineering:cokebrick");
        RECIPES.put("crusher", "immersiveengineering:steel_scaffolding_standard");
        RECIPES.put("fermenter", "immersiveengineering:light_engineering");
        RECIPES.put("metal_press", "immersiveengineering:component_steel");
        RECIPES.put("mixer", "immersiveengineering:component_iron");
        RECIPES.put("refinery", "immersiveengineering:sheetmetal_steel");
        RECIPES.put("sawmill", "immersiveengineering:sawblade");
        RECIPES.put("squeezer", "immersiveengineering:hemp_fiber");
    }

    @Test
    void definesAllMissingMachineMoldRecipes() throws IOException {
        for (Map.Entry<String, String> entry : RECIPES.entrySet()) {
            JsonObject recipe = read(entry.getKey());

            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(PATTERN, strings(recipe.getAsJsonArray("pattern")));

            JsonObject conditions = recipe.getAsJsonArray("neoforge:conditions")
                    .get(0).getAsJsonObject();
            assertEquals("neoforge:mod_loaded", conditions.get("type").getAsString());
            assertEquals("immersiveengineering", conditions.get("modid").getAsString());

            JsonObject key = recipe.getAsJsonObject("key");
            assertEquals(INGOT, key.getAsJsonObject("I").get("item").getAsString());
            assertEquals(GLASS, key.getAsJsonObject("G").get("item").getAsString());
            assertEquals(entry.getValue(), key.getAsJsonObject("C").get("item").getAsString());

            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals("immersiveengineering:" + entry.getKey(), result.get("id").getAsString());
            assertEquals(1, result.get("count").getAsInt());
            assertFalse(recipe.toString().toLowerCase().contains("coil"));

            assertTrue(BuiltInRegistries.ITEM.containsKey(id(INGOT)), INGOT);
            assertTrue(BuiltInRegistries.ITEM.containsKey(id(GLASS)), GLASS);
            assertTrue(BuiltInRegistries.ITEM.containsKey(id(entry.getValue())), entry.getValue());
        }
    }

    @Test
    void doesNotDuplicateNativeWorkbenchOrClocheRecipes() {
        assertNull(getResource("workbench"));
        assertNull(getResource("cloche"));
        assertEquals(12, RECIPES.size());
    }

    private static JsonObject read(String name) throws IOException {
        try (InputStream stream = getResource(name)) {
            assertNotNull(stream, name);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static InputStream getResource(String name) {
        return ImmersiveEngineeringMoldRecipeResourceTest.class
                .getResourceAsStream(RECIPE_ROOT + name + ".json");
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(element -> element.getAsString()).toList();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
