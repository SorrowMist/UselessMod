package com.sorrowmist.useless.event;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventHandlerTest {

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
}
