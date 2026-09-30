package com.sorrowmist.useless.compat.ae;

import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 无线物流接 AE2「局部通用库存」的桥接口。
 *
 * <p>本接口刻意<b>不出现任何 AE2 类型</b>：常驻的 {@code StaffLinkTargets} 只认这个契约，
 * 真正的实现（{@link AeGenericInvCompat}）由 {@link AeGenericInvCompatLoader} 反射加载。
 * 这样没装 AE2 的整合包里，无线物流的全部代码都不会触发 AE2 的类解析错误。</p>
 *
 * <h2>这跟 {@link AeLogisticsBridge} 不是一回事</h2>
 *
 * <p>{@link AeLogisticsBridge} 交付的是「<b>整张 ME 网络</b>」的 long 视角（{@code ME_STORAGE}
 * 能力），对应 {@code LinkMedium#AE_ITEM} / {@code AE_FLUID}——资源在网里，槽位是即时编号的。</p>
 *
 * <p>本桥交付的是「<b>方块自己那一小格本地库存</b>」（{@code GENERIC_INTERNAL_INV} 能力）。
 * 对 ME 接口而言就是它那 9 格 {@code storage}（{@code InterfaceLogic#getStorage()}），
 * 与它挂在哪张网、网上有什么完全无关。AE2 已经为这类方块<b>顺带</b>注册了
 * {@code Capabilities.ItemHandler.BLOCK}（见 {@code InitCapabilityProviders#registerGenericAdapters}，
 * 投影成 {@code GenericStackItemStorage}），所以它本来就能被无线物流当作普通容器搬——
 * 只是那条路要来回 {@code AEKey ↔ ItemStack} 物化。</p>
 *
 * <h2>为什么要专门开一条路</h2>
 *
 * <p>{@code GenericInternalInventory} 的 {@code getAmount} / {@code insert} / {@code extract}
 * <b>本来就是 long 签名，而且不碰 {@code ItemStack}</b>。走它就能把「抽取」这一步的物化整个
 * 去掉——实测那一段占无线物流总耗时的相当一块（见内部无线物流性能分析报告第十三节）。</p>
 *
 * <h2>优先级</h2>
 *
 * <p>命中了 generic 就用 generic，不再问 {@code ItemHandler.BLOCK}。两者语义等价
 * （后者本就是前者的投影），没有方块会同时是「真容器」和「generic 库存」，
 * 因此「谁赢」在结果上不可区分。</p>
 */
public interface AeGenericInvBridge {

    /**
     * 该坐标是不是一个「有局部通用库存」的 AE2 方块。
     *
     * <p>刻意不看在线状态与内容：掉电、空库存都是暂时的。</p>
     */
    boolean isGenericInv(Level level, BlockPos pos);

    /**
     * 把该方块的局部通用库存当作「物品容器」。
     *
     * @param side 只在调用方指定时透传；generic 能力的注册 lambda 忽略 context，
     *             因此任何面（含 {@code null}）都返回同一个库存对象
     * @return 不是这种方块时返回 {@code null}
     */
    @Nullable
    LongItemHandler itemEndpoint(Level level, BlockPos pos, @Nullable Direction side);

    /** 把该方块的局部通用库存当作「流体容器」；语义同 {@link #itemEndpoint}。 */
    @Nullable
    LongFluidHandler fluidEndpoint(Level level, BlockPos pos, @Nullable Direction side);
}
