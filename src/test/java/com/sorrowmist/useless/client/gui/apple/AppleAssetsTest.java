package com.sorrowmist.useless.client.gui.apple;

import com.google.gson.JsonParser;
import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.DesignAssets;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppleAssetsTest {
    @Test
    void everyIconUsesNonEmptySuppliedArtworkRatherThanOrdinalAtlasTiles() throws Exception {
        for (var icon : AppleUi.Icon.values()) {
            String key = AppleUi.iconKey(icon);
            assertTrue(DesignAssets.keys().contains(key), icon.name());
            var resource = DesignAssets.resource(key, 2);
            try (var stream = getClass().getResourceAsStream("/assets/" + resource.getNamespace() + "/" + resource.getPath())) {
                assertNotNull(stream, key);
                var image = ImageIO.read(stream);
                int pixels = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    if ((image.getRGB(x, y) >>> 24) > 32) pixels++;
                assertTrue(pixels > 0, icon.name() + " is missing its original artwork");
            }
        }
    }

    @Test
    void newDynamicLabelsAreTranslatedInAllBundledLanguages() throws Exception {
        for (String language : List.of("en_us", "zh_cn", "zh_tw")) {
            try (var stream = getClass().getResourceAsStream("/assets/useless_mod/lang/" + language + ".json")) {
                assertNotNull(stream);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : List.of("gui.useless_mod.thermostat.quick_steps", "gui.useless_mod.thermostat.enabled_short",
                        "gui.useless_mod.thermostat.disabled_short", "gui.useless_mod.mode_config.wheel_current",
                        "gui.useless_mod.chain_group.group_count")) {
                    assertTrue(json.has(key), language + ": " + key);
                    assertFalse(json.get(key).getAsString().isBlank());
                }
            }
        }
    }
}
