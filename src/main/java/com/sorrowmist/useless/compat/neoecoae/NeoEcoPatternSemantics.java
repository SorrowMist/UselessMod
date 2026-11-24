package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternPlanning;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;

final class NeoEcoPatternSemantics implements ECOPatternSemanticAdapter {
    @Override
    public boolean supports(IPatternDetails pattern) {
        return SmartDoublingPatterns.unwrap(pattern) instanceof DynamicComponentPattern;
    }

    @Override
    public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
        if (!base.supported()) return base;
        var dynamic = (DynamicComponentPattern) SmartDoublingPatterns.unwrap(pattern);
        boolean relaxedInput = false;
        int slots = pattern.getInputs().length;
        for (int slot = 0; slot < slots; slot++) {
            if (dynamic.isItemIdInput(slot) || dynamic.isTagInput(slot) || dynamic.isFluidTagInput(slot)) {
                relaxedInput = true;
                break;
            }
        }
        boolean dynamicOutput = dynamic.usesDynamicOutputs();
        return new ECOPatternSemantics(pattern, base.physicalDefinition(), base.consumedInputs(),
                base.producedOutputs(), base.returnedOutputs(), base.feedbackEdges(),
                relaxedInput || dynamicOutput ? ECOPatternSemantics.MatchingMode.SUBSTITUTION : base.matchingMode(),
                ECOPatternSemantics.ExecutionRestriction.NONE, true,
                base.cycleSafe() && !dynamicOutput && (!relaxedInput || base.returnedOutputs().isEmpty()), null);
    }

    @Override
    public boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return SmartDoublingPatterns.unwrap(pattern) instanceof DynamicComponentPattern dynamic
                && inputSlot >= 0 && inputSlot < pattern.getInputs().length && dynamic.isItemIdInput(inputSlot);
    }

    @Override
    public AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService craftingService) {
        if (!ignoresComponents(pattern, inputSlot) || pattern.getInputs()[inputSlot].getRemainingKey(encodedKey) != null) {
            return encodedKey;
        }
        return DynamicPatternPlanning.preferDeclaredCraftableInput(pattern, inputSlot, encodedKey, craftingService);
    }
}
