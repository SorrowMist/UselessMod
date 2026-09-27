package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 网络级别的操作：新建一张、解散当前这张、给当前这张改名。
 *
 * <p>三件事共用一条包：它们的作用对象都是「界面上当前那张网络」，参数形状也一样
 * （一个动作 + 一个可选名字），拆成三条包只会多两份样板。</p>
 *
 * <p>网络挂在<b>归属者</b>（玩家或队伍）名下，所以新建 / 解散只需要归属者 ID，
 * 不再需要「在玩家身上找一把登记了这张网络的杖」。每个动作前都过一遍
 * {@link StaffLinkManager#canAccess}，防止伪造包去动别人的网络。</p>
 */
public record StaffLinkNetworkPacket(Action action, String name) implements CustomPacketPayload {

    public enum Action {
        /** 新建一张空网络并切过去。 */
        NEW,
        /** 解散当前这张网络。 */
        DISSOLVE,
        /** 给当前这张网络改名。 */
        RENAME
    }

    public static final Type<StaffLinkNetworkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkNetworkPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeEnum(packet.action());
                        buffer.writeUtf(packet.name(), StaffLinkNetwork.MAX_NAME);
                    },
                    buffer -> new StaffLinkNetworkPacket(
                            buffer.readEnum(Action.class),
                            buffer.readUtf(StaffLinkNetwork.MAX_NAME)));

    public static void handle(StaffLinkNetworkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof StaffLinkMenu menu)) {
                return;
            }
            UUID networkId = menu.getNetworkId();
            // 越权校验：只能动自己（或自己队伍）名下的网络。
            if (!StaffLinkManager.canAccess(player.server, player, networkId)) {
                return;
            }
            switch (packet.action()) {
                case RENAME -> rename(player, networkId, packet.name());
                case NEW -> create(player);
                case DISSOLVE -> dissolve(player, networkId);
            }
        });
    }

    private static void rename(ServerPlayer player, UUID networkId, String name) {
        StaffLinkNetwork network = StaffLinkManager.networkById(player.server, networkId);
        if (network == null) {
            return;
        }
        network.setName(name);
        StaffLinkSavedData.get(player.server).markDirty();
        PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(
                player.server, StaffLinkManager.ownerIdOf(player), network));
    }

    private static void create(ServerPlayer player) {
        UUID ownerId = StaffLinkManager.ownerIdOf(player);
        StaffLinkNetwork created = StaffLinkManager.createNetwork(player.server, ownerId);
        if (created == null) {
            return;
        }
        // 新网络成了当前网络，服务端菜单也要一起切过去。
        if (player.containerMenu instanceof StaffLinkMenu menu) {
            menu.setNetworkId(created.id());
        }
        PacketDistributor.sendToPlayer(player,
                StaffLinkSyncPacket.of(player.server, ownerId, created));
    }

    private static void dissolve(ServerPlayer player, UUID networkId) {
        UUID ownerId = StaffLinkManager.ownerIdOf(player);
        StaffLinkManager.dissolveActive(player.server, ownerId);

        StaffLinkNetwork next = StaffLinkManager.activeNetwork(player.server, ownerId);
        if (next == null) {
            // 一张都不剩了：界面没有可编辑的对象，直接关掉。
            player.closeContainer();
            return;
        }
        // 解散后会自动切到相邻的一张，服务端菜单必须跟着走。
        if (player.containerMenu instanceof StaffLinkMenu menu) {
            menu.setNetworkId(next.id());
        }
        PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(player.server, ownerId, next));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
