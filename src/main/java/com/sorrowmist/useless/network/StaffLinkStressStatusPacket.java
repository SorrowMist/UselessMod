package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端：应力线路上各端点的运行状态。
 *
 * <p><b>为什么需要单独一个包。</b>线路配置（转速、方向）本来就随整网快照下发，界面上能编辑。
 * 但「源网络现在有多少可用应力」「目标网络现在需要多少」「这个输出为什么没转」都是
 * <b>每 tick 变化的运行时状态</b>，不属于配置，快照里没有它们的位置。</p>
 *
 * <p>只发给正开着无线物流界面的玩家，且每 20 tick 一次：这些数字是给人看的，
 * 不需要每 tick 刷新，也没必要广播给所有人。</p>
 *
 * @param entries 每个「网络 × 锚点 × 线路」一条
 */
public record StaffLinkStressStatusPacket(List<Entry> entries) implements CustomPacketPayload {

    /** 状态码长度上限；状态是固定的几个短标识，不是自由文本。 */
    public static final int MAX_STATE = 32;

    /** 三个应力量：提供 / 消耗 / 第三个（语义随角色变）。 */
    public record Numbers(float supplied, float consumed, float extra) {
        public static final Numbers ZERO = new Numbers(0.0F, 0.0F, 0.0F);
    }

    /**
     * 一个端点的运行状态。
     *
     * @param input 这一端是释放端（源）还是吸收端（输出）
     * @param local <b>本锚点</b>的三个数，用于容器列表里那一行：
     *              释放端 = 它所在网络自己提供了多少；吸收端 = 目标网络需要多少
     * @param line  <b>整条线路</b>的三个数，用于配置区的状态行：
     *              提供（各源网络容量合计）/ 消耗（各源网络自身负载合计）/ 可用（两者之差，
     *              也就是这条链路真正能借出去的额度）。吸收端不使用，恒为 0
     * @param state 状态码；空串表示正常。界面按它取翻译键
     */
    public record Entry(UUID networkId, GlobalPos anchor, int route, boolean input,
                        Numbers local, Numbers line, String state) {
    }

    public static final Type<StaffLinkStressStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "staff_link_stress_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkStressStatusPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeVarInt(packet.entries().size());
                        for (Entry entry : packet.entries()) {
                            buffer.writeUUID(entry.networkId());
                            StaffLinkRoute.writeAnchor(buffer, entry.anchor());
                            buffer.writeVarInt(entry.route());
                            buffer.writeBoolean(entry.input());
                            writeNumbers(buffer, entry.local());
                            writeNumbers(buffer, entry.line());
                            buffer.writeUtf(entry.state(), MAX_STATE);
                        }
                    },
                    buffer -> {
                        int size = buffer.readVarInt();
                        List<Entry> entries = new ArrayList<>(size);
                        for (int index = 0; index < size; index++) {
                            UUID networkId = buffer.readUUID();
                            GlobalPos anchor = StaffLinkRoute.readAnchor(buffer);
                            int route = buffer.readVarInt();
                            boolean input = buffer.readBoolean();
                            Numbers local = readNumbers(buffer);
                            Numbers routeNumbers = readNumbers(buffer);
                            String state = buffer.readUtf(MAX_STATE);
                            entries.add(new Entry(networkId, anchor, route, input,
                                    local, routeNumbers, state));
                        }
                        return new StaffLinkStressStatusPacket(List.copyOf(entries));
                    });

    private static void writeNumbers(RegistryFriendlyByteBuf buffer, Numbers numbers) {
        buffer.writeFloat(numbers.supplied());
        buffer.writeFloat(numbers.consumed());
        buffer.writeFloat(numbers.extra());
    }

    private static Numbers readNumbers(RegistryFriendlyByteBuf buffer) {
        return new Numbers(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(StaffLinkStressStatusPacket packet, IPayloadContext context) {
        // 客户端类型统一收敛到 ClientPacketHandlers，避免专用服务器加载类时解析到客户端类。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleStaffLinkStressStatus(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
