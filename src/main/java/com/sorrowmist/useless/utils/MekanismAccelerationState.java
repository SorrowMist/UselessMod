package com.sorrowmist.useless.utils;

import net.minecraftforge.fml.ModList;

import java.util.function.BiConsumer;

public class MekanismAccelerationState {
    /** Set by UpgradeMixin while Mekanism's Upgrade enum is being constructed. */
    public static String name;
    public static final int MAX_EXTRA_OPERATIONS_PER_TICK = 128;
    public static final ThreadLocal<Boolean> isInjecting = ThreadLocal.withInitial(() -> false);
    private static Boolean rebornLoaded;

    /**
     * Mekanism Upgrades: Reborn installs equivalent extra-operation callbacks.
     * Running both would process the same machine twice per tick, so only one
     * implementation may own the acceleration loop.
     */
    public static boolean rebornOwnsAcceleration() {
        Boolean cached = rebornLoaded;
        if (cached == null) {
            try {
                if (ModList.get() == null) {
                    return false;
                }
                cached = ModList.get().isLoaded("mekanismupgradesreborn");
            } catch (Throwable ignored) {
                cached = false;
            }
            rebornLoaded = cached;
        }
        return cached;
    }

    public static final BiConsumer<Integer, Runnable>
            inject = (reqTime, process) -> {
        if (!rebornOwnsAcceleration() && !isInjecting.get()) {
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
