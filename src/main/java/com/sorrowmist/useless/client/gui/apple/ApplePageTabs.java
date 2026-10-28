package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Compact reference page tabs. A moving pair of page numbers keeps large inventories usable. */
public final class ApplePageTabs extends AppleWidget {
    private final IntSupplier page, count, activeCount;
    private final IntConsumer select;

    public ApplePageTabs(int x, int y, int w, int h, IntSupplier page, IntSupplier count,
                         IntSupplier activeCount, IntConsumer select) {
        super(x, y, w, h);
        this.page = page; this.count = count; this.activeCount = activeCount; this.select = select;
    }

    public record Tab(int page, boolean recovery, int left, int width) {}

    /** Shared by drawing and hit testing; no font, renderer or Minecraft context required. */
    public static List<Tab> tabs(int width, int page, int count, int activeCount) {
        if (width < 3 || count <= 0) return List.of();
        int active = Math.min(count, Math.max(1, activeCount));
        boolean recovery = count > active;
        int shown = Math.min(recovery ? 2 : 3, active);
        int recoveryW = recovery ? Math.min(42, (width - 2) * 2 / 3) : 0;
        int numbersW = width - 2 - recoveryW;
        int start = Math.max(0, Math.min(page - 1, active - shown));
        List<Tab> result = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            int left = 1 + numbersW * i / shown;
            result.add(new Tab(start + i, false, left, 1 + numbersW * (i + 1) / shown - left));
        }
        if (recovery) result.add(new Tab(Math.max(active, Math.min(page, count - 1)), true,
                1 + numbersW, recoveryW));
        return List.copyOf(result);
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mx, int my, float pt) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        DesignAssets.draw(g, "segmented_track_240x32", x, y, w, h);
        for (Tab tab : tabs(w, page.getAsInt(), count.getAsInt(), activeCount.getAsInt())) {
            boolean chosen = page.getAsInt() == tab.page();
            if (chosen) DesignAssets.draw(g, "segmented_thumb_116x28", x + tab.left(), y + 1, tab.width(), h - 2);
            Component label = tab.recovery() ? Component.translatable("gui.useless_mod.apple.recovery")
                    : Component.literal(Integer.toString(tab.page() + 1));
            AppleTextLayout.drawCentered(g, font(), label, x + tab.left(), y, tab.width(), h,
                    2, 3, null, tab.recovery() ? AppleStyle.RED_TEXT : chosen ? AppleStyle.LABEL : AppleStyle.SECONDARY);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        for (Tab tab : tabs(getSizeWidth(), page.getAsInt(), count.getAsInt(), activeCount.getAsInt())) {
            double local = mx - getPositionX();
            if (local >= tab.left() && local < tab.left() + tab.width()) {
                if (page.getAsInt() != tab.page()) select.accept(tab.page());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseWheelMove(double mx, double my, double delta) {
        if (!isVisible() || !isEnabled() || !isMouseOverElement(mx, my) || delta == 0) return false;
        int target = Math.max(0, Math.min(count.getAsInt() - 1, page.getAsInt() + (delta < 0 ? 1 : -1)));
        if (target != page.getAsInt()) select.accept(target);
        return true;
    }
}
