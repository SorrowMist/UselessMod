package com.sorrowmist.useless.content.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 恒温方块「主动驱动」外部热系统的钩子。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>Mekanism 的传热是<b>发送方驱动</b>：{@code ITileHeatHandler.simulateAdjacent()} 的公式只用
 * 「<b>自身</b>温度 − 环境温度」，<b>从不读邻居温度</b>。所以一个只暴露 {@code Capabilities.HEAT}
 * 的方块只会被 Mek 机器当成「通往 300K 的散热片」，永远加不热机器。</p>
 *
 * <p>要让恒温方块真正驱动 Mek 机器，必须由方块自己当发送方，每 tick 主动把热推给邻居。
 * 那段逻辑要用 {@code mekanism.api.heat.IHeatHandler}，属于可选集成，不能出现在主代码里，
 * 因此这里只留一个<b>不依赖任何外部类型</b>的钩子：compat 层在注册能力时安装实现，
 * 没装 Mekanism 时 {@link #driver} 为 {@code null}，{@link PlasticThermostatBlockEntity} 的
 * tick 就成了空操作。</p>
 */
public final class PlasticThermostatHeatHook {

    /** 驱动实现；由可选集成层安装，未装对应模组时为 {@code null}。 */
    @Nullable
    public static volatile HeatDriver driver;

    private PlasticThermostatHeatHook() {
    }

    /** 一次服务端 tick 的热驱动。 */
    @FunctionalInterface
    public interface HeatDriver {
        void tick(ServerLevel level, BlockPos pos, PlasticThermostatBlockEntity thermostat);
    }
}
