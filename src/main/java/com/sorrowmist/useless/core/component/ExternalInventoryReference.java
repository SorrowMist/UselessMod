package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

public record ExternalInventoryReference(ExternalInventoryKind kind, UUID id) {
    public static final Codec<ExternalInventoryReference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExternalInventoryKind.CODEC.fieldOf("kind").forGetter(ExternalInventoryReference::kind),
            UUIDUtil.CODEC.fieldOf("id").forGetter(ExternalInventoryReference::id)
    ).apply(instance, ExternalInventoryReference::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ExternalInventoryReference> STREAM_CODEC =
            StreamCodec.composite(
                    ExternalInventoryKind.STREAM_CODEC,
                    ExternalInventoryReference::kind,
                    UUIDUtil.STREAM_CODEC,
                    ExternalInventoryReference::id,
                    ExternalInventoryReference::new);

    public ExternalInventoryReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
