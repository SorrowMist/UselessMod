package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 能量条：首行「⚡ 标题 ……… 数值」，下方胶囊条（绿 → 低于 20% 变橙）。高度 = 12 + barH。
 * 悬停显示精确数值由宿主 Screen 原 tooltip 逻辑负责。
 */
public class AppleEnergyBar extends AppleWidget {
    private final LongSupplier stored, capacity;
    private final Supplier<Component> title, value;
    private final int barH;

    public AppleEnergyBar(int x, int y, int w, int barH, Supplier<Component> title, Supplier<Component> value,
                          LongSupplier stored, LongSupplier capacity) {
        super(x, y, w, 12 + barH);
        this.title = title; this.value = value; this.stored = stored; this.capacity = capacity; this.barH = barH;
    }

    public double ratio() {
        long c = capacity.getAsLong();
        return c <= 0 ? 0 : Math.min(1.0, (double) stored.getAsLong() / c);
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth();
        double r = ratio();
        int col = r < 0.2 ? AppleStyle.ORANGE : AppleStyle.GREEN;
        AppleUi.icon(g, AppleUi.Icon.BOLT, x, y, 8, col);
        if (title != null) text(g, title.get(), x + 11, y + 1, AppleStyle.SECONDARY);
        if (value != null) {
            Component v = value.get();
            text(g, v, x + w - font().width(v), y + 1, AppleStyle.LABEL);
        }
        AppleProgress.draw(g, x, y + 12, w, barH, r, col);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
