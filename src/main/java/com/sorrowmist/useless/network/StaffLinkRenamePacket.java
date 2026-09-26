package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
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
 * 客户端给某个已绑定锚点改名（空串表示恢复成方块本名）。
 *
 * <p>与其它无线物流包一样，服务端只认「当前打开的那个界面所属的网络」，包体里不带网络 ID。</p>
 */
public record StaffLinkRenamePacket(GlobalPos anchor, String name) implements CustomPacketPayload {

    public static final Type<StaffLinkRenamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkRenamePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeUtf(packet.name(), StaffLinkNetwork.MAX_ANCHOR_NAME);
                    },
                    buffer -> new StaffLinkRenamePacket(
                            StaffLinkRoute.readAnchor(buffer),
                            buffer.readUtf(StaffLinkNetwork.MAX_ANCHOR_NAME)));

    public static void handle(StaffLinkRenamePacket packet, IPayloadContext ctx) {
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
            network.setAnchorName(packet.anchor(), packet.name());
            StaffLinkSavedData.get(player.server).markDirty();
            PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
