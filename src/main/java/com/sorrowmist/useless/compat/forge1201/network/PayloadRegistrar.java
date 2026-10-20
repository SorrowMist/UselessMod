package com.sorrowmist.useless.compat.forge1201.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PayloadRegistrar {
    private final String modId;
    private String version;
    private SimpleChannel channel;
    private final Map<ResourceLocation,Entry<?>> entries=new ConcurrentHashMap<>();
    private static final Map<ResourceLocation,SimpleChannel> CHANNELS=new ConcurrentHashMap<>();
    record Envelope(CustomPacketPayload payload) {}
    private record Entry<T extends CustomPacketPayload>(StreamCodec<? super FriendlyByteBuf,T> codec,
            BiConsumer<T,IPayloadContext> handler, NetworkDirection direction) {
        @SuppressWarnings("unchecked") void encode(FriendlyByteBuf buffer,CustomPacketPayload payload) { codec.encode(buffer,(T)payload); }
        @SuppressWarnings("unchecked") void handle(CustomPacketPayload payload, IPayloadContext context) { handler.accept((T)payload,context); }
    }
    PayloadRegistrar(String modId,String version) { this.modId=modId; this.version=version; }
    public PayloadRegistrar versioned(String version) {
        if(channel!=null) throw new IllegalStateException("Channel already registered");
        this.version=version; return this;
    }
    private void ensureChannel() {
        if(channel!=null) return;
        String protocol="forge1201-"+version;
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation(modId,"payloads_"+version.replaceAll("[^a-z0-9/._-]","_")),
                ()->protocol, protocol::equals,protocol::equals);
        channel.registerMessage(0,Envelope.class, (message,buffer)-> {
            ResourceLocation id=message.payload().type().id(); buffer.writeResourceLocation(id);
            Entry<?> entry=entries.get(id);
            if(entry==null) throw new IllegalArgumentException("Unregistered payload: "+id);
            entry.encode(buffer,message.payload());
        }, buffer->{
            ResourceLocation id=buffer.readResourceLocation(); Entry<?> entry=entries.get(id);
            if(entry==null) throw new IllegalArgumentException("Unknown payload: "+id);
            CustomPacketPayload payload=entry.codec().decode(buffer);
            if(buffer.isReadable()) throw new IllegalArgumentException("Trailing payload bytes for "+id);
            return new Envelope(payload);
        }, (message,supplier)-> {
            var context=supplier.get(); Entry<?> entry=entries.get(message.payload().type().id());
            if(entry==null || entry.direction()!=context.getDirection())
                throw new IllegalArgumentException("Payload received in the wrong direction");
            // NeoForge payload handlers run on the game thread by default. Forge's
            // SimpleChannel invokes this consumer on Netty; dispatch before touching
            // screens, inventories or levels. Existing enqueueWork calls are safe here.
            context.enqueueWork(() -> entry.handle(message.payload(),new IPayloadContext(context)));
            context.setPacketHandled(true);
        });
    }
    private <T extends CustomPacketPayload> PayloadRegistrar register(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf,T> codec,BiConsumer<T,IPayloadContext> handler, NetworkDirection direction) {
        ensureChannel();
        if(entries.putIfAbsent(type.id(),new Entry<>(codec,handler,direction))!=null)
            throw new IllegalStateException("Duplicate payload: "+type.id());
        if(CHANNELS.putIfAbsent(type.id(),channel)!=null)
            throw new IllegalStateException("Payload registered on two channels: "+type.id());
        return this;
    }
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf,T> codec,BiConsumer<T,IPayloadContext> handler) {
        return register(type,codec,handler,NetworkDirection.PLAY_TO_SERVER);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf,T> codec,BiConsumer<T,IPayloadContext> handler) {
        return register(type,codec,handler,NetworkDirection.PLAY_TO_CLIENT);
    }
    static SimpleChannel channel(CustomPacketPayload payload) {
        SimpleChannel value=CHANNELS.get(payload.type().id());
        if(value==null) throw new IllegalStateException("Payload not registered: "+payload.type().id());
        return value;
    }
}
