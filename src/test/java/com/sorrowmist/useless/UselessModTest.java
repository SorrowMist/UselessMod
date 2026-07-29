package com.sorrowmist.useless;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UselessModTest {

    @Test
    void lightningRodInteractionPreemptsAndHandlesCanceledWrenchHooks() throws NoSuchMethodException {
        SubscribeEvent annotation = UselessMod.class
                .getDeclaredMethod("onRightClickBlock", PlayerInteractEvent.RightClickBlock.class)
                .getAnnotation(SubscribeEvent.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
        assertTrue(annotation.receiveCanceled());
    }
}
