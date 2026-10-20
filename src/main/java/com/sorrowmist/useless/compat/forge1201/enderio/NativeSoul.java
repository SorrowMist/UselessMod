package com.sorrowmist.useless.compat.forge1201.enderio;

import com.enderio.api.capability.StoredEntityData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Ender IO 6's stored entity data, without inventing a 1.21 soul component. */
public final class NativeSoul {
    private final StoredEntityData data;
    private NativeSoul(StoredEntityData data) { this.data = copyData(data); }
    public static NativeSoul from(StoredEntityData data) { return new NativeSoul(data); }
    public static NativeSoul empty() { return from(StoredEntityData.empty()); }
    public static NativeSoul of(EntityType<?> type) { return of(BuiltInRegistries.ENTITY_TYPE.getKey(type)); }
    public static NativeSoul of(ResourceLocation id) { return from(StoredEntityData.of(id)); }
    public boolean isEmpty() { return data.getEntityType().isEmpty(); }
    public ResourceLocation entityTypeId() { return data.getEntityType().orElse(null); }
    public EntityType<?> entityType() {
        ResourceLocation id = entityTypeId();
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
    }
    public NativeSoul copy() { return from(data); }
    public StoredEntityData storedData() { return copyData(data); }
    private static StoredEntityData copyData(StoredEntityData data) {
        var result = new StoredEntityData();
        if (data != null) result.deserializeNBT(data.serializeNBT().copy());
        return result;
    }
}
