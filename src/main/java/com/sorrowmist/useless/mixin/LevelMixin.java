package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = Level.class)
public class LevelMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void useless_mod$filterBeefProtectedPlayers(EntityTypeTest<Entity, T> entityTypeTest, AABB area, Predicate<? super T> predicate, CallbackInfoReturnable<List<T>> cir) {
        List<T> entities = cir.getReturnValue();
        if (entities == null || entities.isEmpty()) {
            return;
        }

        List<T> filtered = null;
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (entity instanceof Player player && EventHandler.hasBeefInvulnerabilityItem(player)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(entities.size());
                    for (T previous : entities.subList(0, i)) {
                        filtered.add(previous);
                    }
                }
                continue;
            }

            if (filtered != null) {
                filtered.add(entity);
            }
        }

        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(
            method = "isDay",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectIsDay(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "isRaining",
            at = @At("HEAD"),
            cancellable = true
    )
    private void uselessDimAlwaysClear_rain(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "isThundering",
            at = @At("HEAD"),
            cancellable = true
    )
    private void uselessDimAlwaysClear_thunder(CallbackInfoReturnable<Boolean> cir) {
        if (this.isUselessDimension()) {
            cir.setReturnValue(false);
        }
    }

    private boolean isUselessDimension() {
        Level level = (Level) (Object) this;
        return UselessMod.MODID.equals(level.dimension().location().getNamespace());
    }
}
