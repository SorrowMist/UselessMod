package com.sorrowmist.useless.compat.neoecoae.compact.ui;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState;

/** Supplies the ordinary ECO artwork with the compact host's built-in level-nine hardware. */
public final class CompactHostPanelState {
    private static final int LEVEL_NINE = 3;

    private CompactHostPanelState() {}

    public static CraftingControllerPanelState levelNine(CraftingControllerPanelState panel) {
        if (!panel.formed()) return panel;
        int tiers = hardwareTiers(panel.fxCells(), CraftingControllerPanelState.SLOT_COUNT);
        return new CraftingControllerPanelState(true, LEVEL_NINE, panel.fxCells(), tiers, tiers,
                panel.coolantOverlay(), panel.switchOverlay(), panel.queuePermille(), panel.energyPermille());
    }

    public static ComputationControllerPanelState levelNine(ComputationControllerPanelState panel) {
        if (!panel.formed()) return panel;
        int tiers = hardwareTiers(panel.cells(), ComputationControllerPanelState.SLOT_COUNT);
        return new ComputationControllerPanelState(true, LEVEL_NINE, panel.cells(), tiers, tiers, tiers);
    }

    private static int hardwareTiers(int cells, int slotCount) {
        int tiers = 0;
        for (int cell = 0; cell < slotCount; cell++) {
            if ((cells & (1 << cell)) != 0) tiers |= LEVEL_NINE << (cell * 2);
        }
        return tiers;
    }
}
