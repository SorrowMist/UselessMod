package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.client.BeefToolFlightClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录「服务端确实授予过飞行能力」这一事实，供造化杖的客户端飞行兜底使用。
 * <p>
 * 只有服务端发过 {@code canFly=true}，才说明飞行能力是模组该给的 ——
 * {@code LocalPlayerMixin} 的外部清除拦截与 {@code BeefToolFlightClient} 的本地兜底都靠它加闸，
 * 否则在服务端把 {@code enable_flight_effect} 关掉时会被误触发（客户端以为自己能飞、服务端不认）。
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(
            method = "handlePlayerAbilities(Lnet/minecraft/network/protocol/game/ClientboundPlayerAbilitiesPacket;)V",
            at = @At("TAIL"))
    private void useless_mod$trackServerGrantedFlight(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        BeefToolFlightClient.onServerAbilities(packet.canFly());
    }
}
