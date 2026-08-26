package com.sorrowmist.useless.content.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import com.sorrowmist.useless.init.ModEntities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeefTimeAccelerationEntityTest {
    @Test
    void savesAndRestoresTargetAndSyncedState() {
        EntityType<BeefTimeAccelerationEntity> type = ModEntities.BEEF_TIME_ACCELERATION.get();
        TestEntity original = new TestEntity(type);
        original.setTargetPos(new net.minecraft.core.BlockPos(3, 64, -2));
        original.setTickSpeed(5);
        original.setTotalTime(600);
        original.setRemainingTime(417);

        CompoundTag tag = new CompoundTag();
        original.writeState(tag);

        TestEntity restored = new TestEntity(type);
        restored.readState(tag);

        assertEquals(new net.minecraft.core.BlockPos(3, 64, -2), restored.getTargetPos());
        assertEquals(5, restored.getTickSpeed());
        assertEquals(600, restored.getTotalTime());
        assertEquals(417, restored.getRemainingTime());
    }

    private static final class TestEntity extends BeefTimeAccelerationEntity {
        private TestEntity(EntityType<? extends BeefTimeAccelerationEntity> type) {
            super(type, null);
        }

        private void writeState(CompoundTag tag) {
            this.addAdditionalSaveData(tag);
        }

        private void readState(CompoundTag tag) {
            this.readAdditionalSaveData(tag);
        }
    }
}
