package com.sorrowmist.useless.compat.ars;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 魔源（Ars Nouveau）搬运桥。
 *
 * <p>刻意<b>不</b>出现任何 Ars Nouveau 类型：{@link com.sorrowmist.useless.content.stafflink.StaffLinkTargets}
 * 只依赖这个接口，真正引用 {@code ISourceTile} 的实现类由
 * {@link ArsSourceCompatLoader} 在检测到模组后才反射加载。</p>
 */
public interface SourceBridge {
    /** 该坐标是不是一个魔源容器。 */
    boolean hasTile(Level level, BlockPos pos);

    /**
     * 把魔源从一处搬到另一处。
     *
     * @return 实际搬运量（0 表示没搬）
     */
    int moveSource(Level sourceLevel, BlockPos sourcePos, Level targetLevel, BlockPos targetPos, int limit);
}
