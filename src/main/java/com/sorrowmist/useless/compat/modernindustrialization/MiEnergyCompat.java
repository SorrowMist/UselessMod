package com.sorrowmist.useless.compat.modernindustrialization;

import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.api.energy.MIEnergyStorage;
import aztech.modern_industrialization.config.MIServerConfig;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@link MiEnergyBridge} 的 Modern Industrialization 实现。
 *
 * <p><b>只有本类（以及它引用的 MI / GrandPower 类）会在装了 MI 时被加载</b>：常驻代码碰到的
 * 永远是 {@link MiEnergyBridge} 那个不含模组类型的接口。</p>
 *
 * <h2>为什么不能靠 NeoForge 的 FE 能力</h2>
 *
 * <p>MI 的机器只注册自己的 {@link EnergyApi#SIDED}（单位是 EU）。MI 确实也有一份「FE 视角」的
 * 通用能力（{@code ILongEnergyStorage.BLOCK}），但它挂在 MI 的启动配置
 * {@code bidirectionalEnergyCompat} 下，<b>默认是关的</b>——指望它等于要求玩家改配置。
 * 所以这里直接取 {@code SIDED}，换算由我们自己完成。</p>
 *
 * <h2>电压</h2>
 *
 * <p>MI 的电压门槛是 {@link MIEnergyStorage#canConnect}（拿仓的等级去比）。这里<b>一次都不调</b>，
 * 等于无视电压等级。要注意的是：MI 机器每次实际能收多少仍由它自己的容量与实现决定，
 * 那部分绕不过去，也不该绕——超出的量会退回源，下一 tick 继续。</p>
 *
 * <h2>换算比例</h2>
 *
 * <p>1 EU 值多少 FE <b>跟随 MI 自己的配置</b> {@code forgeEnergyPerEu}（默认 10），
 * 而不是写死 4：整合包里通常还有别的 FE↔EU 桥，跟 MI 保持一致才不会凭空造电或凭空吃电。</p>
 */
public final class MiEnergyCompat implements MiEnergyBridge {
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    @Nullable
    public LongEnergyHandler energyEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        MIEnergyStorage storage = lookup(level, pos, side);
        return storage == null ? null : new EuEndpoint(storage);
    }

    /**
     * 取该坐标的 MI 能量存储。
     *
     * <p>探测顺序与 {@code StaffLinkTargets#capability} 一致：指定面 → 不指定 → 逐面。
     * 逐面那一步不是多余的：MI 的<b>输出</b>仓只在「朝向的那一面」交出可抽出的存储，
     * 传 {@code null} 会拿到 null。</p>
     */
    @Nullable
    private static MIEnergyStorage lookup(Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        if (side != null) {
            return level.getCapability(EnergyApi.SIDED, pos, side);
        }
        MIEnergyStorage unspecified = level.getCapability(EnergyApi.SIDED, pos, null);
        if (unspecified != null) {
            return unspecified;
        }
        for (Direction direction : DIRECTIONS) {
            MIEnergyStorage found = level.getCapability(EnergyApi.SIDED, pos, direction);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 把 MI 的 EU 存储包成本模组的 FE 长整型契约。 */
    private static final class EuEndpoint implements LongEnergyHandler {
        private final MIEnergyStorage storage;

        EuEndpoint(MIEnergyStorage storage) {
            this.storage = storage;
        }

        /** 1 EU 值多少 FE；读 MI 的配置，取不到或非法时退回 1。 */
        private static long ratio() {
            return Math.max(1L, MIServerConfig.INSTANCE.forgeEnergyPerEu.getAsInt());
        }

        @Override
        public long extract(long amount, boolean simulate) {
            long eu = amount / ratio();
            if (eu <= 0L) {
                // 连 1 EU 都凑不出来：不搬。否则会把 3 FE 变成 1 EU 再换回 4 FE，凭空造电。
                return 0L;
            }
            return Math.max(0L, storage.extract(eu, simulate)) * ratio();
        }

        @Override
        public long receive(long amount, boolean simulate) {
            long eu = amount / ratio();
            if (eu <= 0L) {
                return 0L;
            }
            return Math.max(0L, storage.receive(eu, simulate)) * ratio();
        }

        @Override
        public long stored() {
            return Math.max(0L, storage.getAmount()) * ratio();
        }

        @Override
        public long capacity() {
            long capacity = storage.getCapacity();
            if (capacity <= 0L) {
                return Long.MAX_VALUE;
            }
            long scaled = capacity * ratio();
            return scaled < 0L ? Long.MAX_VALUE : scaled;
        }

        @Override
        public boolean canExtract() {
            return storage.canExtract();
        }

        @Override
        public boolean canReceive() {
            return storage.canReceive();
        }
    }
}
