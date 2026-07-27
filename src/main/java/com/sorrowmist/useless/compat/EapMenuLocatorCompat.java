package com.sorrowmist.useless.compat;

import appeng.menu.locator.MenuLocators;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;

public final class EapMenuLocatorCompat {
    private static final String EAEP_MOD_ID = "extendedae_plus";
    private static final String CURIOS_MOD_ID = "curios";
    private static final String AE2WTLIB_MOD_ID = "ae2wtlib";
    private static final String LOCATOR_CLASS_NAME = "com.extendedae_plus.menu.locator.CuriosItemLocator";

    private EapMenuLocatorCompat() {}

    public static void registerIfNeeded() {
        ModList modList = ModList.get();
        if (!modList.isLoaded(EAEP_MOD_ID)
                || !modList.isLoaded(CURIOS_MOD_ID)
                || !modList.isLoaded(AE2WTLIB_MOD_ID)) {
            return;
        }

        registerLocator();
    }

    static void registerLocator() {
        try {
            MenuLocators.register(
                    CuriosItemLocator.class,
                    CuriosItemLocator::writeToPacket,
                    CuriosItemLocator::readFromPacket);
            UselessMod.LOGGER.info("Registered ExtendedAE Plus Curios menu locator with AE2");
        } catch (IllegalStateException exception) {
            if (isDuplicateRegistration(exception)) {
                UselessMod.LOGGER.debug("ExtendedAE Plus Curios menu locator is already registered");
            } else {
                UselessMod.LOGGER.warn("Failed to register ExtendedAE Plus Curios menu locator", exception);
            }
        } catch (LinkageError | RuntimeException exception) {
            UselessMod.LOGGER.warn("Failed to register ExtendedAE Plus Curios menu locator", exception);
        }
    }

    private static boolean isDuplicateRegistration(IllegalStateException exception) {
        String message = exception.getMessage();
        return message != null
                && message.contains(LOCATOR_CLASS_NAME)
                && message.contains("already registered");
    }
}
