package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 服务端 → 客户端：下发整张无线物流网络的最新状态。 */
public record StaffLinkSyncPacket(StaffLinkNetwork network) implements CustomPacketPayload {

    public static final Type<StaffLinkSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> StaffLinkNetwork.STREAM_CODEC.encode(buffer, packet.network()),
                    buffer -> new StaffLinkSyncPacket(StaffLinkNetwork.STREAM_CODEC.decode(buffer)));

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
