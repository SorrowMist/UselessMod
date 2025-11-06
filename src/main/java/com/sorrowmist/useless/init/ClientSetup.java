package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.EnumColor;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onItemColor(RegisterColorHandlersEvent.Item event) {
        // 注册所有颜色的物品染色器
        for (EnumColor color : EnumColor.valuesInOrder()) {
            var block = GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(color).get();
            event.register((stack, tintIndex) -> {
                // 只对 tintIndex 0 应用颜色染色
                return tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF;
            }, block.asItem());
        }
    }

    @SubscribeEvent
    public static void onBlockColor(RegisterColorHandlersEvent.Block event) {
        // 注册所有颜色的方块染色器
        for (EnumColor color : EnumColor.valuesInOrder()) {
            var block = GlowPlasticBlock.GLOW_PLASTIC_BLOCKS.get(color).get();
            event.register((state, world, pos, tintIndex) -> {
                // 只对 tintIndex 0 应用颜色染色
                return tintIndex == 0 ? color.getRgb() : 0xFFFFFFFF;
            }, block);
        }
    }
}