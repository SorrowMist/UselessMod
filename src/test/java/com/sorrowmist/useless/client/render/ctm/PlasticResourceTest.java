package com.sorrowmist.useless.client.render.ctm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.api.enums.EnumColor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlasticResourceTest {
    private static final String ASSETS = "assets/useless_mod/";

    @Test
    void everyPlasticVariantHasBlockstateAndItemModel() throws IOException {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            String prefix = color.getRegistryPrefix();
            assertVariantResources(prefix + "_plastic");
            assertVariantResources(prefix + "_glow_plastic");
            assertVariantResources(prefix + "_plastic_ctm");
            assertVariantResources(prefix + "_glow_plastic_ctm");
        }
    }

    @Test
    void plasticCtmAtlasIsAvailableInBothResourcePacks() throws IOException {
        assertTextureSize(ASSETS + "textures/block/plastic_glow_block_ctm.png");
        assertTextureSize("xia/" + ASSETS + "textures/block/plastic_glow_block_ctm.png");
    }

    private static void assertVariantResources(String name) throws IOException {
        JsonObject blockstate = readJson(ASSETS + "blockstates/" + name + ".json");
        assertEquals("useless_mod:block/plastic/glow",
                blockstate.getAsJsonObject("variants").getAsJsonObject("")
                        .get("model").getAsString(), name);

        JsonObject itemModel = readJson(ASSETS + "models/item/" + name + ".json");
        assertEquals("useless_mod:block/plastic/glow",
                itemModel.get("parent").getAsString(), name);
    }

    private static void assertTextureSize(String resource) throws IOException {
        try (InputStream input = openResource(resource)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, resource);
            assertEquals(32, image.getWidth(), resource);
            assertEquals(32, image.getHeight(), resource);
        }
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (Reader reader = new InputStreamReader(openResource(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream openResource(String resource) {
        InputStream input = PlasticResourceTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(input, resource);
        return input;
    }
}
