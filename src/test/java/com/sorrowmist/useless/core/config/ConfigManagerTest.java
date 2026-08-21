package com.sorrowmist.useless.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void recipeConversionDefaultsMatchTheOptInVanillaCraftingPolicy() {
        assertFalse(ConfigManager.isCraftingRecipeConversionEnabled());
        assertTrue(ConfigManager.isSmeltingRecipeConversionEnabled());
        assertTrue(ConfigManager.isBrewingRecipeConversionEnabled());

        List<String> externalSources = List.of(
                "extendedae",
                "advanced_ae",
                "mekanism",
                "mekanismgenerators",
                "appmek",
                "ae2",
                "ae2cs",
                "industrialforegoing",
                "actuallyadditions",
                "ars_nouveau",
                "mysticalagriculture",
                "ae2lt",
                "data_energistics",
                "productivebees",
                "draconicevolution",
                "powah",
                "extendedcrafting",
                "neoecoae",
                "naturesaura",
                "forbidden_arcanus",
                "occultism",
                "malum",
                "enderio",
                "create",
                "oritech",
                "neovitae");

        for (String source : externalSources) {
            assertTrue(ConfigManager.isRecipeConversionEnabled(source), source);
        }
    }
}
