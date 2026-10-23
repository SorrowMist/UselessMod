package com.sorrowmist.useless;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import com.sorrowmist.useless.client.gui.PortConfigScreen;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class UselessClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Configured generates the full editor only when a mod has not supplied its own factory.
        if (ModList.get().isLoaded("configured")) return;
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenFactory.class,
                () -> new ConfigScreenFactory((minecraft, parent) -> new PortConfigScreen(parent)));
    }
}
