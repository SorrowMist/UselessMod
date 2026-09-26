package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.content.stafflink.StaffLinkTargets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 服务端 → 客户端：某张网络最近一次搬运的结果。
 *
 * <p>界面开着时每秒推一次，用来显示「上次请求搬 N 个、实际搬走 M 个、分给了 K 个输出」——
 * 有这行读数，玩家就不用靠猜来判断「设置到底生效没有」。</p>
 */
public record StaffLinkStatusPacket(UUID networkId, long requested, long moved, int targets, long tick,
                                    StaffLinkTargets.TransferBlocker blocker)
        implements CustomPacketPayload {

    public static final Type<StaffLinkStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkStatusPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeUUID(packet.networkId());
                        buffer.writeVarLong(packet.requested());
                        buffer.writeVarLong(packet.moved());
                        buffer.writeVarInt(packet.targets());
                        buffer.writeVarLong(packet.tick());
                        buffer.writeEnum(packet.blocker());
                    },
                    buffer -> new StaffLinkStatusPacket(
                            buffer.readUUID(),
                            buffer.readVarLong(),
                            buffer.readVarLong(),
                            buffer.readVarInt(),
                            buffer.readVarLong(),
                            buffer.readEnum(StaffLinkTargets.TransferBlocker.class)));

    public static void handle(StaffLinkStatusPacket packet, IPayloadContext context) {
        // 客户端类型统一收敛到 ClientPacketHandlers，避免专用服务器加载类时解析到客户端类。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleStaffLinkStatus(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
