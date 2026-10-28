package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 客户端给某个已绑定锚点改名（空串表示恢复成方块本名）。
 *
 * <p>networkId 仅用于拒绝切网前发出的旧指令，不能指定编辑另一张网络。
 * 服务端仍验证当前菜单的网络及归属权限。</p>
 */
public record StaffLinkRenamePacket(UUID networkId, GlobalPos anchor, String name) implements CustomPacketPayload {

    public static final Type<StaffLinkRenamePacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "staff_link_rename"));
    public static final StreamCodec<FriendlyByteBuf, StaffLinkRenamePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeUUID(packet.networkId());
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeUtf(packet.name(), StaffLinkNetwork.MAX_ANCHOR_NAME);
                    },
                    buffer -> new StaffLinkRenamePacket(buffer.readUUID(),
                            StaffLinkRoute.readAnchor(buffer),
                            buffer.readUtf(StaffLinkNetwork.MAX_ANCHOR_NAME)));

    boolean applyTo(StaffLinkNetwork network) {
        if (!networkId.equals(network.id()) || !network.isBound(anchor)) return false;
        network.setAnchorName(anchor, name);
        return true;
    }

    public static void handle(StaffLinkRenamePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof StaffLinkMenu menu)) {
                return;
            }
            // 越权校验：界面所属的网络必须挂在他（或他队伍）名下。
            if (!StaffLinkManager.canAccess(player.server, player, menu.getNetworkId())) {
                return;
            }
            StaffLinkNetwork network = StaffLinkManager.networkById(player.server, menu.getNetworkId());
            if (network == null) return;
            if (packet.applyTo(network)) StaffLinkSavedData.get(player.server).markDirty();
            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                    player.server, StaffLinkManager.ownerIdOf(player), network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
