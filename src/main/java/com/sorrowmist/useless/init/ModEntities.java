package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.entities.BeefTimeAccelerationEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UselessMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BeefTimeAccelerationEntity>> BEEF_TIME_ACCELERATION =
            ENTITY_TYPES.register(
                    "beef_time_acceleration",
                    () -> EntityType.Builder.<BeefTimeAccelerationEntity>of(
                                    BeefTimeAccelerationEntity::new,
                                    MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(UselessMod.id("beef_time_acceleration").toString())
            );

    private ModEntities() {
    }
}
