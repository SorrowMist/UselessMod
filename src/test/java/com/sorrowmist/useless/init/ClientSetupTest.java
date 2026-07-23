package com.sorrowmist.useless.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClientSetupTest {
    @Test
    void omniversalPatternRecipeTooltipAcceptsNamespacedRecipeIds() {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "draconicevolution", "awakened_draconium_block_converted");

        var tooltip = assertDoesNotThrow(() -> ClientSetup.createRecipeTooltip(recipeId));

        var contents = assertInstanceOf(TranslatableContents.class, tooltip.getContents());
        assertEquals(recipeId.toString(), contents.getArgs()[0]);
    }
}
