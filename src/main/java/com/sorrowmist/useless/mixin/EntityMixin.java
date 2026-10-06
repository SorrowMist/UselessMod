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
        if (entity instanceof Player player && EventHandler.hasBeefAdvancedStealthItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void useless_mod$makeBeefPlayerUnattackable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefAdvancedStealthItem(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void useless_mod$makeBeefPlayerProjectileUntargetable(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && EventHandler.hasBeefAdvancedStealthItem(player)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 从源头拦截「被保护的玩家着火」。
     *
     * <p>{@code setRemainingFireTicks} 是原版所有点火路径（火方块 {@code entityInside}、
     * 岩浆 {@code lavaHurt}、火附魔 / 火焰箭 / 火球走的 {@code igniteForSeconds}）的
     * <b>唯一汇入点</b>，且本身只是纯字段赋值。在 HEAD 直接取消写入，字段就恒为 0，
     * 于是 {@code Entity#baseTick} 末尾的 {@code setSharedFlagOnFire(true)} 永远不会触发，
     * 客户端的火焰叠加层与火焰粒子也就根本不会出现——无需每 tick 清理。</p>
     *
     * <p>注意 {@link Player} 覆写了该方法（创造/无敌时把值钳到 1），虚分派下对玩家永远走
     * {@code Player} 的实现，因此 {@code PlayerMixin} 里必须再注一份，否则此处会被绕过。</p>
     */
    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
    private void useless_mod$preventBeefPlayerIgnition(int ticks, CallbackInfo ci) {
        if (ticks > 0 && (Object) this instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
            ci.cancel();
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
        return reason == Entity.RemovalReason.DISCARDED;
    }

    private static boolean isUnsafePosition(Player player, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return true;
        }

        Level level = player.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        return y < level.getMinBuildHeight() || !level.getWorldBorder().isWithinBounds(pos);
    }
}
