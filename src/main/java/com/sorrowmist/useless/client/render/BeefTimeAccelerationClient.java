package com.sorrowmist.useless.client.render;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BeefTimeAccelerationClient {
    private BeefTimeAccelerationClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BEEF_TIME_ACCELERATION.get(), BeefTimeAccelerationRenderer::new);
    }
}
