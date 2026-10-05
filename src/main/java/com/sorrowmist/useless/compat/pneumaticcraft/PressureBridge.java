package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 气动工艺气压端点的桥接口。
 *
 * <p>与 {@link com.sorrowmist.useless.compat.ars.SourceBridge} 同一套手法：本接口<b>不含任何
 * PneumaticCraft 类型</b>，实现类由 {@link PneumaticCraftPressureCompatLoader} 反射加载，
 * 因此没装气动工艺时 {@code StaffLinkTargets} 照样能加载、只是永远解析不出气压端点。</p>
 */
public interface PressureBridge {

    /**
     * 该坐标在指定面上的气压端点；不是气动设备、或该面没有空气处理器时返回 {@code null}。
     *
     * @param side {@code null} 表示不限面（实现方自行决定探测顺序）
     */
    @Nullable
    LongPressureHandler pressureEndpoint(Level level, BlockPos pos, @Nullable Direction side);
}
