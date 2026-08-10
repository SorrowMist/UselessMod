package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleClientCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void useless_mod$preventProtectedPlayerRespawn(
            ServerboundClientCommandPacket packet,
            CallbackInfo ci
    ) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                && !this.player.wonGame
                && this.player.getHealth() <= 0.0F
                && EventHandler.shouldApplyBeefInvulnerability(this.player)) {
            EventHandler.restoreBeefProtectedPlayer(this.player);
            ci.cancel();
        }
    }
}
