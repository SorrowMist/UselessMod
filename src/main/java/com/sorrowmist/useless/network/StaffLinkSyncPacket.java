package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端：下发整张无线物流网络的最新状态。
 *
 * <p>顺带把「第几张 / 共几张」一起发下去：网络列表现在挂在<b>归属者</b>（玩家或队伍）名下、
 * 存在服务端存档里，客户端手里没有这份列表，界面上那句「网络 2/3」就没法自己算出来。</p>
 */
public record StaffLinkSyncPacket(StaffLinkNetwork network, int index, int count)
        implements CustomPacketPayload {

    public static final Type<StaffLinkSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        StaffLinkNetwork.STREAM_CODEC.encode(buffer, packet.network());
                        buffer.writeVarInt(packet.index());
                        buffer.writeVarInt(packet.count());
                    },
                    buffer -> new StaffLinkSyncPacket(
                            StaffLinkNetwork.STREAM_CODEC.decode(buffer),
                            buffer.readVarInt(), buffer.readVarInt()));

    /** 服务端：按归属者算出位置信息，一起打包。 */
    public static StaffLinkSyncPacket of(MinecraftServer server, UUID ownerId, StaffLinkNetwork network) {
        List<UUID> ids = StaffLinkManager.networkIds(server, ownerId);
        int index = ids.indexOf(network.id());
        return new StaffLinkSyncPacket(network, Math.max(0, index), Math.max(1, ids.size()));
    }

    public static void handle(StaffLinkSyncPacket packet, IPayloadContext context) {
        // 客户端类型统一收敛到 ClientPacketHandlers，避免专用服务器加载类时解析到客户端类。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleStaffLinkSync(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
