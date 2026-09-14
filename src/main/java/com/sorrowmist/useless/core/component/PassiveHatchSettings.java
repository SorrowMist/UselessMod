package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable settings of a passive crafting hatch: the global interval, the global default
 * multiplier, and the sparse per-slot multipliers that override that default.
 */
public record PassiveHatchSettings(
        int intervalTicks, long multiplier, List<SlotMultiplier> slotMultipliers) {
    /** Upper bound on the number of stored overrides; the hatch never reports more than this. */
    public static final int MAX_SLOT_ENTRIES = 4096;

    /**
     * A single slot pinned to its own batch size. Absent slots follow the global multiplier,
     * which is why clearing an override is expressed by removing the entry, not by storing 1.
     */
    public record SlotMultiplier(int slot, long multiplier) {
        public static final Codec<SlotMultiplier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SlotMultiplier::slot),
                Codec.LONG.fieldOf("multiplier").forGetter(SlotMultiplier::multiplier)
        ).apply(instance, SlotMultiplier::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotMultiplier> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, SlotMultiplier::slot,
                        ByteBufCodecs.VAR_LONG, SlotMultiplier::multiplier,
                        SlotMultiplier::new);

        public SlotMultiplier {
            slot = Math.max(0, slot);
            multiplier = Math.max(0L, multiplier);
        }

        public boolean valid() {
            return slot < MAX_SLOT_ENTRIES && multiplier > 0L;
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<SlotMultiplier>> MULTIPLIER_LIST_CODEC =
            StreamCodec.of(PassiveHatchSettings::encodeSlotMultipliers,
                    PassiveHatchSettings::decodeSlotMultipliers);

    public static final Codec<PassiveHatchSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("interval_ticks").forGetter(PassiveHatchSettings::intervalTicks),
            Codec.LONG.fieldOf("multiplier").forGetter(PassiveHatchSettings::multiplier),
            SlotMultiplier.CODEC.listOf().optionalFieldOf("slot_multipliers", List.of())
                    .forGetter(PassiveHatchSettings::slotMultipliers)
    ).apply(instance, PassiveHatchSettings::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PassiveHatchSettings> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PassiveHatchSettings::intervalTicks,
                    ByteBufCodecs.VAR_LONG, PassiveHatchSettings::multiplier,
                    MULTIPLIER_LIST_CODEC, PassiveHatchSettings::slotMultipliers,
                    PassiveHatchSettings::new);

    public PassiveHatchSettings {
        intervalTicks = Math.max(0, intervalTicks);
        multiplier = Math.max(0L, multiplier);
        slotMultipliers = slotMultipliers == null
                ? List.of()
                : slotMultipliers.stream().filter(entry -> entry != null && entry.valid())
                .limit(MAX_SLOT_ENTRIES).toList();
    }

    /** Convenience constructor for hatches that only carry the global settings. */
    public PassiveHatchSettings(int intervalTicks, long multiplier) {
        this(intervalTicks, multiplier, List.of());
    }

    public boolean hasSlotMultipliers() {
        return !slotMultipliers.isEmpty();
    }

    private static void encodeSlotMultipliers(RegistryFriendlyByteBuf buffer, List<SlotMultiplier> entries) {
        buffer.writeVarInt(entries.size());
        for (SlotMultiplier entry : entries) {
            SlotMultiplier.STREAM_CODEC.encode(buffer, entry);
        }
    }

    private static List<SlotMultiplier> decodeSlotMultipliers(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SLOT_ENTRIES) {
            throw new IllegalArgumentException("Invalid passive hatch slot multiplier count: " + size);
        }
        List<SlotMultiplier> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(SlotMultiplier.STREAM_CODEC.decode(buffer));
        }
        return List.copyOf(entries);
    }
}
