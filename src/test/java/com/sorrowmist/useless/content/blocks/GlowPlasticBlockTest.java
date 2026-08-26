package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.api.enums.EnumColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlowPlasticBlockTest {
    @Test
    void registersFourVariantsForEveryColor() {
        assertEquals(4, GlowPlasticBlock.ALL_BLOCK_MAPS.size());
        assertEquals(4, GlowPlasticBlock.ALL_BLOCK_ITEM_MAPS.size());
        for (var blockMap : GlowPlasticBlock.ALL_BLOCK_MAPS) {
            assertEquals(EnumColor.values().length, blockMap.size());
        }
        for (var itemMap : GlowPlasticBlock.ALL_BLOCK_ITEM_MAPS) {
            assertEquals(EnumColor.values().length, itemMap.size());
        }
    }

    @Test
    void variantsKeepExpectedRegistryNamesAndProperties() {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            assertVariant(GlowPlasticBlock.PLASTIC_BLOCKS.get(color).get(), color,
                    color.getRegistryPrefix() + "_plastic", false, false);
            assertVariant(GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(color).get(), color,
                    color.getRegistryPrefix() + "_glow_plastic", true, false);
            assertVariant(GlowPlasticBlock.PLASTIC_CTM_BLOCKS.get(color).get(), color,
                    color.getRegistryPrefix() + "_plastic_ctm", false, true);
            assertVariant(GlowPlasticBlock.GLOW_PLASTIC_CTM_BLOCKS.get(color).get(), color,
                    color.getRegistryPrefix() + "_glow_plastic_ctm", true, true);
        }
    }

    private static void assertVariant(
            GlowPlasticBlock block, EnumColor color, String registryName,
            boolean glowing, boolean connectedTexture) {
        assertEquals(registryName, BuiltInRegistries.BLOCK.getKey(block).getPath());
        assertEquals(color, block.getColor());
        assertEquals(glowing, block.isGlowing());
        assertEquals(connectedTexture, block.hasConnectedTexture());
        assertEquals(glowing ? 15 : 0,
                block.getLightEmission(block.defaultBlockState(), null, BlockPos.ZERO));
        assertTrue(block.defaultBlockState().requiresCorrectToolForDrops());
    }
}
