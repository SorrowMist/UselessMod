package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.content.blockentities.ThermostatHeatHook;
import com.sorrowmist.useless.init.ModBlockEntities;
import mekanism.api.heat.IHeatHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 温度调节器方块的 Mekanism 热兼容。
 *
 * <p><b>只引用纯 API（{@code mekanism.api.heat.IHeatHandler}），不碰任何 Mekanism internal 类。</b>
 * 能力对象自己声明：{@code mekanism:heat_handler} 这个 ResourceLocation 与 Mek 自己的
 * {@code Capabilities.HEAT} 相同，{@code BlockCapability.createSided} 对同一 RL 返回同一个对象
 * （气动工艺的 {@code MekanismIntegration} 就是这么做的），所以不必引用 {@code common} 包。</p>
 *
 * <h2>为什么必须「主动驱动」</h2>
 *
 * <p>Mek 的传热是<b>发送方驱动</b>：{@code ITileHeatHandler.simulateAdjacent()} 的公式是
 * {@code (自身温度 − 环境温度) / 导热系数}，<b>从不读邻居温度</b>（读邻居温度的正确分支被作者
 * 注释掉了）。所以一个只暴露 {@code Capabilities.HEAT} 的方块只会被 Mek 机器当成「通往 300K
 * 的散热片」，<b>加不热机器</b>。要让恒温方块真正驱动机器，必须由方块自己当发送方，每 tick 用
 * {@link IHeatHandler#handleHeat(double)} 把热推给邻居——这段就在 {@link #drive} 里。</p>
 */
public final class ThermostatHeatCompat {

    /** 与 Mek {@code Capabilities.HEAT} 同 RL ⇒ 同一个能力对象。 */
    private static final BlockCapability<IHeatHandler, Direction> HEAT = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "heat_handler"), IHeatHandler.class);

    private static final String CACHE_KEY = "mekanism";

    /**
     * 我方导热系数，取 Mek 的 {@code AIR_INVERSE_COEFFICIENT} 同值（10000）。
     *
     * <p>故意「绝缘」：机器按 {@code (自身温度 − 300K) / (我们的系数 + 它的系数)} 向我们排热，
     * 系数取大值等于把这条通道掐掉，稳态就不会被拉到「300K 与设定值的中点」。</p>
     */
    private static final double INSULATION_INVERSE_CONDUCTION = 10_000.0D;

    /** 驱动步长：每 tick 走完温差的一半，收敛快且绝不超调。 */
    private static final double DRIVE_STEP = 0.5D;

    /** 低于这个温差就不再动作，省掉无意义的浮点运算。 */
    private static final double DRIVE_EPSILON = 0.001D;

    private ThermostatHeatCompat() {
    }

    /** 关掉恒温时对外报告的温度：环境温度，与 Mek 的 {@code HeatAPI.AMBIENT_TEMP} 一致。 */
    private static final double AMBIENT_TEMPERATURE = 300.0D;

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // ⚠️ 刻意<b>不</b>按 enabled 返回 null。
        //
        // 能力在「null ↔ 非 null」之间切换时，NeoForge 的缓存能失效，但第三方缓存不一定——
        // Mek 的 CapabilityCache、气动的 IdentityHashMap 都只在方块变化时重新发现。
        // 表现就是「开了恒温、机器却毫无反应」。所以**始终交出适配器**，由适配器按 enabled
        // 决定行为：关着的时候报告 300K，配合下面的大导热系数，几乎不参与任何交换。
        event.registerBlockEntity(HEAT, ModBlockEntities.TEMPERATURE_REGULATOR.get(),
                (blockEntity, side) -> adapter(blockEntity));
        // 安装「主动驱动」；未装 Mekanism 时这个钩子保持 null，主代码的 tick 就是空操作。
        ThermostatHeatHook.driver = ThermostatHeatCompat::drive;
    }

    private static IHeatHandler adapter(ThermostatBlockEntity thermostat) {
        Object cached = thermostat.getHeatAdapter(CACHE_KEY);
        if (cached instanceof Adapter adapter) {
            return adapter;
        }
        Adapter created = new Adapter(thermostat);
        thermostat.putHeatAdapter(CACHE_KEY, created);
        return created;
    }

    /**
     * 主动把热推给相邻的热处理器（Mek 机器、热导体等）。
     *
     * <p>转移量 = 温差 × 邻居热容 × 半步系数：热量单位是焦耳，邻居升温
     * {@code ΔT = transfer / 热容}，所以乘上热容正好把邻居推向我们的温度，乘半步则不会超调。</p>
     *
     * <p><b>必须先判热容 &gt; 0</b>：Mek 的 {@code getTotalHeatCapacity()} 在「没有电容」时返回 0，
     * 拿它当乘数虽不会除零，但会传 0 热量；而真正危险的是拿它当除数（会得到 NaN）。</p>
     */
    private static void drive(ServerLevel level, BlockPos pos, ThermostatBlockEntity thermostat) {
        double temperature = thermostat.getTemperature();
        for (Direction side : Direction.values()) {
            IHeatHandler sink = level.getCapability(HEAT, pos.relative(side), side.getOpposite());
            if (sink == null) {
                continue;
            }
            double capacity = sink.getTotalHeatCapacity();
            if (!(capacity > 0.0D)) {
                continue;
            }
            double delta = temperature - sink.getTotalTemperature();
            if (Math.abs(delta) < DRIVE_EPSILON) {
                continue;
            }
            sink.handleHeat(delta * capacity * DRIVE_STEP);
        }
    }

    /** 恒温方块的 Mek 热处理器视角：温度恒定，收下 / 给出多少热都不变。 */
    private static final class Adapter implements IHeatHandler {
        private final ThermostatBlockEntity thermostat;

        Adapter(ThermostatBlockEntity thermostat) {
            this.thermostat = thermostat;
        }

        @Override
        public int getHeatCapacitorCount() {
            return 1;
        }

        @Override
        public double getTemperature(int capacitor) {
            return thermostat.isEnabled() ? thermostat.getTemperature() : AMBIENT_TEMPERATURE;
        }

        @Override
        public double getInverseConduction(int capacitor) {
            return INSULATION_INVERSE_CONDUCTION;
        }

        @Override
        public double getHeatCapacity(int capacitor) {
            return 1.0D;
        }

        @Override
        public void handleHeat(int capacitor, double transfer) {
            // 无限恒温源：不改变自身温度。
        }
    }
}
