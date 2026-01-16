package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClearFluidPacket {
    private final BlockPos pos;
    private final boolean clearInput;
    private final int tankIndex;

    public ClearFluidPacket(BlockPos pos, boolean clearInput) {
        this(pos, clearInput, -1); // 默认值-1表示清空所有槽位
    }

    public ClearFluidPacket(BlockPos pos, boolean clearInput, int tankIndex) {
        this.pos = pos;
        this.clearInput = clearInput;
        this.tankIndex = tankIndex;
    }

    public static void encode(ClearFluidPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.clearInput);
        buffer.writeInt(packet.tankIndex);
    }

    public static ClearFluidPacket decode(FriendlyByteBuf buffer) {
        return new ClearFluidPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readInt()
        );
    }

    public static void handle(ClearFluidPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                Level level = player.level();
                if (level.hasChunkAt(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                        if (packet.clearInput) {
                            if (packet.tankIndex >= 0) {
                                // 清空指定索引的输入流体槽
                                furnace.clearInputFluid(packet.tankIndex);
                            } else {
                                // 清空所有输入流体槽
                                furnace.clearInputFluid();
                            }
                        } else {
                            if (packet.tankIndex >= 0) {
                                // 清空指定索引的输出流体槽
                                furnace.clearOutputFluid(packet.tankIndex);
                            } else {
                                // 清空所有输出流体槽
                                furnace.clearOutputFluid();
                            }
                        }
                    }
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}