package com.sorrowmist.useless.compat.neoecoae.compact.ui;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompactHostPanelStateTest {
    @Test
    void craftingArtworkShowsL9CoresWithoutLosingRuntimeOverlays() {
        var original = new CraftingControllerPanelState(true, 3, (1 << 11) - 1,
                0, 0, true, true, 573, 821);

        var result = CompactHostPanelState.levelNine(original);

        assertEquals(original.fxCells(), result.fxCells());
        for (int cell = 0; cell < 11; cell++) {
            assertEquals(3, result.upperTier(cell));
            assertEquals(3, result.lowerTier(cell));
        }
        assertTrue(result.coolantOverlay());
        assertTrue(result.switchOverlay());
        assertEquals(573, result.queuePermille());
        assertEquals(821, result.energyPermille());
    }

    @Test
    void computationArtworkRestoresExplicitMembersAndPreservesMirroredCells() {
        var original = new ComputationControllerPanelState(true, 3, (1 << 9) | (1 << 10), 0, 0, 0);

        var result = CompactHostPanelState.levelNine(original);

        assertEquals(original.cells(), result.cells());
        assertEquals(3, result.hostTier());
        for (int cell = 0; cell < 11; cell++) {
            boolean present = cell >= 9;
            for (var member : new ComputationControllerPanelState.Member[]{
                    result.upperMember(cell), result.coreMember(cell), result.lowerMember(cell)}) {
                assertEquals(present ? 3 : 0, member.tier());
                assertEquals(present, member.present());
                assertNull(member.texture());
            }
        }
    }

    @Test
    void unformedPanelStaysUnformed() {
        assertSame(CraftingControllerPanelState.UNFORMED,
                CompactHostPanelState.levelNine(CraftingControllerPanelState.UNFORMED));
    }
}
