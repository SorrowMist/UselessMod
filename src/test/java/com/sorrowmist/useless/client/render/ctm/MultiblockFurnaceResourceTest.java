package com.sorrowmist.useless.client.render.ctm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockFurnaceResourceTest {
    private static final String ASSETS = "assets/useless_mod/";
    private static final String TEXTURES =
            ASSETS + "textures/block/multiblock_alloy_furnace/";
    private static final List<String> OVERLAYS = List.of(
            "core_base_overlay.png", "controller_idle_overlay.png",
            "controller_run_overlay.png", "controller_wait_overlay.png",
            "pattern_assembly_overlay.png", "mold_hub_overlay.png",
            "passive_crafting_hatch_overlay.png");
    private static final List<String> ACTIVE_COIL_TEXTURES = List.of(
            "useless_coil_active.png", "useless_coil_active_ctm.png",
            "useful_coil_active.png", "useful_coil_active_ctm.png");
    private static final List<String> DIRECTIONAL_PARTS = List.of(
            "me_pattern_assembly", "omniversal_mold_hub", "passive_crafting_hatch");
    private static final Set<String> SIX_FACING_VARIANTS = Set.of(
            "facing=down", "facing=up", "facing=north",
            "facing=east", "facing=south", "facing=west");
    @Test
    void texturesHaveTheRequiredPixelDimensions() throws IOException {
        for (String resource : textureResources()) {
            BufferedImage image = readImage(resource);
            int expectedWidth = resource.endsWith("_ctm.png") ? 32 : 16;
            int expectedHeight = resource.endsWith("useful_coil_ctm.png") ? 64
                    : resource.endsWith("useful_coil.png") ? 32 : expectedWidth;
            assertEquals(expectedWidth, image.getWidth(), resource);
            assertEquals(expectedHeight, image.getHeight(), resource);
        }
    }

    @Test
    void everyForegroundContainsVisibleAndTransparentPixels() throws IOException {
        List<String> foregrounds = new ArrayList<>(OVERLAYS);
        ACTIVE_COIL_TEXTURES.forEach(name -> foregrounds.add("coils/" + name));
        for (String name : foregrounds) {
            BufferedImage image = readImage(TEXTURES + name);
            boolean transparent = false;
            boolean visible = false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    transparent |= alpha < 255;
                    visible |= alpha > 0;
                }
            }
            assertTrue(transparent, name + " must preserve transparent background pixels");
            assertTrue(visible, name + " must contain a visible foreground");
        }
    }

    @Test
    void usefulCoilUsesSlowInterpolatedTwoFrameAnimation() throws IOException {
        assertFramesDiffer("useful_coil.png");
        assertFramesDiffer("useful_coil_ctm.png");
        assertAnimationMetadata("useful_coil.png.mcmeta");
        assertAnimationMetadata("useful_coil_ctm.png.mcmeta");
    }

    @Test
    void everyCoilProvidesInactiveAndActiveModels() throws IOException {
        Set<String> expectedVariants = Set.of("active=false", "active=true");
        for (int tier = UselessCoilBlock.MIN_TIER;
             tier <= UselessCoilBlock.MAX_TIER; tier++) {
            String name = UselessCoilBlock.registryName(tier);
            String resource = ASSETS + "blockstates/" + name + ".json";
            JsonObject variants = readJson(resource).getAsJsonObject("variants");
            assertNotNull(variants, resource);
            assertEquals(expectedVariants, variants.keySet(), resource);
            assertEquals("useless_mod:block/" + name,
                    variants.getAsJsonObject("active=false").get("model").getAsString(),
                    resource);
            assertEquals("useless_mod:block/" + name + "_active",
                    variants.getAsJsonObject("active=true").get("model").getAsString(),
                    resource);
        }
    }

    @Test
    void omniversalPatternUsesItsOwnTexture() throws IOException {
        String modelResource = ASSETS + "models/item/omniversal_pattern.json";
        JsonObject model = readJson(modelResource);
        assertEquals("ae2:item/processing_pattern", model.get("parent").getAsString());
        assertEquals("useless_mod:item/omniversal_pattern",
                model.getAsJsonObject("textures").get("layer0").getAsString());

        String textureResource = ASSETS + "textures/item/omniversal_pattern.png";
        BufferedImage texture = readImage(textureResource);
        assertEquals(16, texture.getWidth(), textureResource);
        assertEquals(16, texture.getHeight(), textureResource);
    }

    @Test
    void functionalPartsProvideModelsForAllSixFacings() throws IOException {
        for (String name : DIRECTIONAL_PARTS) {
            String resource = ASSETS + "blockstates/" + name + ".json";
            JsonObject variants = readJson(resource).getAsJsonObject("variants");
            assertNotNull(variants, resource);
            assertEquals(SIX_FACING_VARIANTS, variants.keySet(), resource);
            assertEquals(90, variants.getAsJsonObject("facing=down").get("x").getAsInt(), resource);
            assertEquals(270, variants.getAsJsonObject("facing=up").get("x").getAsInt(), resource);
            variants.asMap().values().forEach(variant ->
                    assertTrue(variant.getAsJsonObject().get("uvlock").getAsBoolean(), resource));
        }
    }

    @Test
    void allNewJsonReferencesResolve() throws IOException {
        List<String> jsonResources = jsonResources();
        assertFalse(jsonResources.isEmpty());
        for (String resource : jsonResources) {
            try (Reader reader = new InputStreamReader(
                    openResource(resource), StandardCharsets.UTF_8)) {
                validateObject(JsonParser.parseReader(reader).getAsJsonObject(), resource);
            }
        }
    }

    private static void assertFramesDiffer(String name) throws IOException {
        BufferedImage image = readImage(TEXTURES + "coils/" + name);
        int frameSize = image.getWidth();
        assertEquals(frameSize * 2, image.getHeight(), name);
        for (int y = 0; y < frameSize; y++) {
            for (int x = 0; x < frameSize; x++) {
                if (image.getRGB(x, y) != image.getRGB(x, y + frameSize)) {
                    return;
                }
            }
        }
        throw new AssertionError(name + " must contain two distinct animation frames");
    }

    private static void assertAnimationMetadata(String name) throws IOException {
        String resource = TEXTURES + "coils/" + name;
        JsonObject animation = readJson(resource).getAsJsonObject("animation");
        assertNotNull(animation, resource);
        assertEquals(40, animation.get("frametime").getAsInt(), resource);
        assertTrue(animation.get("interpolate").getAsBoolean(), resource);
        assertEquals(List.of(0, 1), animation.getAsJsonArray("frames").asList().stream()
                .map(JsonElement::getAsInt).toList(), resource);
    }

    private static List<String> textureResources() {
        List<String> resources = new ArrayList<>();
        OVERLAYS.forEach(name -> resources.add(TEXTURES + name));
        resources.add(TEXTURES + "furnace_casing.png");
        resources.add(TEXTURES + "furnace_casing_ctm.png");
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            String name = UselessCoilBlock.registryName(tier);
            resources.add(TEXTURES + "coils/" + name + ".png");
            resources.add(TEXTURES + "coils/" + name + "_ctm.png");
        }
        ACTIVE_COIL_TEXTURES.forEach(name -> resources.add(TEXTURES + "coils/" + name));
        return resources;
    }

    private static List<String> jsonResources() {
        List<String> names = new ArrayList<>(List.of(
                "me_pattern_assembly", "multiblock_alloy_furnace_core",
                "omniversal_furnace_casing", "omniversal_mold_hub",
                "passive_crafting_hatch"));
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            names.add(UselessCoilBlock.registryName(tier));
        }
        List<String> resources = new ArrayList<>();
        names.forEach(name -> {
            resources.add(ASSETS + "blockstates/" + name + ".json");
            resources.add(ASSETS + "models/item/" + name + ".json");
        });
        names.stream()
                .filter(name -> !name.equals("multiblock_alloy_furnace_core"))
                .forEach(name -> resources.add(ASSETS + "models/block/" + name + ".json"));
        resources.add(ASSETS + "models/block/multiblock_furnace_overlay.json");
        resources.add(ASSETS + "models/block/multiblock_alloy_furnace_core_base.json");
        resources.add(ASSETS + "models/block/multiblock_alloy_furnace_core_idle.json");
        resources.add(ASSETS + "models/block/multiblock_alloy_furnace_core_run.json");
        resources.add(ASSETS + "models/block/multiblock_alloy_furnace_core_wait.json");
        resources.add(ASSETS + "models/block/multiblock_coil_active.json");
        resources.add(ASSETS + "models/item/omniversal_pattern.json");
        for (int tier = UselessCoilBlock.MIN_TIER;
             tier <= UselessCoilBlock.MAX_TIER; tier++) {
            resources.add(ASSETS + "models/block/"
                    + UselessCoilBlock.registryName(tier) + "_active.json");
        }
        return resources;
    }

    private static BufferedImage readImage(String resource) throws IOException {
        try (InputStream input = openResource(resource)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, resource);
            return image;
        }
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (Reader reader = new InputStreamReader(openResource(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream openResource(String resource) {
        InputStream input = MultiblockFurnaceResourceTest.class
                .getClassLoader().getResourceAsStream(resource);
        assertNotNull(input, resource);
        return input;
    }

    private static void validateObject(JsonObject object, String source) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if ((entry.getKey().equals("model") || entry.getKey().equals("parent"))
                    && value.isJsonPrimitive()) {
                validateReference(value.getAsString(), "models", ".json", source);
            } else if (entry.getKey().equals("textures") && value.isJsonObject()) {
                for (JsonElement texture : value.getAsJsonObject().asMap().values()) {
                    if (texture.isJsonPrimitive()) {
                        validateReference(texture.getAsString(), "textures", ".png", source);
                    }
                }
            }
            if (value.isJsonObject()) {
                validateObject(value.getAsJsonObject(), source);
            } else if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        validateObject(child.getAsJsonObject(), source);
                    }
                }
            }
        }
    }

    private static void validateReference(
            String reference, String directory, String extension, String source) {
        if (reference.startsWith("#") || !reference.startsWith("useless_mod:")) {
            return;
        }
        String relative = reference.substring("useless_mod:".length());
        String target = ASSETS + directory + "/" + relative + extension;
        assertNotNull(MultiblockFurnaceResourceTest.class
                        .getClassLoader().getResource(target),
                source + " references missing resource " + target);
    }
}
