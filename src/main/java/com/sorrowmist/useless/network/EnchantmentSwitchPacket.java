package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.utils.EnchantmentUtil;
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
            buf -> new EnchantmentSwitchPacket(buf.readEnum(EnchantmentMode.class))
    );
    private final EnchantmentMode mode;

    public EnchantmentSwitchPacket(EnchantmentMode mode) {
        this.mode = mode;
    }

    public static void handle(final EnchantmentSwitchPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            Level level = player.level();
            ItemStack mainHandItem = player.getMainHandItem();

            Holder<Enchantment> fortune = EnchantmentUtil.getEnchantmentHolder(level, Enchantments.FORTUNE);
            Holder<Enchantment> silkTouch = EnchantmentUtil.getEnchantmentHolder(level, Enchantments.SILK_TOUCH);

            // 根据模式切换自定义模型数据（0=Fortune 外观，1=Silk Touch 外观）
            mainHandItem.set(DataComponents.CUSTOM_MODEL_DATA,
                             new CustomModelData(msg.mode == EnchantmentMode.FORTUNE ? 0 : 1)
            );

            if (msg.mode == EnchantmentMode.FORTUNE) {
                mainHandItem.set(UComponents.EnchantModeComponent, EnchantMode.FORTUNE);
            } else { // SILK_TOUCH
                mainHandItem.set(UComponents.EnchantModeComponent, EnchantMode.SILK_TOUCH);
            }

            EnchantmentHelper.updateEnchantments(mainHandItem, mutable -> {
                if (msg.mode == EnchantmentMode.FORTUNE) {
                    mutable.set(silkTouch, 0);   // 移除 Silk Touch
                    mutable.set(fortune, 10);    // Fortune 10 级
                } else { // SILK_TOUCH
                    mutable.set(silkTouch, 1);   // Silk Touch 1 级
                    mutable.set(fortune, 0);     // 移除 Fortune
                }
            });
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum EnchantmentMode {
        FORTUNE,
        SILK_TOUCH
    }
}