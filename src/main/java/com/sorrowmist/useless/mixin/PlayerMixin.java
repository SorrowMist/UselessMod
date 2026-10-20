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
        if (EventHandler.hasBeefAdvancedStealthItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventAttackingBeefProtectedPlayer(Entity target, CallbackInfo ci) {
        if (target instanceof Player player && EventHandler.hasBeefAdvancedStealthItem(player)) {
            ci.cancel();
        }
    }

    /**
     * {@link Entity} 的 {@code setRemainingFireTicks} 被 {@link Player} 覆写
     * （带 {@code abilities.invulnerable} 钳制），虚分派下对玩家永远走这里。
     * 因此 {@code EntityMixin} 那份对玩家不会生效，必须在本类再拦一份，
     * 否则「玩家保护开启后仍着火」的问题会原样保留。
     */
    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventBeefPlayerIgnition(int ticks, CallbackInfo ci) {
        if (ticks > 0 && EventHandler.hasBeefInvulnerabilityItem((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
