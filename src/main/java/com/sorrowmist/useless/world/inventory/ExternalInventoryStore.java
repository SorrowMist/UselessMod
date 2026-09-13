package com.sorrowmist.useless.world.inventory;

import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ExternalInventoryStore {
    private ExternalInventoryStore() {
    }

    public static ExternalInventoryReference bindAt(
            Level level,
            ExternalInventoryKind kind,
            @Nullable ExternalInventoryReference requested,
            BlockPos pos,
            ItemStackHandler inventory,
            @Nullable CompoundTag legacyInventory,
            HolderLookup.Provider registries) {
        ExternalInventoryReference reference = requested != null && requested.kind() == kind
                ? requested
                : new ExternalInventoryReference(kind, UUID.randomUUID());
        if (level.isClientSide() || level.getServer() == null) return reference;

        ExternalInventorySavedData data = ExternalInventorySavedData.get(level.getServer(), reference);
        if (!data.claimAt(level, pos)) {
            ExternalInventoryReference fork = new ExternalInventoryReference(kind, UUID.randomUUID());
            ExternalInventorySavedData forked = ExternalInventorySavedData.get(level.getServer(), fork);
            data.copyInventoryTo(forked);
            forked.claimAt(level, pos);
            forked.loadInto(inventory, registries);
            return fork;
        }

        if (data.isInitialized()) {
            data.loadInto(inventory, registries);
        } else if (legacyInventory != null) {
            inventory.deserializeNBT(registries, legacyInventory.copy());
            data.saveFrom(inventory, registries);
        } else {
            data.saveFrom(inventory, registries);
        }
        return reference;
    }

    public static @Nullable ExternalInventoryReference getReference(BlockEntity entity) {
        return entity.components().get(UComponents.EXTERNAL_INVENTORY_REFERENCE.get());
    }

    public static void setReference(BlockEntity entity, ExternalInventoryReference reference) {
        DataComponentMap components = DataComponentMap.builder()
                .addAll(entity.components())
                .set(UComponents.EXTERNAL_INVENTORY_REFERENCE.get(), reference)
                .build();
        entity.setComponents(components);
        entity.setChanged();
    }

    public static void clearLegacyComponent(BlockEntity entity, ExternalInventoryKind kind) {
        DataComponentMap.Builder components = DataComponentMap.builder().addAll(entity.components());
        if (kind == ExternalInventoryKind.FURNACE) {
            components.set(UComponents.FURNACE_DATA.get(), null);
        } else {
            components.set(UComponents.MULTIBLOCK_PART_DATA.get(), null);
        }
        entity.setComponents(components.build());
        entity.setChanged();
    }

    public static void save(Level level, @Nullable ExternalInventoryReference reference,
                             ItemStackHandler inventory, HolderLookup.Provider registries) {
        if (reference == null || level.isClientSide() || level.getServer() == null) return;
        ExternalInventorySavedData.get(level.getServer(), reference).saveFrom(inventory, registries);
    }

    public static void release(Level level, BlockPos pos,
                               @Nullable ExternalInventoryReference reference) {
        if (reference == null || level.isClientSide() || level.getServer() == null) return;
        ExternalInventorySavedData.get(level.getServer(), reference).releaseAt(level, pos);
    }
}
