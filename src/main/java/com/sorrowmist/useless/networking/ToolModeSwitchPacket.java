package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.modes.ModeManager;
import com.sorrowmist.useless.modes.ToolMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToolModeSwitchPacket {
    private final ToolMode mode;

    public ToolModeSwitchPacket(ToolMode mode) {
        this.mode = mode;
    }

    public ToolModeSwitchPacket(FriendlyByteBuf buf) {
        this.mode = ToolMode.valueOf(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mode.name());
    }

    public static void handle(ToolModeSwitchPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ItemStack mainHandItem = player.getMainHandItem();
                
                // 处理永恒牛排或包含我们NBT数据的Mekanism配置器
                net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf || 
                    (itemId != null && itemId.toString().equals("mekanism:configurator") && 
                     mainHandItem.hasTag() && mainHandItem.getTag().contains("ToolModes"))) {
                    
                    // 创建模式管理器并加载当前状态
                    ModeManager modeManager = new ModeManager();
                    modeManager.loadFromStack(mainHandItem);
                    
                    // 切换模式
                    modeManager.toggleMode(packet.mode);
                    
                    // 保存模式状态到物品栈
                    modeManager.saveToStack(mainHandItem);
                    
                    // 无论当前物品是什么，都创建一个新的永恒牛排物品栈
                    ItemStack newStack = new ItemStack(EndlessBeafItem.ENDLESS_BEAF_ITEM.get());
                    
                    // 复制原有物品的所有NBT数据到新实例
                    if (mainHandItem.hasTag()) {
                        newStack.setTag(mainHandItem.getTag().copy());
                    }
                    
                    // 更新实际的附魔NBT
                    EndlessBeafItem baseItem = (EndlessBeafItem) EndlessBeafItem.ENDLESS_BEAF_ITEM.get();
                    baseItem.updateEnchantments(newStack);
                    
                    // 切换物品实例
                    ItemStack finalStack = baseItem.switchToolModeItem(newStack, modeManager);
                    if (!finalStack.isEmpty()) {
                        // 替换玩家手中的物品（服务端）
                        player.setItemInHand(player.getUsedItemHand(), finalStack);
                    }
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}