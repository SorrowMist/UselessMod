package com.sorrowmist.useless.world.inventory;

import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ExternalInventorySavedData extends SavedData {
    private static final int CURRENT_VERSION = 1;
    private static final String INVENTORY = "Inventory";
    private static final String PAYLOAD = "Payload";
    private static final String KIND = "Kind";
    private static final String VERSION = "Version";
    private static final String BOUND = "Bound";
    private static final String OWNER_DIMENSION = "OwnerDimension";
    private static final String OWNER_POS = "OwnerPos";

    private final ExternalInventoryReference reference;
    private CompoundTag inventory = new CompoundTag();
    /**
     * 非物品栏的通用载荷：给「一整块任意 NBT」用（紧凑 F9 的 176 条影子样板总线数据）。
     * 它和 {@link #inventory} 互不影响，同一个文件里只会用到其中一种。
     */
    private CompoundTag payload = new CompoundTag();
    private boolean hasPayload;
    private boolean initialized;
    private boolean claimed;
    private String ownerDimension;
    private long ownerPos;

    private ExternalInventorySavedData(ExternalInventoryReference reference) {
        this.reference = Objects.requireNonNull(reference, "reference");
    }

    public static ExternalInventorySavedData get(MinecraftServer server,
                                                  ExternalInventoryReference reference) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new ExternalInventorySavedData(reference),
                        (tag, registries) -> load(reference, tag)),
                fileId(reference));
    }

    private static ExternalInventorySavedData load(ExternalInventoryReference reference, CompoundTag tag) {
        ExternalInventorySavedData data = new ExternalInventorySavedData(reference);
        if (tag.contains(INVENTORY, Tag.TAG_COMPOUND)) {
            data.inventory = tag.getCompound(INVENTORY).copy();
            data.initialized = true;
        }
        if (tag.contains(PAYLOAD, Tag.TAG_COMPOUND)) {
            data.payload = tag.getCompound(PAYLOAD).copy();
            data.hasPayload = true;
        }
        data.claimed = tag.getBoolean(BOUND);
        if (tag.contains(OWNER_DIMENSION, Tag.TAG_STRING)) {
            data.ownerDimension = tag.getString(OWNER_DIMENSION);
            data.ownerPos = tag.getLong(OWNER_POS);
        }
        return data;
    }

    private static String fileId(ExternalInventoryReference reference) {
        return "useless_mod_inventory_" + reference.kind().serializedName() + "_" + reference.id();
    }

    public boolean claimAt(Level level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        if (claimed) {
            return ownerDimension != null
                    && dimension.equals(ownerDimension)
                    && pos.asLong() == ownerPos;
        }
        claimed = true;
        ownerDimension = dimension;
        ownerPos = pos.asLong();
        setDirty();
        return true;
    }

    public void releaseAt(Level level, BlockPos pos) {
        if (!claimed
                || ownerDimension == null
                || !ownerDimension.equals(level.dimension().location().toString())
                || ownerPos != pos.asLong()) {
            return;
        }
        claimed = false;
        ownerDimension = null;
        ownerPos = 0L;
        setDirty();
    }

    public void loadInto(ItemStackHandler target, HolderLookup.Provider registries) {
        CompoundTag tag = inventory.copy();
        tag.remove("Size");
        target.deserializeNBT(registries, tag);
    }

    public void saveFrom(ItemStackHandler source, HolderLookup.Provider registries) {
        inventory = source.serializeNBT(registries).copy();
        initialized = true;
        setDirty();
    }

    // ---------------------------------------------------------------- 通用载荷

    /** 写入一段原始 NBT（调用方保证它已经是完整的一份，内部会再拷一次避免外部改动）。 */
    public void saveRawPayload(CompoundTag value) {
        payload = value.copy();
        hasPayload = true;
        initialized = true;
        setDirty();
    }

    /** 取出上次写入的原始 NBT；没写过时返回 {@code null}。 */
    @Nullable
    public CompoundTag rawPayload() {
        return hasPayload ? payload.copy() : null;
    }

    public void copyInventoryTo(ExternalInventorySavedData target) {
        target.inventory = inventory.copy();
        target.initialized = initialized;
        if (hasPayload) {
            target.payload = payload.copy();
            target.hasPayload = true;
        }
        target.setDirty();
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION, CURRENT_VERSION);
        tag.putString(KIND, reference.kind().serializedName());
        tag.putBoolean("Initialized", initialized);
        tag.putBoolean(BOUND, claimed);
        if (claimed && ownerDimension != null) {
            tag.putString(OWNER_DIMENSION, ownerDimension);
            tag.putLong(OWNER_POS, ownerPos);
        }
        tag.put(INVENTORY, inventory.copy());
        if (hasPayload) {
            tag.put(PAYLOAD, payload.copy());
        }
        return tag;
    }
}
