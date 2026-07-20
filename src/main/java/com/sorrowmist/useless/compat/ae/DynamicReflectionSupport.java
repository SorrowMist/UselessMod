package com.sorrowmist.useless.compat.ae;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Null-safe reflection helpers for optional CPU implementations. */
public final class DynamicReflectionSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_FAILURES =
            Collections.synchronizedSet(new HashSet<>());

    private DynamicReflectionSupport() {
    }

    @Nullable
    public static Class<?> findClassSafe(String name) {
        try {
            return Class.forName(name);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    public static Field findFieldSafe(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Method findMethodSafe(@Nullable Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Object get(@Nullable Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            logOnce("read field", exception);
            return null;
        }
    }

    public static long getLong(@Nullable Field field, Object target, long fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            return field.getLong(target);
        } catch (ReflectiveOperationException exception) {
            logOnce("read long field", exception);
            return fallback;
        }
    }

    public static void setLong(@Nullable Field field, Object target, long value, String action) {
        if (field == null) {
            return;
        }
        try {
            field.setLong(target, value);
        } catch (ReflectiveOperationException exception) {
            logOnce(action, exception);
        }
    }

    @Nullable
    public static Object invoke(@Nullable Method method, Object target, String action, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            logOnce(action, exception);
            return null;
        }
    }

    private static void logOnce(String action, Throwable exception) {
        if (LOGGED_FAILURES.add(action)) {
            LOGGER.warn("Useless Mod optional CPU reflection failed while trying to {}.", action, exception);
        }
    }
}
