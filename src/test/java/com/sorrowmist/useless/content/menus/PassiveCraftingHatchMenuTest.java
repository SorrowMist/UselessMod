package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import net.minecraft.world.inventory.ContainerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class PassiveCraftingHatchMenuTest {
    @Test
    void clientUsesWritableDataInsteadOfTheBlockEntityReadOnlyView() {
        ContainerData readOnly = readOnlyData();
        ContainerData client = PassiveCraftingHatchMenu.createMenuData(true, readOnly);

        assertNotSame(readOnly, client);
        for (int index = 0; index < PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT; index++) {
            client.set(index, index * 17 + 1);
            assertEquals(index * 17 + 1, client.get(index));
        }
        assertSame(readOnly, PassiveCraftingHatchMenu.createMenuData(false, readOnly));
    }

    private static ContainerData readOnlyData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT;
            }
        };
    }
}
