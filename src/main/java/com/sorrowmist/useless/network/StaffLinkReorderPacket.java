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
 * 客户端请求把某个锚点挪到列表里的指定位置。
 *
 * <p>次序影响的是<b>同权重下谁先拿</b>：引擎在同一条线路上按权重降序排，权重相同时保持列表
 * 次序，而分配时除不尽的余数留给靠前的那个。</p>
 *
 * <p>传的是<b>目标下标</b>而不是「上移/下移一格」：界面上可能开着搜索过滤，可见邻居未必是
 * 列表里的邻居。由客户端把「越过上一个可见行」翻译成目标下标，服务端夹到合法范围后照做，
 * 这样过滤状态下也是「眼睛看到的那样动」。</p>
 *
 * <p>与解绑一样，服务端只认「当前打开的那个物流界面所属的网络」，包体里不带网络 ID，
 * 玩家无法用它去动别人的网络。</p>
 *
 * @param anchor      要移动的锚点
 * @param targetIndex 目标位置（在完整锚点列表里的下标）
 */
public record StaffLinkReorderPacket(GlobalPos anchor, int targetIndex) implements CustomPacketPayload {

    public static final Type<StaffLinkReorderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_reorder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkReorderPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        StaffLinkRoute.writeAnchor(buffer, packet.anchor());
                        buffer.writeVarInt(packet.targetIndex());
                    },
                    buffer -> new StaffLinkReorderPacket(
                            StaffLinkRoute.readAnchor(buffer), buffer.readVarInt()));

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
            if (network == null || !network.isBound(packet.anchor())) {
                return;
            }
            if (!network.moveAnchorTo(packet.anchor(), packet.targetIndex())) {
                // 位置没变，不必回包。
                return;
            }
            StaffLinkSavedData.get(player.server).markDirty();
            // 次序变了，立刻重跑一遍，别让玩家等上一次排定的退避。
            StaffLinkEngine.wake(network.id());
            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                    player.server, StaffLinkManager.ownerIdOf(player), network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
