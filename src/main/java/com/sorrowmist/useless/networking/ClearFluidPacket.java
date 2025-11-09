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

    public ClearFluidPacket(BlockPos pos, boolean clearInput) {
        this.pos = pos;
        this.clearInput = clearInput;
    }

    public static void encode(ClearFluidPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.clearInput);
    }

    public static ClearFluidPacket decode(FriendlyByteBuf buffer) {
        return new ClearFluidPacket(
                buffer.readBlockPos(),
                buffer.readBoolean()
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
                            furnace.clearInputFluid();
                        } else {
                            furnace.clearOutputFluid();
                        }
                    }
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}