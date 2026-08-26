package com.sorrowmist.useless.content.entities;

import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BeefTimeAccelerationEntity extends Entity {
    public static final int DEFAULT_TOTAL_TIME = 600;
    public static final int MAX_TICK_SPEED = 8;

    private static final EntityDataAccessor<Integer> TICK_SPEED =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING_TIME =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOTAL_TIME =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);

    private BlockPos targetPos;

    public BeefTimeAccelerationEntity(EntityType<? extends BeefTimeAccelerationEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public BeefTimeAccelerationEntity(Level level, BlockPos targetPos) {
        this(ModEntities.BEEF_TIME_ACCELERATION.get(), level);
        this.targetPos = targetPos.immutable();
        this.setPos(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
        this.setTickSpeed(1);
        this.setTotalTime(DEFAULT_TOTAL_TIME);
        this.setRemainingTime(DEFAULT_TOTAL_TIME);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!(this.level() instanceof ServerLevel level) || this.targetPos == null) {
            this.discard();
            return;
        }
        if (this.getRemainingTime() <= 0) {
            this.discard();
            return;
        }
        if (!level.isLoaded(this.targetPos)) {
            return;
        }

        BlockState state = level.getBlockState(this.targetPos);
        BlockEntity blockEntity = level.getBlockEntity(this.targetPos);
        if (!BeefTimeAcceleration.isValidTarget(level, state, blockEntity)) {
            this.discard();
            return;
        }

        BeefTimeAcceleration.tickTarget(level, this.targetPos, state, blockEntity, this.getTickSpeed());
        this.setRemainingTime(this.getRemainingTime() - 1);
        if (this.getRemainingTime() <= 0) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TICK_SPEED, 1);
        builder.define(REMAINING_TIME, DEFAULT_TOTAL_TIME);
        builder.define(TOTAL_TIME, DEFAULT_TOTAL_TIME);
    }

    public BlockPos getTargetPos() {
        return this.targetPos != null ? this.targetPos : this.blockPosition();
    }

    public void setTargetPos(BlockPos targetPos) {
        this.targetPos = targetPos == null ? null : targetPos.immutable();
    }

    public int getTickSpeed() {
        return this.entityData.get(TICK_SPEED);
    }

    public void setTickSpeed(int tickSpeed) {
        this.entityData.set(TICK_SPEED, tickSpeed);
    }

    public int getRemainingTime() {
        return this.entityData.get(REMAINING_TIME);
    }

    public void setRemainingTime(int remainingTime) {
        this.entityData.set(REMAINING_TIME, remainingTime);
    }

    public int getTotalTime() {
        return this.entityData.get(TOTAL_TIME);
    }

    public void setTotalTime(int totalTime) {
        this.entityData.set(TOTAL_TIME, totalTime);
    }

    public void addTime(int ticks) {
        this.setRemainingTime(this.getRemainingTime() + ticks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("targetPos")) {
            this.targetPos = BlockPos.of(tag.getLong("targetPos"));
        }
        this.setTickSpeed(tag.getInt("tickSpeed"));
        this.setRemainingTime(tag.getInt("remainingTime"));
        this.setTotalTime(tag.getInt("totalTime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetPos != null) {
            tag.putLong("targetPos", this.targetPos.asLong());
        }
        tag.putInt("tickSpeed", this.getTickSpeed());
        tag.putInt("remainingTime", this.getRemainingTime());
        tag.putInt("totalTime", this.getTotalTime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
