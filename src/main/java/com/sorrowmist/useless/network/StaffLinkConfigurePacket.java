package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端提交一条线路配置。
 *
 * <p>服务端是唯一的校验点：只接受「当前打开的网络 + 已绑定的锚点 + 当前环境支持的资源类型」，
 * 数值再由 {@link StaffLinkRoute} 的构造器夹取一次。通过后回发整网快照，客户端以服务端为准。</p>
 */
public record StaffLinkConfigurePacket(GlobalPos anchor, int route, StaffLinkRoute config)
        implements CustomPacketPayload {

    public static final Type<StaffLinkConfigurePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkConfigurePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeVarInt(packet.route());
                        StaffLinkRoute.STREAM_CODEC.encode(buffer, packet.config());
                    },
                    buffer -> {
                        GlobalPos anchor = StaffLinkRoute.readAnchor(buffer);
                        int route = buffer.readVarInt();
                        StaffLinkRoute config = StaffLinkRoute.STREAM_CODEC.decode(buffer);
                        return new StaffLinkConfigurePacket(anchor, route, config);
                    });

    public static void handle(StaffLinkConfigurePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof StaffLinkMenu menu)) {
                return;
            }
            StaffLinkNetwork network = StaffLinkManager.networkById(player.server, menu.getNetworkId());
            if (network == null || !network.isBound(packet.anchor())) {
                return;
            }
            if (packet.route() < 0 || packet.route() >= StaffLinkNetwork.ROUTE_COUNT) {
                return;
            }
            if (!packet.config().medium().isSupported()) {
                return;
            }

            // 以包里的 anchor / route 为准重写一次，防止客户端送来不匹配的字段。
            StaffLinkRoute sanitized = new StaffLinkRoute(
                    packet.anchor(),
                    packet.route(),
                    packet.config().enabled(),
                    packet.config().flow(),
                    packet.config().medium(),
                    packet.config().amount(),
                    packet.config().interval(),
                    packet.config().side(),
                    packet.config().trigger(),
                    packet.config().weight(),
                    packet.config().filter());

            network.putRoute(sanitized);
            StaffLinkSavedData.get(player.server).markDirty();
            // 改完立刻重跑：新开的配对不该等上一次排定的退避。
            StaffLinkEngine.wake(network.id());
            PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
