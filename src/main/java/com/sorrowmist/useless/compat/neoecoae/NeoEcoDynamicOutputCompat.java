package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import org.jetbrains.annotations.Nullable;

/** Routes native-NBT item outputs through ECO 20.5.0's atomic output-claim API. */
public final class NeoEcoDynamicOutputCompat {
    private NeoEcoDynamicOutputCompat() {
    }

    // Keep IGrid in this signature: GridOutputTarget resolves it with getMethod(IGrid, AEKey, long).
    public static long claim(@Nullable IGrid grid, AEKey actualKey, long amount) {
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
                            Math.min(amount, candidate.remainingAmount()),
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
        manager.claimEcoExact(
                match.craftingId(), match.exactExpectedKey(), committed.claimedAmount());
        // Persist partial claims too; the lifecycle only marks a finished job dirty itself.
        cpu.markDirty();
        return committed.claimedAmount();
    }
}
