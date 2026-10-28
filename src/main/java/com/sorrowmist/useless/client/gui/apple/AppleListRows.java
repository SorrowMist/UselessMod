package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 容器列表行：拖拽点 · 彩色图标块（蓝=输出 / 橙=输入）· 名称 · 副标题 · 右侧 上移/下移/移除。
 * 只负责外观；命中判定继续用宿主 Screen 原有的列表逻辑（Shift 区间、Ctrl 多选、移动、解绑）。
 */
public class AppleListRows extends AppleWidget {
    public record Row(Component name, Component detail, boolean output, boolean selected, boolean enabled,
                      boolean canUp, boolean canDown) {}

    private final Supplier<List<Row>> rows;
    private final int rowH;
    private Supplier<Component> empty = () -> Component.empty();

    public AppleListRows(int x, int y, int w, int h, int rowH, Supplier<List<Row>> rows) {
        super(x, y, w, h);
        this.rowH = rowH; this.rows = rows;
    }

    public AppleListRows empty(Supplier<Component> e) { empty = e; return this; }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        List<Row> list = rows.get();
        if (list.isEmpty()) {
            Component e = empty.get();
            text(g, e, x + (w - font().width(e)) / 2, y + h / 2 - 4, AppleTex.SECONDARY);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = y + i * rowH;
            if (ry + rowH > y + h) break;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            if (r.selected()) AppleUi2.round(g, x + 2, ry + 1, w - 4, rowH - 2, 4.5f, AppleTex.SELECTED_ROW);
            else if (hov) AppleUi2.round(g, x + 2, ry + 1, w - 4, rowH - 2, 4.5f, 0x0B000000);
            // 拖拽点
            AppleUi.icon(g, AppleUi.Icon.GRIP, x + 4, ry + (rowH - 8) / 2, 8, 0xFFC0C0C6);
            // 图标块
            int tile = 13, tx = x + 14, ty = ry + (rowH - tile) / 2;
            int tint = r.output() ? 0xFFD9E9FF : 0xFFFFEBD1;
            int glyph = r.output() ? AppleTex.BLUE : 0xFFF08A00;
            AppleUi2.round(g, tx, ty, tile, tile, 3.5f, dim(tint, r.enabled()));
            AppleUi.icon(g, r.output() ? AppleUi.Icon.ARROW_OUT : AppleUi.Icon.ARROW_IN, tx + 2, ty + 2, 9, dim(glyph, r.enabled()));
            // 名称
            int nx = tx + tile + 5;
            int right = x + w - 42;
            Component name = r.name();
            String s = font().plainSubstrByWidth(name.getString(), right - nx);
            int nameCol = r.enabled() ? AppleTex.LABEL : AppleTex.SECONDARY;
            text(g, Component.literal(s).withStyle(name.getStyle()), nx, ry + (rowH - 8) / 2 + 1, nameCol);
            // 右侧操作
            int ic = 0xFF8E8E93;
            int bx = x + w - 40;
            AppleUi.icon(g, AppleUi.Icon.ARROW_UP, bx + 2, ry + (rowH - 8) / 2, 8, r.canUp() ? hot(mouseX, mouseY, bx, ry, ic) : 0x40000000);
            AppleUi.icon(g, AppleUi.Icon.ARROW_DOWN, bx + 13, ry + (rowH - 8) / 2, 8, r.canDown() ? hot(mouseX, mouseY, bx + 11, ry, ic) : 0x40000000);
            AppleUi.icon(g, AppleUi.Icon.XMARK, bx + 26, ry + (rowH - 8) / 2, 8,
                    mouseX >= bx + 24 && mouseX < bx + 38 && mouseY >= ry && mouseY < ry + rowH ? AppleTex.RED : ic);
            if (i < list.size() - 1 && !r.selected() && !list.get(i + 1).selected())
                g.fill(nx, ry + rowH - 1, x + w - 4, ry + rowH, AppleTex.SEPARATOR);
        }
    }

    private int hot(int mx, int my, int bx, int ry, int c) {
        return mx >= bx && mx < bx + 11 && my >= ry && my < ry + rowH ? AppleTex.BLUE : c;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
