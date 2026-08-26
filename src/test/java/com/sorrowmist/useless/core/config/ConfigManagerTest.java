package com.sorrowmist.useless.core.config;

import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                "neovitae",
                "ufo",
                "modern_industrialization",
                "immersiveengineering",
                "pneumaticcraft",
                "bigreactors");

        for (String source : externalSources) {
            assertTrue(ConfigManager.isRecipeConversionEnabled(source), source);
        }

        Object valueSpec = ConfigManager.COMMON_SPEC.getSpec()
                .get(List.of("advanced_alloy_furnace", "enable_ufo_recipe_conversion"));
        assertInstanceOf(ModConfigSpec.ValueSpec.class, valueSpec);
        assertEquals(Boolean.TRUE, ((ModConfigSpec.ValueSpec) valueSpec).getDefault());
        assertTrue(ConfigManager.isRecipeConversionEnabled(RecipeSourceIds.UFO));

        Object pneumaticSpec = ConfigManager.COMMON_SPEC.getSpec()
                .get(List.of("advanced_alloy_furnace", "enable_pneumaticcraft_recipe_conversion"));
        assertInstanceOf(ModConfigSpec.ValueSpec.class, pneumaticSpec);
        assertEquals(Boolean.TRUE, ((ModConfigSpec.ValueSpec) pneumaticSpec).getDefault());
        assertTrue(ConfigManager.isRecipeConversionEnabled(RecipeSourceIds.PNEUMATICCRAFT));

        Object extremeReactorsSpec = ConfigManager.COMMON_SPEC.getSpec()
                .get(List.of("advanced_alloy_furnace", "enable_bigreactors_recipe_conversion"));
        assertInstanceOf(ModConfigSpec.ValueSpec.class, extremeReactorsSpec);
        assertEquals(Boolean.TRUE, ((ModConfigSpec.ValueSpec) extremeReactorsSpec).getDefault());
        assertTrue(ConfigManager.isRecipeConversionEnabled(RecipeSourceIds.BIG_REACTORS));
    }

    @Test
    void uselessDimensionBlacklistUsesAnEmptyServerListByDefault() {
        Object valueSpec = ConfigManager.SERVER_SPEC.getSpec()
                .get(List.of("useless_dimension", "floor_block_blacklist"));

        ModConfigSpec.ListValueSpec listSpec =
                assertInstanceOf(ModConfigSpec.ListValueSpec.class, valueSpec);
        assertEquals(List.of(), listSpec.getDefault());
        assertEquals("", listSpec.getNewElementSupplier().get());
        assertEquals(List.of(), ConfigManager.getUselessDimensionFloorBlockBlacklist());
    }
}
