package com.sorrowmist.useless.compat.ars;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link ArsSourceCompat}。
 *
 * <p>与 {@code MekanismCompatLoader} 同一套手法：常驻类只引用 {@link SourceBridge}
 * 这个不含 Ars 类型的接口，实现类用 {@code Class.forName} 反射加载，从而在没装
 * Ars Nouveau 的整合包里也不会触发类解析错误。</p>
 */
public final class ArsSourceCompatLoader {
    public static final String MOD_ID = "ars_nouveau";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ars.ArsSourceCompat";

    private static volatile boolean initialized;
    private static volatile SourceBridge bridge;

    private ArsSourceCompatLoader() {
    }

    /** 魔源搬运桥；未加载 Ars Nouveau 或初始化失败时为 {@code null}。 */
    @Nullable
    public static SourceBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, ArsSourceCompatLoader.class.getClassLoader());
                    bridge = (SourceBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the Ars Nouveau source bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }
}
