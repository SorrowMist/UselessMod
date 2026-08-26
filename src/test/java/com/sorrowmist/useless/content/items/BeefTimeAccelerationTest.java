package com.sorrowmist.useless.content.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeefTimeAccelerationTest {
    @Test
    void increasesSpeedUntilMaximum() {
        assertEquals(1, BeefTimeAcceleration.nextTickSpeed(0, 8));
        assertEquals(8, BeefTimeAcceleration.nextTickSpeed(7, 8));
        assertEquals(-1, BeefTimeAcceleration.nextTickSpeed(8, 8));
    }

    @Test
    void extendsOnlyTheElapsedHalfOfTheCurrentEffect() {
        assertEquals(600, BeefTimeAcceleration.refreshRemainingTime(600));
        assertEquals(500, BeefTimeAcceleration.refreshRemainingTime(400));
    }
}
