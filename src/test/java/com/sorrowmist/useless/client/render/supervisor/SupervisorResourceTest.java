package com.sorrowmist.useless.client.render.supervisor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorResourceTest {
    private static final String ASSETS = "assets/useless_mod/";
    private static final String DATA = "data/useless_mod/";

    @Test
    void wrapperKeepsTheSourceModelOutsideVanillaElements() throws IOException {
        JsonObject wrapper = readJson(ASSETS + "models/block/supervisor.json");
        JsonObject source = wrapper.getAsJsonObject("supervisor_model");

        assertEquals("useless_mod:supervisor", wrapper.get("loader").getAsString());
        assertFalse(wrapper.has("elements"));
        assertEquals(24, source.getAsJsonArray("elements").size());
        assertEquals(2, source.getAsJsonArray("groups").size());
        assertEquals("iava_user", source.getAsJsonObject("textures").get("2").getAsString());
        assertEquals("zzj_pluto", source.getAsJsonObject("textures").get("3").getAsString());
        assertEquals("useless_mod:block/iava_user",
                wrapper.getAsJsonObject("textures").get("2").getAsString());
        assertEquals("useless_mod:block/pluto",
                wrapper.getAsJsonObject("textures").get("3").getAsString());
    }

    @Test
    void customParserAcceptsTheSourceRotations() throws IOException {
        JsonObject wrapper = readJson(ASSETS + "models/block/supervisor.json");

        assertNotNull(SupervisorGeometry.read(wrapper.getAsJsonObject("supervisor_model")));
    }

    @Test
    void sourceTexturesHaveExpectedDimensions() throws IOException {
        assertImageSize(ASSETS + "textures/block/iava_user.png");
        assertImageSize(ASSETS + "textures/block/zzj_pluto.png");
    }

    @Test
    void blockstateMapsAllHorizontalFacingsToTheSupervisorModel() throws IOException {
        JsonObject variants = readJson(ASSETS + "blockstates/supervisor.json")
                .getAsJsonObject("variants");

        assertEquals(4, variants.size());
        assertVariant(variants, "facing=north", null);
        assertVariant(variants, "facing=east", 90);
        assertVariant(variants, "facing=south", 180);
        assertVariant(variants, "facing=west", 270);
    }

    @Test
    void supervisorRecipeUsesTheUsefulIngotAndEveryRequestedWoolColor() throws IOException {
        JsonObject recipe = readJson(DATA + "recipe/crafting/supervisor.json");
        var ingredients = recipe.getAsJsonArray("ingredients");
        Set<String> expected = Set.of(
                "useless_mod:useful_ingot",
                "minecraft:pink_wool",
                "minecraft:black_wool",
                "minecraft:gray_wool",
                "minecraft:white_wool",
                "minecraft:green_wool",
                "minecraft:blue_wool",
                "minecraft:yellow_wool",
                "minecraft:orange_wool");
        Set<String> actual = new HashSet<>();
        for (JsonElement ingredient : ingredients) {
            actual.add(ingredient.getAsJsonObject().get("item").getAsString());
        }

        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("useless_mod:supervisor", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(expected, actual);
        assertEquals(expected.size(), ingredients.size());
    }

    @Test
    void chineseItemTranslationUsesSupervisorName() throws IOException {
        JsonObject translations = readJson(ASSETS + "lang/zh_cn.json");

        assertEquals("监工", translations.get("item.useless_mod.supervisor").getAsString());
    }

    @Test
    void soundDefinitionReferencesTheHowlOggResource() throws IOException {
        JsonObject sounds = readJson(ASSETS + "sounds.json");
        JsonObject howl = sounds.getAsJsonObject("howl");

        assertNotNull(howl);
        assertEquals("useless_mod:howl", howl.getAsJsonArray("sounds").get(0).getAsString());

        try (InputStream input = openResource(ASSETS + "sounds/howl.ogg")) {
            assertEquals("OggS", new String(input.readNBytes(4), StandardCharsets.US_ASCII));
        }
    }

    private static void assertVariant(JsonObject variants, String key, Integer yRotation) {
        JsonObject variant = variants.getAsJsonObject(key);
        assertNotNull(variant, key);
        assertEquals("useless_mod:block/supervisor", variant.get("model").getAsString());
        if (yRotation == null) {
            assertFalse(variant.has("y"));
        } else {
            assertEquals(yRotation, variant.get("y").getAsInt());
        }
    }

    private static void assertImageSize(String resource) throws IOException {
        try (InputStream input = openResource(resource)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, resource);
            assertEquals(64, image.getWidth(), resource);
            assertEquals(64, image.getHeight(), resource);
        }
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (Reader reader = new InputStreamReader(openResource(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream openResource(String resource) {
        InputStream input = SupervisorResourceTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(input, resource);
        return input;
    }
}
