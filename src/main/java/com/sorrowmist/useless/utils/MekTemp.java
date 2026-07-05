package com.sorrowmist.useless.utils;

import java.util.function.BiConsumer;

public class MekTemp {
    public static String name;
    public static final int MAX_EXTRA_OPERATIONS_PER_TICK = 128;
    public static final ThreadLocal<Boolean> isInjecting = ThreadLocal.withInitial(() -> false);
    public static final BiConsumer<Integer, Runnable>
            inject = (reqTime, process) -> {
        if (!isInjecting.get()) {
            isInjecting.set(true);

            try {
                int extraOperations = Math.min(-reqTime, MAX_EXTRA_OPERATIONS_PER_TICK);
                for(int i = 0; i < extraOperations; ++i) {
                    process.run();
                }
            } finally {
                isInjecting.set(false);
            }
        }
    };
}
