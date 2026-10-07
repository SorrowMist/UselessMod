package com.sorrowmist.useless.compat.ars;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * 按需加载 {@link ArsCreatureTimeCompat}。
 *
 * <p>与 {@link ArsSourceCompatLoader} 同一套手法：常驻类只引用 {@link ArsCreatureTimeBridge}
 * 这个不含 Ars 类型的接口，实现类用 {@code Class.forName} 反射加载，从而在没装
 * Ars Nouveau 的整合包里也不会触发类解析错误。</p>
 */
public final class ArsCreatureTimeCompatLoader {
    public static final String MOD_ID = "ars_nouveau";

    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.ars.ArsCreatureTimeCompat";

    private static volatile boolean initialized;
    private static volatile ArsCreatureTimeBridge bridge;

    private ArsCreatureTimeCompatLoader() {
    }

    /** 生物加速桥；未加载 Ars Nouveau 或初始化失败时为 {@code null}。 */
    private static ArsCreatureTimeBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, ArsCreatureTimeCompatLoader.class.getClassLoader());
                    bridge = (ArsCreatureTimeBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the Ars Nouveau creature time bridge", exception);
                }
            }
        }
        return bridge;
    }

    /**
     * 让 Ars Nouveau 的工作生物也吃到生物加速；未加载该模组时是空操作。
     *
     * <p>本方法每 tick 每只被加速的生物都会调用一次，所以除了 {@code bridge()} 里的
     * {@code volatile} 读之外不做任何额外工作。</p>
     */
    public static void accelerate(LivingEntity target, int extra) {
        ArsCreatureTimeBridge resolved = bridge();
        if (resolved == null) {
            return;
        }
        try {
            resolved.accelerate(target, extra);
        } catch (LinkageError error) {
            // 实现类里的字段引用是在首次执行时链接的，Class.forName 的 try/catch 覆盖不到。
            // 一旦 Ars 换了字段名（版本错配），这里会抛 NoSuchFieldError —— 每 tick 每只生物抛一次
            // 会刷爆日志，所以记一次就永久停用桥。
            bridge = null;
            UselessMod.LOGGER.error(
                    "Disabling the Ars Nouveau creature time bridge after a linkage failure", error);
        }
    }
}
