package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.content.stafflink.SourceHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 无线物流接「AE 网络里的魔源」的桥接口。
 *
 * <p>魔源能进 ME 网络靠的是 <b>Ars Énergistique</b>（modid {@code arseng}）注册的
 * {@code SourceKey}。同 {@link AeChemicalBridge}，这里刻意不含任何 AE2 / Ars 类型，
 * 由独立实现类在对应前置齐备时才被反射载入。</p>
 */
public interface AeSourceBridge {

    /**
     * 把该端点所属 ME 网络里的<b>魔源</b>当作一个容器端点。
     *
     * @return 不是可用的 AE 端点时返回 {@code null}
     */
    @Nullable
    SourceHandlerView sourceEndpoint(Level level, BlockPos pos);
}
