package com.sorrowmist.useless.compat.forge1201.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceKey;

/** Source-compatible sending facade; actual distribution is performed by Forge. */
public final class PacketDistributor {
    private PacketDistributor() {}
    private static void send(net.minecraftforge.network.PacketDistributor.PacketTarget target, CustomPacketPayload... messages) {
        for(var message:messages) PayloadRegistrar.channel(message).send(target,new PayloadRegistrar.Envelope(message));
    }
    public static void sendToServer(CustomPacketPayload... messages) {
        for(var message:messages) PayloadRegistrar.channel(message).sendToServer(new PayloadRegistrar.Envelope(message));
    }
    public static void sendToPlayer(ServerPlayer player,CustomPacketPayload... messages) {
        send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),messages);
    }
    public static void sendToAllPlayers(CustomPacketPayload... messages) {
        send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),messages);
    }
    public static void sendToPlayersInDimension(ResourceKey<Level> dimension,CustomPacketPayload... messages) {
        send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(()->dimension),messages);
    }
    public static void sendToPlayersTrackingChunk(LevelChunk chunk,CustomPacketPayload... messages) {
        send(net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(()->chunk),messages);
    }
    public static void sendToPlayersTrackingChunk(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.ChunkPos pos,CustomPacketPayload... messages) {
        sendToPlayersTrackingChunk(level.getChunk(pos.x,pos.z),messages);
    }
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity,CustomPacketPayload... messages) {
        send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),messages);
    }
}
