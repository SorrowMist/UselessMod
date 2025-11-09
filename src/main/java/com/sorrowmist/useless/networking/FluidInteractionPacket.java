package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FluidInteractionPacket {
    private final BlockPos pos;
    private final boolean isInputTank;
    private final boolean isFill;
    private final int button;

    public FluidInteractionPacket(BlockPos pos, boolean isInputTank, boolean isFill, int button) {
        this.pos = pos;
        this.isInputTank = isInputTank;
        this.isFill = isFill;
        this.button = button;
    }

    public static void encode(FluidInteractionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.isInputTank);
        buffer.writeBoolean(packet.isFill);
        buffer.writeInt(packet.button);
    }

    public static FluidInteractionPacket decode(FriendlyByteBuf buffer) {
        return new FluidInteractionPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readInt()
        );
    }

    public static void handle(FluidInteractionPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                Level level = player.level();
                if (level.hasChunkAt(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                        ItemStack carried = player.containerMenu.getCarried();
                        if (!carried.isEmpty()) {
                            furnace.interactWithFluid(player, carried, packet.isInputTank, packet.isFill);
                            // 更新客户端手持物品
                            player.containerMenu.setCarried(carried);
                        }
                    }
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}