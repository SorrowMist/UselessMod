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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 网络级别的操作：新建一张、解散当前这张、给当前这张改名。
 *
 * <p>三件事共用一条包：它们的作用对象都是「界面上当前那张网络」，参数形状也一样
 * （一个动作 + 一个可选名字），拆成三条包只会多两份样板。</p>
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
            switch (packet.action()) {
                case RENAME -> rename(player, menu.getNetworkId(), packet.name());
                case NEW -> create(player, menu.getNetworkId());
                case DISSOLVE -> dissolve(player, menu.getNetworkId());
            }
        });
    }

    private static void rename(ServerPlayer player, java.util.UUID networkId, String name) {
        StaffLinkNetwork network = StaffLinkManager.networkById(player.server, networkId);
        if (network == null) {
            return;
        }
        network.setName(name);
        StaffLinkSavedData.get(player.server).markDirty();
        PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
    }

    private static void create(ServerPlayer player, java.util.UUID networkId) {
        ItemStack staff = StaffLinkManager.findStaffWithNetwork(player, networkId);
        if (staff.isEmpty()) {
            return;
        }
        StaffLinkNetwork created = StaffLinkManager.createNetwork(player.server, staff);
        if (created == null) {
            return;
        }
        player.containerMenu.broadcastChanges();
        PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(created));
    }

    private static void dissolve(ServerPlayer player, java.util.UUID networkId) {
        ItemStack staff = StaffLinkManager.findStaffWithNetwork(player, networkId);
        if (staff.isEmpty()) {
            return;
        }
        StaffLinkManager.dissolveActive(player.server, staff);
        player.containerMenu.broadcastChanges();

        StaffLinkNetwork next = StaffLinkManager.activeNetwork(player.server, staff);
        if (next == null) {
            // 一张都不剩了：界面没有可编辑的对象，直接关掉。
            player.closeContainer();
            return;
        }
        PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(next));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
