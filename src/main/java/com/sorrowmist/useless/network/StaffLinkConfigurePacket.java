package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.content.stafflink.LinkFilterPattern;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
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
            // 越权校验：界面所属的网络必须挂在他（或他队伍）名下。
            if (!StaffLinkManager.canAccess(player.server, player, menu.getNetworkId())) {
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
                    packet.config().weight(),
                    sanitizeFilter(packet.config().filter()));

            network.putRoute(sanitized);
            StaffLinkSavedData.get(player.server).markDirty();
            // 改完立刻重跑：新开的配对不该等上一次排定的退避。
            StaffLinkEngine.wake(network.id());
            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                    player.server, StaffLinkManager.ownerIdOf(player), network));
        });
    }

    /**
     * 逐格清洗过滤器。
     *
     * <p>第九轮的过滤器一格里有三样东西：标记、模式文本、两个数量限制。{@link StaffLinkRoute}
     * 的构造器只会把「格数」补齐到 {@code FILTER_LIMIT}，不校验格<b>内容</b>；
     * 一个被改过的客户端可以塞进来非法模式或超长的限制值。所以这里逐格重造一遍：</p>
     *
     * <ul>
     *   <li>模式文本必须能被 {@link LinkFilterPattern#parse} 接受，否则整格清空
     *       （宁可「什么都不搬」，也不要留一个语义不明的字符串在存档里）。</li>
     *   <li>两个限制夹到非负；{@code 0} 就是不限制。</li>
     * </ul>
     *
     * <p><b>不能跳过这一步直接把 {@code packet.config().filter()} 传下去</b>：
     * 那样等于把服务端当成客户端的镜子，任何越界值都会直接落进存档。</p>
     */
    private static java.util.List<LinkFilterSlot> sanitizeFilter(
            java.util.List<LinkFilterSlot> received) {
        java.util.List<LinkFilterSlot> cleaned =
                new java.util.ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            LinkFilterSlot slot = received != null && index < received.size()
                    ? received.get(index) : null;
            if (slot == null || slot.isEmpty()) {
                cleaned.add(LinkFilterSlot.EMPTY);
                continue;
            }
            if (slot.isPattern()) {
                LinkFilterPattern pattern = LinkFilterPattern.parse(slot.pattern());
                if (pattern == null) {
                    cleaned.add(LinkFilterSlot.EMPTY);
                    continue;
                }
                cleaned.add(LinkFilterSlot.ofPattern(pattern)
                        .withLimits(slot.keepAtSource(), slot.maxInto()));
                continue;
            }
            // 物品 / 流体标记：构造器已经归一化（count=1、类型互斥），限制值再夹一次非负。
            cleaned.add(slot.withLimits(Math.max(0L, slot.keepAtSource()),
                    Math.max(0L, slot.maxInto())));
        }
        return cleaned;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
