package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.ModList;

/** Client-only dispatch seam: native LDLib when installed, vanilla fallback otherwise. */
public interface AppleWidgetRuntime {
    void background(WidgetGroup group, GuiGraphics graphics, int x, int y, float partialTick);
    void foreground(WidgetGroup group, GuiGraphics graphics, int x, int y, float partialTick);
    boolean click(WidgetGroup group, double x, double y, int button);
    boolean release(WidgetGroup group, double x, double y, int button);
    boolean drag(WidgetGroup group, double x, double y, int button, double dx, double dy);
    boolean scroll(WidgetGroup group, double x, double y, double delta);
    void tick(WidgetGroup group);

    static AppleWidgetRuntime create() {
        // Keep LDLib types out of the screen registration/class-loading path.
        if (ModList.get() == null || !ModList.get().isLoaded("ldlib")) return new Vanilla();
        try {
            return (AppleWidgetRuntime) Class.forName(
                    "com.sorrowmist.useless.client.gui.apple.ldl.LDLibWidgetRuntime")
                    .getConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot initialize the installed LDLib UI backend", exception);
        }
    }

    final class Vanilla implements AppleWidgetRuntime {
        public void background(WidgetGroup g, GuiGraphics out, int x, int y, float pt) { g.drawInBackground(out, x, y, pt); }
        public void foreground(WidgetGroup g, GuiGraphics out, int x, int y, float pt) { g.drawInForeground(out, x, y, pt); }
        public boolean click(WidgetGroup g, double x, double y, int b) { return g.mouseClicked(x, y, b); }
        public boolean release(WidgetGroup g, double x, double y, int b) { return g.mouseReleased(x, y, b); }
        public boolean drag(WidgetGroup g, double x, double y, int b, double dx, double dy) { return g.mouseDragged(x, y, b, dx, dy); }
        public boolean scroll(WidgetGroup g, double x, double y, double d) { return g.mouseWheelMove(x, y, d); }
        public void tick(WidgetGroup g) { g.updateScreen(); }
    }
}
