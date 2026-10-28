package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 翻页器：灰色胶囊「‹  2 / 5  ›」，两端可点。 */
public class ApplePager extends AppleWidget {
    private final Supplier<Component> label;
    private final BooleanSupplier canPrev, canNext;
    private final Runnable prev, next;

    public ApplePager(int x, int y, int w, int h, Supplier<Component> label,
                      BooleanSupplier canPrev, Runnable prev, BooleanSupplier canNext, Runnable next) {
        super(x, y, w, h);
        this.label = label; this.canPrev = canPrev; this.prev = prev; this.canNext = canNext; this.next = next;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean on = isEnabled();
        AppleTex.capsule(AppleStyle.FILL).draw(g, mouseX, mouseY, x, y, w, h);
        int b = h;
        boolean p = on && canPrev.getAsBoolean(), n = on && canNext.getAsBoolean();
        boolean hp = p && mouseX >= x && mouseX < x + b && mouseY >= y && mouseY < y + h;
        boolean hn = n && mouseX >= x + w - b && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hp) AppleUi2.round(g, x, y, b, h, h / 2f, AppleStyle.FILL_PRESSED);
        if (hn) AppleUi2.round(g, x + w - b, y, b, h, h / 2f, AppleStyle.FILL_PRESSED);
        AppleUi.icon(g, AppleUi.Icon.CHEVRON_LEFT, x + (b - 8) / 2, y + (h - 8) / 2, 8, p ? AppleStyle.BLUE : AppleStyle.QUATERNARY);
        AppleUi.icon(g, AppleUi.Icon.CHEVRON_RIGHT, x + w - b + (b - 8) / 2, y + (h - 8) / 2, 8, n ? AppleStyle.BLUE : AppleStyle.QUATERNARY);
        Component c = label.get();
        text(g, c, x + (w - font().width(c)) / 2, cy(y, h), dim(AppleStyle.LABEL, on));
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int x = getPositionX(), w = getSizeWidth(), b = getSizeHeight();
        if (mx < x + b) { if (canPrev.getAsBoolean()) prev.run(); return true; }
        if (mx >= x + w - b) { if (canNext.getAsBoolean()) next.run(); return true; }
        return false;
    }
}
