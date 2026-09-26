package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 无线物流接 AE 网络的桥接口。
 *
 * <p>本接口刻意<b>不出现任何 AE2 类型</b>：常驻的 {@code StaffLinkTargets} 只认这个契约，
 * 真正的实现（{@link AeLogisticsCompat}）由 {@link AeLogisticsCompatLoader} 反射加载。
 * 这样没装 AE2 的整合包里，无线物流的全部代码都不会触发 AE2 的类解析错误。</p>
 *
 * <p>桥交付的不是「一次插入 / 一次抽出」这样的动作，而是<b>端点本身</b>：AE 网络被包装成
 * 标准的 {@link LongItemHandler} / {@link LongFluidHandler}，于是搬运那一侧完全不必知道自己
 * 在跟 AE 打交道 —— {@code moveItems} / {@code moveFluid} 里那套「先模拟后提交、余量退回源」
 * 的逻辑原样复用，连诊断路径都是同一份代码。</p>
 *
 * <p>数量全程 {@code long}：AE2 的 {@code MEStorage#insert} / {@code #extract} 本身就是 long
 * 签名，接进来不需要任何 int 中转，也不存在「超 int 要分多次」的限制。</p>
 *
 * <p>坐标指的是<b>挂在 AE 网格上的方块</b>那一格（无线访问点、ME 接口、终端、总线都算）：
 * 桥自己解析它的网格与 ME 存储，调用方不必关心网格是怎么拿到的。</p>
 */
public interface AeLogisticsBridge {

    /**
     * 该坐标是不是一个 AE 网络端点：方块注册了 AE2 的网格节点宿主能力即可。
     *
     * <p>刻意不看在线状态：掉电、掉线、节点尚未 create 都是暂时的，据此判「方块没了」
     * 会让无线物流的自愈逻辑把玩家配好的线整条删掉。</p>
     */
    boolean isEndpoint(Level level, BlockPos pos);

    /**
     * 把该端点所属的 ME 网络当作「物品容器」。
     *
     * <p>返回的处理器既可抽出（网络是源）也可插入（网络是目标）；网络没有固定槽位，
     * 槽位是按当前内容即时编号的，详见实现里的说明。</p>
     *
     * @return 不是可用的 AE 端点时返回 {@code null}
     */
    @Nullable
    LongItemHandler itemEndpoint(Level level, BlockPos pos);

    /** 把该端点所属的 ME 网络当作「流体容器」；语义同 {@link #itemEndpoint}。 */
    @Nullable
    LongFluidHandler fluidEndpoint(Level level, BlockPos pos);
}