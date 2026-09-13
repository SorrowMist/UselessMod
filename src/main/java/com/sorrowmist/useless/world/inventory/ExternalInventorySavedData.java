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

import java.util.Objects;

public final class ExternalInventorySavedData extends SavedData {
    private static final int CURRENT_VERSION = 1;
    private static final String INVENTORY = "Inventory";
    private static final String KIND = "Kind";
    private static final String VERSION = "Version";
    private static final String BOUND = "Bound";
    private static final String OWNER_DIMENSION = "OwnerDimension";
    private static final String OWNER_POS = "OwnerPos";

    private final ExternalInventoryReference reference;
    private CompoundTag inventory = new CompoundTag();
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
        if (claimed && (!dimension.equals(ownerDimension) || pos.asLong() != ownerPos)) {
            return false;
        }
        if (!claimed || !dimension.equals(ownerDimension) || pos.asLong() != ownerPos) {
            claimed = true;
            ownerDimension = dimension;
            ownerPos = pos.asLong();
            setDirty();
        }
        return true;
    }

    public void releaseClaim() {
        if (!claimed) return;
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

    public void copyInventoryTo(ExternalInventorySavedData target) {
        target.inventory = inventory.copy();
        target.initialized = initialized;
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
        return tag;
    }
}
