package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.init.ModBlockEntities;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.function.BiPredicate;

/**
 * 温度调节器方块的气动工艺热兼容。
 *
 * <p>气动工艺的热交换是<b>接收方驱动</b>：机器自己的 {@code HeatExchangerLogicTicking.tick()} 会遍历
 * 相连的热交换器并调用 {@code addHeat}，所以只要把 {@link IHeatExchangerLogic} 通过
 * {@code PNCCapabilities.HEAT_EXCHANGER_BLOCK} 暴露出去，机器就会自动向我们收敛——不需要我们 tick。</p>
 *
 * <p><b>注意：气动工艺要求方块有 BlockEntity 才看得到热能力。</b>
 * {@code HeatExchangerManager.getLogic} 走的是 {@code IOHelper.getCap(te, ...)}，而它在
 * {@code te == null} 时直接返回 empty。塑料方块因此加了 BE。</p>
 *
 * <p>实现照抄气动自带的 {@code HeatExchangerLogicConstant}（岩浆 / 冰用的那种）：温度恒定、
 * {@code addHeat} 空操作，于是它就是一个「无限热源 / 热汇」。</p>
 */
public final class ThermostatHeatCompat {

    private static final String CACHE_KEY = "pneumaticcraft";
    private static final double THERMAL_CAPACITY = 1000.0D;
    /** 开启时的热阻：与气动自带方块同量级，交换很快。 */
    private static final double ACTIVE_THERMAL_RESISTANCE = 1.0D;
    /**
     * 关闭时的热阻：取得很大，让交换量趋近于 0。
     *
     * <p>气动的 {@code exchange()} 是 {@code deltaTemp /= (对方热阻 + 我方热阻)}，热阻取 10000
     * 就等于把这条通路掐掉——这样「始终暴露能力」才不会让所有塑料方块都变成 300K 的热汇。</p>
     */
    private static final double IDLE_THERMAL_RESISTANCE = 10_000.0D;
    /** 关掉恒温时对外报告的温度：环境温度。 */
    private static final double AMBIENT_TEMPERATURE = 300.0D;

    private ThermostatHeatCompat() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // ⚠️ 刻意<b>不</b>按 enabled 返回 null：气动的机器只在 onLoad / neighborChanged 时
        // 重新扫一遍热连接，开了恒温之后它不会重新发现我们，表现就是「设置不生效」。
        // 始终交出逻辑，由逻辑按 enabled 决定温度与热阻。
        event.registerBlockEntity(PNCCapabilities.HEAT_EXCHANGER_BLOCK,
                ModBlockEntities.TEMPERATURE_REGULATOR.get(),
                (blockEntity, side) -> adapter(blockEntity));
    }

    private static IHeatExchangerLogic adapter(ThermostatBlockEntity thermostat) {
        Object cached = thermostat.getHeatAdapter(CACHE_KEY);
        if (cached instanceof Logic logic) {
            return logic;
        }
        Logic created = new Logic(thermostat);
        thermostat.putHeatAdapter(CACHE_KEY, created);
        return created;
    }

    /** 恒温式热交换逻辑，语义同气动自带的 {@code HeatExchangerLogicConstant}。 */
    private static final class Logic implements IHeatExchangerLogic {
        private final ThermostatBlockEntity thermostat;

        Logic(ThermostatBlockEntity thermostat) {
            this.thermostat = thermostat;
        }

        @Override
        public void tick() {
            // 恒温源不需要自 tick：邻居机器的 exchange() 会主动与我们交换。
        }

        @Override
        public void initializeAsHull(Level level, BlockPos pos,
                                     BiPredicate<LevelAccessor, BlockPos> blockFilter, Direction... validSides) {
            // 同上：连接由邻居侧建立。
        }

        @Override
        public void initializeAmbientTemperature(Level level, BlockPos pos) {
        }

        @Override
        public void setTemperature(double temperature) {
            // 恒温源：外部不能改我们的温度（设定只通过权杖界面）。
        }

        @Override
        public double getTemperature() {
            return thermostat.isEnabled() ? thermostat.getTemperature() : AMBIENT_TEMPERATURE;
        }

        @Override
        public int getTemperatureAsInt() {
            return (int) getTemperature();
        }

        @Override
        public double getAmbientTemperature() {
            return getTemperature();
        }

        @Override
        public void setThermalResistance(double thermalResistance) {
        }

        @Override
        public double getThermalResistance() {
            return thermostat.isEnabled() ? ACTIVE_THERMAL_RESISTANCE : IDLE_THERMAL_RESISTANCE;
        }

        @Override
        public void setThermalCapacity(double capacity) {
        }

        @Override
        public double getThermalCapacity() {
            return THERMAL_CAPACITY;
        }

        @Override
        public void addHeat(double amount) {
            // 无限热容：收下 / 给出多少热都不改变自身温度。
        }

        @Override
        public boolean isSideConnected(Direction side) {
            return true;
        }
    }
}
