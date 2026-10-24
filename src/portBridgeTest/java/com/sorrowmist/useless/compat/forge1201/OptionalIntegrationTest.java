package com.sorrowmist.useless.compat.forge1201;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionalIntegrationTest {
    @Test
    void missingOptionalClassFallsBackInsteadOfCrashing() {
        assertEquals("fallback", OptionalIntegration.call(
                "minecraft",
                "com.sorrowmist.useless.integration.DoesNotExist",
                "run",
                String.class,
                "fallback",
                new Class<?>[0]));
    }
}
