package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/** Captures the strict AE2 match before the local dynamic claim is attempted. */
public final class DynamicPatternInsertContext {
    private final AEKey key;
    private final long requestedAmount;
    private final Actionable actionable;
    private long strictMatched;

    public DynamicPatternInsertContext(AEKey key, long requestedAmount, Actionable actionable) {
        this.key = key;
        this.requestedAmount = requestedAmount;
        this.actionable = actionable;
    }

    public long requestedAmount() {
        return requestedAmount;
    }

    public Actionable actionable() {
        return actionable;
    }

    public long strictMatched() {
        return strictMatched;
    }

    public void setStrictMatched(long strictMatched) {
        this.strictMatched = strictMatched;
    }
}
