package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventAttackingBeefProtectedPlayer(Entity target, CallbackInfo ci) {
        if (target instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            ci.cancel();
        }
    }
}
