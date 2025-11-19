package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(UselessMod.MODID);
        registrar.playToServer(EnchantmentSwitchPacket.TYPE, EnchantmentSwitchPacket.STREAM_CODEC, EnchantmentSwitchPacket::handle);
    }
}