package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/** Selects a deterministic child output before AE falls back to fuzzy component matching. */
public final class DynamicPatternPlanning {
    private DynamicPatternPlanning() {
    }

    public static AEKey preferDeclaredCraftableInput(
            IPatternDetails pattern,
            int slot,
            AEKey encodedKey,
            ICraftingService craftingService) {
        if (!(pattern instanceof DynamicComponentPattern dynamic)
                || !dynamic.isItemIdInput(slot)
                || craftingService == null) {
            return encodedKey;
        }

        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (slot < 0 || slot >= inputs.length || inputs[slot] == null) {
            return encodedKey;
        }

        GenericStack[] candidates = inputs[slot].getPossibleInputs();
        if (candidates == null || candidates.length == 0 || candidates[0] == null) {
            return encodedKey;
        }

        long primaryAmount = candidates[0].amount();
        for (GenericStack candidate : candidates) {
            if (candidate == null || candidate.what() == null || candidate.amount() != primaryAmount) {
                continue;
            }
            if (!craftingService.getCraftingFor(candidate.what()).isEmpty()) {
                return candidate.what();
            }
        }
        return encodedKey;
    }
}
