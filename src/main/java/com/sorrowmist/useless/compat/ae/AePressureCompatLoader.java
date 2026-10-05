package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AePressureCompat}。
 *
 * <p>与 {@link AeEnergyCompatLoader} 同一套手法：常驻类只引用 {@link AePressureBridge} 这个
 * 不含 AE2 / Applied Pneumatics 类型的接口，实现类用 {@code Class.forName} 反射加载，
 * 从而在没装这些模组的整合包里也不会触发类解析错误。</p>
 *
 * <p>空气要进 ME 网络得靠 Applied Pneumatics（它把空气做成一个 AE 资源键），
 * 只有 AE2 是不够的，所以这里同时确认两个模组都在。</p>
 */
public final class AePressureCompatLoader {
    public static final String MOD_ID = "appliedpneumatics";
    private static final String REQUIRED_MOD_ID = "ae2";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ae.AePressureCompat";

    private static volatile boolean initialized;
    private static volatile AePressureBridge bridge;

    private AePressureCompatLoader() {
    }

    /** AE 气压桥；未加载 Applied Pneumatics 或初始化失败时为 {@code null}。 */
    @Nullable
    public static AePressureBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID) && ModList.get().isLoaded(REQUIRED_MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AePressureCompatLoader.class.getClassLoader());
                    bridge = (AePressureBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the Applied Pneumatics bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标的 AE 气压端点；未加载模组或不是端点时返回 {@code null}。 */
    @Nullable
    public static LongPressureHandler pressureEndpoint(Level level, BlockPos pos) {
        AePressureBridge resolved = bridge();
        return resolved == null ? null : resolved.pressureEndpoint(level, pos);
    }
}
