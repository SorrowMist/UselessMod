package com.sorrowmist.useless.compat.enderio.client;

/** Ender IO 6.2 has no travel-staff key binding or held-key latch to clear. */
public final class EnderIOTravelClientCompat {
    private EnderIOTravelClientCompat() {}
    public static void suppressTravelKey() {
        // Native 6.2 travel uses item interaction; the UselessMod wheel consumes its own input.
    }
}
