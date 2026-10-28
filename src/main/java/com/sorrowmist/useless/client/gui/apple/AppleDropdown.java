package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 下拉选择（macOS Pop-up button）：灰色胶囊「当前值 ⌄」，点击在其下方弹出 {@link AppleMenu}，
 * 选中后回调宿主原逻辑并关闭。可设为 plain（无底色，用在设置行右侧）。
 */
public class AppleDropdown extends AppleWidget {
    private final AppleHost host;
    private final Supplier<List<Component>> options;
    private final IntSupplier selected;
    private final IntConsumer onSelect;
    private IntPredicate enabledAt = i -> true;
    private boolean plain, rightAlign;

    public AppleDropdown(AppleHost host, int x, int y, int w, int h, Supplier<List<Component>> options,
                         IntSupplier selected, IntConsumer onSelect) {
        super(x, y, w, h);
        this.host = host; this.options = options; this.selected = selected; this.onSelect = onSelect;
    }

    public AppleDropdown plain() { plain = true; return this; }
    public AppleDropdown rightAlign() { rightAlign = true; return this; }
    public AppleDropdown optionEnabled(IntPredicate p) { enabledAt = p; return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean on = isEnabled();
        boolean hov = on && isMouseOverElement(mouseX, mouseY);
        boolean open = host.isOpenFor(this);
        if (!plain) AppleTex.round(open || hov ? AppleStyle.FILL_HOVER : AppleStyle.FILL, AppleStyle.R_FIELD).draw(g, mouseX, mouseY, x, y, w, h);
        else if (hov || open) AppleUi2.round(g, x, y, w, h, AppleStyle.R_FIELD, AppleStyle.HOVER_ROW);
        List<Component> opts = options.get();
        int sel = selected.getAsInt();
        Component c = sel >= 0 && sel < opts.size() ? opts.get(sel) : Component.literal("—");
        int col = dim(plain ? AppleStyle.SECONDARY : AppleStyle.LABEL, on);
        var fitted = AppleTextLayout.fit(font(), c, Math.max(0, w - 24));
        int tw = font().width(fitted);
        int tx = rightAlign ? x + w - 17 - tw : x + 7;
        g.drawString(font(), fitted, tx, cy(y, h), col, false);
        // ⌄：两段斜线画的 chevron（无对应图标，直接画像素）
        int cx = x + w - 9, cyy = y + h / 2 - 1;
        int cc = dim(plain ? AppleStyle.TERTIARY : AppleStyle.SECONDARY, on);
        for (int i = 0; i < 3; i++) {
            g.fill(cx - 3 + i, cyy + i, cx - 2 + i, cyy + i + 1, cc);
            g.fill(cx + 2 - i, cyy + i, cx + 3 - i, cyy + i + 1, cc);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        List<Component> opts = options.get();
        int w = AppleMenu.widthFor(opts, getSizeWidth());
        int px = getSelfPosition().x + (rightAlign ? getSizeWidth() - w - 6 : -3);
        int py = getSelfPosition().y + getSizeHeight() + 2;
        // 自身坐标相对 root；若嵌在子组里则换算为相对 root。
        if (getParent() != null && getParent() != host.root()) {
            px = getPositionX() - host.root().getPositionX() + (rightAlign ? getSizeWidth() - w - 6 : -3);
            py = getPositionY() - host.root().getPositionY() + getSizeHeight() + 2;
        }
        ApplePopover pop = new ApplePopover(px, py, w + 6, opts.size() * AppleMenu.ROW + 6);
        pop.addWidget(new AppleMenu(3, 3, w, opts, selected.getAsInt(), enabledAt, i -> {
            host.closePopover();
            onSelect.accept(i);
        }));
        host.openPopover(pop, true, this);
        return true;
    }
}
