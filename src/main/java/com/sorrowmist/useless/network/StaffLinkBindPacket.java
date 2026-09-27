package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.stafflink.StaffLinkBinding;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 客户端请求绑定 / 解绑一个（或一批）锚点。
 *
 * <p>绑定必须由客户端发起：修饰键状态（Shift、Ctrl）只存在于客户端，服务端在
 * {@code PlayerInteractEvent} 里读不到 Ctrl。所以服务端那边只负责取消原版交互，
 * 真正的绑定逻辑一律走这个包。</p>
 *
 * @param pos   被右键的方块坐标
 * @param batch 是否批量（按住 Ctrl）：把连在一起的同种机器一起绑定
 */
public record StaffLinkBindPacket(BlockPos pos, boolean batch) implements CustomPacketPayload {

    public static final Type<StaffLinkBindPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkBindPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeBlockPos(packet.pos());
                        buffer.writeBoolean(packet.batch());
                    },
                    buffer -> new StaffLinkBindPacket(buffer.readBlockPos(), buffer.readBoolean()));

    public static void handle(StaffLinkBindPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            // 服务端再验一次手持物：包是可以伪造的，不能只信客户端说「我拿着杖」。
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
            // 网络挂在归属者（玩家或队伍）名下，不再挂在杖上；杖只是「拿着它才能操作」的凭据。
            // 先并一次队，免得刚组队时这里先新建一张空网络。
            StaffLinkManager.mergePersonalIntoTeam(player.server, player);
            UUID ownerId = StaffLinkManager.ownerIdOf(player);
            if (packet.batch()) {
                StaffLinkBinding.toggleBatch(level, player, ownerId, packet.pos());
            } else {
                StaffLinkBinding.toggle(level, player, ownerId, packet.pos());
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}