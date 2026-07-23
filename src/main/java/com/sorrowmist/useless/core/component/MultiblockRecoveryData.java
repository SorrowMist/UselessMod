package com.sorrowmist.useless.core.component;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record MultiblockRecoveryData(int version, long energy, List<GenericStack> contents) {
    public static final int CURRENT_VERSION = 1;
    public static final Codec<MultiblockRecoveryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(MultiblockRecoveryData::version),
            Codec.LONG.optionalFieldOf("energy", 0L).forGetter(MultiblockRecoveryData::energy),
            GenericStack.CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(MultiblockRecoveryData::contents)
    ).apply(instance, MultiblockRecoveryData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockRecoveryData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MultiblockRecoveryData::version,
                    ByteBufCodecs.VAR_LONG, MultiblockRecoveryData::energy,
                    GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockRecoveryData::contents,
                    MultiblockRecoveryData::new);

    public MultiblockRecoveryData {
        energy = Math.max(0L, energy);
        contents = List.copyOf(contents == null ? List.of() : contents);
    }

    public boolean isEmpty() {
        return energy <= 0L && contents.isEmpty();
    }
}
