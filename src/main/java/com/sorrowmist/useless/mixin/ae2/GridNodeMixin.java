package com.sorrowmist.useless.mixin.ae2;

import appeng.me.GridNode;
import com.sorrowmist.useless.compat.ae.AeLinkChannelBypass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 精准静音 AE2 的「通道数超过节点容量」自检。
 *
 * <pre>
 * if (this.usedChannels &gt; getMaxChannels()) {
 *     LOG.error("Internal channel assignment error. Grid node {} has {} channels passing through it "
 *             + "but it only supports up to {}. Please open an issue on the AE2 repository.", ...);
 * }
 * </pre>
 *
 * <p>这条告警的原意是「这里本不该发生」。而造化杖的通道豁免正是<b>故意</b>让节点超容，
 * 所以豁免一生效它就必然触发，玩家看着还像我们的 bug。这里直接把那次 {@code LOG.error} 调用掐掉，
 * <b>仅限确实位于「链路」上的节点</b>（机器侧子网 + 机器节点到控制器的祖先链）——
 * 其它节点、其它网格保持 AE2 原生行为，真正的通道分配异常照样报出来。</p>
 */
@Mixin(targets = "appeng.me.GridNode", remap = false)
public abstract class GridNodeMixin {
    @Inject(method = "propagateChannelsUpwards",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;[Ljava/lang/Object;)V"),
            cancellable = true)
    private void uselessMod$silenceLinkOverflowLog(boolean consumesChannel, CallbackInfo callback) {
        if (AeLinkChannelBypass.carriesLinkTraffic((GridNode) (Object) this)) {
            callback.cancel();
        }
    }
}
