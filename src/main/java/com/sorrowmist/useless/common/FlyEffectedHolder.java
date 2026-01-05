package com.sorrowmist.useless.common;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyEffectedHolder {
    private static final ThreadLocal<Set<UUID>> UUID_HOLDER =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    private FlyEffectedHolder() {} // 防止实例化

    public static boolean add(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return UUID_HOLDER.get().add(uuid);
    }

    public static boolean contains(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return UUID_HOLDER.get().contains(uuid);
    }

    public static UUID get(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return contains(uuid) ? uuid : null;
    }

    public static boolean remove(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return UUID_HOLDER.get().remove(uuid);
    }

    public static int size() {
        return UUID_HOLDER.get().size();
    }

    public static boolean isEmpty() {
        return UUID_HOLDER.get().isEmpty();
    }
}
