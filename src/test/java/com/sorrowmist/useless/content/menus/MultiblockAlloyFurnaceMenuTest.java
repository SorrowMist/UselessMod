package com.sorrowmist.useless.content.menus;

import net.minecraft.world.inventory.ContainerData;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultiblockAlloyFurnaceMenuTest {
    @Test
    void clientAlwaysUsesAWritableSynchronizationBuffer() {
        int[] liveValues = {1, 9, 0, 0, 0, 0, 3, 2, 10, -1, Integer.MAX_VALUE};
        ContainerData readOnlyLiveData = readOnly(liveValues);

        ContainerData clientData = MultiblockAlloyFurnaceMenu.createMenuData(true, readOnlyLiveData);
        assertNotSame(readOnlyLiveData, clientData);
        for (int index = 0; index < liveValues.length; index++) {
            clientData.set(index, liveValues[index]);
            assertEquals(liveValues[index], clientData.get(index));
        }
    }

    @Test
    void serverKeepsTheCoreLiveDataAndLongValuesRoundTrip() {
        ContainerData liveData = readOnly(new int[11]);
        assertSame(liveData, MultiblockAlloyFurnaceMenu.createMenuData(false, liveData));

        long tierNineCapacity = 3_276_800_000L;
        int low = (int) tierNineCapacity;
        int high = (int) (tierNineCapacity >>> 32);
        assertEquals(tierNineCapacity, MultiblockAlloyFurnaceMenu.join(low, high));

        assertEquals(Long.MAX_VALUE, MultiblockAlloyFurnaceMenu.join(
                (int) Long.MAX_VALUE, (int) (Long.MAX_VALUE >>> 32)));
    }

    @Test
    void taskProgressSnapshotSurvivesSourceChangesAndCanBeCleared() {
        var task = new AETaskProgressPacket.TaskProgressData(
                "bound book", 1, 20, 1L, 1L);
        var source = new ArrayList<>(List.of(task));
        var snapshot = new MultiblockAlloyFurnaceMenu.TaskProgressSnapshot();

        snapshot.update(source);
        source.clear();
        assertEquals(List.of(task), snapshot.get());

        snapshot.update(List.of());
        assertEquals(List.of(), snapshot.get());
    }

    private static ContainerData readOnly(int[] values) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return values[index];
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return values.length;
            }
        };
    }
}
