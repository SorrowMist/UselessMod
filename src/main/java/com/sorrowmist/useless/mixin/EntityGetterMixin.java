package com.sorrowmist.useless.mixin;

import com.sorrowmist.useless.utils.ProtectedPlayerQueries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Forge's Mixin 0.8.5 cannot inject into interfaces. Preserve the vanilla default
 * queries here, changing only their input list to hide protected players. */
@Mixin(EntityGetter.class)
public interface EntityGetterMixin {
    /** @author UselessMod @reason Exclude advanced stealth players before selecting the nearest. */
    @Overwrite
    default Player getNearestPlayer(double x, double y, double z, double range,
                                    Predicate<Entity> predicate) {
        Player nearest = null;
        double distance = -1;
        for (Player player : ProtectedPlayerQueries.visiblePlayers((EntityGetter) this)) {
            if (predicate != null && !predicate.test(player)) continue;
            double candidate = player.distanceToSqr(x, y, z);
            if ((range < 0 || candidate < range * range) && (distance < 0 || candidate < distance)) {
                distance = candidate;
                nearest = player;
            }
        }
        return nearest;
    }

    /** @author UselessMod @reason Preserve the alive query while excluding protected players. */
    @Overwrite
    default boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
        for (Player player : ProtectedPlayerQueries.visiblePlayers((EntityGetter) this)) {
            if (EntitySelector.NO_SPECTATORS.test(player)
                    && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)
                    && (range < 0 || player.distanceToSqr(x, y, z) < range * range)) return true;
        }
        return false;
    }

    /** @author UselessMod @reason Select from visible players with vanilla targeting conditions. */
    @Overwrite
    default Player getNearestPlayer(TargetingConditions conditions, LivingEntity source) {
        EntityGetter self = (EntityGetter) this;
        return self.getNearestEntity(ProtectedPlayerQueries.visiblePlayers(self), conditions,
                source, source.getX(), source.getY(), source.getZ());
    }

    /** @author UselessMod @reason Select from visible players with vanilla targeting conditions. */
    @Overwrite
    default Player getNearestPlayer(TargetingConditions conditions, LivingEntity source,
                                    double x, double y, double z) {
        EntityGetter self = (EntityGetter) this;
        return self.getNearestEntity(ProtectedPlayerQueries.visiblePlayers(self), conditions, source, x, y, z);
    }

    /** @author UselessMod @reason Select from visible players with vanilla targeting conditions. */
    @Overwrite
    default Player getNearestPlayer(TargetingConditions conditions, double x, double y, double z) {
        EntityGetter self = (EntityGetter) this;
        return self.getNearestEntity(ProtectedPlayerQueries.visiblePlayers(self), conditions, null, x, y, z);
    }

    /** @author UselessMod @reason Preserve the vanilla AABB and targeting checks for visible players. */
    @Overwrite
    default List<Player> getNearbyPlayers(TargetingConditions conditions, LivingEntity source, AABB area) {
        List<Player> result = new ArrayList<>();
        for (Player player : ProtectedPlayerQueries.visiblePlayers((EntityGetter) this)) {
            if (area.contains(player.getX(), player.getY(), player.getZ()) && conditions.test(source, player)) {
                result.add(player);
            }
        }
        return result;
    }

}
