package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 分段控件：浅灰轨道 + 白色滑块（带阴影），选中段加粗。selected 返回 -1 表示无选中。 */
public class AppleSegmented extends AppleWidget {
    private final Supplier<List<Component>> options;
    private final IntSupplier selected;
    private final IntConsumer onSelect;
    private float anim = -1;

    public AppleSegmented(int x, int y, int w, int h, Supplier<List<Component>> options, IntSupplier selected, IntConsumer onSelect) {
        super(x, y, w, h);
        this.options = options; this.selected = selected; this.onSelect = onSelect;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        List<Component> opts = options.get();
        int n = opts.size();
        if (n == 0) {
            anim = -1;
            return;
        }
        boolean on = isEnabled();
        DesignAssets.draw(g, "segmented_track_240x32", x, y, w, h);
        float seg = (w - 3f) / n;
        int sel = selected.getAsInt();
        if (sel >= 0 && sel < n) {
            if (anim < 0) anim = sel;
            anim += (sel - anim) * 0.35f;
            if (Math.abs(sel - anim) < 0.01f) anim = sel;
            float sx = x + 1.5f + anim * seg;
            DesignAssets.draw(g, "segmented_thumb_116x28", sx, y + 1.5f, seg, h - 3f);
        }
        for (int i = 0; i < n; i++) {
            Component c = opts.get(i);
            int sx = Math.round(x + 1.5f + seg * i);
            int sw = Math.round(x + 1.5f + seg * (i + 1)) - sx;
            int col = dim(i == sel ? AppleTex.LABEL : 0xFF3A3A3C, on);
            if (i == sel) c = c.copy().withStyle(s -> s.withBold(true));
            AppleTextLayout.drawCentered(g, font(), c, sx, y, sw, h, 4, 3, null, col);
            // 未选中段之间的细分隔线（iOS 风格）
            if (i > 0 && i != sel && i - 1 != sel) {
                int lx = Math.round(x + 1.5f + seg * i);
                g.fill(lx, y + 4, lx + 1, y + h - 4, 0x22000000);
            }
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int n = options.get().size();
        if (n == 0) return false;
        float seg = (getSizeWidth() - 3f) / n;
        int i = (int) ((mx - getPositionX() - 1.5f) / seg);
        if (i < 0 || i >= n) return false;
        onSelect.accept(i);
        return true;
    }
}
