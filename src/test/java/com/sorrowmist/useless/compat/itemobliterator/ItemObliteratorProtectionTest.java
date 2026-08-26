package com.sorrowmist.useless.compat.itemobliterator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemObliteratorProtectionTest {
    @Test
    void protectsOnlyTheUselessModNamespace() {
        assertTrue(ItemObliteratorProtection.isProtectedItemId("useless_mod:item"));
        assertTrue(ItemObliteratorProtection.isProtectedItemId("useless_mod:block_item"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId("useless_modded:item"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId("minecraft:item"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId("useless_mod"));
        assertFalse(ItemObliteratorProtection.isProtectedItemId(""));
        assertFalse(ItemObliteratorProtection.isProtectedItemId(null));
    }

    @Test
    void mixinConfigIsOptionalAndContainsBothProtectionMixins() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("useless_mod.item_obliterator.mixins.json")) {
            assertNotNull(input);
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();

                assertFalse(config.get("required").getAsBoolean());
                JsonArray mixins = config.getAsJsonArray("mixins");
                assertTrue(contains(mixins, "ItemObliteratorUtilsMixin"));
                assertTrue(contains(mixins, "ItemObliteratorContainerMixin"));
            }
        }
    }

    private static boolean contains(JsonArray values, String expected) {
        for (JsonElement value : values) {
            if (expected.equals(value.getAsString())) return true;
        }
        return false;
    }
}
