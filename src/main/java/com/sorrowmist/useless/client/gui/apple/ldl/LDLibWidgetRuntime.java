package com.sorrowmist.useless.client.gui.apple.ldl;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.sorrowmist.useless.client.gui.apple.AppleWidgetRuntime;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native LDLib 1.x client-side widget hosting. Native menus/Slot instances remain
 * authoritative for inventory and JEI; no second container or network protocol is created.
 * Each host layer has a real LDLib WidgetGroup whose children forward to the skin widgets.
 */
public final class LDLibWidgetRuntime implements AppleWidgetRuntime {
    private final Map<com.sorrowmist.useless.client.gui.apple.core.WidgetGroup, Layer> layers = new IdentityHashMap<>();

    private Layer layer(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup source) {
        Layer layer = layers.computeIfAbsent(source, ignored -> new Layer());
        List<com.sorrowmist.useless.client.gui.apple.core.Widget> children = new ArrayList<>(source.widgets);
        if (!children.equals(layer.sources)) {
            layer.nativeGroup.clearAllWidgets();
            layer.sources = children;
            for (var child : children) layer.nativeGroup.addWidget(new SkinWidget(child));
        }
        for (Widget widget : layer.nativeGroup.widgets) ((SkinWidget) widget).syncPosition();
        return layer;
    }

    public void background(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, GuiGraphics out, int x, int y, float pt) { layer(g).nativeGroup.drawInBackground(out, x, y, pt); }
    public void foreground(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, GuiGraphics out, int x, int y, float pt) { layer(g).nativeGroup.drawInForeground(out, x, y, pt); }
    public boolean click(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, double x, double y, int b) { return layer(g).nativeGroup.mouseClicked(x, y, b); }
    public boolean release(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, double x, double y, int b) { return layer(g).nativeGroup.mouseReleased(x, y, b); }
    public boolean drag(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, double x, double y, int b, double dx, double dy) { return layer(g).nativeGroup.mouseDragged(x, y, b, dx, dy); }
    public boolean scroll(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g, double x, double y, double d) { return layer(g).nativeGroup.mouseWheelMove(x, y, d); }
    public void tick(com.sorrowmist.useless.client.gui.apple.core.WidgetGroup g) { layer(g).nativeGroup.updateScreen(); }

    /** Hosted by a vanilla Screen, not a ModularUI. Never call Widget's
     * default drag implementation: it dereferences getGui() even when no child
     * consumes the event (e.g. dragging an empty area or a vanilla slot). */
    private static final class HostedWidgetGroup extends WidgetGroup {
        HostedWidgetGroup() { super(0, 0, 0, 0); setClientSideWidget(); }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget child = widgets.get(i);
                if (child.isVisible() && child.isActive() && child.mouseDragged(x, y, button, dx, dy))
                    return true;
            }
            return false; // Let the original Screen handle inventory/text/preview dragging.
        }
    }

    private static final class Layer {
        final WidgetGroup nativeGroup = new HostedWidgetGroup();
        List<com.sorrowmist.useless.client.gui.apple.core.Widget> sources = List.of();
    }

    private static final class SkinWidget extends Widget {
        private final com.sorrowmist.useless.client.gui.apple.core.Widget source;
        SkinWidget(com.sorrowmist.useless.client.gui.apple.core.Widget source) {
            super(source.getPositionX(), source.getPositionY(), source.getSizeWidth(), source.getSizeHeight());
            this.source = source;
            setClientSideWidget();
        }
        void syncPosition() {
            setSelfPosition(source.getPositionX(), source.getPositionY());
            setSize(source.getSizeWidth(), source.getSizeHeight());
        }
        @Override public boolean isVisible() { return source.isVisible(); }
        @Override public boolean isActive() { return source.isActive(); }
        @Override public boolean isMouseOverElement(double x, double y) { return source.isMouseOverElement(x, y); }
        @Override public void drawInBackground(GuiGraphics g, int x, int y, float pt) { source.drawInBackground(g, x, y, pt); }
        @Override public void drawInForeground(GuiGraphics g, int x, int y, float pt) { source.drawInForeground(g, x, y, pt); }
        @Override public boolean mouseClicked(double x, double y, int b) { return source.mouseClicked(x, y, b); }
        @Override public boolean mouseReleased(double x, double y, int b) { return source.mouseReleased(x, y, b); }
        @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { return source.mouseDragged(x, y, b, dx, dy); }
        @Override public boolean mouseWheelMove(double x, double y, double d) { return source.mouseWheelMove(x, y, d); }
        @Override public void updateScreen() { source.updateScreen(); }
    }
}
