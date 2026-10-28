package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.AppleCard;
import com.sorrowmist.useless.client.gui.apple.AppleLabel;
import com.sorrowmist.useless.client.gui.apple.AppleSettingRow;
import com.sorrowmist.useless.client.gui.apple.AppleSwitch;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** iOS「设置」分组：圆角卡片里若干等高行，行间细分隔线由 AppleSettingRow 自己画。 */
public final class SettingsGroup extends UiNode {
    private final List<UiNode> rows = new ArrayList<>();

    /** 文字 + 右侧值 + 箭头（点击打开二级页面 / 弹层）。 */
    public SettingsGroup row(Supplier<Component> label, Supplier<Component> value, Runnable onClick) {
        rows.add(new Leaf(0, AppleTheme.get().rowH, (x, y, w, h) -> new AppleSettingRow(x, y, w, h, label, value, onClick)));
        return this;
    }
    /** 文字 + 开关。 */
    public SettingsGroup toggleRow(Supplier<Component> label, BooleanSupplier value, Runnable toggle) {
        return toggleRow(label, value, toggle, null);
    }
    public SettingsGroup toggleRow(Supplier<Component> label, BooleanSupplier value, Runnable toggle, String id) {
        AppleTheme t = AppleTheme.get();
        UiNode sw = new Leaf(t.switchW, t.switchH, (x, y, w, h) -> new AppleSwitch(x, y, w, h, value, toggle));
        if (id != null) sw.id(id);
        rows.add(new Flex(true, new Ui.LabelNode(AppleLabel.Kind.BODY, label).grow().nudge(0, 3), sw).center().height(t.rowH + 3));
        return this;
    }
    /** 任意自定义行。 */
    public SettingsGroup row(UiNode custom) { rows.add(custom); return this; }

    private int pad() { return AppleTheme.get().cardPad; }
    @Override protected int prefW() { int m = 0; for (UiNode r : rows) m = Math.max(m, r.measureW()); return m + pad() * 2; }
    @Override protected int prefH() { int s = 0; for (UiNode r : rows) s += r.measureH(); return s + 4; }
    @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
        ctx.add(new AppleCard(x, y, w, h));
        int cy = y + 2;
        for (UiNode r : rows) { int rh = r.measureH(); r.layoutAt(ctx, x + pad(), cy, w - pad() * 2, rh); cy += rh; }
    }
}
