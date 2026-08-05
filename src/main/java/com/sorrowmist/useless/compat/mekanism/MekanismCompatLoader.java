package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Entry point loaded only after Mekanism has been detected. */
public final class MekanismCompatLoader {
    private MekanismCompatLoader() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        MekanismEnergyCompat.registerCapabilities(event);
        MekanismChemicalCompat.registerCapabilities(event);
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
            UselessMod.LOGGER.error("Failed to register AppMek chemical capabilities", exception);
        }
    }
}
