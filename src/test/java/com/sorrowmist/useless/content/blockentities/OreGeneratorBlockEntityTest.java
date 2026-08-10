package com.sorrowmist.useless.content.blockentities;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreGeneratorBlockEntityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void externalCountClampsAtIntegerMaximum() {
        assertEquals(0, OreGeneratorBlockEntity.clampExternalCount(0L));
        assertEquals(17, OreGeneratorBlockEntity.clampExternalCount(17L));
        assertEquals(Integer.MAX_VALUE,
                OreGeneratorBlockEntity.clampExternalCount(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE,
                OreGeneratorBlockEntity.clampExternalCount(Long.MAX_VALUE));
    }

    @Test
    void externalOutputKeepsSampleComponentsAndUsesOneIntSizedStack() {
        ItemStack sample = new ItemStack(Items.IRON_ORE, 1);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("named sample"));

        ItemStack output = OreGeneratorBlockEntity.createExternalOutput(sample, Long.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, output.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(sample, output));
        assertTrue(OreGeneratorBlockEntity.createExternalOutput(sample, 0L).isEmpty());
    }

    @Test
    void sampleSlotsNormalizeCountAndRejectInvalidItems() {
        AtomicInteger activeSlots = new AtomicInteger(9);
        OreGeneratorSampleHandler samples = new OreGeneratorSampleHandler(
                activeSlots::get,
                stack -> stack.is(Items.IRON_ORE),
                () -> { });

        ItemStack sample = new ItemStack(Items.IRON_ORE, 64);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("sample"));
        samples.setStackInSlot(0, sample);
        samples.setStackInSlot(1, new ItemStack(Items.GOLD_ORE));

        assertEquals(1, samples.getStackInSlot(0).getCount());
        assertEquals("sample", samples.getStackInSlot(0).getHoverName().getString());
        assertTrue(samples.getStackInSlot(1).isEmpty());
        assertFalse(samples.isItemValid(9, new ItemStack(Items.IRON_ORE)));
    }
}
