package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.event.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {
    @Redirect(
            method = {
                    "getNearestPlayer(DDDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/player/Player;",
                    "getNearestPlayer(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/entity/player/Player;",
                    "getNearestPlayer(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/player/Player;",
                    "getNearestPlayer(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;DDD)Lnet/minecraft/world/entity/player/Player;",
                    "getNearbyPlayers(Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    "hasNearbyAlivePlayer(DDDD)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/EntityGetter;players()Ljava/util/List;"
            )
    )
    private static List<? extends Player> useless_mod$filterProtectedPlayers(EntityGetter getter) {
        return getter.players().stream()
                .filter(player -> !EventHandler.hasBeefInvulnerabilityItem(player))
                .toList();
    }
}
