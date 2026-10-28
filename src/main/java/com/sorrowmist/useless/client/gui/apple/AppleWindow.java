package com.sorrowmist.useless.client.gui.apple;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** 窗口底板：玻璃渐变面板 + 标题栏（加粗标题，可选灰色副标题）+ 标题栏下细分隔线。无交通灯。 */
public class AppleWindow extends AppleWidget {
    private final Supplier<Component> title;
    private Supplier<Component> subtitle;
    private boolean center, separator = true;
    private com.sorrowmist.useless.client.gui.AppleUi.Icon icon;
    private String referenceIcon;
    private int iconAccent = AppleStyle.BLUE;

    /** Pixel-art item icon used by the reference, without an unrelated colored tile. */
    public AppleWindow referenceIcon(String name) { referenceIcon = "reference_" + name; return this; }

    public AppleWindow icon(com.sorrowmist.useless.client.gui.AppleUi.Icon icon) {
        return icon(icon, AppleStyle.BLUE);
    }

    public AppleWindow icon(com.sorrowmist.useless.client.gui.AppleUi.Icon icon, int accent) {
        this.icon = icon;
        this.iconAccent = accent;
        return this;
    }

    public AppleWindow(int w, int h, Supplier<Component> title) {
        super(0, 0, w, h);
        this.title = title;
    }

    public AppleWindow subtitle(Supplier<Component> s) { subtitle = s; return this; }
    public AppleWindow centerTitle() { center = true; return this; }
    public AppleWindow noSeparator() { separator = false; return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        AppleTex.glassWindow(AppleStyle.R_WINDOW).draw(g, mouseX, mouseY, x, y, w, h);
        com.sorrowmist.useless.client.gui.DesignAssets.draw(g, "titlebar_480", x, y, w, AppleStyle.TITLE_BAR);
        if (referenceIcon != null) {
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g, referenceIcon, x + 12, y + 7, 14, 14);
        } else if (icon != null) {
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g, "icontile_" +
                    com.sorrowmist.useless.client.gui.DesignAssets.colorName(iconAccent) + "_28", x + 12, y + 7, 14, 14);
            com.sorrowmist.useless.client.gui.AppleUi.icon(g, icon, x + 15, y + 10, 8, 0xFFFFFFFF);
        }
        Component t = title == null ? null : title.get();
        if (t != null) {
            Component b = t.copy().withStyle(s -> s.withBold(true));
            int tw = font().width(b);
            int tx = center ? x + (w - tw) / 2 : x + (icon == null && referenceIcon == null ? AppleStyle.PAD : 30);
            int ty = y + (AppleStyle.TITLE_BAR - font().lineHeight) / 2;
            text(g, b, tx, ty, AppleStyle.LABEL);
            if (subtitle != null && !center) {
                Component s = subtitle.get();
                if (s != null) text(g, s, tx + tw + 6, ty, AppleStyle.TERTIARY);
            }
        }
        if (separator) g.fill(x + 6, y + AppleStyle.TITLE_BAR, x + w - 6, y + AppleStyle.TITLE_BAR + 1, AppleStyle.SEPARATOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
