package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端把「未成型核心当前最低的那个错误方块」发给附近客户端，用于世界线框高亮。
 *
 * <p>只发一条最低错误而不是全部：玩家按从下往上的顺序修，一次盯一个方块就够了。
 * {@code errorPos} 为 {@code null} 表示当前没有可高亮的错误，客户端据此清掉该核心的残留框。</p>
 *
 * <p>不带 request id：提示是幂等的「最新值覆盖」。核心坐标 {@code corePos} 即客户端缓存的主键。</p>
 */
public record AlloyFurnaceErrorHighlightPacket(BlockPos corePos,
                                               @Nullable BlockPos errorPos) implements CustomPacketPayload {
    public static final Type<AlloyFurnaceErrorHighlightPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "alloy_furnace_error_highlight"));
    public static final StreamCodec<FriendlyByteBuf, AlloyFurnaceErrorHighlightPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.corePos);
                        buf.writeBoolean(packet.errorPos != null);
                        if (packet.errorPos != null) {
                            buf.writeBlockPos(packet.errorPos);
                        }
                    },
                    buf -> {
                        BlockPos corePos = buf.readBlockPos();
                        BlockPos errorPos = buf.readBoolean() ? buf.readBlockPos() : null;
                        return new AlloyFurnaceErrorHighlightPacket(corePos, errorPos);
                    });

    public AlloyFurnaceErrorHighlightPacket {
        corePos = corePos.immutable();
        errorPos = errorPos == null ? null : errorPos.immutable();
    }

    public static void handle(AlloyFurnaceErrorHighlightPacket packet, IPayloadContext context) {
        // 客户端类型收敛到 ClientPacketHandlers：本类会在专用服务端被加载，
        // 直接引用客户端渲染器会让校验器在服务端解析到客户端类型。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleAlloyFurnaceErrorHighlight(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
