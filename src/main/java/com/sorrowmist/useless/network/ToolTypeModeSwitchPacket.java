package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
            // 使用工具方法查找目标工具
            ItemStack targetItem = null;
            InteractionHand targetHand = null;

            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isPresent()) {
                var entry = toolEntry.get();
                targetItem = entry.getKey();
                targetHand = entry.getValue();
            }

            // 如果找到了目标物品
            if (targetItem != null) {
                ItemStack newStack = switch (msg.mode) {
                    case WRENCH_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_WRENCH.get());
                    case SCREWDRIVER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_SCREWDRIVER.get());
                    case MALLET_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_MALLET.get());
                    case CROWBAR_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_CROWBAR.get());
                    case HAMMER_MODE -> new ItemStack(ModItems.ENDLESS_BEAF_HAMMER.get());
                    case OMNITOOL_MODE -> {
                        ResourceLocation omnitoolId = ResourceLocation.fromNamespaceAndPath("omnitools", "omni_wrench");
                        if (BuiltInRegistries.ITEM.containsKey(omnitoolId)) {
                            ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(omnitoolId));
                            itemStack.set(UComponents.CurrentToolTypeComponent, ToolTypeMode.OMNITOOL_MODE);
                            yield itemStack;
                        } else {
                            yield new ItemStack(ModItems.ENDLESS_BEAF_WRENCH.get());
                        }
                    }
                    default -> new ItemStack(ModItems.ENDLESS_BEAF_ITEM.get());
                };

                // 复制原有物品的所有NBT数据到新实例
                DataComponentMap components = targetItem.getComponents();

                for (TypedDataComponent<?> component : components) {
                    newStack.set((DataComponentType) component.type(), component.value());
                }

                // 切换物品实例
                if (!newStack.isEmpty()) {
                    // 替换玩家手中的物品（在正确的手中）
                    newStack.set(UComponents.CurrentToolTypeComponent, msg.mode);
                    player.setItemInHand(targetHand, newStack);
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}