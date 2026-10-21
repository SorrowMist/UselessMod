package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 维护仓方块实体。
 *
 * <p>当前阶段维护仓只作为结构件存在，没有任何交互与实际效果；保留方块实体是因为
 * 结构校验需要判断该部件是否已被其它核心占用，这部分归属信息必须随方块持久化。</p>
 */
public final class MaintenanceHatchBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    public MaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAINTENANCE_HATCH.get(), pos, state);
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            setChanged();
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
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isMaintenanceHatchLinked(worldPosition, structureGeneration) ? core : null;
    }

    /**
     * 维护仓不可共用：只要仍归属于另一个成型核心即视为被占用。
     * 陈旧或已失效的链接不算占用，便于旧结构破坏后由新结构重新认领。
     */
    public boolean isClaimedByOtherController(BlockPos controllerPos) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && !controller.getBlockPos().equals(controllerPos);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
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
}