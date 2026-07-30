package com.sorrowmist.useless.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FurnaceFaceModeTest {
    @Test
    void previousCyclesBackwardAndWraps() {
        assertEquals(FurnaceFaceMode.MOLD_INPUT, FurnaceFaceMode.DISABLED.previous());
        assertEquals(FurnaceFaceMode.DISABLED, FurnaceFaceMode.MATERIAL_INPUT.previous());

        for (FurnaceFaceMode mode : FurnaceFaceMode.values()) {
            assertEquals(mode, mode.next().previous());
        }
    }
}
