package com.sorrowmist.useless.mixin.ae2;

import appeng.me.GridNode;
import com.sorrowmist.useless.compat.ae.AeLinkChannelBypass;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
 *
 * <h2>为什么是 {@code @Redirect} 而不是 {@code @Inject(cancellable = true)}</h2>
 *
 * <p>踩过一次：{@code propagateChannelsUpwards(boolean)} 返回 {@code int}，
 * 用 {@code @Inject} + {@code CallbackInfo} 会在应用 mixin 时直接抛
 * {@code InvalidInjectionException: CallbackInfoReturnable is required}，
 * <b>整个 mixin 静默失效</b>（配置里 {@code required = false}，所以只有一条 WARN，不崩），
 * 于是「静音」从来没生效过 —— 造化杖一接上就满屏 AE2 的通道自检报错。</p>
 *
 * <p>改用 {@code @Redirect} 有两个好处：</p>
 * <ul>
 *   <li>不碰控制流，方法的返回值原样保留。若用 {@code @Inject(cancellable = true)} 再
 *       {@code cancel()}，注入点在方法尾部，取消会连带跳过 {@code return this.usedChannels}，
 *       让本节点对 {@code channelsByBlocks} 的贡献变成 0 —— 静音日志却改坏了通道统计。</li>
 *   <li>只在确实要打这条 ERROR 时才走判定，没有额外开销。</li>
 * </ul>
 */
@Mixin(targets = "appeng.me.GridNode", remap = false)
public abstract class GridNodeMixin {
    @Redirect(method = "propagateChannelsUpwards",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;[Ljava/lang/Object;)V"),
            require = 1)
    private void uselessMod$silenceLinkOverflowLog(Logger logger, String message, Object[] arguments) {
        if (!AeLinkChannelBypass.carriesLinkTraffic((GridNode) (Object) this)) {
            logger.error(message, arguments);
        }
    }
}
