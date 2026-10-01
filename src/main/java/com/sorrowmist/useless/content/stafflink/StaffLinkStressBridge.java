package com.sorrowmist.useless.content.stafflink;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 应力搬运桥。
 *
 * <p><b>刻意不出现任何外部模组的类型。</b>{@link StaffLinkTargets} 与
 * {@code StaffLinkEngine} 只依赖这个接口，真正引用动力学方块实体类型的实现类由
 * {@code CreateStressCompatLoader} 在检测到对应模组后才反射加载。</p>
 *
 * <p><b>为什么它不像别的介质那样只交出「端点」。</b>物品、流体、能量都是「一轮搬多少」的
 * 流量语义：一端抽出、另一端收下，逐对搬运就够。转速与应力容量却是<b>整张网络的连续状态量</b>——
 * 同一个网络上所有机器共享一个转速，输出端能拿到多少容量取决于输入端网络的富余量，
 * 还要在多个输出之间按权重分配。这些都必须拿到「同一条线路上全部输入与全部输出」才算得出来，
 * 因此这里暴露的是「跑一整条线路」而不是「搬一对端点」。</p>
 */
public interface StaffLinkStressBridge {

    /**
     * 把该坐标的方块包成应力端点。
     *
     * <p>返回的对象对常驻代码是<b>不透明</b>的：调用方只拿它做 {@code null} 判断
     * （决定这个坐标能不能绑成应力锚点）。</p>
     *
     * @return 该坐标不是动力学方块时返回 {@code null}
     */
    @Nullable
    Object resolveEndpoint(Level level, BlockPos pos);

    /**
     * 跑一整条线路：读输入端的转速与富余应力，驱动输出端，并把负载回馈给输入端网络。
     *
     * <p>由引擎<b>每 tick</b> 调用一次（不走周期与退避）：驱动关系必须持续维持，
     * 一旦停手，输出端的转速会随时间失效。</p>
     *
     * @param releases 该线路上全部启用的释放端（输入）
     * @param absorbs  该线路上全部启用的吸收端（输出）
     */
    void applyRoute(MinecraftServer server, UUID networkId, int routeIndex,
                    List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs);

    /**
     * 清掉已经过期的「驱动认领」。
     *
     * <p><b>必须每 tick 调用</b>，不能并进低频任务：桥停止驱动之后，输出端方块身上还留着
     * 上一轮的虚拟转速，只有这里的过期判定能把它摘掉。迟一个周期，玩家就会看到
     * 「源已经停了，目标还在转」。</p>
     */
    void sweep(MinecraftServer server);

    /** 把运行状态（可用应力 / 需求 / 每个端点的状态与原因）下发给正在看界面的玩家。 */
    void syncStatus(MinecraftServer server);

    /** 服务器停止时清掉运行时状态，别把上一局的 tick 数带进下一局。 */
    void clearRuntimeState();
}
