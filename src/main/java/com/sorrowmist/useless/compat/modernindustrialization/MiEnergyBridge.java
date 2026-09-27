package com.sorrowmist.useless.compat.modernindustrialization;

import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Modern Industrialization 能量端点的桥接口。
 *
 * <p>与 {@code AeLogisticsBridge} 同一套手法：本接口<b>不含任何 MI 类型</b>，实现类由
 * {@link MiEnergyCompatLoader} 反射加载，因此没装 MI 时 {@code StaffLinkTargets} 照样能加载、
 * 只是永远解析不出 EU 端点。</p>
 */
public interface MiEnergyBridge {

    /**
     * 该坐标的 EU 能量端点（对外表现为 FE）；不是 MI 能量方块时返回 {@code null}。
     *
     * @param side 指定面；{@code null} 表示不指定，由实现自行探测
     */
    @Nullable
    LongEnergyHandler energyEndpoint(Level level, BlockPos pos, @Nullable Direction side);
}
