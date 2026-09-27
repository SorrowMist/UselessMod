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

import java.util.UUID;

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
            // 杖是「打开配置界面」的钥匙，所以仍然要求手持。
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) {
                return;
            }
            ItemStack staff = toolEntry.get().getKey();
            if (!(staff.getItem() instanceof EndlessBeafItem)) {
                return;
            }

            // 但网络挂在归属者（玩家或队伍）名下，与手上这把杖无关。
            // 先并一次队：玩家可能刚被拉进队伍，不等 20 tick 的周期刷新也能立刻看到合并后的结果，
            // 否则这里会先给他新建一张空网络，看着像「配置全没了」。
            StaffLinkManager.mergePersonalIntoTeam(player.server, player);
            UUID ownerId = StaffLinkManager.ownerIdOf(player);
            StaffLinkNetwork network = StaffLinkManager.activeNetwork(player.server, ownerId, true);
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

            PacketDistributor.sendToPlayer(player, StaffLinkSyncPacket.of(player.server, ownerId, network));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
