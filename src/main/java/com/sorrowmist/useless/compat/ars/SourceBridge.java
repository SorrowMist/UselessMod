package com.sorrowmist.useless.compat.ars;

import com.sorrowmist.useless.content.stafflink.SourceHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 魔源（Ars Nouveau）搬运桥。
 *
 * <p>刻意<b>不</b>出现任何 Ars Nouveau 类型：{@link com.sorrowmist.useless.content.stafflink.StaffLinkTargets}
 * 只依赖这个接口，真正引用 {@code ISourceTile} 的实现类由
 * {@link ArsSourceCompatLoader} 在检测到模组后才反射加载。</p>
 *
 * <p>交付的是<b>端点</b>而不是「从 A 搬到 B」这个动作：魔源既要能在方块容器之间搬，
 * 也要能跟 ME 网络里的魔源（Ars Énergistique）互搬，而后者没有方块坐标。
 * 抽象成端点后，搬运侧对两种来源一视同仁。</p>
 */
public interface SourceBridge {

    /**
     * 把该坐标的方块魔源容器包成端点。
     *
     * @return 该坐标不是魔源容器时返回 {@code null}
     */
    @Nullable
    SourceHandlerView sourceHandler(Level level, BlockPos pos);
}
