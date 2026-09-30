package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端把「当前网络各锚点按流向分好的三组坐标」发给客户端，用于手持杖时的世界高亮。
 *
 * <p>分三组而不是带一个流向字段：客户端只关心「用什么颜色画哪个方块」，
 * 分组后渲染侧就是三次遍历，不用逐条判类型。</p>
 *
 * <ul>
 *   <li>{@code release} —— 至少有一条启用的 {@link LinkFlow#RELEASE} 线路（发送端，暖色）</li>
 *   <li>{@code absorb} —— 没有启用的 RELEASE，但有启用的 ABSORB（接收端，冷色）</li>
 *   <li>{@code disabled} —— 所有线路都没启用（暗灰）</li>
 * </ul>
 *
 * <p>同一锚点同时有启用的 RELEASE 和 ABSORB 时归 {@code release}（RELEASE 优先）。
 * 不带 request id：提示是幂等的「最新值覆盖」。</p>
 */
public record StaffLinkHighlightPacket(List<GlobalPos> release,
                                       List<GlobalPos> absorb,
                                       List<GlobalPos> disabled) implements CustomPacketPayload {
    private static final int MAX_ANCHORS = 4096;

    public static final Type<StaffLinkHighlightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_highlight"));
    public static final StreamCodec<FriendlyByteBuf, StaffLinkHighlightPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        writeAnchors(buf, packet.release);
                        writeAnchors(buf, packet.absorb);
                        writeAnchors(buf, packet.disabled);
                    },
                    buf -> new StaffLinkHighlightPacket(
                            readAnchors(buf), readAnchors(buf), readAnchors(buf))
            );

    public StaffLinkHighlightPacket {
        release = List.copyOf(release);
        absorb = List.copyOf(absorb);
        disabled = List.copyOf(disabled);
        if (release.size() + absorb.size() + disabled.size() > MAX_ANCHORS) {
            throw new IllegalArgumentException("Staff link highlight is too large");
        }
    }

    /** 锚点带维度，所以复用 {@link StaffLinkRoute} 现成的「维度 + 坐标」编解码。 */
    private static void writeAnchors(FriendlyByteBuf buf, List<GlobalPos> anchors) {
        buf.writeVarInt(anchors.size());
        for (GlobalPos anchor : anchors) {
            StaffLinkRoute.writeAnchor(buf, anchor);
        }
    }

    private static List<GlobalPos> readAnchors(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ANCHORS) {
            throw new IllegalArgumentException("Invalid staff link highlight size: " + size);
        }
        List<GlobalPos> anchors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            anchors.add(StaffLinkRoute.readAnchor(buf));
        }
        return anchors;
    }

    /** 服务端：读玩家手里的杖 → 取他（或他队伍）当前的网络 → 按流向三分类回给客户端。 */
    public static void answer(ServerPlayer player) {
        // 高亮仍然要求「手持一把开着无线物流模式的杖」——这是玩家主动要看的意思表示。
        var toolEntry = UselessItemUtils.findTargetToolInHands(player);
        if (toolEntry.isEmpty()) {
            return;
        }
        ItemStack staff = toolEntry.get().getKey();
        if (!(staff.getItem() instanceof EndlessBeafItem)) {
            // findTargetToolInHands 也会认 omnitools 的扳手模式，那类工具没有物流网络。
            return;
        }
        if (!EndlessBeafItem.isStaffLinkEnabled(staff)) {
            return;
        }

        MinecraftServer server = player.getServer();
        // 但高亮的是「他名下的当前网络」——换一把杖、把杖放进背包都不影响看到的是哪张网络。
        StaffLinkNetwork network = server == null
                ? null : StaffLinkManager.activeNetwork(server, StaffLinkManager.ownerIdOf(player));
        if (network == null) {
            // 一张有效网络都没有：回空包，让客户端把上一张网络的残留框清掉。
            PacketDistributor.sendToPlayer(player,
                    new StaffLinkHighlightPacket(List.of(), List.of(), List.of()));
            return;
        }

        List<GlobalPos> release = new ArrayList<>();
        List<GlobalPos> absorb = new ArrayList<>();
        List<GlobalPos> disabled = new ArrayList<>();
        for (GlobalPos anchor : network.anchors()) {
            boolean hasRelease = false;
            boolean hasAbsorb = false;
            for (StaffLinkRoute route : network.routes()) {
                if (!route.enabled() || !route.anchor().equals(anchor)) {
                    continue;
                }
                if (route.flow() == LinkFlow.RELEASE) {
                    hasRelease = true;
                } else {
                    hasAbsorb = true;
                }
            }
            if (hasRelease) {
                release.add(anchor);
            } else if (hasAbsorb) {
                absorb.add(anchor);
            } else {
                disabled.add(anchor);
            }
        }
        PacketDistributor.sendToPlayer(player,
                new StaffLinkHighlightPacket(release, absorb, disabled));
    }

    public static void handle(StaffLinkHighlightPacket packet, IPayloadContext context) {
        // 客户端类型收敛到 ClientPacketHandlers：本类会在专用服务端被加载，
        // 直接引用客户端渲染器会让校验器在服务端解析到客户端类型。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleStaffLinkHighlight(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
