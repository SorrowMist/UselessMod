package com.sorrowmist.useless.client.event;

import com.sorrowmist.useless.client.ModeWheelScreen;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 模式轮盘处理器，负责处理模式轮盘的显示、隐藏和交互
 */
@EventBusSubscriber(Dist.CLIENT)
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
        if (event.getKey() == KeyBindings.SWITCH_MODE_WHEEL_KEY.get().getKey().getValue() && event.getAction() == 1) {
            // 检查主手和副手物品是否是永恒牛排工具或包含我们NBT数据的Omnitool扳手
            ItemStack mainHandItem = minecraft.player.getMainHandItem();
            ItemStack offHandItem = minecraft.player.getOffhandItem();

            ItemStack targetItem = null;

            // 检查主手
            ResourceLocation mainItemId = BuiltInRegistries.ITEM.getKey(mainHandItem.getItem());
            // TODO omni_wrench 处理
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                targetItem = mainHandItem;
            }
            // 检查副手
            else {
                ResourceLocation offItemId = BuiltInRegistries.ITEM.getKey(offHandItem.getItem());
                // TODO omni_wrench 处理
                if (offHandItem.getItem() instanceof EndlessBeafItem) {
                    targetItem = offHandItem;
                }
            }

            if (targetItem != null && !(minecraft.screen instanceof ModeWheelScreen)) {
                // 显示模式轮盘屏幕
                // TODO 待完善
                Minecraft.getInstance().setScreen(new ModeWheelScreen(mainHandItem));
            }
        }
    }
}