package com.sorrowmist.useless.world.dimension;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionGenerationConfigTest {
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("dirt");
    private static final ResourceLocation GLASS = ResourceLocation.withDefaultNamespace("glass");

    @Test
    void defaultsKeepThePreviousGenerationValues() {
        DimensionGenerationConfig defaults = DimensionGenerationConfig.defaults();

        assertEquals(69, defaults.platformLayers());
        assertEquals(-64, defaults.platformStartY());
        assertTrue(defaults.generateBedrock());
        assertFalse(defaults.bedrockAtBottom());
        assertEquals(DimensionGenerationConfig.DEFAULT_BORDER_BLOCK, defaults.borderBlockId());
    }

    @Test
    void numericValuesAreValidatedAndNormalizedAtTheMenuBoundary() {
        DimensionGenerationConfig invalid = config(-1, 257);
        assertFalse(invalid.isValid());
        assertEquals(1, invalid.normalized().platformLayers());
        assertEquals(256, invalid.normalized().platformStartY());
        assertTrue(config(1, -64).isValid());
        assertTrue(config(256, 256).isValid());
    }

    @Test
    void networkAndNbtRoundTripsPreserveEveryField() {
        DimensionGenerationConfig expected = new DimensionGenerationConfig(
                STONE, DIRT, GLASS, 17, 42, false, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        expected.write(buffer);
        assertEquals(expected, DimensionGenerationConfig.read(buffer));

        CompoundTag tag = expected.save(new CompoundTag());
        assertEquals(expected, DimensionGenerationConfig.load(tag));
    }

    @Test
    void savedDataKeepsDimensionsIndependentAndUsesFirstWriterForFirstSetup() {
        UselessDimensionConfigSavedData data = new UselessDimensionConfigSavedData();
        ResourceKey<Level> first = dimension("uselessdim");
        ResourceKey<Level> second = dimension("uselessdim2");
        DimensionGenerationConfig firstConfig = config(3, -20);
        DimensionGenerationConfig secondConfig = config(7, 30);

        data.put(first, firstConfig);
        data.putIfAbsent(first, secondConfig);
        data.put(second, secondConfig);

        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        UselessDimensionConfigSavedData restored =
                UselessDimensionConfigSavedData.load(saved, RegistryAccess.EMPTY);

        assertEquals(firstConfig, restored.get(first).orElseThrow());
        assertEquals(secondConfig, restored.get(second).orElseThrow());
        assertTrue(restored.isConfigured(first));
        assertTrue(restored.isConfigured(second));
    }

    private static DimensionGenerationConfig config(int layers, int startY) {
        return new DimensionGenerationConfig(STONE, DIRT, GLASS,
                layers, startY, true, false);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("useless_mod_test", path));
    }
}
