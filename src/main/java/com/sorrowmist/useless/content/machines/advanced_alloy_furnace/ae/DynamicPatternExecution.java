package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Resolves execution wrappers while preserving their effective copy count. */
public final class DynamicPatternExecution {
    private DynamicPatternExecution() {
    }

    @Nullable
    public static Resolved resolve(IPatternDetails details) {
        Objects.requireNonNull(details, "details");
        SmartDoublingPatterns.Resolved resolved = SmartDoublingPatterns.resolve(details);
        IPatternDetails current = resolved.pattern();
        long copies = resolved.operationsPerPush();

        return current instanceof DynamicComponentPattern dynamic
                ? new Resolved(dynamic, copies)
                : null;
    }

    public record Resolved(DynamicComponentPattern pattern, long copies) {
        public Resolved {
            Objects.requireNonNull(pattern, "pattern");
            if (copies <= 0) {
                throw new IllegalArgumentException("copies must be positive");
            }
        }
    }
}
