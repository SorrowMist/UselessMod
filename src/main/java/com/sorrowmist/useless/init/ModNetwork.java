package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.MiningDataSyncPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.TabKeyPressedPacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(UselessMod.MODID).versioned("1");
        registrar.playToServer(EnchantmentSwitchPacket.TYPE, EnchantmentSwitchPacket.STREAM_CODEC,
                               EnchantmentSwitchPacket::handle
        );
        registrar.playToServer(ToolTypeModeSwitchPacket.TYPE, ToolTypeModeSwitchPacket.STREAM_CODEC,
                               ToolTypeModeSwitchPacket::handle
        );
        registrar.playToServer(TabKeyPressedPacket.TYPE, TabKeyPressedPacket.STREAM_CODEC,
                               TabKeyPressedPacket::handle
        );
        registrar.playToServer(ModeTogglePacket.TYPE, ModeTogglePacket.STREAM_CODEC,
                               ModeTogglePacket::handle
        );
        registrar.playToClient(MiningDataSyncPacket.TYPE, MiningDataSyncPacket.STREAM_CODEC,
                               MiningDataSyncPacket::handle
        );
    }
}
