package com.sorrowmist.useless.compat.modernindustrialization;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link MiEnergyCompat}。
 *
 * <p>与 {@code AeLogisticsCompatLoader} 同一套手法：常驻类只引用 {@link MiEnergyBridge} 这个
 * 不含 MI 类型的接口，实现类用 {@code Class.forName} 反射加载，从而在没装
 * Modern Industrialization 的整合包里也不会触发类解析错误。</p>
 */
public final class MiEnergyCompatLoader {
    public static final String MOD_ID = "modern_industrialization";

    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.modernindustrialization.MiEnergyCompat";

    private static volatile boolean initialized;
    private static volatile MiEnergyBridge bridge;

    private MiEnergyCompatLoader() {
    }

    /** MI 能量桥；未加载 MI 或初始化失败时为 {@code null}。 */
    @Nullable
    public static MiEnergyBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, MiEnergyCompatLoader.class.getClassLoader());
                    bridge = (MiEnergyBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the Modern Industrialization energy bridge",
                            exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标的 MI 能量端点（对外表现为 FE）；未加载 MI 或不是能量方块时返回 {@code null}。 */
    @Nullable
    public static LongEnergyHandler energyEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        MiEnergyBridge resolved = bridge();
        return resolved == null ? null : resolved.energyEndpoint(level, pos, side);
    }
}
