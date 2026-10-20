package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.sorrowmist.useless.UselessMod;
import net.minecraftforge.fml.ModList;
import com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent;

/** Entry point loaded only after Mekanism has been detected. */
public final class MekanismCompatLoader {
    private MekanismCompatLoader() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        MekanismEnergyCompat.registerCapabilities(event);
        MekanismChemicalCompat.registerCapabilities(event);
        // 温度调节器方块的热能力，并安装「主动驱动」钩子（Mek 传热是发送方驱动，见该类注释）。
        ThermostatHeatCompat.registerCapabilities(event);
        if (ModList.get().isLoaded("appmek")) {
            invokeAppMekCapabilities(event);
        }
    }

    private static void invokeAppMekCapabilities(RegisterCapabilitiesEvent event) {
        try {
            Class<?> compat = Class.forName(
                    "com.sorrowmist.useless.compat.appmek.AppMekChemicalCompat", true,
                    MekanismCompatLoader.class.getClassLoader());
            compat.getMethod("register", RegisterCapabilitiesEvent.class).invoke(null, event);
        } catch (ReflectiveOperationException | LinkageError exception) {
            RateLimitedLogger.error(UselessMod.LOGGER, "Failed to register AppMek chemical capabilities", exception);
        }
    }
}
