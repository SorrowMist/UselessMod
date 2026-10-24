package com.sorrowmist.useless.compat.forge1201;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Optional source boundaries: no class linkage when the corresponding mod is absent. */
public final class OptionalIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    private OptionalIntegration() {}

    public static boolean hasClass(String className) {
        return OptionalIntegration.class.getClassLoader()
                .getResource(className.replace('.', '/') + ".class") != null;
    }

    public static <T> T call(String modId,String className,String method,Class<T> resultType,T absent,
            Class<?>[] signature,Object... arguments) {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded(modId)) return absent;
        try {
            Object result=Class.forName(className).getMethod(method,signature).invoke(null,arguments);
            return resultType==Void.class ? null : resultType.cast(result);
        } catch (InvocationTargetException e) {
            reportFailure(modId, className, method, e.getCause());
            return absent;
        } catch (ReflectiveOperationException | LinkageError e) {
            reportFailure(modId, className, method, e);
            return absent;
        } catch (ClassCastException e) {
            reportFailure(modId, className, method, e);
            return absent;
        }
    }

    /**
     * Optional integrations must not take the game down. A missing class is common
     * when this Forge build intentionally excludes a NeoForge-only adapter; report
     * the first occurrence once and let the caller continue with its fallback.
     */
    private static void reportFailure(String modId, String className, String method, Throwable cause) {
        String key = modId + "#" + className + "#" + method;
        if (REPORTED_FAILURES.add(key)) {
            boolean missing = cause instanceof ClassNotFoundException
                    || cause instanceof NoClassDefFoundError;
            if (missing) {
                // Build-profile exclusions are expected; keep them out of normal logs.
                LOGGER.debug("Optional integration {} is not present at {}#{}; skipping it",
                        modId, className, method);
                return;
            }
            if (cause == null) {
                LOGGER.warn("Optional integration {} is unavailable or failed at {}#{}; skipping it",
                        modId, className, method);
            } else {
                LOGGER.warn("Optional integration {} is unavailable or failed at {}#{}; skipping it",
                        modId, className, method, cause);
            }
        }
    }
}
