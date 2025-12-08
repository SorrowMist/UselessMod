package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnchantmentSwitchPacket {
    private final boolean switchToSilkTouch;

    public EnchantmentSwitchPacket(boolean switchToSilkTouch) {
        this.switchToSilkTouch = switchToSilkTouch;
    }

    public EnchantmentSwitchPacket(FriendlyByteBuf buf) {
        this.switchToSilkTouch = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(switchToSilkTouch);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 使用专门的方法切换附魔模式，确保模式管理器状态和NBT标签一致
                    endlessBeaf.switchEnchantmentMode(mainHandItem, switchToSilkTouch);
                    
                    if (switchToSilkTouch) {
                        player.displayClientMessage(Component.translatable("message.useless_mod.switched_to_silk_touch"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("message.useless_mod.switched_to_fortune"), true);
                    }
                }
            }
        });
        return true;
    }
}