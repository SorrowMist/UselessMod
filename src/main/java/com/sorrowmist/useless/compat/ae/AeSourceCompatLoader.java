package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.stafflink.SourceHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AeSourceCompat}。
 *
 * <p>需要 AE2 <b>与</b> Ars Énergistique 同时在场：前者提供 ME 存储，后者提供
 * {@code SourceKey} 这个「魔源在 ME 网络里的身份」。</p>
 */
public final class AeSourceCompatLoader {
    private static final String AE2 = "ae2";
    private static final String ARS_ENERGISTIQUE = "arseng";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ae.AeSourceCompat";

    private static volatile boolean initialized;
    private static volatile AeSourceBridge bridge;

    private AeSourceCompatLoader() {
    }

    @Nullable
    public static AeSourceBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(AE2) && ModList.get().isLoaded(ARS_ENERGISTIQUE)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AeSourceCompatLoader.class.getClassLoader());
                    bridge = (AeSourceBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the AE source bridge", exception);
                }
            }
        }
        return bridge;
    }

    /** 该坐标的 AE 网络端点（魔源）；不可用时返回 {@code null}。 */
    @Nullable
    public static SourceHandlerView sourceEndpoint(Level level, BlockPos pos) {
        AeSourceBridge resolved = bridge();
        return resolved == null ? null : resolved.sourceEndpoint(level, pos);
    }
}
