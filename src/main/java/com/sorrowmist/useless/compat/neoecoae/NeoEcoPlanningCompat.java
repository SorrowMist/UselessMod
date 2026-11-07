package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import net.minecraftforge.fml.ModList;

/** ECO 20.5.0 confirmation-scope guard; ordinary AE2 plans never acquire ECO ownership here. */
public final class NeoEcoPlanningCompat {
    private NeoEcoPlanningCompat() {
    }

    public static boolean shouldPreserveSubmissionPlan(ICraftingPlan plan) {
        if (plan == null || !ModList.get().isLoaded("neoecoae")) {
            return false;
        }
        try {
            return PlanningApi.shouldPreserveSubmissionPlan(plan);
        } catch (NoClassDefFoundError | NoSuchMethodError unavailable) {
            // Older ECO releases do not expose this API; keep their existing submission path.
            return false;
        }
    }

    private static final class PlanningApi {
        static boolean shouldPreserveSubmissionPlan(ICraftingPlan plan) {
            // The API checks the complete vector inside the synchronous confirmation scope.
            // ECO provenance or matching final outputs alone must not suppress other planners.
            return ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(plan);
        }
    }
}
