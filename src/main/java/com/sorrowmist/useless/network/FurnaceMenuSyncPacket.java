package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.compat.forge1201.network.ByteBufCodecs;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.PortCodecs;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

public record FurnaceMenuSyncPacket(int containerId, int stateId, List<ItemStack> slots,
                                    ItemStack carried, List<Integer> data) implements CustomPacketPayload {
    public static final Type<FurnaceMenuSyncPacket> TYPE = new Type<>(UselessMod.id("furnace_menu_sync"));
    private static final StreamCodec<FriendlyByteBuf, List<ItemStack>> ITEMS =
            PortCodecs.ITEM_STACK.apply(ByteBufCodecs.list(512));
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<Integer>> DATA =
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(256));
    public static final StreamCodec<FriendlyByteBuf, FurnaceMenuSyncPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.containerId);
                buffer.writeVarInt(packet.stateId);
                ITEMS.encode(buffer, packet.slots);
                PortCodecs.ITEM_STACK.encode(buffer, packet.carried);
                DATA.encode(buffer, packet.data);
            }, buffer -> new FurnaceMenuSyncPacket(buffer.readVarInt(), buffer.readVarInt(),
                    ITEMS.decode(buffer), PortCodecs.ITEM_STACK.decode(buffer), DATA.decode(buffer)));

    public static void handle(FurnaceMenuSyncPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientPacketHandlers.handleFurnaceMenuSync(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
