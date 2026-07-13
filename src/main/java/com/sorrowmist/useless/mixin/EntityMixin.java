package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void useless_mod$makeBeefPlayerUnpickable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void useless_mod$makeBeefPlayerUnattackable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void useless_mod$makeBeefPlayerProjectileUntargetable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void useless_mod$protectBeefPlayerFromUnsafeSetPos(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player) && isUnsafePosition(player, x, y, z)) {
            EventHandler.restoreBeefProtectedPlayer(player);
            ci.cancel();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void useless_mod$protectBeefPlayerFromSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && shouldProtectFromRemoval(reason) && EventHandler.shouldApplyBeefInvulnerability(player)) {
            EventHandler.restoreBeefProtectedPlayer(player);
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void useless_mod$protectBeefPlayerFromRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && shouldProtectFromRemoval(reason) && EventHandler.shouldApplyBeefInvulnerability(player)) {
            EventHandler.restoreBeefProtectedPlayer(player);
            ci.cancel();
        }
    }

    private static boolean shouldProtectFromRemoval(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }

    private static boolean isUnsafePosition(Player player, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return true;
        }

        Level level = player.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        return y < level.getMinBuildHeight() || y > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(pos);
    }
}
