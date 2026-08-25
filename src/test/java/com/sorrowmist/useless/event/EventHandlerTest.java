package com.sorrowmist.useless.event;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static TestPlayer createTestPlayer(boolean protectedState) throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        player.testLevel = (Level) unsafe.allocateInstance(ServerLevel.class);
        unsafe.putBoolean(player.testLevel,
                unsafe.objectFieldOffset(Level.class.getDeclaredField("isClientSide")), true);
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
    }
}
