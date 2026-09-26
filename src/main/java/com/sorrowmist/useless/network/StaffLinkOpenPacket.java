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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端请求打开无线物流界面（仅手持造化杖时发送）。 */
public class StaffLinkOpenPacket implements CustomPacketPayload {

    public static final Type<StaffLinkOpenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkOpenPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
            }, buffer -> new StaffLinkOpenPacket());

    public StaffLinkOpenPacket() {
    }

    public static void handle(StaffLinkOpenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) {
                return;
            }
            ItemStack staff = toolEntry.get().getKey();
            if (!(staff.getItem() instanceof EndlessBeafItem)) {
                return;
            }

            StaffLinkNetwork network = StaffLinkManager.activeNetwork(player.server, staff, true);
            if (network == null) {
                return;
            }

            player.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.translatable("menu.useless_mod.wireless_logistics");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                    return new StaffLinkMenu(containerId, inventory, network.id());
                }
            }, buffer -> buffer.writeUUID(network.id()));

            PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
