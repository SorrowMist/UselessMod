package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void useless_mod$hideProtectedPlayerFromEnemyChecks(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (EventHandler.hasBeefInvulnerabilityItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventAttackingBeefProtectedPlayer(Entity target, CallbackInfo ci) {
        if (target instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            ci.cancel();
        }
    }
}
