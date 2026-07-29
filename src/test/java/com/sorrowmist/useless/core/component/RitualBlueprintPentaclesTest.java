package com.sorrowmist.useless.core.component;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitualBlueprintPentaclesTest {
    private static final ResourceLocation FIRST = ResourceLocation.fromNamespaceAndPath("occultism", "craft_afrit");
    private static final ResourceLocation SECOND = ResourceLocation.fromNamespaceAndPath("occultism", "possess_djinni");

    @Test
    void decodesAndCanonicalizesMultiplePentacles() {
        JsonArray encoded = new JsonArray();
        encoded.add(SECOND.toString());
        encoded.add(FIRST.toString());
        encoded.add(SECOND.toString());

        RitualBlueprintPentacles decoded = RitualBlueprintPentacles.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(List.of(FIRST, SECOND), decoded.pentacles());
        assertTrue(decoded.contains(FIRST));
        assertTrue(decoded.contains(SECOND));
    }
}
