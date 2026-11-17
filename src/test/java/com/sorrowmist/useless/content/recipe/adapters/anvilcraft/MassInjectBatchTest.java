package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MassInjectBatchTest {
    @Test
    void batchConservesMassWithoutDiscardingRemainders() {
        var batch = MassInjectRecipeAdapter.batch(10_000_000, 3_000_000);
        assertEquals(10, batch.inputs());
        assertEquals(3, batch.outputs());
    }

    @Test
    void rejectsInvalidMass() {
        assertThrows(IllegalArgumentException.class, () -> MassInjectRecipeAdapter.batch(10, 0));
    }
}
