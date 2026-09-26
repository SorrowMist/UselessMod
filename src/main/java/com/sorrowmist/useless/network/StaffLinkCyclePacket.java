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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 切换当前网络：手持造化杖 Shift+滚轮，或者界面上的 {@code <} / {@code >} 按钮。
 *
 * <p>包体只有一个方向值，服务端自己找杖、自己算下一张——客户端无从指定目标。</p>
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
            ItemStack staff = findStaff(player);
            if (staff.isEmpty()) {
                return;
            }
            if (!StaffLinkManager.cycleActive(player.server, staff, packet.delta())) {
                return;
            }
            // 组件改了要显式同步，否则客户端手上的杖还是旧的下标。
            player.containerMenu.broadcastChanges();

            List<UUID> ids = StaffLinkManager.networkIds(staff);
            int index = Math.floorMod(StaffLinkManager.activeIndex(staff), Math.max(1, ids.size()));
            StaffLinkNetwork network = StaffLinkManager.activeNetwork(player.server, staff);
            if (network != null) {
                // 界面开着时要跟着换一张网络，否则后续的编辑还是打到旧网络上。
                // 服务端菜单也必须换：所有编辑包都按菜单里的 networkId 寻址。
                if (player.containerMenu instanceof StaffLinkMenu menu) {
                    menu.setNetworkId(network.id());
                }
                PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
            }
            player.displayClientMessage(Component.translatable(
                    "gui.useless_mod.wireless_logistics.network_switched",
                    index + 1, ids.size(), displayName(network)), true);
        });
    }

    /**
     * 找玩家身上的造化杖：先手上，再背包。
     *
     * <p>界面开着时玩家完全可能把杖换到别的格子，只看手持会「按了没反应」。</p>
     */
    private static ItemStack findStaff(ServerPlayer player) {
        var toolEntry = UselessItemUtils.findTargetToolInHands(player);
        if (toolEntry.isPresent()) {
            return toolEntry.get().getKey();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof EndlessBeafItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
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
