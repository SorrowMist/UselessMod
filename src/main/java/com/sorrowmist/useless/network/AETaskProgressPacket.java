package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record AETaskProgressPacket(BlockPos pos, List<AETaskProgressPacket.TaskProgressData> tasks) implements CustomPacketPayload {
    public static class TaskProgressData {
        public final String productName;
        public final int progress;
        public final int maxProgress;
        public final int craftCount;
        public final int totalOutputCount;
        public final String statusKey;
        public final String statusDetail;

        public TaskProgressData(String productName, int progress, int maxProgress, int craftCount, int totalOutputCount) {
            this(productName, progress, maxProgress, craftCount, totalOutputCount,
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }

        public TaskProgressData(String productName, int progress, int maxProgress, int craftCount, int totalOutputCount, String statusKey, String statusDetail) {
            this.productName = productName;
            this.progress = progress;
            this.maxProgress = maxProgress;
            this.craftCount = craftCount;
            this.totalOutputCount = totalOutputCount;
            this.statusKey = statusKey;
            this.statusDetail = statusDetail;
        }
    }

    public static final StreamCodec<FriendlyByteBuf, AETaskProgressPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> pkt.encode(buf),
            buf -> decode(buf)
    );

    public static final Type<AETaskProgressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "ae_task_progress"));

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(tasks.size());
        for (TaskProgressData task : tasks) {
            buf.writeUtf(task.productName);
            buf.writeInt(task.progress);
            buf.writeInt(task.maxProgress);
            buf.writeInt(task.craftCount);
            buf.writeInt(task.totalOutputCount);
            buf.writeUtf(task.statusKey);
            buf.writeUtf(task.statusDetail);
        }
    }

    public static AETaskProgressPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readInt();
        List<TaskProgressData> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String productName = buf.readUtf();
            int progress = buf.readInt();
            int maxProgress = buf.readInt();
            int craftCount = buf.readInt();
            int totalOutputCount = buf.readInt();
            String statusKey = buf.readUtf();
            String statusDetail = buf.readUtf();
            tasks.add(new TaskProgressData(productName, progress, maxProgress, craftCount, totalOutputCount, statusKey, statusDetail));
        }
        return new AETaskProgressPacket(pos, tasks);
    }

    public static void handle(AETaskProgressPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                var blockEntity = mc.level.getBlockEntity(msg.pos);
                if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                    furnace.updateClientTaskProgress(msg.tasks);
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
