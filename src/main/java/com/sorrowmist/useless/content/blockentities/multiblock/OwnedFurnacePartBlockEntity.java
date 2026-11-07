package com.sorrowmist.useless.content.blockentities.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Small, synchronized ownership record. Generation is not an ownership arbitration lock. */
abstract class OwnedFurnacePartBlockEntity extends BlockEntity {
    @Nullable private BlockPos controllerPos;
    private long structureGeneration;
    protected boolean unloading;

    protected OwnedFurnacePartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public final void linkController(@Nullable BlockPos pos, long generation) {
        if (unloading || isRemoved()) return;
        if (Objects.equals(controllerPos, pos) && structureGeneration == generation) return;
        controllerPos = pos == null ? null : pos.immutable();
        structureGeneration = generation;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public final boolean isLinkedToController(BlockPos pos) { return Objects.equals(controllerPos, pos); }

    @Nullable
    protected final MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && isClaimedBy(core) ? core : null;
    }

    protected abstract boolean isClaimedBy(MultiblockAlloyFurnaceCoreBlockEntity core);

    public final boolean isClaimedByOtherController(BlockPos pos) {
        var controller = getController();
        return controller != null && !controller.getBlockPos().equals(pos);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        readOwnership(tag);
    }

    private void readOwnership(CompoundTag tag) {
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeOwnership(tag);
    }

    private void writeOwnership(CompoundTag tag) {
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeOwnership(tag);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag) { readOwnership(tag); }

    @Override public void onDataPacket(net.minecraft.network.Connection connection,
                                       ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void clearRemoved() { super.clearRemoved(); unloading = false; }
    @Override public void onChunkUnloaded() { unloading = true; super.onChunkUnloaded(); }
    @Override public void setRemoved() { unloading = true; super.setRemoved(); }
}
