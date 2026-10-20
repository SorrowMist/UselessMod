package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 无线物流接「AE 网络里的化学品」的桥接口。
 *
 * <p>独立于 {@link AeLogisticsBridge} 的原因：化学品需要 <b>Applied Mekanistics</b>（modid
 * {@code appmek}）才能存在于 ME 网络里，而物品/流体只要 AE2。若把两件事塞进同一个实现类，
 * 只装 AE2 没装 appmek 的整合包会在加载该实现类时直接 {@code NoClassDefFoundError}。
 * 拆开之后两个实现各自只在对应前置齐备时才被反射载入。</p>
 *
 * <p>本接口同样<b>不出现任何 AE2 或 Mekanism 类型</b>：{@code ChemicalHandlerView} 本就是
 * 本模组的 long 级抽象。</p>
 */
public interface AeChemicalBridge {

    /**
     * 把该端点所属 ME 网络里的<b>化学品</b>当作一个容器端点。
     *
     * @return 不是可用的 AE 端点时返回 {@code null}
     */
    @Nullable
    ChemicalHandlerView chemicalEndpoint(Level level, BlockPos pos);
}
