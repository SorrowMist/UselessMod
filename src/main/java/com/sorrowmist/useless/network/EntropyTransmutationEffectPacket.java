package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.render.EntropyTransmutationPreviewRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 熵变转化成功那一刻的瞬时反馈：让附近客户端在该位置播放一次短暂的高亮动画。
 *
 * <p>与 {@link EntropyTransmutationPreviewPacket} 的持续预览不同，这个包只携带位置，
 * 客户端自行按本地时间渲染约 0.4 秒的扩散淡出线框。</p>
 */
public record EntropyTransmutationEffectPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<EntropyTransmutationEffectPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "entropy_transmutation_effect"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationEffectPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.pos),
                    buf -> new EntropyTransmutationEffectPacket(buf.readBlockPos())
            );

    public static void handle(EntropyTransmutationEffectPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> EntropyTransmutationPreviewRenderer.triggerEffect(packet.pos()));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
