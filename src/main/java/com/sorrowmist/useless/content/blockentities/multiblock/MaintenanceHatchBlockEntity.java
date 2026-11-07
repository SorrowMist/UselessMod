package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import com.sorrowmist.useless.content.multiblock.MaintenanceCalibrationRules;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.UUID;

/** Calibration is a transient, single-player session; only the controller persists machine health. */
public final class MaintenanceHatchBlockEntity extends OwnedFurnacePartBlockEntity implements MenuProvider {
    private byte phase = MaintenanceCalibrationRules.PHASE_IDLE;
    private int roundsCleared;
    private float targetCenter = 0.5F;
    private long roundStartTime;
    private long roundId;
    private UUID operator;

    public MaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MAINTENANCE_HATCH.get(), pos, state);
    }

    MaintenanceHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override protected boolean isClaimedBy(MultiblockAlloyFurnaceCoreBlockEntity core) {
        return core.claimsMaintenanceHatch(worldPosition);
    }

    public byte calibrationPhase() { return phase; }
    public int roundsCleared() { return roundsCleared; }
    public float targetCenter() { return targetCenter; }
    public long roundId() { return roundId; }
    public long elapsedTicks() {
        return level == null || phase != MaintenanceCalibrationRules.PHASE_RUNNING
                ? 0L : Math.max(0L, level.getGameTime() - roundStartTime);
    }

    public boolean startCalibration(UUID player) {
        if (!canAct() || phase == MaintenanceCalibrationRules.PHASE_RUNNING
                && !Objects.equals(operator, player)) return false;
        operator = player;
        phase = MaintenanceCalibrationRules.PHASE_RUNNING;
        roundsCleared = 0;
        beginRound();
        return true;
    }

    public boolean submitClick(UUID player, long expectedRound, long reportedElapsed) {
        if (!canAct() || phase != MaintenanceCalibrationRules.PHASE_RUNNING
                || !Objects.equals(operator, player) || expectedRound != roundId
                || !MaintenanceCalibrationRules.isPlausibleElapsed(reportedElapsed, elapsedTicks())) return false;
        boolean hit = MaintenanceCalibrationRules.isHit(MaintenanceCalibrationRules.pointerAt(reportedElapsed),
                targetCenter, MaintenanceCalibrationRules.halfWidthFor(roundsCleared));
        if (!hit) {
            roundsCleared = 0;
            beginRound();
        } else if (++roundsCleared == MaintenanceCalibrationRules.ROUNDS_TOTAL) {
            phase = MaintenanceCalibrationRules.PHASE_COMPLETED;
            roundId++;
            var controller = getController();
            if (controller != null) controller.restoreMaintenance();
        } else {
            beginRound();
        }
        return hit;
    }

    public void abortCalibration(UUID player) {
        if (!Objects.equals(operator, player)) return;
        resetSession();
    }

    private boolean canAct() {
        return !unloading && !isRemoved() && level != null && !level.isClientSide;
    }

    private void beginRound() {
        targetCenter = 0.15F + level.random.nextFloat() * 0.70F;
        roundStartTime = level.getGameTime();
        roundId++; // A delayed/duplicated click must never act on the next target.
    }

    private void resetSession() {
        phase = MaintenanceCalibrationRules.PHASE_IDLE;
        roundsCleared = 0;
        operator = null;
        roundId++;
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        resetSession(); // Never resume a saved game without its original menu/player.
    }

    @Override public void onChunkUnloaded() { resetSession(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { resetSession(); super.setRemoved(); }

    @Override public Component getDisplayName() {
        return Component.translatable("block.useless_mod.maintenance_hatch");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MaintenanceHatchMenu(id, inventory, worldPosition);
    }
}
