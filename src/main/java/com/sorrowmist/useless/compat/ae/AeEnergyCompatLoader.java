package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link AeEnergyCompat}。
 *
 * <p>与 {@link AeLogisticsCompatLoader} 同一套手法：常驻类只引用 {@link AeEnergyBridge} 这个
 * 不含 AE2 / AppliedFlux 类型的接口，实现类用 {@code Class.forName} 反射加载，从而在没装
 * AppliedFlux 的整合包里也不会触发类解析错误。</p>
 *
 * <p>「AE 能量」比其它 AE 资源多一道门槛：FE 要进 ME 网络得靠 AppliedFlux 的通量元件，
 * 只有 AE2 是不够的，所以这里同时确认两个模组都在。</p>
 */
public final class AeEnergyCompatLoader {
    public static final String MOD_ID = "appflux";
    private static final String REQUIRED_MOD_ID = "ae2";

    private static final String IMPLEMENTATION = "com.sorrowmist.useless.compat.ae.AeEnergyCompat";

    private static volatile boolean initialized;
    private static volatile AeEnergyBridge bridge;

    private AeEnergyCompatLoader() {
    }

    /** AE 能量桥；未加载 AppliedFlux 或初始化失败时为 {@code null}。 */
    @Nullable
    public static AeEnergyBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID) && ModList.get().isLoaded(REQUIRED_MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, AeEnergyCompatLoader.class.getClassLoader());
                    bridge = (AeEnergyBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the AppliedFlux energy bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标的 AE 能量端点；未加载模组或不是端点时返回 {@code null}。 */
    @Nullable
    public static LongEnergyHandler energyEndpoint(Level level, BlockPos pos) {
        AeEnergyBridge resolved = bridge();
        return resolved == null ? null : resolved.energyEndpoint(level, pos);
    }
}
