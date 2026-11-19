package com.sorrowmist.useless.client.render;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public final class CreatorDollBounceClient {
    private CreatorDollBounceClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CREATOR_DOLL.get(), CreatorDollBounceRenderer::new);
    }
}
