package com.sorrowmist.useless.compat.enderio.client;

import com.enderio.enderio.client.content.keymaps.KeymapHandler;

/**
 * Client-only guards for Ender IO's travel key while the UselessMod mode wheel is open.
 *
 * <p>Ender IO tracks its travel key until release in {@code KeymapHandler}. The guard mirrors
 * that key mapping's state handling so releasing the shared wheel key cannot trigger a travel
 * action. The original key handling is in
 * <a href="https://github.com/Team-EnderIO/EnderIO/blob/dev/1.21.1/enderio/src/main/java/com/enderio/enderio/client/content/keymaps/KeymapHandler.java">Ender IO KeymapHandler</a>.</p>
 */
public final class EnderIOTravelClientCompat {
    private EnderIOTravelClientCompat() {
    }

    public static void suppressTravelKey() {
        KeymapHandler.TRAVEL_STAFF_KEY.get().setDown(false);
    }
}
