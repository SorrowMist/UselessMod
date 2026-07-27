package com.sorrowmist.useless.content.blockentities;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighStackItemStackHandlerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void componentStacksRoundTripWhenInventoryTotalExceedsIntegerLimit() {
        HighStackItemStackHandler source = handler();

        ItemStack namedDiamonds = new ItemStack(Items.DIAMOND);
        namedDiamonds.set(DataComponents.CUSTOM_NAME, Component.literal("high-count"));
        namedDiamonds.setCount(Integer.MAX_VALUE);
        source.setStackInSlot(0, namedDiamonds);
        source.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, Integer.MAX_VALUE));

        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var serialized = assertDoesNotThrow(() -> source.serializeNBT(registries));

        HighStackItemStackHandler restored = handler();
        assertDoesNotThrow(() -> restored.deserializeNBT(registries, serialized));

        ItemStack restoredDiamonds = restored.getStackInSlot(0);
        assertEquals(Integer.MAX_VALUE, restoredDiamonds.getCount());
        assertEquals("high-count", restoredDiamonds.getHoverName().getString());
        assertEquals(Integer.MAX_VALUE, restored.getStackInSlot(1).getCount());
        assertTrue((long) restoredDiamonds.getCount() + restored.getStackInSlot(1).getCount()
                > Integer.MAX_VALUE);
    }

    @Test
    void legacyFurnaceDropInventoryStillRestoresComponentsAndHighCount() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ItemStack namedDiamonds = new ItemStack(Items.DIAMOND);
        namedDiamonds.set(DataComponents.CUSTOM_NAME, Component.literal("legacy"));
        CompoundTag savedStack = (CompoundTag) namedDiamonds.save(registries);

        CompoundTag itemTag = new CompoundTag();
        itemTag.putByte("Slot", (byte) 0);
        itemTag.put("id", StringTag.valueOf("minecraft:diamond"));
        itemTag.putInt("RealCount", Integer.MAX_VALUE);
        itemTag.put("components", savedStack.getCompound("components"));
        ListTag items = new ListTag();
        items.add(itemTag);
        CompoundTag legacyInventory = new CompoundTag();
        legacyInventory.put("Items", items);

        HighStackItemStackHandler restored = handler();
        assertDoesNotThrow(() -> restored.deserializeNBT(registries, legacyInventory));

        ItemStack restoredDiamonds = restored.getStackInSlot(0);
        assertEquals(Integer.MAX_VALUE, restoredDiamonds.getCount());
        assertEquals("legacy", restoredDiamonds.getHoverName().getString());
    }

    private static HighStackItemStackHandler handler() {
        return new HighStackItemStackHandler(
                4, 3, 2, 0, 2, 4, 3,
                () -> {}, null, null, null);
    }
}
