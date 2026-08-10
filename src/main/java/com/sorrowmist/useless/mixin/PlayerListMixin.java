package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventProtectedPlayerClone(
            ServerPlayer player,
            boolean keepInventory,
            Entity.RemovalReason reason,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        if (reason == Entity.RemovalReason.KILLED && EventHandler.shouldApplyBeefInvulnerability(player)) {
            EventHandler.restoreBeefProtectedPlayer(player);
            cir.setReturnValue(player);
        }
    }
}
