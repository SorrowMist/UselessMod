package com.sorrowmist.useless.client.gui;

import net.minecraft.core.GlobalPos;

import java.util.List;
import java.util.UUID;

import static com.sorrowmist.useless.client.gui.StaffLinkViewLayout.*;

/** Client-only drag preview. It never changes route order or sends packets while moving. */
final class StaffLinkAnchorDrag {
    record Drop(int targetIndex, int lineY) {}

    private UUID network;
    private GlobalPos anchor;
    private double startX, startY, mouseX, mouseY;
    private boolean moved;

    void begin(UUID network, GlobalPos anchor, double x, double y) {
        this.network = network;
        this.anchor = anchor;
        startX = mouseX = x;
        startY = mouseY = y;
        moved = false;
    }

    void update(double x, double y) {
        mouseX = x;
        mouseY = y;
        moved |= Math.hypot(x - startX, y - startY) >= 4;
    }

    boolean pending() { return anchor != null; }
    boolean moved() { return pending() && moved; }
    GlobalPos anchor() { return anchor; }

    void cancel() {
        network = null;
        anchor = null;
        moved = false;
    }

    boolean valid(UUID network, List<GlobalPos> all, List<GlobalPos> visible) {
        return pending() && this.network.equals(network) && all.contains(anchor) && visible.contains(anchor);
    }

    /** A drop between visible rows is translated back to the full, unfiltered server order. */
    Drop drop(UUID network, List<GlobalPos> all, List<GlobalPos> visible, int scrollOffset) {
        if (!moved() || !valid(network, all, visible)) return null;
        int row = anchorRowAt(mouseX, mouseY);
        if (row < 0 || visible.isEmpty()) return null; // Outside release cancels, never clamps to another panel.
        int hover = Math.min(visible.size() - 1, Math.max(0, scrollOffset) + row);
        int shownRow = hover - Math.max(0, scrollOffset);
        boolean after = mouseY >= anchorRowY(shownRow) + LIST_ROW_HEIGHT / 2.0;
        int from = all.indexOf(anchor);
        int hoverIndex = all.indexOf(visible.get(hover));
        if (hoverIndex < 0) return null;
        int insertion = hoverIndex + (after ? 1 : 0);
        int target = Math.max(0, Math.min(all.size() - 1, insertion - (from < insertion ? 1 : 0)));
        return new Drop(target, anchorRowY(shownRow) + (after ? LIST_ROW_HEIGHT : 0));
    }

    int scrollDirection() {
        if (!moved() || anchorRowAt(mouseX, mouseY) < 0) return 0;
        if (mouseY < LIST_FIRST_ROW_Y + 12) return -1;
        if (mouseY >= LIST_BOTTOM - 14) return 1;
        return 0;
    }
}
