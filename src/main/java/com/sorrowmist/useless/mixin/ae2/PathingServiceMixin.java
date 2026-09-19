package com.sorrowmist.useless.mixin.ae2;

import appeng.api.networking.IGridNode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorrowmist.useless.compat.ae.AeLinkChannelBypass;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 无控制器（ad-hoc）网格的通道配额里，把「造化杖连进来的那一侧」排除掉。
 *
 * <p>AE2 对没有控制器的网格走的完全是另一套算法：{@code PathingService.calculateAdHocChannels()}
 * 数一遍 {@code nodesNeedingChannels}，一旦超过
 * {@code ChannelMode#getAdHocNetworkChannels()}（DEFAULT = 8，X2 = 16，X4 = 32），
 * 就整张网<b>全部归零</b>。这道闸门跟「哪条连接」无关，所以必须单独处理：
 * 把机器侧那片子网的节点从计数里剔除，其余节点照旧受 8 的约束。</p>
 *
 * <p>注意 {@code nodesNeedingChannels} 是 {@code Set}，foreach 的字节码会调
 * {@code Set.iterator()}，所以这里必须返回 {@code Set} 而不是 {@code List}。</p>
 */
@Mixin(targets = "appeng.me.service.PathingService", remap = false)
public abstract class PathingServiceMixin {
    @ModifyExpressionValue(method = "calculateAdHocChannels",
            at = @At(value = "FIELD",
                    target = "Lappeng/me/service/PathingService;nodesNeedingChannels:Ljava/util/Set;",
                    opcode = Opcodes.GETFIELD))
    private Set<IGridNode> uselessMod$excludeLinkedNodes(Set<IGridNode> original) {
        if (original.isEmpty() || !AeLinkChannelBypass.hasLinks()) {
            return original;
        }
        Set<IGridNode> farSide = AeLinkChannelBypass.farSideNodes();
        if (farSide.isEmpty()) {
            return original;
        }
        List<IGridNode> kept = new ArrayList<>(original.size());
        for (IGridNode node : original) {
            if (!farSide.contains(node)) {
                kept.add(node);
            }
        }
        return kept.size() == original.size() ? original : new LinkedHashSet<>(kept);
    }
}
