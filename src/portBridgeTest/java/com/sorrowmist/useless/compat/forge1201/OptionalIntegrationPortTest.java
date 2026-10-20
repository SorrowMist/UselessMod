package com.sorrowmist.useless.compat.forge1201;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalIntegrationPortTest {
    @Test void integrationClassesAreCheckedWithoutLoadingOptionalApiTypes() {
        assertTrue(OptionalIntegration.hasClass(OptionalIntegrationPortTest.class.getName()));
        assertFalse(OptionalIntegration.hasClass(
                "com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry"));
        assertFalse(OptionalIntegration.hasClass("cn.dancingsnow.neoecoae.api.EcoOptionalType"));
    }
}
