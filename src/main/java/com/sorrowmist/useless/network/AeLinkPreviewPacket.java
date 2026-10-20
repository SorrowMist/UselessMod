package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.render.AeLinkHighlightRenderer;
import com.sorrowmist.useless.compat.ae.AeDeviceLinker;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端把「工具绑定的访问点 + 它连了哪些机器」发给客户端，用于手持杖子时的连接提示。
 *
 * <p>不带 request id：提示是幂等的「最新值覆盖」，客户端只关心当前这个画面该画哪些方块。</p>
 */
public record AeLinkPreviewPacket(ResourceLocation machineDimension,
                                  BlockPos accessPoint,
                                  List<BlockPos> machines) implements CustomPacketPayload {
    private static final int MAX_MACHINES = 1024;

    public static final Type<AeLinkPreviewPacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "ae_link_preview"));
    public static final StreamCodec<FriendlyByteBuf, AeLinkPreviewPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeResourceLocation(packet.machineDimension);
                        buf.writeBlockPos(packet.accessPoint);
                        buf.writeVarInt(packet.machines.size());
                        for (BlockPos pos : packet.machines) buf.writeBlockPos(pos);
                    },
                    buf -> {
                        ResourceLocation machineDimension = buf.readResourceLocation();
                        BlockPos accessPoint = buf.readBlockPos();
                        int size = buf.readVarInt();
                        if (size < 0 || size > MAX_MACHINES) {
                            throw new IllegalArgumentException("Invalid AE link preview size: " + size);
                        }
                        List<BlockPos> machines = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) machines.add(buf.readBlockPos());
                        return new AeLinkPreviewPacket(machineDimension, accessPoint, machines);
                    }
            );

    public AeLinkPreviewPacket {
        if (machines.size() > MAX_MACHINES) {
            throw new IllegalArgumentException("AE link preview is too large");
        }
        machines = List.copyOf(machines);
    }

    /** 服务端：读玩家手里的造化杖 → 取绑定坐标 → 把该访问点连着的机器回给客户端。 */
    public static void answer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var toolEntry = UselessItemUtils.findTargetToolInHands(player);
        if (toolEntry.isEmpty()) {
            return;
        }
        ItemStack tool = toolEntry.get().getKey();
        GlobalPos bound = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(tool, UComponents.WIRELESS_LINK_TARGET.get());
        if (bound == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AeLinkPreviewPacket(
                level.dimension().location(),
                bound.pos(),
                AeDeviceLinker.linkedMachines(level, bound.dimension(), bound.pos())));
    }

    public static void handle(AeLinkPreviewPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> AeLinkHighlightRenderer.setLinks(
                    packet.machineDimension(), packet.accessPoint(), packet.machines()));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
