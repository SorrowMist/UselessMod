package com.sorrowmist.useless.compat.extendedae;

import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;

public final class ExtendedAEPlusJeiCompat {
    private ExtendedAEPlusJeiCompat() {
    }
    public static void presetAlloyFurnaceSearchKey() {
        ExtendedAEPatternUploadUtil.setLastProcessingName("Omni Alloy Furnace");
    }
}
