package com.sorrowmist.useless.client;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 造化杖提示翻页的按键入口。
 *
 * <p>翻页键 {@code [} 与 {@code ]} 不注册为 {@code KeyMapping}：它们只在物品提示框
 * 显示期间生效，注册为常规绑定会被原版按键系统在游戏内持续分发，而并不存在对应的游戏内行为。</p>
 *
 * <p>{@link InputEvent.Key} 不提供取消机制，因此按键仍会继续传递。这两个键在游戏中未绑定
 * 任何行为，且分页只在提示框可见时改写页码，无提示框时处理逻辑提前返回，故不产生副作用。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(Dist.CLIENT)
public final class BeefTooltipKeyHandler {

    private BeefTooltipKeyHandler() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        int delta;
        if (event.getKey() == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            delta = 1;
        } else if (event.getKey() == GLFW.GLFW_KEY_LEFT_BRACKET) {
            delta = -1;
        } else {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TooltipPageState.isTooltipVisible()) return;

        // 必须使用提示框渲染时记录的那个物品栈，而不是重新按主手/副手推断：
        // 在物品栏中悬停时，被查看的是槽位内的物品栈实例，与快捷栏的 ItemStack
        // 并非同一对象，两者的标识哈希不同。若按键阶段改用主手物品翻页，页码会写到
        // 渲染阶段从不读取的键上，表现为按键无效。
        ItemStack staff = TooltipPageState.getViewedStack();
        if (staff == null || !(staff.getItem() instanceof EndlessBeafItem)) return;

        BeefTooltipPager.turnPage(staff, delta);
    }
}