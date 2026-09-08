package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.constructionwand.ConstructionWandLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ConstructionWandPreviewRequestPacket(int requestId, boolean air,
                                                    BlockHitResult hit, InteractionHand hand)
        implements CustomPacketPayload {
    public static final Type<ConstructionWandPreviewRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "construction_wand_preview_request"));
    public static final StreamCodec<FriendlyByteBuf, ConstructionWandPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.requestId);
                        buf.writeBoolean(packet.air);
                        if (!packet.air) buf.writeBlockHitResult(packet.hit);
                        buf.writeEnum(packet.hand);
                    },
                    buf -> {
                        int requestId = buf.readVarInt();
                        boolean air = buf.readBoolean();
                        BlockHitResult hit = air ? null : buf.readBlockHitResult();
                        return new ConstructionWandPreviewRequestPacket(
                                requestId, air, hit, buf.readEnum(InteractionHand.class));
                    }
            );

    public static ConstructionWandPreviewRequestPacket block(int requestId, BlockHitResult hit,
                                                              InteractionHand hand) {
        return new ConstructionWandPreviewRequestPacket(requestId, false, hit, hand);
    }

    public static ConstructionWandPreviewRequestPacket air(int requestId, InteractionHand hand) {
        return new ConstructionWandPreviewRequestPacket(requestId, true, null, hand);
    }

    public static void handle(ConstructionWandPreviewRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(player, new ConstructionWandPreviewPacket(
                    packet.requestId,
                    ConstructionWandLogic.preview(player, packet.hand, packet.hit, packet.air)));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
