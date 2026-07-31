package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Portable inventory and user settings for multiblock furnace functional parts. */
public record MultiblockPartData(int version, CompoundTag inventory, int intervalTicks, long multiplier) {
    public static final int CURRENT_VERSION = 1;
    public static final Codec<MultiblockPartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(MultiblockPartData::version),
            CompoundTag.CODEC.fieldOf("inventory").forGetter(MultiblockPartData::inventory),
            Codec.INT.optionalFieldOf("interval_ticks", 0).forGetter(MultiblockPartData::intervalTicks),
            Codec.LONG.optionalFieldOf("multiplier", 0L).forGetter(MultiblockPartData::multiplier)
    ).apply(instance, MultiblockPartData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockPartData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MultiblockPartData::version,
                    ByteBufCodecs.COMPOUND_TAG, MultiblockPartData::inventory,
                    ByteBufCodecs.VAR_INT, MultiblockPartData::intervalTicks,
                    ByteBufCodecs.VAR_LONG, MultiblockPartData::multiplier,
                    MultiblockPartData::new);

    public MultiblockPartData {
        inventory = inventory == null ? new CompoundTag() : inventory.copy();
        intervalTicks = Math.max(0, intervalTicks);
        multiplier = Math.max(0L, multiplier);
    }

    public static MultiblockPartData inventory(
            ItemStackHandler inventory, HolderLookup.Provider registries) {
        return new MultiblockPartData(
                CURRENT_VERSION, inventory.serializeNBT(registries), 0, 0L);
    }

    public static MultiblockPartData passiveHatch(
            ItemStackHandler inventory, HolderLookup.Provider registries,
            int intervalTicks, long multiplier) {
        return new MultiblockPartData(
                CURRENT_VERSION, inventory.serializeNBT(registries), intervalTicks, multiplier);
    }

    @Override
    public CompoundTag inventory() {
        return inventory.copy();
    }

    public void restoreInventory(ItemStackHandler target, HolderLookup.Provider registries) {
        target.deserializeNBT(registries, inventory.copy());
    }

    public boolean hasInventoryContents() {
        return !inventory.getList("Items", Tag.TAG_COMPOUND).isEmpty();
    }

    public boolean isEmpty() {
        return !hasInventoryContents() && intervalTicks == 0 && multiplier == 0L;
    }
}
