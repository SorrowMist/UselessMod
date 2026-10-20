package com.sorrowmist.useless.compat.forge1201.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import java.util.concurrent.CompletableFuture;

public final class IPayloadContext {
    private final NetworkEvent.Context context;
    IPayloadContext(NetworkEvent.Context context) { this.context=context; }
    public Player player() {
        return context.getSender()!=null ? context.getSender() : ClientAccess.player();
    }
    public CompletableFuture<Void> enqueueWork(Runnable task) { return context.enqueueWork(task); }
    private static final class ClientAccess {
        static Player player() { return net.minecraft.client.Minecraft.getInstance().player; }
    }
}
