package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import com.sorrowmist.useless.compat.EapCompat;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Resolves execution wrappers while preserving their effective copy count. */
public final class DynamicPatternExecution {
    private static final int MAX_WRAPPER_DEPTH = 16;

    private DynamicPatternExecution() {
    }

    @Nullable
    public static Resolved resolve(IPatternDetails details) {
        Objects.requireNonNull(details, "details");
        IPatternDetails current = details;
        long copies = 1L;

        for (int depth = 0; depth < MAX_WRAPPER_DEPTH; depth++) {
            IPatternDetails unwrapped = EapCompat.unwrap(current);
            if (unwrapped == current) {
                break;
            }
            copies = saturatingMultiply(copies, EapCompat.getMultiplierLong(current));
            current = unwrapped;
        }

        return current instanceof DynamicComponentPattern dynamic
                ? new Resolved(dynamic, copies)
                : null;
    }

    private static long saturatingMultiply(long first, long second) {
        if (first <= 0 || second <= 0) {
            return 1L;
        }
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
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
