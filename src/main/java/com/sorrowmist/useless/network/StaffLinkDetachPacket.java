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
 * 客户端请求解绑一个锚点。
 *
 * <p>服务端只认「当前打开的那个物流界面所属的网络」，因此包体里不带网络 ID——
 * 玩家无法用它去动别人的网络。</p>
 */
public record StaffLinkDetachPacket(GlobalPos anchor) implements CustomPacketPayload {

    public static final Type<StaffLinkDetachPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_detach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkDetachPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> StaffLinkRoute.writeAnchor(buffer, packet.anchor()),
                    buffer -> new StaffLinkDetachPacket(StaffLinkRoute.readAnchor(buffer)));

    public static void handle(StaffLinkDetachPacket packet, IPayloadContext ctx) {
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
            network.detach(packet.anchor());
            StaffLinkSavedData data = StaffLinkSavedData.get(player.server);
            data.markDirty();
            if (network.isEmpty()) {
                // 锚点清空 = 这张网络没有意义了：连登记一起删掉，界面随后自动关闭。
                data.remove(network.id());
                return;
            }
            StaffLinkEngine.wake(network.id());
            PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
