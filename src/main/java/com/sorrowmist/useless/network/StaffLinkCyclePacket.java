package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 切换当前网络：手持造化杖 Shift+滚轮，或者界面上的 {@code <} / {@code >} 按钮。
 *
 * <p>包体只有一个方向值，服务端自己验手持、自己算下一张——客户端无从指定目标。</p>
 *
 * <p>切的是<b>归属者</b>（玩家或队伍）名下的当前网络，与手上这把杖无关：换一把杖、
 * 把杖放进箱子，切到的那张仍然是你的。</p>
 */
public record StaffLinkCyclePacket(int delta) implements CustomPacketPayload {

    public static final Type<StaffLinkCyclePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_cycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkCyclePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeVarInt(packet.delta()),
                    buffer -> new StaffLinkCyclePacket(buffer.readVarInt()));

    public static void handle(StaffLinkCyclePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // 服务端再验一次手持物与模式开关：包是可以伪造的。
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) {
                return;
            }
            ItemStack staff = toolEntry.get().getKey();
            if (!(staff.getItem() instanceof EndlessBeafItem)) {
                return;
            }
            if (!EndlessBeafItem.isStaffLinkEnabled(staff)) {
                return;
            }

            // 先并一次队，免得刚组队时这里先给他新建一张空网络（看着像配置全丢了）。
            StaffLinkManager.mergePersonalIntoTeam(player.server, player);
            UUID ownerId = StaffLinkManager.ownerIdOf(player);
            if (!StaffLinkManager.cycleActive(player.server, ownerId, packet.delta())) {
                return;
            }

            List<UUID> ids = StaffLinkManager.networkIds(player.server, ownerId);
            int index = Math.floorMod(StaffLinkManager.activeIndex(player.server, ownerId),
                    Math.max(1, ids.size()));
            StaffLinkNetwork network = StaffLinkManager.activeNetwork(player.server, ownerId);
            if (network != null) {
                // 界面开着时要跟着换一张网络，否则后续的编辑还是打到旧网络上。
                // 服务端菜单也必须换：所有编辑包都按菜单里的 networkId 寻址。
                if (player.containerMenu instanceof StaffLinkMenu menu) {
                    menu.setNetworkId(network.id());
                }
                PacketDistributor.sendToPlayer(player,
                        StaffLinkSyncPacket.of(player.server, ownerId, network));
            }
            player.displayClientMessage(Component.translatable(
                    "gui.useless_mod.wireless_logistics.network_switched",
                    index + 1, ids.size(), displayName(network)), true);
        });
    }

    static Component displayName(StaffLinkNetwork network) {
        if (network == null || network.name().isEmpty()) {
            return Component.translatable("gui.useless_mod.wireless_logistics.network_unnamed");
        }
        return Component.literal(network.name());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
