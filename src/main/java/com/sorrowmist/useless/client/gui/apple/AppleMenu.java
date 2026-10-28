package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/** 弹出菜单的行列表（macOS 菜单风格）：悬停蓝底白字、当前项打勾、禁用项灰色。 */
public class AppleMenu extends AppleWidget {
    public static final int ROW = 15;
    private final List<Component> options;
    private final int selected;
    private final IntConsumer pick;
    private final IntPredicate enabledAt;

    public AppleMenu(int x, int y, int w, List<Component> options, int selected, IntPredicate enabledAt, IntConsumer pick) {
        super(x, y, w, options.size() * ROW);
        this.options = options; this.selected = selected; this.pick = pick; this.enabledAt = enabledAt;
    }

    public static int widthFor(List<Component> opts, int min) {
        int w = min;
        for (Component c : opts) w = Math.max(w, font().width(c) + 30);
        return w;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth();
        for (int i = 0; i < options.size(); i++) {
            int ry = y + i * ROW;
            boolean on = enabledAt.test(i);
            boolean hov = on && mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
            if (hov) DesignAssets.draw(g, "dropdown_item_selected_188x34", x, ry, w, ROW);
            int col = hov ? AppleStyle.ON_ACCENT : on ? AppleStyle.LABEL : AppleStyle.QUATERNARY;
            if (i == selected) AppleUi.icon(g, AppleUi.Icon.CHECK, x + 5, ry + (ROW - 8) / 2, 8, hov ? AppleStyle.ON_ACCENT : AppleStyle.BLUE);
            text(g, options.get(i), x + 17, cy(ry, ROW), col);
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int i = (int) ((my - getPositionY()) / ROW);
        if (i < 0 || i >= options.size() || !enabledAt.test(i)) return true;
        pick.accept(i);
        return true;
    }
}
