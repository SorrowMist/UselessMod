package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 按需加载 {@link PneumaticCraftPressureCompat}。
 *
 * <p>与 {@link com.sorrowmist.useless.compat.ae.AeEnergyCompatLoader} 同一套手法：常驻类只引用
 * {@link PressureBridge} 这个不含 PneumaticCraft 类型的接口，实现类用 {@code Class.forName}
 * 反射加载，从而在没装气动工艺的整合包里也不会触发类解析错误。</p>
 */
public final class PneumaticCraftPressureCompatLoader {
    public static final String MOD_ID = "pneumaticcraft";

    private static final String IMPLEMENTATION =
            "com.sorrowmist.useless.compat.pneumaticcraft.PneumaticCraftPressureCompat";

    private static volatile boolean initialized;
    private static volatile PressureBridge bridge;

    private PneumaticCraftPressureCompatLoader() {
    }

    /** 气压桥；未加载气动工艺或初始化失败时为 {@code null}。 */
    @Nullable
    public static PressureBridge bridge() {
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> implementation = Class.forName(
                            IMPLEMENTATION, true, PneumaticCraftPressureCompatLoader.class.getClassLoader());
                    bridge = (PressureBridge) implementation.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError exception) {
                    UselessMod.LOGGER.error("Failed to initialise the PneumaticCraft pressure bridge", exception);
                }
            }
        }
        return bridge;
    }

    public static boolean isAvailable() {
        return bridge() != null;
    }

    /** 该坐标的气压端点；未加载模组或不是气压方块时返回 {@code null}。 */
    @Nullable
    public static LongPressureHandler pressureEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        PressureBridge resolved = bridge();
        return resolved == null ? null : resolved.pressureEndpoint(level, pos, side);
    }
}
