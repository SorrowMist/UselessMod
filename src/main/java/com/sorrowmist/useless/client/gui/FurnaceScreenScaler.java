package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.client.gui.apple.AppleContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Fits large container panels without changing the player's saved GUI scale option. */
public final class FurnaceScreenScaler {
    /** Total edge clearance in framebuffer pixels; it must not grow with GUI scale. */
    private static final int EDGE_CLEARANCE_PIXELS = 16;
    private static boolean fitting;
    private static double appliedScale;

    private FurnaceScreenScaler() {
    }

    public static void update() {
        Minecraft client = Minecraft.getInstance();
        var screen = client.screen;
        var window = client.getWindow();
        double current = window.getGuiScale();
        boolean fittedScreen = screen instanceof AppleContainerScreen<?>
                || screen instanceof StaffLinkScreen;
        if (fittedScreen) {
            fitting = true;
            // Recalculate from the saved option and current framebuffer, just like vanilla.
            // A window resize can select the same value as our previous fit, so comparing
            // current/applied scales cannot reliably detect a changed vanilla preference.
            double preferred = window.calculateScale(client.options.guiScale().get(), client.isEnforceUnicode());
            var container = (AbstractContainerScreen<?>) screen;
            int fitHeight = screen instanceof DimensionConfigScreen dimension ? dimension.fitHeight() : container.getYSize();
            appliedScale = scaleForWindow(preferred, window.getWidth(), window.getHeight(),
                    container.getXSize(), fitHeight);
            if (current == appliedScale) return;
            window.setGuiScale(appliedScale);
        } else {
            if (!fitting) return;
            fitting = false;
            if (current != appliedScale) return;
            double restore = window.calculateScale(client.options.guiScale().get(), client.isEnforceUnicode());
            if (current == restore) return;
            window.setGuiScale(restore);
        }
        // Resize both drawing and mouse hit boxes into the new logical coordinate space.
        if (screen != null) screen.resize(client, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    static double scaleForWindow(double preferred, int width, int height, int panelWidth, int panelHeight) {
        double heightScale = scaleForWindow(preferred, height, panelHeight);
        return Math.max(1.0D, Math.min(heightScale, (double) (width - EDGE_CLEARANCE_PIXELS) / panelWidth));
    }

    static double scaleForWindow(double preferred, int height, int panelHeight) {
        // A 466-high panel at GUI 3 is 1398px high and fits 1440px. Adding 16 logical
        // pixels and flooring used to turn a 6px margin shortfall into an entire 3 -> 2 jump.
        // Keep the preferred scale when it fits; otherwise shrink only by the space needed.
        return Math.max(1.0D, Math.min(preferred, (double) (height - EDGE_CLEARANCE_PIXELS) / panelHeight));
    }
}
