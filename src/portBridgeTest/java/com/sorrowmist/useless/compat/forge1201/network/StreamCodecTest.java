package com.sorrowmist.useless.compat.forge1201.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class StreamCodecTest {
    record Settings(int speed, boolean enabled, String name) {}

    @Test void compositeRoundTripsInFieldOrder() {
        StreamCodec<FriendlyByteBuf,Settings> codec = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Settings::speed,
                ByteBufCodecs.BOOL, Settings::enabled,
                ByteBufCodecs.STRING_UTF8, Settings::name, Settings::new);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Settings value = new Settings(1024, true, "范围加速 · 1.20.1");
            codec.encode(buffer, value);
            assertEquals(value, codec.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void varLongPreservesCountsAboveIntegerRange() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (long value : new long[]{0, 1, Integer.MAX_VALUE + 1L, Long.MAX_VALUE}) {
                ByteBufCodecs.VAR_LONG.encode(buffer, value);
                assertEquals(value, ByteBufCodecs.VAR_LONG.decode(buffer));
            }
        } finally { buffer.release(); }
    }

    @Test void collectionsRoundTripAndRejectInvalidLengths() {
        StreamCodec<FriendlyByteBuf,List<Integer>> codec = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, List.of(1, 128, 4096));
            assertEquals(List.of(1, 128, 4096), codec.decode(buffer));
            buffer.clear(); buffer.writeVarInt(-1);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
            buffer.clear(); buffer.writeVarInt(65537);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void optionalAndMappedCodecsRoundTrip() {
        StreamCodec<FriendlyByteBuf,Optional<String>> codec = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8.<FriendlyByteBuf>cast());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, Optional.empty());
            codec.encode(buffer, Optional.of("样板"));
            assertEquals(Optional.empty(), codec.decode(buffer));
            assertEquals(Optional.of("样板"), codec.decode(buffer));
        } finally { buffer.release(); }
    }
}
