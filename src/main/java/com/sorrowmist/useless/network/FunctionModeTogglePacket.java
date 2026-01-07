package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumSet;

public record FunctionModeTogglePacket(FunctionMode mode) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, FunctionModeTogglePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeEnum(packet.mode),
                    buf -> new FunctionModeTogglePacket(buf.readEnum(FunctionMode.class))
            );
    public static final Type<FunctionModeTogglePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "function_mode_toggle"));

    public static void handle(FunctionModeTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            EnumSet<FunctionMode> modes = stack.getOrDefault(
                    UComponents.FunctionModesComponent,
                    EnumSet.noneOf(FunctionMode.class)
            );

            FunctionMode m = msg.mode;
            // 使用 getTitleComponent 但去掉颜色
            String titleText = m.getTitleComponent().getString();  // 只取纯文本
            Component message;

            if (m == FunctionMode.ENHANCED_CHAIN_MINING) {
                // 特殊处理：增强连锁挖掘
                if (modes.contains(FunctionMode.ENHANCED_CHAIN_MINING)) {
                    // 关闭增强 → 退回默认连锁
                    modes.remove(FunctionMode.ENHANCED_CHAIN_MINING);
                    modes.add(FunctionMode.CHAIN_MINING);

                    message = Component.literal(titleText + "：关闭（默认连锁）");
                } else {
                    // 开启增强
                    modes.remove(FunctionMode.CHAIN_MINING);
                    modes.add(FunctionMode.ENHANCED_CHAIN_MINING);

                    message = Component.literal(titleText + "：开启");
                }
            } else {
                // 其他模式：普通开关
                if (modes.contains(m)) {
                    modes.remove(m);
                    message = Component.literal(titleText + "：关闭");
                } else {
                    modes.add(m);
                    message = Component.literal(titleText + "：开启");
                }
            }

            // 保存
            stack.set(UComponents.FunctionModesComponent,
                      modes.isEmpty() ? null : EnumSet.copyOf(modes)
            );

            // 发送纯白文字行动栏消息
            player.displayClientMessage(message, true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}