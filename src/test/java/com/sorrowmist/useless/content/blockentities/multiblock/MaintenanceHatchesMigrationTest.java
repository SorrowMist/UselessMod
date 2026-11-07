package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.multiblock.MaintenanceCalibrationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceHatchesMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void serverRequiresOneOperatorFreshRoundTokensAndThreeValidHits() throws Exception {
        var clock = new AtomicLong();
        var level = server(clock);
        var hatch = hatch(level);
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        BlockPos controller = new BlockPos(2, 0, 0);
        when(level.isLoaded(controller)).thenReturn(true);
        when(level.getBlockEntity(controller)).thenReturn(core);
        when(core.claimsMaintenanceHatch(BlockPos.ZERO)).thenReturn(true);
        hatch.linkController(controller, 3);
        UUID operator = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(hatch.startCalibration(operator));
        assertFalse(hatch.startCalibration(other));
        long firstRound = hatch.roundId();
        assertFalse(hatch.submitClick(other, firstRound, 0));
        assertFalse(hatch.submitClick(operator, firstRound, Long.MAX_VALUE));
        for (int round = 0; round < 3; round++) {
            long token = hatch.roundId();
            long elapsed = Math.round(hatch.targetCenter() * MaintenanceCalibrationRules.TRAVEL_TICKS);
            clock.addAndGet(elapsed);
            assertTrue(hatch.submitClick(operator, token, elapsed));
            assertFalse(hatch.submitClick(operator, token, elapsed));
            assertEquals(round + 1, hatch.roundsCleared());
        }
        assertEquals(MaintenanceCalibrationRules.PHASE_COMPLETED, hatch.calibrationPhase());
        verify(core, times(1)).restoreMaintenance();
    }

    @Test void aMissRestartsTheRoundsAndUnloadDoesNotPersistAnOperatorSession() throws Exception {
        var clock = new AtomicLong();
        var hatch = hatch(server(clock));
        UUID operator = UUID.randomUUID();
        assertTrue(hatch.startCalibration(operator));
        long token = hatch.roundId();
        assertFalse(hatch.submitClick(operator, token, 0)); // targets never include the left endpoint
        assertTrue(hatch.roundId() > token);
        assertEquals(0, hatch.roundsCleared());
        hatch.abortCalibration(UUID.randomUUID());
        assertEquals(MaintenanceCalibrationRules.PHASE_RUNNING, hatch.calibrationPhase());
        hatch.onChunkUnloaded();
        assertEquals(MaintenanceCalibrationRules.PHASE_IDLE, hatch.calibrationPhase());
        assertFalse(hatch.startCalibration(operator));
        hatch.clearRemoved();
        assertTrue(hatch.startCalibration(operator));
        hatch.load(new CompoundTag());
        assertEquals(MaintenanceCalibrationRules.PHASE_IDLE, hatch.calibrationPhase());
    }

    @Test void twoAirBlocksAreRequiredForExhaustNotJustTheBlockImmediatelyAbove() {
        BlockGetter level = mock(BlockGetter.class);
        when(level.getBlockState(BlockPos.ZERO.above())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(BlockPos.ZERO.above(2))).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(MufflerHatchBlockEntity.isExhaustClear(level, BlockPos.ZERO));
        when(level.getBlockState(BlockPos.ZERO.above(2))).thenReturn(Blocks.STONE.defaultBlockState());
        assertFalse(MufflerHatchBlockEntity.isExhaustClear(level, BlockPos.ZERO));
        when(level.getBlockState(BlockPos.ZERO.above())).thenReturn(Blocks.WATER.defaultBlockState());
        assertFalse(MufflerHatchBlockEntity.isExhaustClear(level, BlockPos.ZERO));
    }

    @Test void externalAshCapabilityIsExtractOnlySupportsSimulationAndNeverLeaksMutableStacks() {
        var hatch = new MufflerHatchBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        hatch.getOutput().insertItem(0, new ItemStack(Items.CHARCOAL, 32), false);
        var external = hatch.getAshOutput();
        ItemStack inserted = new ItemStack(Items.CHARCOAL, 4);
        assertSame(inserted, external.insertItem(0, inserted, false));
        assertSame(inserted, external.insertItem(0, inserted, true));
        assertFalse(external.isItemValid(0, inserted));
        external.getStackInSlot(0).shrink(30);
        assertEquals(32, external.getStackInSlot(0).getCount());
        assertEquals(12, external.extractItem(0, 12, true).getCount());
        assertEquals(32, external.getStackInSlot(0).getCount());
        assertEquals(12, external.extractItem(0, 12, false).getCount());
        assertEquals(20, external.getStackInSlot(0).getCount());
    }

    @Test void ashInventoryAndFractionalProgressSurviveSaveAndLoad() {
        var hatch = new MufflerHatchBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        CompoundTag saved = new CompoundTag(); saved.putInt("AshProgress", 3599);
        hatch.load(saved);
        hatch.getOutput().insertItem(0, new ItemStack(Items.CHARCOAL, 31), false);
        CompoundTag roundtrip = new CompoundTag(); hatch.saveAdditional(roundtrip);
        assertEquals(3599, roundtrip.getInt("AshProgress"));
        var restored = new MufflerHatchBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        restored.load(roundtrip);
        assertEquals(31, restored.getAshOutput().getStackInSlot(0).getCount());
        roundtrip.putInt("AshProgress", Integer.MAX_VALUE); restored.load(roundtrip);
        CompoundTag clamped = new CompoundTag(); restored.saveAdditional(clamped);
        assertEquals(3599, clamped.getInt("AshProgress"));
    }

    private static ServerLevel server(AtomicLong clock) throws Exception {
        var level = mock(ServerLevel.class);
        when(level.getGameTime()).thenAnswer(invocation -> clock.get());
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var random = Level.class.getDeclaredField("random"); random.setAccessible(true);
        random.set(level, RandomSource.create(42L));
        return level;
    }
    private static MaintenanceHatchBlockEntity hatch(ServerLevel level) {
        var hatch = new MaintenanceHatchBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        hatch.setLevel(level); return hatch;
    }
}
