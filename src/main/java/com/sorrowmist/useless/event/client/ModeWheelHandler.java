package com.sorrowmist.useless.event.client;

import com.sorrowmist.useless.client.gui.ModeWheelScreen;
import com.sorrowmist.useless.compat.enderio.EnderIOTravelCompat;
import com.sorrowmist.useless.compat.enderio.client.EnderIOTravelClientCompat;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.AbstractMap;

/**
 * 模式轮盘处理器，负责处理模式轮盘的显示、隐藏和交互
 */
@EventBusSubscriber(Dist.CLIENT)
public class ModeWheelHandler {
    private static boolean suppressEnderIoTravelKey;

    /**
     * 处理键盘按下事件
     */
    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;

        // 检查是否是G键按下事件 (GLFW_PRESS = 1)
        if (event.getKey() == KeyBindings.SWITCH_MODE_WHEEL_KEY.get().getKey().getValue()
                && event.getAction() == 1) {
            // 使用工具方法查找目标工具
            ItemStack targetItem = UselessItemUtils.findTargetToolInHands(minecraft.player)
                                                   .map(AbstractMap.SimpleImmutableEntry::getKey)
                                                   .orElse(null);

            if (targetItem != null && !(minecraft.screen instanceof ModeWheelScreen)) {
                // 显示模式轮盘屏幕
                suppressEnderIoTravelKey = true;
                Minecraft.getInstance().setScreen(new ModeWheelScreen(targetItem));
            }
        }
    }

    /**
     * The wheel and Ender IO both default to G. Keep Ender IO from interpreting the wheel-key
     * release as a travel request while the wheel is being used.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!suppressEnderIoTravelKey) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (ModList.get().isLoaded(EnderIOTravelCompat.MOD_ID)) {
            EnderIOTravelClientCompat.suppressTravelKey();
        }

        boolean wheelKeyDown = KeyBindings.SWITCH_MODE_WHEEL_KEY.get().isDown();
        if (!wheelKeyDown && !(minecraft.screen instanceof ModeWheelScreen)) {
            suppressEnderIoTravelKey = false;
        }
    }
}
