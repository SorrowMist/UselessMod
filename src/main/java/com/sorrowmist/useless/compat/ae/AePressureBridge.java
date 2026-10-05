package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE 气压端点的桥接口。
 *
 * <p>与 {@link AeEnergyBridge} 同一套手法：本接口<b>不含任何 AE2 / Applied Pneumatics 类型</b>，
 * 实现类由 {@link AePressureCompatLoader} 反射加载，因此没装这些模组时
 * {@code StaffLinkTargets} 照样能加载、只是永远解析不出气压端点。</p>
 *
 * <p>「AE 气压」来自 Applied Pneumatics：它把气动工艺的空气建模成一个 AE 资源键
 * （{@code AirKey}），于是空气能像物品一样存进 ME 网络。网络里只有「一堆空气（mL）」，
 * 没有体积，因此这条端点 {@link LongPressureHandler#volume()} 恒为 0——目标气压对它无意义。</p>
 */
public interface AePressureBridge {

    /** 该坐标是不是 AE 网络端点。 */
    boolean isEndpoint(Level level, BlockPos pos);

    /** 该坐标所属 ME 网络的气压视角；不是端点、或网络取不到时返回 {@code null}。 */
    @Nullable
    LongPressureHandler pressureEndpoint(Level level, BlockPos pos);
}
