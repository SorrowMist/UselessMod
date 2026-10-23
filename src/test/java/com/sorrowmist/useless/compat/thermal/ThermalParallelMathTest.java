package com.sorrowmist.useless.compat.thermal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThermalParallelMathTest {
    @Test void noAugmentKeepsNativeCapacityAndOneExtraDoublesExactlyOnce() {
        assertEquals(0, ThermalParallelMath.extraOperations(0.0F, 5000.0F, false));
        assertEquals(64, ThermalParallelMath.scale(64, 0));
        assertEquals(128, ThermalParallelMath.scale(64, 1));
        assertEquals(9, ThermalParallelMath.extraOperations(0.0F, 10.0F, true));
    }

    @Test void largeAugmentsAndTransfersSaturateInsteadOfBecomingNegative() {
        assertEquals(Integer.MAX_VALUE, ThermalParallelMath.scale(Integer.MAX_VALUE, 256));
        assertEquals(Integer.MAX_VALUE, ThermalParallelMath.scale(1000000, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 1, ThermalParallelMath.extraOperations(Float.MAX_VALUE, Float.MAX_VALUE, true));
        assertEquals(0, ThermalParallelMath.extraOperations(-1.0F, 1.0F, false));
    }
}
