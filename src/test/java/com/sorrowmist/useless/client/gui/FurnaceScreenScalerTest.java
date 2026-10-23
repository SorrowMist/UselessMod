package com.sorrowmist.useless.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceScreenScalerTest {
    @Test void smallWindowFitsEveryFurnacePanelAndLargeWindowPreservesPreference() {
        for (int panel : new int[]{178, 222, 260, 306, 336}) {
            double scale = FurnaceScreenScaler.scaleForWindow(2.0D, 480, panel);
            assertTrue((panel + 16) * scale <= 480);
            assertTrue(scale >= 1.0D && scale <= 2.0D);
        }
        assertEquals(3.0D, FurnaceScreenScaler.scaleForWindow(3.0D, 2160, 336));
        assertEquals(1.0D, FurnaceScreenScaler.scaleForWindow(4.0D, 200, 336));
    }
}
