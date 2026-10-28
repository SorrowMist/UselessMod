package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 客户端请求把某个锚点挪到完整列表的指定位置。
 *
 * <p>目标下标仍由客户端从过滤后的可见行换算。网络 ID 和顺序令牌只作前置条件：
 * 服务端仍只编辑当前已打开菜单、且玩家有权访问的网络。切网或队友已重排时拒绝旧指令，
 * 并回发当前快照，不能把旧下标应用到新顺序。令牌不是授权凭证。</p>
 */
public record StaffLinkReorderPacket(UUID networkId, GlobalPos anchor, int targetIndex, UUID orderToken)
        implements CustomPacketPayload {

    public static final Type<StaffLinkReorderPacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "staff_link_reorder"));
    public static final StreamCodec<FriendlyByteBuf, StaffLinkReorderPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeUUID(packet.networkId());
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeVarInt(packet.targetIndex());
                        buffer.writeUUID(packet.orderToken());
                    },
                    buffer -> new StaffLinkReorderPacket(buffer.readUUID(),
                            StaffLinkRoute.readAnchor(buffer), buffer.readVarInt(), buffer.readUUID()));

    /** Deterministic across client/server JVMs; dimension names and packed positions are unambiguous. */
    public static UUID orderToken(List<GlobalPos> anchors) {
        StringBuilder key = new StringBuilder();
        for (GlobalPos anchor : anchors)
            key.append(anchor.dimension().location()).append('@').append(anchor.pos().asLong()).append('\n');
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    boolean applyTo(StaffLinkNetwork network) {
        return networkId.equals(network.id()) && orderToken.equals(orderToken(network.anchors()))
                && network.isBound(anchor) && network.moveAnchorTo(anchor, targetIndex);
    }

    public static void handle(StaffLinkReorderPacket packet, IPayloadContext ctx) {
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
            if (packet.applyTo(network)) {
                StaffLinkSavedData.get(player.server).markDirty();
                // 次序变了，立刻重跑一遍，别让玩家等上一次排定的退避。
                StaffLinkEngine.wake(network.id());
            }
            // Rejected stale commands and no-ops must also correct the client's snapshot.
            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                    player.server, StaffLinkManager.ownerIdOf(player), network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
