package com.sorrowmist.useless.datagen;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.datagen.providers.recipes.AcquisitionRecipes;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class AcquisitionRecipesMigrationTest {
    @Test void everyAcquisitionDefinitionExactlyMatchesTheShippedForgeResource() throws Exception {
        var definitions = AcquisitionRecipes.definitions();
        assertEquals(23, definitions.size());
        for (var entry : definitions.entrySet()) {
            Path path = Path.of("src/generated/resources/data/useless_mod/recipes", entry.getKey() + ".json");
            assertEquals(JsonParser.parseString(Files.readString(path)), entry.getValue(), path.toString());
            assertFalse(entry.getValue().toString().contains("components"));
        }
    }

    @Test void finishedRecipesExposeUniqueOriginalIdsAndDoNotWriteAdvancements() {
        var ids = new HashSet<String>();
        AcquisitionRecipes.generate(recipe -> {
            assertTrue(ids.add(recipe.getId().toString()));
            assertNull(recipe.serializeAdvancement());
            assertNull(recipe.getAdvancementId());
            var json = new com.google.gson.JsonObject();
            recipe.serializeRecipeData(json);
            assertFalse(json.has("type")); // FinishedRecipe adds the native serializer ID.
        });
        assertEquals(23, ids.size());
    }

    @Test void largeTransformInputsAndLongEnergyRemainUntruncated() {
        var definitions = AcquisitionRecipes.definitions();
        assertEquals(129, definitions.get("advanced_alloy/ingot/useless_ingot_tier_2").getAsJsonArray("ingredients").size());
        assertEquals(145, definitions.get("advanced_alloy/ingot/useless_ingot_tier_3").getAsJsonArray("ingredients").size());
        var useful = definitions.get("advanced_alloy/ingot/useful_ingot");
        assertEquals(3_276_800_000L, useful.get("energy").getAsLong());
        assertEquals(9, useful.get("tier").getAsInt());
        assertFalse(useful.has("input_fluids"));
    }

    @Test void materialShapeOutputsUseTheNativeForgeItemStackJson() throws Exception {
        for (String shape : new String[]{"gear", "glass"}) {
            for (int tier = 1; tier <= 9; tier++) {
                String item = "useless_mod:useless_" + shape + "_tier_" + tier;
                Path path = Path.of("src/generated/resources/data/useless_mod/recipes/advanced_alloy",
                        shape, "useless_" + shape + "_tier_" + tier + ".json");
                var output = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                        .getAsJsonArray("outputs").get(0).getAsJsonObject();
                assertEquals(item, output.get("item").getAsString(), path.toString());
                assertEquals(1, output.get("count").getAsInt());
                assertFalse(output.has("id"), "Regenerated material outputs use the canonical Forge item field");
            }
        }
    }

    @Test void handAuthoredHatchesAreNotRegeneratedUnderDuplicateIds() throws Exception {
        for (String id : new String[]{"maintenance_hatch", "auto_maintenance_hatch", "muffler_hatch"}) {
            assertFalse(AcquisitionRecipes.definitions().containsKey("crafting/" + id));
            assertTrue(Files.exists(Path.of("src/main/resources/data/useless_mod/recipes/crafting/" + id + ".json")));
            assertFalse(Files.exists(Path.of("src/generated/resources/data/useless_mod/recipes/crafting/" + id + ".json")));
        }
    }
}
