package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ResetMasterPatternPacket {
    public ResetMasterPatternPacket() {
        // 空构造函数
    }

    public static void encode(ResetMasterPatternPacket packet, FriendlyByteBuf buffer) {
        // 不需要编码任何数据
    }

    public static ResetMasterPatternPacket decode(FriendlyByteBuf buffer) {
        return new ResetMasterPatternPacket();
    }

    public static void handle(ResetMasterPatternPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // 检查玩家主手是否是EndlessBeafItem
            if (player.getMainHandItem().getItem() instanceof EndlessBeafItem) {
                // 调用重置主方块的静态方法
                EndlessBeafItem.resetMasterPatternProvider(player.level());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("已重置扩展样板供应器主方块选择").withStyle(net.minecraft.ChatFormatting.RED));
            }
        });
        context.setPacketHandled(true);
    }
}