package com.sorrowmist.useless.mixin.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.me.GridNode;
import com.sorrowmist.useless.compat.ae.AeLinkChannelBypass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 让造化杖那条连接变成「一根不占通道、也不封顶的线缆」。
 *
 * <p>AE2 的 {@code tryUseChannel} 是两段：先按路径上各节点的
 * {@code GridNode#getMaxChannels()}（{@code 8 × factor}，dense 才 {@code 32 × factor}）校验能否放行，
 * 再给路径上每个节点 {@code channelBottlenecks.addTo(pi, 1)} 记账。
 * 这里对「去路跨过造化杖连接」的请求<b>直接放行且完全不记账</b>：</p>
 * <ul>
 *   <li>远端设备不受容量限制 ✓</li>
 *   <li>远端流量不占用绑定网络线缆的通道额度 ⇒ <b>绑定网络自己的设备完全不受影响</b> ✓</li>
 *   <li>但仍然 {@code channelNodes.add(start)}：{@code propagateAssignments} 靠
 *       {@code channelNodes.contains(item)} 决定 {@code consumesChannel}，
 *       而 {@code meetsChannelRequirements()} = {@code usedChannels > 0} 才是设备活着的唯一依据。</li>
 * </ul>
 *
 * <p>别改成「只把容量抬成 MAX、照常记账」—— 那样远端流量会把绑定网络线缆的计数顶满，
 * 反而把绑定侧自己的设备挤到没通道。</p>
 */
@Mixin(targets = "appeng.me.pathfinding.PathingCalculation", remap = false)
public abstract class PathingCalculationMixin {
    /** {@code private final Set<GridNode> channelNodes}：只读引用、往集合里加，不重新赋值。 */
    @Shadow(remap = false)
    @Final
    private Set<GridNode> channelNodes;

    /** 每次重算开始，把「链路节点」缓存作废（网格结构在这之前可能已经变过）。 */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void uselessMod$beginCalculation(IGrid grid, CallbackInfo callback) {
        AeLinkChannelBypass.invalidate();
    }

    @Inject(method = "tryUseChannel", at = @At("HEAD"), cancellable = true)
    private void uselessMod$grantChannelAcrossLinks(GridNode start, CallbackInfoReturnable<Boolean> callback) {
        if (!AeLinkChannelBypass.crossesLink(start)) {
            return;
        }
        if (start.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !start.getSubtreeAllowsCompressedChannels()) {
            // 保持 AE2 原本对「压缩通道不能穿过这台设备」的约束（P2P 嵌套那条规则）。
            callback.setReturnValue(false);
            return;
        }
        channelNodes.add(start);
        callback.setReturnValue(true);
    }
}
