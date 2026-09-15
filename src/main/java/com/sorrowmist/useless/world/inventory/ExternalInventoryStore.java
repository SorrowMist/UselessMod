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

    /**
     * 认领一份「通用载荷」外置数据（不是物品栏，而是任意一段 NBT，例如紧凑 F9 的全部样板）。
     *
     * <p>占位规则与 {@link #bindAt} 完全一致：目标坐标已被别的方块占着就分叉出一份新 UUID，
     * 把老数据整份复制过去，避免复制出来的两台机器互相踩。</p>
     */
    public static ExternalInventoryReference bindRawAt(
            Level level,
            ExternalInventoryKind kind,
            @Nullable ExternalInventoryReference requested,
            BlockPos pos) {
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
            return fork;
        }
        return reference;
    }

    /** 读出外置存储里的通用载荷；从没写过时返回 {@code null}。 */
    public static @Nullable CompoundTag loadRawPayload(Level level, @Nullable ExternalInventoryReference reference) {
        if (reference == null || level.isClientSide() || level.getServer() == null) return null;
        return ExternalInventorySavedData.get(level.getServer(), reference).rawPayload();
    }

    /** 把通用载荷写进外置存储。调用方负责控制写入频率——这份载荷可能有 MB 级。 */
    public static void saveRawPayload(Level level, @Nullable ExternalInventoryReference reference,
                                      CompoundTag payload) {
        if (reference == null || level.isClientSide() || level.getServer() == null) return;
        ExternalInventorySavedData.get(level.getServer(), reference).saveRawPayload(payload);
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
