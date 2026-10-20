package com.sorrowmist.useless.compat.forge1201.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.Codec;
import java.util.*;
import java.util.function.*;

public final class ByteBufCodecs {
    private ByteBufCodecs() {}
    private static FriendlyByteBuf wrap(ByteBuf b) { return b instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(b); }
    public static final StreamCodec<ByteBuf,Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf,Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    public static final StreamCodec<ByteBuf,Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    public static final StreamCodec<ByteBuf,Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf,Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf,Byte> BYTE = StreamCodec.of((b,v)->b.writeByte(v), ByteBuf::readByte);
    public static final StreamCodec<ByteBuf,Integer> VAR_INT = StreamCodec.of((b,v)->wrap(b).writeVarInt(v), b->wrap(b).readVarInt());
    public static final StreamCodec<ByteBuf,Long> VAR_LONG = StreamCodec.of((b,v)->wrap(b).writeVarLong(v), b->wrap(b).readVarLong());
    public static final StreamCodec<ByteBuf,String> STRING_UTF8 = StreamCodec.of((b,v)->wrap(b).writeUtf(v), b->wrap(b).readUtf());
    public static final StreamCodec<ByteBuf,CompoundTag> COMPOUND_TAG = StreamCodec.of((b,v)->wrap(b).writeNbt(v), b->wrap(b).readNbt());
    private static int checkedSize(FriendlyByteBuf buffer) {
        int size=buffer.readVarInt();
        if(size<0 || size>65536) throw new IllegalArgumentException("Invalid collection length: " + size);
        return size;
    }
    public static <B extends ByteBuf,V> Function<StreamCodec<B,V>,StreamCodec<B,List<V>>> list() {
        return codec -> collection(ArrayList::new,codec);
    }
    public static <B extends ByteBuf,V> Function<StreamCodec<B,V>,StreamCodec<B,List<V>>> list(int maximum) {
        if(maximum<0 || maximum>65536) throw new IllegalArgumentException("Invalid list limit");
        return codec -> StreamCodec.of((buffer,values)-> {
            if(values.size()>maximum) throw new IllegalArgumentException("List too large");
            wrap(buffer).writeVarInt(values.size()); for(V value:values) codec.encode(buffer,value);
        }, buffer -> {
            int size=checkedSize(wrap(buffer));
            if(size>maximum) throw new IllegalArgumentException("List too large");
            List<V> values=new ArrayList<>(size); for(int i=0;i<size;i++) values.add(codec.decode(buffer)); return values;
        });
    }
    public static <B extends ByteBuf,V,C extends Collection<V>> StreamCodec<B,C> collection(IntFunction<C> factory,StreamCodec<? super B,V> codec) {
        return StreamCodec.of((b,values)-> {
            if(values.size()>65536) throw new IllegalArgumentException("Collection too large");
            wrap(b).writeVarInt(values.size()); for(V value:values) codec.encode(b,value);
        }, b->{ int n=checkedSize(wrap(b)); C values=factory.apply(n); for(int i=0;i<n;i++) values.add(codec.decode(b)); return values; });
    }
    public static <B extends ByteBuf,V> StreamCodec<B,Optional<V>> optional(StreamCodec<B,V> codec) {
        return StreamCodec.of((b,v)->{ b.writeBoolean(v.isPresent()); v.ifPresent(x->codec.encode(b,x)); },
                b->b.readBoolean()?Optional.of(codec.decode(b)):Optional.empty());
    }
    public static <B extends ByteBuf,T> StreamCodec<B,T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodec(codec);
    }
    public static <B extends ByteBuf,T> StreamCodec<B,T> fromCodec(Codec<T> codec) {
        return StreamCodec.of((b,value)-> {
            var tag=codec.encodeStart(NbtOps.INSTANCE,value).getOrThrow(false, message->{throw new IllegalArgumentException(message);});
            CompoundTag envelope=new CompoundTag(); envelope.put("value",tag); wrap(b).writeNbt(envelope);
        }, b->{
            CompoundTag envelope=wrap(b).readNbt();
            if(envelope==null || !envelope.contains("value")) throw new IllegalArgumentException("Missing codec payload");
            return codec.parse(NbtOps.INSTANCE,envelope.get("value")).getOrThrow(false,message->{throw new IllegalArgumentException(message);});
        });
    }
}
