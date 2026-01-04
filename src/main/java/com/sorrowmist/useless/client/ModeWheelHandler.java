package com.sorrowmist.useless.client;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.modes.ModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 模式轮盘处理器，负责处理模式轮盘的显示、隐藏和交互
 */
@Mod.EventBusSubscriber(modid = UselessMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ModeWheelHandler {
    /**
     * 处理键盘按下事件
     */
    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        
        // 检查是否是G键按下事件 (GLFW_PRESS = 1)
        if (event.getKey() == KeyBindings.SWITCH_MODE_WHEEL_KEY.getKey().getValue() && event.getAction() == 1) {
            // 检查主手和副手物品是否是永恒牛排工具或包含我们NBT数据的Omnitool扳手
            ItemStack mainHandItem = minecraft.player.getMainHandItem();
            ItemStack offHandItem = minecraft.player.getOffhandItem();
            
            ItemStack targetItem = null;
            
            // 检查主手
            net.minecraft.resources.ResourceLocation mainItemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
            if (mainHandItem.getItem() instanceof EndlessBeafItem || 
                (mainItemId != null && mainItemId.toString().equals("omnitools:omni_wrench") && 
                 mainHandItem.hasTag() && mainHandItem.getTag().contains("ToolModes"))) {
                targetItem = mainHandItem;
            } 
            // 检查副手
            else {
                net.minecraft.resources.ResourceLocation offItemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offHandItem.getItem());
                if (offHandItem.getItem() instanceof EndlessBeafItem || 
                    (offItemId != null && offItemId.toString().equals("omnitools:omni_wrench") && 
                     offHandItem.hasTag() && offHandItem.getTag().contains("ToolModes"))) {
                    targetItem = offHandItem;
                }
            }
            
            if (targetItem != null && !(minecraft.screen instanceof ModeWheelScreen)) {
                // 显示模式轮盘屏幕
                ModeManager modeManager = new ModeManager();
                modeManager.loadFromStack(targetItem);
                ModeWheelScreen.show(modeManager, targetItem);
            }
        }
    }
}