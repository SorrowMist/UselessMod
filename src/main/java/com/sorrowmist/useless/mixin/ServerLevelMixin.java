package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
    private void useless_mod$protectBeefPlayerFromBroadcastDeathEvent(Entity entity, byte eventId, CallbackInfo ci) {
        if (eventId == 3 && entity instanceof Player player && EventHandler.shouldApplyBeefInvulnerability(player)) {
            EventHandler.restoreBeefProtectedPlayer(player);
            ci.cancel();
        }
    }
}
