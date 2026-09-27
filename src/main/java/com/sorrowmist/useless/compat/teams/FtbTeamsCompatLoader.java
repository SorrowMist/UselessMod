package com.sorrowmist.useless.compat.teams;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link FtbTeamsCompat}。
 *
 * <p>与 {@code AeLogisticsCompatLoader} 同一套手法：常驻类只引用 {@link SharedOwnerBridge}
 * 这个不含 FTB Teams 类型的接口，实现类用 {@code Class.forName} 反射加载，从而在没装
 * FTB Teams 的整合包里也不会触发类解析错误。</p>
 *
 * <p>注意 FTB Teams 只是 {@code build.gradle} 里的 {@code implementation} 依赖，
 * <b>没有</b>写进 {@code neoforge.mods.toml}，所以运行时确实可能缺席——这个隔离是必需的。</p>
 */
public final class FtbTeamsCompatLoader {
    public static final String MOD_ID = "ftbteams";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.teams.FtbTeamsCompat";

    private static volatile boolean initialized;
    private static volatile SharedOwnerBridge bridge;

    private FtbTeamsCompatLoader() {
    }

    /** FTB Teams 桥；未加载该模组或初始化失败时为 {@code null}。 */
    @Nullable
    public static SharedOwnerBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, FtbTeamsCompatLoader.class.getClassLoader());
                    bridge = (SharedOwnerBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the FTB Teams bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }
}
