package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** iOS 分组设置行：左标签，右侧灰色值 + ›；可选底部内缩分隔线。onClick 为 null 时只做展示（右侧可叠放开关/分段/输入框）。 */
public class AppleSettingRow extends AppleWidget {
    private final Supplier<Component> label;
    private final Supplier<Component> value;
    private final Runnable onClick;
    private boolean separator = true;
    private AppleUi.Icon leading;
    private Integer leadingAccent;

    public AppleSettingRow(int x, int y, int w, int h, Supplier<Component> label, Supplier<Component> value, Runnable onClick) {
        super(x, y, w, h);
        this.label = label; this.value = value; this.onClick = onClick;
    }

    public AppleSettingRow noSeparator() { separator = false; return this; }
    public AppleSettingRow leading(AppleUi.Icon icon) { leading = icon; return this; }
    public AppleSettingRow leading(AppleUi.Icon icon, int accent) { leading = icon; leadingAccent = accent; return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean on = isEnabled();
        if (onClick != null && on && isMouseOverElement(mouseX, mouseY)) {
            g.fill(x, y, x + w, y + h, 0x0A000000);
        }
        int tx = x + 6;
        if (leading != null) {
            if (leadingAccent != null) {
                AppleIconTile.draw(g, x + 8, y + (h - 12) / 2, 12, leading, dim(leadingAccent, on), true);
                tx = x + 28;
            } else {
                AppleUi.icon(g, leading, tx, y + (h - 8) / 2, 8, dim(AppleTex.BLUE, on));
                tx += 12;
            }
        }
        int right = x + w - 6;
        if (onClick != null) {
            AppleUi.icon(g, AppleUi.Icon.CHEVRON_RIGHT, right - 6, y + (h - 8) / 2, 8, dim(0xFFB0B0B6, on));
            right -= 9;
        }
        int labelRight = right;
        if (value != null) {
            var v = AppleTextLayout.fit(font(), value.get(), Math.max(0, (right - tx) / 2));
            int vw = font().width(v);
            if (vw > 0) {
                g.drawString(font(), v, right - vw, cy(y, h), dim(AppleTex.SECONDARY, on), false);
                labelRight = right - vw - 8;
            }
        }
        AppleTextLayout.draw(g, font(), label.get(), tx, y + (h - font().lineHeight * .85f) / 2,
                Math.max(0, labelRight - tx), .85f, dim(AppleTex.LABEL, on), false);
        if (separator) g.fill(leadingAccent == null ? x + 6 : tx, y + h - 1, x + w, y + h, AppleTex.SEPARATOR);
    }

    @Override
    protected boolean onClick(double mx, double my) {
        if (onClick == null) return false;
        onClick.run();
        return true;
    }
}
