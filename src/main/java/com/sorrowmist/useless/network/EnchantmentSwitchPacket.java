package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class EnchantmentSwitchPacket implements CustomPacketPayload {

    public static final Type<EnchantmentSwitchPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "enchantment_switch"));
    public static final StreamCodec<FriendlyByteBuf, EnchantmentSwitchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeEnum(pkt.mode);
            },
            buf -> new EnchantmentSwitchPacket(buf.readEnum(EnchantMode.class))
    );
    private final EnchantMode mode;

    public EnchantmentSwitchPacket(EnchantMode mode) {
        this.mode = mode;
    }

    public static void handle(EnchantmentSwitchPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return; // 没找到工具直接返回

            var entry = toolEntry.get();
            ItemStack stack = entry.getKey();

            Level level = player.level();

            Holder<Enchantment> fortune = EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE);
            Holder<Enchantment> silk = EnchantmentUtil.getEnchantmentHolder(level, Enchantments.SILK_TOUCH);

            // 写入组件
            stack.set(UComponents.EnchantModeComponent.get(), msg.mode);

            // 切换模型数据
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                      new CustomModelData(msg.mode == EnchantMode.FORTUNE ? 0 : 1)
            );

            // 更新附魔
            EnchantmentHelper.updateEnchantments(
                    stack,
                    ench -> {
                        if (msg.mode == EnchantMode.FORTUNE) {
                            ench.set(silk, 0);
                            ench.set(fortune, ConfigManager.getFortuneLevel());
                        } else {
                            ench.set(silk, 1);
                            ench.set(fortune, 0);
                        }
                    }
            );

            // 显式同步物品到客户端
            player.containerMenu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}