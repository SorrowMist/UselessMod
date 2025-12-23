package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetSlavePatternPacket {
    private final BlockPos pos;
    private final Direction direction;

    public SetSlavePatternPacket(BlockPos pos, Direction direction) {
        this.pos = pos;
        this.direction = direction;
    }

    public static void encode(SetSlavePatternPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.direction);
    }

    public static SetSlavePatternPacket decode(FriendlyByteBuf buffer) {
        return new SetSlavePatternPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class));
    }

    public static void handle(SetSlavePatternPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // 检查玩家主手是否是EndlessBeafItem
            if (player.getMainHandItem().getItem() instanceof EndlessBeafItem endlessBeaf) {
                // 调用添加从方块的方法
                // 不再检查方块类型，直接调用addAsSlave方法
                // addAsSlave方法内部会处理是否是扩展样板供应器
                endlessBeaf.addAsSlave(player.level(), packet.pos, packet.direction, player);
            }
        });
        context.setPacketHandled(true);
    }
}