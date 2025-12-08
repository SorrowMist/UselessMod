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
     * 处理键盘输入事件
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        
        // 检查主手物品是否是永恒牛排工具或包含我们NBT数据的Mekanism配置器
        ItemStack mainHandItem = minecraft.player.getMainHandItem();
        net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem) && 
            !(itemId != null && itemId.toString().equals("mekanism:configurator") && 
              mainHandItem.hasTag() && mainHandItem.getTag().contains("ToolModes"))) {
            return;
        }
        
        // 检查G键是否被按下
        if (KeyBindings.SWITCH_MODE_WHEEL_KEY.consumeClick()) {
            // 显示模式轮盘屏幕
            ModeManager modeManager = new ModeManager();
            modeManager.loadFromStack(mainHandItem);
            ModeWheelScreen.show(modeManager, mainHandItem);
        }
    }
}