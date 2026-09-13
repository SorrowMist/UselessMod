package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingOutputClaimResult;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import org.jetbrains.annotations.Nullable;

/** Routes dynamic item outputs through P16's atomic output-claim API. */
public final class NeoEcoDynamicOutputCompat {
    private NeoEcoDynamicOutputCompat() {
    }

    public static long claim(@Nullable Object grid, AEKey actualKey, long amount) {
        if (grid == null || !(actualKey instanceof AEItemKey itemKey) || amount <= 0L) {
            return 0L;
        }

        DynamicPatternCpuStateManager manager = DynamicPatternCpuStateManager.INSTANCE;
        DynamicPatternCpuStateManager.EcoCandidate match = null;
        long simulatedAmount = 0L;
        for (var candidate : manager.ecoCandidates(grid, itemKey.getId())) {
            if (!(candidate.cpu() instanceof ECOCraftingCPU cpu)) {
                continue;
            }
            ECOCraftingOutputClaimResult preview = cpu.getOutputClaimSink().claimCraftingOutput(
                    new ECOCraftingOutputClaimRequest(
                            candidate.craftingId(),
                            candidate.exactExpectedKey(),
                            actualKey,
                            amount,
                            Actionable.SIMULATE));
            if (!preview.accepted() || preview.claimedAmount() <= 0L) {
                continue;
            }
            if (match != null) {
                return 0L;
            }
            match = candidate;
            simulatedAmount = preview.claimedAmount();
        }
        if (match == null) {
            return 0L;
        }

        ECOCraftingCPU cpu = (ECOCraftingCPU) match.cpu();
        ECOCraftingOutputClaimResult committed = cpu.getOutputClaimSink().claimCraftingOutput(
                new ECOCraftingOutputClaimRequest(
                        match.craftingId(),
                        match.exactExpectedKey(),
                        actualKey,
                        simulatedAmount,
                        Actionable.MODULATE));
        if (!committed.accepted() || committed.claimedAmount() <= 0L) {
            return 0L;
        }
        DynamicPatternCpuStateManager.INSTANCE.claimEcoExact(
                match.craftingId(), match.exactExpectedKey(), committed.claimedAmount());
        return committed.claimedAmount();
    }
}
