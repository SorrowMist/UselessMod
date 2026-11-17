package com.sorrowmist.useless.content.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 恒温方块与可选热系统之间的驱动分发器。
 *
 * <p>兼容层在确认对应模组已加载后注册驱动。此类不引用外部模组类型；没有可用驱动时，
 * {@link #tick} 不执行任何操作。</p>
 */
public final class ThermostatHeatHook {

    private static final Map<String, HeatDriver> DRIVERS = new ConcurrentHashMap<>(2);

    private ThermostatHeatHook() {
    }

    /** 注册或替换指定兼容项的热驱动。 */
    public static void registerDriver(String compatibilityId, HeatDriver driver) {
        DRIVERS.put(compatibilityId, driver);
    }

    /** 移除指定兼容项的热驱动。 */
    public static void removeDriver(String compatibilityId) {
        DRIVERS.remove(compatibilityId);
    }

    /** 分发一次服务端 tick 的热驱动。 */
    public static void tick(ServerLevel level, BlockPos pos, ThermostatBlockEntity thermostat) {
        for (HeatDriver driver : DRIVERS.values()) {
            driver.tick(level, pos, thermostat);
        }
    }

    @FunctionalInterface
    public interface HeatDriver {
        void tick(ServerLevel level, BlockPos pos, ThermostatBlockEntity thermostat);
    }
}
