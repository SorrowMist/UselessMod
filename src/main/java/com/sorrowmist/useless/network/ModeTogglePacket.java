package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class ModeTogglePacket implements CustomPacketPayload {

    public static final Type<ModeTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "mode_toggle"));
    public static final StreamCodec<FriendlyByteBuf, ModeTogglePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeEnum(pkt.modeType);
                buf.writeBoolean(pkt.enabled);
            },
            buf -> new ModeTogglePacket(buf.readEnum(ModeType.class), buf.readBoolean())
    );
    private final ModeType modeType;
    private final boolean enabled;
    public ModeTogglePacket(ModeType modeType, boolean enabled) {
        this.modeType = modeType;
        this.enabled = enabled;
    }

    public static void handle(ModeTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return; // 没找到工具直接返回

            var entry = toolEntry.get();
            ItemStack stack = entry.getKey();

            // 根据模式类型处理不同的组件
            switch (msg.modeType) {
                case CHAIN_MINING -> {
                    stack.set(UComponents.EnhancedChainMiningComponent.get(), msg.enabled);
                    // 切换连锁模式时清空缓存，避免使用旧模式的缓存数据
                    MiningDispatcher.clearPlayerCache(player);
                }
                case FORCE_MINING -> {
                    MiningDispatcher.clearPlayerCache(player);
                    stack.set(UComponents.ForceMiningComponent.get(), msg.enabled);
                }
                case AE_STORAGE_PRIORITY -> {
                    stack.set(UComponents.AEStoragePriorityComponent.get(), msg.enabled);
                }
            }

            // 显式同步物品到客户端
            player.containerMenu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ModeType {
        CHAIN_MINING,
        FORCE_MINING,
        AE_STORAGE_PRIORITY
    }
}
