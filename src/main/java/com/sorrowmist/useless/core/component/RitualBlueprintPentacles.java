package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical set of Occultism pentacles imprinted on a ritual blueprint. */
public record RitualBlueprintPentacles(List<ResourceLocation> pentacles) {
    private static final int MAX_PENTACLES = 256;

    public static final Codec<RitualBlueprintPentacles> CODEC = ResourceLocation.CODEC.listOf()
            .xmap(RitualBlueprintPentacles::new, RitualBlueprintPentacles::pentacles);

    public static final StreamCodec<FriendlyByteBuf, RitualBlueprintPentacles> STREAM_CODEC = StreamCodec.of(
            RitualBlueprintPentacles::write, RitualBlueprintPentacles::read);

    public RitualBlueprintPentacles {
        pentacles = pentacles == null ? List.of() : pentacles.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .limit(MAX_PENTACLES)
                .toList();
    }

    public static RitualBlueprintPentacles of(ResourceLocation pentacle) {
        return new RitualBlueprintPentacles(List.of(pentacle));
    }

    public static RitualBlueprintPentacles of(Collection<ResourceLocation> pentacles) {
        return new RitualBlueprintPentacles(pentacles == null ? List.of() : List.copyOf(pentacles));
    }

    public boolean isEmpty() {
        return pentacles.isEmpty();
    }

    public boolean contains(ResourceLocation pentacle) {
        return pentacles.contains(pentacle);
    }

    public boolean containsAll(RitualBlueprintPentacles required) {
        return required != null && pentacles.containsAll(required.pentacles);
    }

    private static void write(FriendlyByteBuf buffer, RitualBlueprintPentacles value) {
        buffer.writeVarInt(value.pentacles.size());
        value.pentacles.forEach(buffer::writeResourceLocation);
    }

    private static RitualBlueprintPentacles read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PENTACLES) {
            throw new IllegalArgumentException("Invalid ritual blueprint pentacle count: " + size);
        }
        java.util.ArrayList<ResourceLocation> pentacles = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pentacles.add(buffer.readResourceLocation());
        }
        return new RitualBlueprintPentacles(pentacles);
    }
}
