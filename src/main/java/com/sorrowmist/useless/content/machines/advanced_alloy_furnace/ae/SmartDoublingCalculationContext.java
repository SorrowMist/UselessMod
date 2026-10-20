package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.networking.IGrid;

/** Internal bridge exposing the grid retained by an AE crafting calculation. */
public interface SmartDoublingCalculationContext {
    IGrid uselessMod$getCraftingGrid();
}
