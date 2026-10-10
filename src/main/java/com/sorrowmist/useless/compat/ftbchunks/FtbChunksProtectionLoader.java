package com.sorrowmist.useless.compat.ftbchunks;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link FtbChunksProtection}。
 *
 * <p>常驻类只引用 {@link BlockEditGuard} 这个不含 FTB Chunks 类型的接口，实现类用
 * {@code Class.forName} 反射加载，因此未安装 FTB Chunks 的整合包里加载本类不会
 * 触发外部类型解析。</p>
 */
public final class FtbChunksProtectionLoader {
    public static final String MOD_ID = "ftbchunks";

    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.ftbchunks.FtbChunksProtection";

    private static volatile boolean initialized;
    private static volatile BlockEditGuard guard;

    private FtbChunksProtectionLoader() {
    }

    /** FTB Chunks 方块编辑保护桥；未加载该模组或初始化失败时为 {@code null}。 */
    @Nullable
    public static BlockEditGuard guard() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, FtbChunksProtectionLoader.class.getClassLoader());
                    guard = (BlockEditGuard) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the FTB Chunks protection bridge", exception);
                }
            }
        }
        return guard;
    }
}