package com.sorrowmist.useless.content.blockentities.multiblock;

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

/**
 * 自动维护仓方块实体。
 *
 * <p>与普通维护仓一样持久化所属核心，使同一个维护仓不能被相邻的多个核心共用。
 * 自动化行为不落在本类：维护度归核心持有，由核心每 tick 检测到本仓后直接恢复满值，
 * 本类只承担归属与占用判定。</p>
 */
public final class AutoMaintenanceHatchBlockEntity extends BlockEntity {
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_STRUCTURE_GENERATION = "StructureGeneration";

    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    public AutoMaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_MAINTENANCE_HATCH.get(), pos, state);
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            // 同普通维护仓：归属必须随方块更新下发，否则客户端预览无法识别占用关系。
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
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
        // 与普通维护仓一致：占用判定不比较结构代次，否则核心状态变化后的代次窗口内
        // 会把已成型核心误判为无主，使相邻核心共用本仓并同时成型。
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.claimsMaintenanceHatch(worldPosition) ? core : null;
    }

    /**
     * 自动维护仓不可共用：只要仍归属于另一个成型核心即视为被占用。
     * 陈旧或已失效的链接不算占用，便于旧结构破坏后由新结构重新认领。
     */
    public boolean isClaimedByOtherController(BlockPos controllerPos) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && !controller.getBlockPos().equals(controllerPos);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains(TAG_CONTROLLER) ? BlockPos.of(tag.getLong(TAG_CONTROLLER)) : null;
        structureGeneration = tag.getLong(TAG_STRUCTURE_GENERATION);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong(TAG_CONTROLLER, controllerPos.asLong());
        tag.putLong(TAG_STRUCTURE_GENERATION, structureGeneration);
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