package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PassiveHatchSettings(int intervalTicks, long multiplier) {
    public static final Codec<PassiveHatchSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("interval_ticks").forGetter(PassiveHatchSettings::intervalTicks),
            Codec.LONG.fieldOf("multiplier").forGetter(PassiveHatchSettings::multiplier)
    ).apply(instance, PassiveHatchSettings::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PassiveHatchSettings> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PassiveHatchSettings::intervalTicks,
                    ByteBufCodecs.VAR_LONG, PassiveHatchSettings::multiplier,
                    PassiveHatchSettings::new);

    public PassiveHatchSettings {
        intervalTicks = Math.max(0, intervalTicks);
        multiplier = Math.max(0L, multiplier);
    }
}
