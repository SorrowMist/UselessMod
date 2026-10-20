package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE 能量端点的桥接口。
 *
 * <p>与 {@link AeLogisticsBridge} 同一套手法：本接口<b>不含任何 AE2 / AppliedFlux 类型</b>，
 * 实现类由 {@link AeEnergyCompatLoader} 反射加载，因此没装这些模组时
 * {@code StaffLinkTargets} 照样能加载、只是永远解析不出能量端点。</p>
 */
public interface AeEnergyBridge {

    /** 该坐标是不是 AE 网络端点。 */
    boolean isEndpoint(Level level, BlockPos pos);

    /**
     * 该坐标所属 ME 网络的 FE 视角；不是端点、或网络取不到通量存储时返回 {@code null}。
     */
    @Nullable
    LongEnergyHandler energyEndpoint(Level level, BlockPos pos);
}
