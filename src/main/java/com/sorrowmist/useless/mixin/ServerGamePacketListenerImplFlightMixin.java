package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录「客户端自己上报的飞行意图」，供造化杖的粘滞飞行使用。
 * <p>
 * <b>为什么需要这个</b>：粘滞飞行要能区分两种情况——
 * <ul>
 *   <li>玩家自己关掉飞行（落地、或空中双击）→ 应该允许，不能把人钉在空中；</li>
 *   <li>别的 mod 在服务端偷偷把 {@code flying} 清掉（实测 Re-Avaritia 的
 *       {@code AbilityHandler#updateClientServerFlight} 每秒清一次）→ 应该被我们拉回来。</li>
 * </ul>
 * 两者在服务端 {@code abilities} 上都是「flying 变成 false」，只看字段分不出来。
 * 但<b>只有前者会经过这里</b>：玩家关飞行时客户端会发
 * {@code ServerboundPlayerAbilitiesPacket}，而 mod 的服务端代码是直接改字段的。
 * <p>
 * 不能用 {@code player.onGround()} 判断落地：{@code ServerGamePacketListenerImpl#handleMovePlayer}
 * 里 {@code setOnGround(flag4)} 的 flag4 含 {@code !player.mayFly()}，只要 mayfly 是开着的，
 * 服务端的 onGround 就恒为 false。
 * <p>
 * 注意：注册在 json 的<b>通用</b> {@code mixins} 段而不是 {@code server} 段 ——
 * 单人存档的集成服务端属于客户端侧，放 {@code server} 段不会生效。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplFlightMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handlePlayerAbilities(Lnet/minecraft/network/protocol/game/ServerboundPlayerAbilitiesPacket;)V",
            at = @At("HEAD"))
    private void useless_mod$trackClientFlightIntent(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        EventHandler.onClientFlightIntent(this.player, packet.isFlying());
    }
}
