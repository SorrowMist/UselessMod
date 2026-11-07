package com.sorrowmist.useless.compat.eaep;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import net.minecraftforge.fml.ModList;

/** Client-side search bridge verified against ExtendedAE-Plus 1.6.4 for Forge 1.20.1. */
public final class ExtendedAEPlusJeiCompat {
    public static final String ALLOY_FURNACE_SEARCH_KEY = "Omni Alloy Furnace";

    private ExtendedAEPlusJeiCompat() {
    }

    /** Call only for a committed, valid alloy-furnace JEI transfer, before AE2 encodes its slots. */
    public static boolean presetAlloyFurnaceSearchKey() {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return false;
        }
        try {
            SearchApi.preset();
            return true;
        } catch (NoClassDefFoundError | NoSuchMethodError unavailable) {
            // Older EAEP versions have a different API. Never break ordinary AE2 transfer.
            return false;
        }
    }

    private static final class SearchApi {
        static void preset() {
            // 1.6.4 removed ExtendedAEPatternUploadUtil. This entry point also respects
            // the user's aliases, and EAEP consumes/clears the preset after an upload.
            RecipeTypeNameConfig.setLastProcessingName(ALLOY_FURNACE_SEARCH_KEY);
        }
    }
}
