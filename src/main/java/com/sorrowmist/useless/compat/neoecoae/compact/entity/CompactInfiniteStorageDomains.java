package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Keeps native domain creation compatible with ECO's storage package move in 20.5.2. */
final class CompactInfiniteStorageDomains {
    private static final Method ACQUIRE;
    private static final Method RELEASE;
    private static final Method IS_HEALTHY;
    private static final Method COMMIT;
    private static final Method SUCCESSFUL;

    static {
        try {
            Class<?> engine = ECOStorageSystemBlockEntity.class.getDeclaredMethod("getInfiniteEngine").getReturnType();
            Class<?> domains = Class.forName(engine.getPackageName() + ".ECOInfiniteStorageDomains",
                    false, ECOStorageSystemBlockEntity.class.getClassLoader());
            ACQUIRE = domains.getMethod("acquire", ServerLevel.class, UUID.class);
            RELEASE = domains.getMethod("release", MinecraftServer.class, UUID.class);
            IS_HEALTHY = engine.getMethod("isHealthy");
            COMMIT = engine.getMethod("commit");
            SUCCESSFUL = COMMIT.getReturnType().getMethod("successful");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CompactInfiniteStorageDomains() {
    }

    static boolean create(ServerLevel level, UUID domainId) {
        try {
            Object engine = ACQUIRE.invoke(null, level, domainId);
            try {
                return (boolean) IS_HEALTHY.invoke(engine)
                        && (boolean) SUCCESSFUL.invoke(COMMIT.invoke(engine));
            } finally {
                RELEASE.invoke(null, level.getServer(), domainId);
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("ECO could not create compact L9 domain " + domainId, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ECO infinite storage API is inaccessible", e);
        }
    }
}
