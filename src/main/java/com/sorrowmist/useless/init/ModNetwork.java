package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.FunctionModeTogglePacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(UselessMod.MODID).versioned("1");
        registrar.playToServer(EnchantmentSwitchPacket.TYPE, EnchantmentSwitchPacket.STREAM_CODEC,
                               EnchantmentSwitchPacket::handle
        );
        registrar.playToServer(FunctionModeTogglePacket.TYPE, FunctionModeTogglePacket.STREAM_CODEC,
                               FunctionModeTogglePacket::handle
        );
        registrar.playToServer(ToolTypeModeSwitchPacket.TYPE, ToolTypeModeSwitchPacket.STREAM_CODEC,
                               ToolTypeModeSwitchPacket::handle
        );
    }
}