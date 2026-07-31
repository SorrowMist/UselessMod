package com.sorrowmist.useless.core.component;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockPartDataTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void passiveHatchRoundTripKeepsRecoverySlotsAndSettings() {
        RecoverableItemStackHandler source = handler();
        source.setStackInSlot(539, new ItemStack(Items.DIAMOND, 17));

        MultiblockPartData data = MultiblockPartData.passiveHatch(source, registries, 321, 45L);
        RecoverableItemStackHandler restored = handler();
        data.restoreInventory(restored, registries);

        assertTrue(data.hasInventoryContents());
        assertFalse(data.isEmpty());
        assertEquals(321, data.intervalTicks());
        assertEquals(45L, data.multiplier());
        assertEquals(17, restored.getStackInSlot(539).getCount());
        assertTrue(restored.getStackInSlot(539).is(Items.DIAMOND));
    }

    @Test
    void emptyInventoryWithoutSettingsDoesNotCreatePortableState() {
        MultiblockPartData data = MultiblockPartData.inventory(handler(), registries);

        assertFalse(data.hasInventoryContents());
        assertTrue(data.isEmpty());
    }

    @Test
    void networkRoundTripKeepsPortableInventoryAndSettings() {
        RecoverableItemStackHandler source = handler();
        source.setStackInSlot(539, new ItemStack(Items.EMERALD, 23));
        MultiblockPartData expected = MultiblockPartData.passiveHatch(
                source, registries, 72_000, Long.MAX_VALUE);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), registries, ConnectionType.OTHER);

        try {
            MultiblockPartData.STREAM_CODEC.encode(buffer, expected);
            MultiblockPartData decoded = MultiblockPartData.STREAM_CODEC.decode(buffer);
            RecoverableItemStackHandler restored = handler();
            decoded.restoreInventory(restored, registries);

            assertEquals(expected, decoded);
            assertEquals(23, restored.getStackInSlot(539).getCount());
            assertTrue(restored.getStackInSlot(539).is(Items.EMERALD));
        } finally {
            buffer.release();
        }
    }

    private static RecoverableItemStackHandler handler() {
        return new RecoverableItemStackHandler(() -> 540, stack -> true, () -> {});
    }
}
