package com.sorrowmist.useless.content.recipe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AdapterGuardsMigrationTest {
    @BeforeEach @AfterEach void reset() { AdapterGuards.clearFailures(); }
    private static final class AdapterA {}
    private static final class AdapterB {}

    @Test void oneBadRecipeDoesNotDisableLaterRecipesFromTheSameAdapter() {
        var adapter = new AdapterA();
        assertEquals("skip", AdapterGuards.callEach(adapter, "convertAll", () -> "broken:recipe",
                () -> { throw new IllegalArgumentException("bad recipe"); }, "skip"));
        assertFalse(AdapterGuards.isDisabled(adapter, "convertAll"));
        assertEquals("valid", AdapterGuards.callEach(adapter, "convertAll", () -> "valid", "skip"));
        assertEquals(1, AdapterGuards.skippedItems(adapter, "convertAll"));
        assertTrue(AdapterGuards.disabledMods().isEmpty());
    }

    @Test void linkageFailureDisablesOnlyThatAdapterActionAndReportsItsSource() {
        var adapter = new AdapterA();
        var attempts = new AtomicInteger();
        AdapterGuards.rememberSource(adapter, "naturesaura");
        for (int i = 0; i < 10; i++) {
            assertEquals(-1, AdapterGuards.callEach(adapter, "convertAll", () -> {
                attempts.incrementAndGet(); throw new NoSuchMethodError("incompatible API");
            }, -1));
        }
        assertEquals(1, attempts.get());
        assertTrue(AdapterGuards.isDisabled(adapter, "convertAll"));
        assertEquals(7, AdapterGuards.call(adapter, "getRecipeClass", () -> 7, -1));
        assertEquals(8, AdapterGuards.callEach(new AdapterB(), "convertAll", () -> 8, -1));
        assertEquals(java.util.List.of("naturesaura"), AdapterGuards.disabledMods());
    }

    @Test void structuralFailureIsNotRetriedOnEveryServerTick() {
        var adapter = new AdapterA();
        AdapterGuards.rememberSource(adapter, "pneumaticcraft");
        var calls = new AtomicInteger();
        for (int i = 0; i < 10; i++) AdapterGuards.call(adapter, "getGeneratedRecipes", () -> {
            calls.incrementAndGet(); throw new IllegalStateException("cannot enumerate");
        }, null);
        assertEquals(1, calls.get());
        assertTrue(AdapterGuards.isDisabled(adapter, "getGeneratedRecipes"));
        assertEquals(0, AdapterGuards.skippedItems(adapter, "getGeneratedRecipes"));
        assertEquals(java.util.List.of("pneumaticcraft"), AdapterGuards.disabledMods());
    }

    @Test void registrationFailuresAreDeduplicatedAndBadDiagnosticSuppliersCannotEscape() {
        AdapterGuards.recordModFailure("immersiveengineering");
        AdapterGuards.recordModFailure("immersiveengineering");
        AdapterGuards.recordModFailure(null);
        assertEquals(java.util.List.of("immersiveengineering"), AdapterGuards.disabledMods());
        assertEquals("skip", AdapterGuards.callEach(new AdapterA(), "convertAll",
                () -> { throw new NoClassDefFoundError("bad description"); },
                () -> { throw new IllegalArgumentException("bad data"); }, "skip"));
    }

    @Test void fatalVmErrorsAreNotMisreportedAsOptionalModIncompatibility() {
        assertThrows(OutOfMemoryError.class, () -> AdapterGuards.callEach(new AdapterA(), "convertAll",
                () -> { throw new OutOfMemoryError("test sentinel"); }, null));
    }
}
