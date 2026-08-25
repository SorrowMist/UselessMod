package com.sorrowmist.useless.event;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHandlerTest {

    @AfterEach
    void clearClientProtectionState() {
        EventHandler.clearClientBeefInvulnerabilityStates();
    }

    @Test
    void wirelessBindingRunsBeforeAeWrenchHook() throws NoSuchMethodException {
        SubscribeEvent annotation = EventHandler.class
                .getDeclaredMethod("onBlockInteract", PlayerInteractEvent.RightClickBlock.class)
                .getAnnotation(SubscribeEvent.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
    }

    @Test
    void playerProtectionLifecycleHandlersAreSubscribed() throws NoSuchMethodException {
        assertNotNull(EventHandler.class
                .getDeclaredMethod("onPlayerLoggedIn", PlayerEvent.PlayerLoggedInEvent.class)
                .getAnnotation(SubscribeEvent.class));
        assertNotNull(EventHandler.class
                .getDeclaredMethod("onPlayerRespawn", PlayerEvent.PlayerRespawnEvent.class)
                .getAnnotation(SubscribeEvent.class));
        assertNotNull(EventHandler.class
                .getDeclaredMethod("onPlayerChangedDimension", PlayerEvent.PlayerChangedDimensionEvent.class)
                .getAnnotation(SubscribeEvent.class));
        assertNotNull(EventHandler.class
                .getDeclaredMethod("onPlayerLoggedOut", PlayerEvent.PlayerLoggedOutEvent.class)
                .getAnnotation(SubscribeEvent.class));
    }

    @Test
    void protectedPlayersCannotBecomeMobTargets() throws ReflectiveOperationException {
        TestPlayer player = createTestPlayer(true);
        LivingChangeTargetEvent event = new LivingChangeTargetEvent(
                player,
                player,
                LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);

        EventHandler.onLivingChangeTarget(event);

        assertTrue(event.isCanceled());
    }

    @Test
    void unprotectedPlayersRemainValidMobTargets() throws ReflectiveOperationException {
        TestPlayer player = createTestPlayer(false);
        LivingChangeTargetEvent event = new LivingChangeTargetEvent(
                player,
                player,
                LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);

        EventHandler.onLivingChangeTarget(event);

        assertFalse(event.isCanceled());
    }

    @Test
    void protectedPlayersRejectNonBeneficialEffectsButKeepBeneficialEffects() throws ReflectiveOperationException {
        TestPlayer player = createTestPlayer(true);
        MobEffectEvent.Applicable darkness = new MobEffectEvent.Applicable(
                player,
                new MobEffectInstance(MobEffects.DARKNESS),
                null);
        MobEffectEvent.Applicable regeneration = new MobEffectEvent.Applicable(
                player,
                new MobEffectInstance(MobEffects.REGENERATION),
                null);

        EventHandler.onMobEffectApplicable(darkness);
        EventHandler.onMobEffectApplicable(regeneration);

        assertEquals(MobEffectEvent.Applicable.Result.DO_NOT_APPLY, darkness.getResult());
        assertEquals(MobEffectEvent.Applicable.Result.DEFAULT, regeneration.getResult());
        assertTrue(EventHandler.isNonBeneficialEffect(darkness.getEffectInstance()));
        assertFalse(EventHandler.isNonBeneficialEffect(regeneration.getEffectInstance()));
    }

    @Test
    void protectedPlayersAreHiddenFromVisibilityChecks() throws ReflectiveOperationException {
        TestPlayer player = createTestPlayer(true);

        assertFalse(player.canBeSeenAsEnemy());
        assertFalse(player.canBeSeenByAnyone());
    }

    @Test
    void unprotectedPlayersKeepNormalVisibility() throws ReflectiveOperationException {
        TestPlayer player = createTestPlayer(false);

        assertTrue(player.canBeSeenAsEnemy());
        assertTrue(player.canBeSeenByAnyone());
    }

    @Test
    void standardPlayerQueriesSkipProtectedPlayersBeforeSelectingTargets() throws ReflectiveOperationException {
        TestPlayer protectedPlayer = createTestPlayer(true);
        TestPlayer normalPlayer = createTestPlayer(false);
        TestPlayer requester = createTestPlayer(false);
        TestEntityGetter getter = new TestEntityGetter(List.of(protectedPlayer, normalPlayer));

        Player nearest = getter.getNearestPlayer(0.0, 0.0, 0.0, -1.0, entity -> true);
        Player nearestWithConditions = getter.getNearestPlayer(
                TargetingConditions.forNonCombat(), requester);
        List<Player> nearby = getter.getNearbyPlayers(
                TargetingConditions.forNonCombat(),
                requester,
                new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0));

        assertSame(normalPlayer, nearest);
        assertSame(normalPlayer, nearestWithConditions);
        assertEquals(List.of(normalPlayer), nearby);
        assertFalse(new TestEntityGetter(List.of(protectedPlayer))
                .hasNearbyAlivePlayer(0.0, 0.0, 0.0, 1.0));
    }

    private static TestPlayer createTestPlayer(boolean protectedState) throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        player.testLevel = (Level) unsafe.allocateInstance(ServerLevel.class);
        unsafe.putBoolean(player.testLevel,
                unsafe.objectFieldOffset(Level.class.getDeclaredField("isClientSide")), true);
        unsafe.putObject(player,
                unsafe.objectFieldOffset(Player.class.getDeclaredField("abilities")),
                new Abilities());
        unsafe.putInt(player,
                unsafe.objectFieldOffset(Entity.class.getDeclaredField("id")),
                NEXT_ENTITY_ID.getAndIncrement());
        unsafe.putObject(player,
                unsafe.objectFieldOffset(Entity.class.getDeclaredField("position")),
                Vec3.ZERO);
        if (protectedState) {
            EventHandler.setClientBeefInvulnerabilityState(player.getId(), true);
        }
        return player;
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1);

    private static final class TestEntityGetter implements EntityGetter {
        private final List<? extends Player> players;

        private TestEntityGetter(List<? extends Player> players) {
            this.players = players;
        }

        @Override
        public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) {
            return List.of();
        }

        @Override
        public <T extends Entity> List<T> getEntities(
                EntityTypeTest<Entity, T> entityTypeTest,
                AABB bounds,
                Predicate<? super T> predicate) {
            return List.of();
        }

        @Override
        public List<? extends Player> players() {
            return players;
        }
    }

    private static final class TestPlayer extends Player {
        private Level testLevel;

        private TestPlayer() {
            super(null, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test"));
        }

        @Override
        public Level level() {
            return testLevel;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public double distanceToSqr(double x, double y, double z) {
            return 0.0;
        }
    }
}
