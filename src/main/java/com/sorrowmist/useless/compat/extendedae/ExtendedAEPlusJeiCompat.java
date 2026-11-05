package com.sorrowmist.useless.compat.extendedae;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;

public final class ExtendedAEPlusJeiCompat {
    private ExtendedAEPlusJeiCompat() {
    }
    public static void presetAlloyFurnaceSearchKey() {
        // ExtendedAE-Plus 1.6.4 起，供应器搜索关键字入口由 ExtendedAEPatternUploadUtil
        // 迁至 RecipeTypeNameConfig，方法名与语义保持不变。
        RecipeTypeNameConfig.setLastProcessingName("Omni Alloy Furnace");
    }
}
