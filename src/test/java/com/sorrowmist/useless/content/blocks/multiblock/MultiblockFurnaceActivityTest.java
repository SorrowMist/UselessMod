package com.sorrowmist.useless.content.blocks.multiblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockFurnaceActivityTest {
    @Test
    void unformedControllerAlwaysUsesIdleDerivedState() {
        assertEquals(MultiblockFurnaceActivity.IDLE,
                MultiblockFurnaceActivity.resolve(false, true, true));
    }

    @Test
    void formedControllerWithoutWorkIsIdle() {
        assertEquals(MultiblockFurnaceActivity.IDLE,
                MultiblockFurnaceActivity.resolve(true, false, false));
    }

    @Test
    void everyBlockedOrQueuedWorkReasonIsWaiting() {
        for (String ignored : List.of("redstone", "energy", "recipe", "mold",
                "queue", "output")) {
            assertEquals(MultiblockFurnaceActivity.WAIT,
                    MultiblockFurnaceActivity.resolve(true, false, true));
        }
    }

    @Test
    void progressTakesPriorityOverConcurrentWaitingWork() {
        assertEquals(MultiblockFurnaceActivity.RUN,
                MultiblockFurnaceActivity.resolve(true, true, true));
    }
}
