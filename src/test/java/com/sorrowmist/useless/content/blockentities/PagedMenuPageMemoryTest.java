package com.sorrowmist.useless.content.blockentities;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedMenuPageMemoryTest {
    @Test
    void storesIndependentPagesPerPlayerAndRoundTripsThroughNbt() {
        AtomicInteger changes = new AtomicInteger();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        PagedMenuPageMemory memory = new PagedMenuPageMemory(changes::incrementAndGet);

        memory.set(firstPlayer, 3);
        memory.set(secondPlayer, 7);

        CompoundTag saved = new CompoundTag();
        memory.save(saved);
        PagedMenuPageMemory restored = new PagedMenuPageMemory(() -> { });
        restored.load(saved);

        assertEquals(3, restored.get(firstPlayer));
        assertEquals(7, restored.get(secondPlayer));
        assertEquals(2, changes.get());
    }

    @Test
    void settingTheFirstPageRemovesThePlayerEntry() {
        UUID player = UUID.randomUUID();
        PagedMenuPageMemory memory = new PagedMenuPageMemory(() -> { });
        memory.set(player, 4);

        memory.set(player, 0);

        CompoundTag saved = new CompoundTag();
        memory.save(saved);
        assertEquals(0, memory.get(player));
        assertTrue(saved.isEmpty());
    }

    @Test
    void aNewBlockMemoryStartsOnTheFirstPage() {
        UUID player = UUID.randomUUID();
        PagedMenuPageMemory oldBlock = new PagedMenuPageMemory(() -> { });
        oldBlock.set(player, 5);

        PagedMenuPageMemory replacementBlock = new PagedMenuPageMemory(() -> { });

        assertEquals(0, replacementBlock.get(player));
    }

    @Test
    void negativePagesAreNormalizedAndDoNotPersist() {
        UUID player = UUID.randomUUID();
        PagedMenuPageMemory memory = new PagedMenuPageMemory(() -> { });

        memory.set(player, -1);

        CompoundTag saved = new CompoundTag();
        memory.save(saved);
        assertEquals(0, memory.get(player));
        assertFalse(saved.contains("PagedMenuPages"));
    }
}
