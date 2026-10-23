package com.sorrowmist.useless.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Fits the furnace panels without changing the player's saved GUI scale option. */
public final class FurnaceScreenScaler {
    private static Double preferredScale;
    private static double appliedScale;

    private FurnaceScreenScaler() {
    }

    public static void update() {
        Minecraft client = Minecraft.getInstance();
        var screen = client.screen;
        var window = client.getWindow();
        double current = window.getGuiScale();
        boolean furnace = screen instanceof AdvancedAlloyFurnaceScreen
                || screen instanceof MultiblockAlloyFurnaceScreen
                || screen instanceof PagedRecoverableScreen<?>
                || screen instanceof PassiveCraftingHatchScreen
                || screen instanceof PatternConverterScreen;
        if (furnace) {
            if (preferredScale == null || current != appliedScale) preferredScale = current;
            var container = (AbstractContainerScreen<?>) screen;
            appliedScale = scaleForWindow(preferredScale, window.getHeight(), container.getYSize());
            if (current == appliedScale) return;
            window.setGuiScale(appliedScale);
        } else {
            if (preferredScale == null) return;
            double restore = preferredScale;
            preferredScale = null;
            if (current != appliedScale) return;
            window.setGuiScale(restore);
        }
        // Resize both drawing and mouse hit boxes into the new logical coordinate space.
        if (screen != null) screen.resize(client, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    static double scaleForWindow(double preferred, int height, int panelHeight) {
        return Math.max(1.0D, Math.min(preferred, Math.floor((double) height / (panelHeight + 16))));
    }
}
