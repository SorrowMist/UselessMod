package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.utils.EnchantmentUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
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
                buf.writeInt(pkt.type);
            },
            buf -> new EnchantmentSwitchPacket(buf.readInt())
    );
    private int type;

    public EnchantmentSwitchPacket(int type) {
        this.type = type;
    }

    public static void handle(final EnchantmentSwitchPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Level level = ctx.player().level();
            ItemStack mainHandItem = player.getMainHandItem();
            RegistryAccess registry = level.registryAccess();
            Holder<Enchantment> silkTouch = registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
            Holder<Enchantment> fortune = registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE);
            mainHandItem.enchant(EnchantmentUtil.getEnchantmentHolder(level, Enchantments.LOOTING), 10);
            mainHandItem.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(msg.type));
            EnchantmentHelper.updateEnchantments(mainHandItem, mutable -> {
                if (msg.type == 0) {
                    mutable.set(silkTouch, 0);
                    mutable.set(fortune, 10);
                } else {
                    mutable.set(silkTouch, 1);
                    mutable.set(fortune, 0);
                }
            });
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}