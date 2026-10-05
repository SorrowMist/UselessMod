package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PressureBridge} 的气动工艺实现。
 *
 * <p><b>只有本类（以及它引用的 PneumaticCraft 类型）会在装了气动工艺时被加载</b>：常驻代码碰到的
 * 永远是 {@link PressureBridge} 那个不含模组类型的接口。</p>
 *
 * <h2>为什么气压端点要报体积</h2>
 *
 * <p>气动工艺的空气量满足 {@code air = pressure × volume}（见 {@code BasicAirHandler}）。
 * 也就是说「3 bar」对不同体积的方块意味着不同的空气量。无线物流的「目标气压」要伺服的是
 * <b>压力</b>而不是空气量，所以端点必须把 {@link IAirHandler#getVolume() 体积} 一起报出来，
 * 由调用方换算 {@code targetAir = targetBar × volume}。</p>
 *
 * <h2>真空（负压）</h2>
 *
 * <p>{@code BasicAirHandler.addAir} 的地板是 {@code -volume}，即 -1 bar 的绝对真空。因此
 * {@link #setAirTo} 允许把空气量调到负值；而 {@link #extract} 只抽到 0 bar 为止，不制造真空
 * ——它服务的是「把源端的气搬走」这条语义。</p>
 */
public final class PneumaticCraftPressureCompat implements PressureBridge {

    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    @Nullable
    public LongPressureHandler pressureEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        IAirHandlerMachine handler = handler(level, pos, side);
        return handler == null ? null : new AirEndpoint(handler);
    }

    /**
     * 取该坐标的空气处理器。
     *
     * <p>{@code side == null} 时按「不限面 → 6 个方向」的顺序探测，与
     * {@code StaffLinkTargets#capability} 同一把尺子（气动设备的空气处理器常常只挂在某几个面上）。</p>
     */
    @Nullable
    private static IAirHandlerMachine handler(Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        if (side != null) {
            return level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, side);
        }
        IAirHandlerMachine unspecified =
                level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, null);
        if (unspecified != null) {
            return unspecified;
        }
        for (Direction direction : DIRECTIONS) {
            IAirHandlerMachine found =
                    level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, direction);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 某个气动方块空气处理器的无线物流视角。 */
    private static final class AirEndpoint implements LongPressureHandler {
        private final IAirHandlerMachine handler;

        AirEndpoint(IAirHandlerMachine handler) {
            this.handler = handler;
        }

        @Override
        public long extract(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            int before = handler.getAir();
            if (before <= 0) {
                // 已经是 0 bar 或真空：没有正空气可搬，也不在搬运路径上制造真空。
                return 0L;
            }
            long take = Math.min(amount, before);
            if (!simulate) {
                handler.addAir((int) -take);
            }
            return take;
        }

        @Override
        public long receive(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            // 刻意不按 maxPressure 夹取：气动方块的 addAir 本身没有上限，超压由爆炸机制处理。
            // 真正的限值由调用方（目标气压伺服）算好再传进来。
            long take = Math.min(amount, Integer.MAX_VALUE);
            if (!simulate) {
                handler.addAir((int) take);
            }
            return take;
        }

        @Override
        public long setAirTo(long targetAir, boolean simulate) {
            int before = handler.getAir();
            long floor = -(long) handler.getVolume();   // -1 bar：硬真空
            long clamped = Math.max(targetAir, floor);
            long delta = clamped - before;
            if (delta == 0L) {
                return 0L;
            }
            if (!simulate) {
                handler.addAir((int) Math.max(Math.min(delta, Integer.MAX_VALUE), Integer.MIN_VALUE));
            }
            return delta;
        }

        @Override
        public long stored() {
            return handler.getAir();
        }

        @Override
        public long capacity() {
            return (long) ((double) handler.maxPressure() * (double) handler.getVolume());
        }

        @Override
        public long volume() {
            return handler.getVolume();
        }
    }
}
