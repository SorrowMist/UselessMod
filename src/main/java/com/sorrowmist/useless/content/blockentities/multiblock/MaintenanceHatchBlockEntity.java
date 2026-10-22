package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.multiblock.MaintenanceCalibrationRules;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Random;

/**
 * 维护仓方块实体。
 *
 * <p>除了作为结构件并持久化归属关系外，本实体还承载维护仓的「校准」小游戏状态。
 * 状态机与判定规则见 {@link MaintenanceCalibrationRules}：指针位置由流逝时间推导，
 * 服务端是唯一判定方，客户端只负责绘制与上报点击时刻。</p>
 *
 * <p>小游戏目前<b>不参与</b>合金炉的主流程：完成或失败都不会停机、也不影响配方运行，
 * 这样玩法的调整不会牵连多方块校验。</p>
 */
public final class MaintenanceHatchBlockEntity extends BlockEntity {
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_STRUCTURE_GENERATION = "StructureGeneration";
    private static final String TAG_PHASE = "CalibrationPhase";
    private static final String TAG_ROUNDS_CLEARED = "CalibrationRounds";
    private static final String TAG_TARGET_CENTER = "CalibrationTarget";
    private static final String TAG_START_TIME = "CalibrationStart";

    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    /** 小游戏阶段，取值为 {@link MaintenanceCalibrationRules} 的 {@code PHASE_*}。 */
    private byte calibrationPhase = MaintenanceCalibrationRules.PHASE_IDLE;
    /** 本局已通过的轮数。 */
    private int roundsCleared;
    /** 本轮目标的归一化中心位置。 */
    private float targetCenter;
    /** 本轮计时起点（服务端世界时间），用于推导流逝 tick。 */
    private long roundStartTime;

    /** 目标中心取随机数用；与存档无关，仅避免为一次随机建对象。 */
    private final Random random = new Random();

    public MaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAINTENANCE_HATCH.get(), pos, state);
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            // 归属信息决定客户端能否判断本仓已被相邻核心占用，必须随方块更新下发；
            // 只 setChanged() 的话客户端永远读到 null，相邻双核心会同时被画成成型。
            markStateChanged();
        }
    }

    /** True when this hatch's persisted link belongs to the given core. */
    public boolean isLinkedToController(BlockPos controllerPos) {
        return Objects.equals(this.controllerPos, controllerPos);
    }

    @Nullable
    private MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        // 占用判定不能比较结构代次：核心状态变化会让代次自增，而本部件要等下一次
        // linkController 才拿到新代次，这期间若比较代次就会把已成型核心误判为无主，
        // 相邻核心因而可以共用同一个维护仓并同时成型。代次只用于丢弃陈旧缓存。
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.claimsMaintenanceHatch(worldPosition) ? core : null;
    }

    /**
     * 维护仓不可共用：只要仍归属于另一个成型核心即视为被占用。
     * 陈旧或已失效的链接不算占用，便于旧结构破坏后由新结构重新认领。
     */
    public boolean isClaimedByOtherController(BlockPos controllerPos) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && !controller.getBlockPos().equals(controllerPos);
    }

    // ------------------------------------------------------------------
    // 校准小游戏
    // ------------------------------------------------------------------

    public byte calibrationPhase() {
        return calibrationPhase;
    }

    public int roundsCleared() {
        return roundsCleared;
    }

    public float targetCenter() {
        return targetCenter;
    }

    /** 本轮已流逝的 tick 数；未在进行中或世界未加载时返回 0。 */
    public long elapsedTicks() {
        if (level == null || calibrationPhase != MaintenanceCalibrationRules.PHASE_RUNNING) {
            return 0L;
        }
        return Math.max(0L, level.getGameTime() - roundStartTime);
    }

    /** 开始一局新的校准，同时作为「重新开始」的入口。 */
    public void startCalibration() {
        calibrationPhase = MaintenanceCalibrationRules.PHASE_RUNNING;
        roundsCleared = 0;
        beginRound();
        markStateChanged();
    }

    /**
     * 处理一次点击。
     *
     * <p>{@code reportedElapsed} 是客户端上报的「自本轮开始以来的流逝 tick」。服务端先把它
     * 限制在自己观测值的邻域内（见 {@link MaintenanceCalibrationRules#isPlausibleElapsed}），
     * 再据此推导指针位置并判定，因此客户端无法通过伪造流逝值来指定命中点。</p>
     *
     * @return 本次点击是否命中目标
     */
    public boolean submitClick(long reportedElapsed) {
        if (calibrationPhase != MaintenanceCalibrationRules.PHASE_RUNNING) {
            return false;
        }
        long observed = elapsedTicks();
        if (!MaintenanceCalibrationRules.isPlausibleElapsed(reportedElapsed, observed)) {
            return false;
        }

        float pointer = MaintenanceCalibrationRules.pointerAt(reportedElapsed);
        float halfWidth = MaintenanceCalibrationRules.halfWidthFor(roundsCleared);
        if (!MaintenanceCalibrationRules.isHit(pointer, targetCenter, halfWidth)) {
            // 失误不惩罚玩家，只是把进度清零并换一个新目标重来。
            roundsCleared = 0;
            beginRound();
            markStateChanged();
            return false;
        }

        roundsCleared++;
        if (roundsCleared >= MaintenanceCalibrationRules.ROUNDS_TOTAL) {
            calibrationPhase = MaintenanceCalibrationRules.PHASE_COMPLETED;
        } else {
            beginRound();
        }
        markStateChanged();
        return true;
    }

    /** 放弃当前局面，回到未开始状态（关闭界面时调用）。 */
    public void abortCalibration() {
        if (calibrationPhase != MaintenanceCalibrationRules.PHASE_RUNNING) {
            return;
        }
        calibrationPhase = MaintenanceCalibrationRules.PHASE_IDLE;
        roundsCleared = 0;
        markStateChanged();
    }

    /** 重置本机状态：随机目标中心，并把计时起点挪到当前时刻。 */
    private void beginRound() {
        targetCenter = 0.15F + random.nextFloat() * 0.70F;
        roundStartTime = level == null ? 0L : level.getGameTime();
    }

    private void markStateChanged() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains(TAG_CONTROLLER) ? BlockPos.of(tag.getLong(TAG_CONTROLLER)) : null;
        structureGeneration = tag.getLong(TAG_STRUCTURE_GENERATION);
        calibrationPhase = tag.getByte(TAG_PHASE);
        roundsCleared = tag.getInt(TAG_ROUNDS_CLEARED);
        targetCenter = tag.getFloat(TAG_TARGET_CENTER);
        roundStartTime = tag.getLong(TAG_START_TIME);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong(TAG_CONTROLLER, controllerPos.asLong());
        tag.putLong(TAG_STRUCTURE_GENERATION, structureGeneration);
        tag.putByte(TAG_PHASE, calibrationPhase);
        tag.putInt(TAG_ROUNDS_CLEARED, roundsCleared);
        tag.putFloat(TAG_TARGET_CENTER, targetCenter);
        tag.putLong(TAG_START_TIME, roundStartTime);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
    }

    /** 客户端结构预览需要归属信息，故方块更新与区块首包都要带上完整 NBT。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
