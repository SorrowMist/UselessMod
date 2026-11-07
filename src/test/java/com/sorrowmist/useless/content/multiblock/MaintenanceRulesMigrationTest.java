package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceRulesMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void wearStartsAt288TicksAnd64CraftsAndIsCappedForHugeBatches() {
        assertEquals(0, MaintenanceWearRules.wearForTicks(287));
        assertEquals(1, MaintenanceWearRules.wearForTicks(288));
        assertEquals(2, MaintenanceWearRules.wearForTicks(576));
        assertEquals(0, MaintenanceWearRules.wearForCrafts(63));
        assertEquals(1, MaintenanceWearRules.wearForCrafts(64));
        assertEquals(20, MaintenanceWearRules.wearForCrafts(Long.MAX_VALUE));
        assertEquals(0, MaintenanceWearRules.wearForCrafts(-1));
        assertEquals(0, MaintenanceWearRules.wearForTicks(Long.MIN_VALUE));
    }

    @Test void warningOnlyLengthensTimeAndExhaustionStopsWorkWithoutOverflow() {
        assertFalse(MaintenanceWearRules.isWarning(600));
        assertTrue(MaintenanceWearRules.isWarning(599));
        assertEquals(1000, MaintenanceWearRules.efficiencyPermille(600));
        assertEquals(750, MaintenanceWearRules.efficiencyPermille(300));
        assertEquals(501, MaintenanceWearRules.efficiencyPermille(1));
        assertEquals(0, MaintenanceWearRules.efficiencyPermille(0));
        assertEquals(134, MaintenanceWearRules.scaledProcessTime(100, 300));
        assertEquals(200, MaintenanceWearRules.scaledProcessTime(100, 1));
        assertEquals(Integer.MAX_VALUE, MaintenanceWearRules.scaledProcessTime(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MAX_VALUE, MaintenanceWearRules.scaledProcessTime(100, 0));
        assertEquals(0, MaintenanceWearRules.clamp(Long.MIN_VALUE));
        assertEquals(1000, MaintenanceWearRules.clamp(Long.MAX_VALUE));
    }

    @Test void controllerDoesNotWearOnPausedTicksAndKeepsFractionalRuntime() throws Exception {
        var core = core();
        for (int i = 0; i < 1000; i++) tick(core, false);
        assertEquals(1000, core.getMaintenanceLevel());
        for (int i = 0; i < 287; i++) tick(core, true);
        assertEquals(1000, core.getMaintenanceLevel());
        tick(core, true);
        assertEquals(999, core.getMaintenanceLevel());
        for (int i = 0; i < 288; i++) tick(core, true);
        assertEquals(998, core.getMaintenanceLevel());
        core.onCraftsCompleted(Long.MAX_VALUE);
        assertEquals(978, core.getMaintenanceLevel());
    }

    @Test void automaticHatchCanRestoreAnExhaustedMachineBeforeAnyWorkResumes() throws Exception {
        var core = core();
        field(core, "maintenanceLevel", 0L);
        field(core, "maintenanceTickRemainder", 287L);
        field(core, "hasAutoMaintenanceHatch", true);
        var maintain = MultiblockAlloyFurnaceCoreBlockEntity.class.getDeclaredMethod("maintainWithAutoHatch");
        maintain.setAccessible(true);
        maintain.invoke(core);
        assertEquals(1000, core.getMaintenanceLevel());
        core.onCraftsCompleted(Long.MAX_VALUE);
        for (int i = 0; i < 576; i++) tick(core, true);
        assertEquals(1000, core.getMaintenanceLevel());
        field(core, "hasAutoMaintenanceHatch", false);
        for (int i = 0; i < 287; i++) tick(core, true);
        assertEquals(1000, core.getMaintenanceLevel());
    }

    @Test void calibrationPointerAndValidationHandleTurnaroundsAndForgedTimes() {
        assertEquals(0, MaintenanceCalibrationRules.pointerAt(-1));
        assertEquals(0, MaintenanceCalibrationRules.pointerAt(0));
        assertEquals(0.5F, MaintenanceCalibrationRules.pointerAt(20));
        assertEquals(1, MaintenanceCalibrationRules.pointerAt(40));
        assertEquals(0.5F, MaintenanceCalibrationRules.pointerAt(60));
        assertEquals(0, MaintenanceCalibrationRules.pointerAt(80));
        assertEquals(0.10F, MaintenanceCalibrationRules.halfWidthFor(0), 0.0001F);
        assertEquals(0.06F, MaintenanceCalibrationRules.halfWidthFor(2), 0.0001F);
        assertTrue(MaintenanceCalibrationRules.isPlausibleElapsed(20, 40));
        assertFalse(MaintenanceCalibrationRules.isPlausibleElapsed(19, 40));
        assertFalse(MaintenanceCalibrationRules.isPlausibleElapsed(-1, 0));
        assertFalse(MaintenanceCalibrationRules.isPlausibleElapsed(Long.MAX_VALUE, 0));
        assertFalse(MaintenanceCalibrationRules.isHit(Float.NaN, 0.5F, 0.1F));
        assertFalse(MaintenanceCalibrationRules.isHit(Float.POSITIVE_INFINITY, 0.5F, 0.1F));
        assertTrue(MaintenanceCalibrationRules.isHit(0.5F, 0.5F, 0.06F));
        assertFalse(MaintenanceCalibrationRules.isHit(0.4F, 0.5F, 0.06F));
    }

    @Test void ashSuppressionAndToxicityUseTheActualOutputFillRatio() {
        for (int roll = 0; roll < 100; roll++) {
            assertFalse(MufflerAshRules.isProductionSuppressed(0, roll));
            assertTrue(MufflerAshRules.isProductionSuppressed(100, roll));
            assertEquals(roll < 40, MufflerAshRules.isProductionSuppressed(40, roll));
        }
        assertEquals(3600, MufflerAshRules.ASH_INTERVAL_TICKS);
        assertEquals(0, MufflerAshRules.toxicChancePermille(300, 0, 64));
        assertEquals(150, MufflerAshRules.toxicChancePermille(300, 32, 64));
        assertEquals(300, MufflerAshRules.toxicChancePermille(300, 64, 64));
        assertEquals(300, MufflerAshRules.toxicChancePermille(300, 128, 64));
    }

    private static MultiblockAlloyFurnaceCoreBlockEntity core() throws Exception {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        when(core.getMaintenanceLevel()).thenCallRealMethod();
        doCallRealMethod().when(core).restoreMaintenance();
        doCallRealMethod().when(core).onCraftsCompleted(anyLong());
        field(core, "maintenanceLevel", 1000L);
        return core;
    }
    private static void tick(MultiblockAlloyFurnaceCoreBlockEntity core, boolean progressed) throws Exception {
        var method = MultiblockAlloyFurnaceCoreBlockEntity.class.getDeclaredMethod("accrueRuntimeMaintenance", boolean.class);
        method.setAccessible(true); method.invoke(core, progressed);
    }
    private static void field(Object core, String name, Object value) throws Exception {
        var field = MultiblockAlloyFurnaceCoreBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true); field.set(core, value);
    }
}
