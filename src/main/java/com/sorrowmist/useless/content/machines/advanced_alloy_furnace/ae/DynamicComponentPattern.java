package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;

/** Marker and policy access used by the local CPU integration. */
public interface DynamicComponentPattern extends IPatternDetails {
    String dynamicPatternIdentity();

    boolean isItemIdInput(int slot);

    boolean isItemIdOutput(int slot);

    boolean usesDynamicOutputs();
}
