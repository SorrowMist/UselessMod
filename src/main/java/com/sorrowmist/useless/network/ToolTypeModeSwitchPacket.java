package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ToolTypeModeSwitchPacket(ToolTypeMode mode) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, ToolTypeModeSwitchPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeEnum(packet.mode),
                    buf -> new ToolTypeModeSwitchPacket(buf.readEnum(ToolTypeMode.class))
            );

    public static final Type<ToolTypeModeSwitchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "tool_type_mode_switch"));

    public static void handle(ToolTypeModeSwitchPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();

            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return; // 没找到工具直接返回

            var entry = toolEntry.get();
            ItemStack targetItem = entry.getKey();
            InteractionHand targetHand = entry.getValue();

            // 1. 创建新物品实例
            ItemStack newStack = switch (msg.mode) {
                case WRENCH_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_WRENCH.get());
                case SCREWDRIVER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_SCREWDRIVER.get());
                case MALLET_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_MALLET.get());
                case CROWBAR_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_CROWBAR.get());
                case HAMMER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_HAMMER.get());
                case OMNITOOL_MODE -> {
                    ResourceLocation omnitoolId = ResourceLocation.fromNamespaceAndPath("omnitools", "omni_wrench");
                    Item toolItem = BuiltInRegistries.ITEM.get(omnitoolId);
                    if (toolItem != Items.AIR) {
                        yield new ItemStack(toolItem);
                    } else {
                        yield new ItemStack(ModItems.ENDLESS_BEAF_WRENCH.get());
                    }
                }
                default -> new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
            };

            // 2. 复制原有物品的所有NBT数据到新实例
            newStack.applyComponents(targetItem.getComponents());

            // 3. 覆盖状态：最后设置当前的工具模式组件
            if (!newStack.isEmpty()) {
                newStack.set(UComponents.CurrentToolTypeComponent.get(), msg.mode);
                player.setItemInHand(targetHand, newStack);
            }
        });
    }
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}