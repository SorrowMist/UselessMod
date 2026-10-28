package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.apple.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.*;

/**
 * Apple 界面声明式 DSL（静态工厂，{@code import static ...api.Ui.*}）。
 * 布局：{@link #column}/{@link #row}/{@link #card}/{@link #section}；控件：label/button/toggle/segmented/...；
 * 绑定：所有文字 / 数值 / 状态都传 Supplier，每帧读取 Menu 同步字段。
 */
public final class Ui {
    private Ui() {}

    public static AppleTheme t() { return AppleTheme.get(); }

    // ------------------------------------------------------------------ 文本
    public static Supplier<Component> tr(String key, Object... args) { Component c = Component.translatable(key, args); return () -> c; }
    /** 动态翻译：参数每帧求值。 */
    public static Supplier<Component> trf(String key, Supplier<?>... args) {
        return () -> { Object[] a = new Object[args.length]; for (int i = 0; i < a.length; i++) a[i] = args[i].get(); return Component.translatable(key, a); };
    }
    public static Supplier<Component> lit(String s) { Component c = Component.literal(s); return () -> c; }
    /** 二选一文本（开/关、在线/离线）。 */
    public static Supplier<Component> either(BooleanSupplier cond, String keyTrue, String keyFalse) {
        Component a = Component.translatable(keyTrue), b = Component.translatable(keyFalse);
        return () -> cond.getAsBoolean() ? a : b;
    }
    /** bindText：任意 Supplier&lt;String&gt; → Component。 */
    public static Supplier<Component> bindText(Supplier<String> s) { return () -> Component.literal(s.get()); }

    // ------------------------------------------------------------------ 布局
    public static Flex column(UiNode... kids) { return new Flex(false, kids); }
    public static Flex row(UiNode... kids) { return new Flex(true, kids).center(); }
    /** 白色圆角卡片（竖排）。 */
    public static Flex card(UiNode... kids) { return column(kids).card(); }
    /** 卡片 + 灰色小标题（iOS 分组标题）。 */
    public static Flex section(Supplier<Component> caption, UiNode... kids) {
        Flex f = card(caption(caption)); f.add(kids); return f;
    }
    /** 卡片 + 彩色图标块标题（Apple 风机器卡片最常见的头部）。 */
    public static Flex section(AppleUi.Icon icon, int accent, Supplier<Component> title, UiNode... kids) {
        Flex f = card(header(icon, accent, title)); f.add(kids); return f;
    }
    /** 图标块 + 加粗标题。 */
    public static Flex header(AppleUi.Icon icon, int accent, Supplier<Component> title) {
        return row(iconTile(icon, accent), headline(title).grow().nudge(0, 1)).gap(5); // 标题文字视觉基线下移 1px，与旧界面一致
    }
    public static UiNode space(int px) { return new Leaf(px, px, (x, y, w, h) -> null) { @Override protected void place(UiContext c, int x, int y, int w, int h) {} }; }
    public static UiNode spacer() { return space(0).grow(); }
    /** 绝对定位容器（迁移旧界面 / 特殊图形时的逃生口）。 */
    public static Absolute absolute(int w, int h) { return new Absolute(w, h); }

    // ------------------------------------------------------------------ 文字控件
    public static LabelNode label(AppleLabel.Kind k, Supplier<Component> s) { return new LabelNode(k, s); }
    public static LabelNode headline(Supplier<Component> s) { return label(AppleLabel.Kind.HEADLINE, s); }
    public static LabelNode body(Supplier<Component> s) { return label(AppleLabel.Kind.BODY, s); }
    public static LabelNode caption(Supplier<Component> s) { return label(AppleLabel.Kind.CAPTION, s); }
    public static LabelNode footnote(Supplier<Component> s) { return label(AppleLabel.Kind.FOOTNOTE, s); }
    /** 大数字 + 小单位（例如倒计时 “12 tick”）。 */
    public static Leaf bigNumber(Supplier<Component> number, String unit) {
        return new Leaf(60, 16, (x, y, w, h) -> new AppleCanvas(x, y, w, h, (g, mx, my, cx, cy, cw, ch) -> {
            var f = Minecraft.getInstance().font;
            Component n = number.get().copy().withStyle(st -> st.withBold(true));
            g.pose().pushPose(); g.pose().translate(cx, cy, 0); g.pose().scale(2, 2, 1);
            g.drawString(f, n, 0, 0, AppleStyle.LABEL, false);
            g.pose().popPose();
            if (unit != null) g.drawString(f, unit, cx + f.width(n) * 2 + 3, cy + 7, AppleStyle.TERTIARY, false);
        }));
    }
    /** 状态胶囊（标题栏右侧 / 卡片里）。 */
    public static Leaf statusPill(Supplier<Component> s, IntSupplier accent) {
        return new Leaf(t().pillW, 12, (x, y, w, h) -> new AppleBadge(x, y, w, s, accent));
    }
    public static Leaf iconTile(AppleUi.Icon icon, int accent) {
        int s = t().iconTile;
        return new Leaf(s, s, (x, y, w, h) -> new AppleIconTile(x, y, Math.min(w, h), () -> icon, () -> accent).filled());
    }

    // ------------------------------------------------------------------ 交互控件
    public static ButtonNode button(Supplier<Component> text, Runnable action) { return new ButtonNode(text, action); }
    public static Leaf toggle(BooleanSupplier value, Runnable toggle) {
        return new Leaf(t().switchW, t().switchH, (x, y, w, h) -> new AppleSwitch(x, y, w, h, value, toggle));
    }
    public static Leaf segmented(List<Component> options, IntSupplier selected, IntConsumer onSelect) {
        return new Leaf(options.size() * 40, t().controlH + 2, (x, y, w, h) -> new AppleSegmented(x, y, w, h, () -> options, selected, onSelect));
    }
    /** 页签 = 顶部分段控件（切换时自动播放翻页淡入）。 */
    public static Leaf tabs(List<Component> options, IntSupplier selected, IntConsumer onSelect) {
        return segmented(options, selected, onSelect);
    }
    public static Leaf field(NumberField f) {
        return new Leaf(80, 16, (x, y, w, h) -> { f.box.setX(0); return new AppleTextField(x, y, w, h, f.box).rightAlign(); });
    }
    /** 搜索框（左侧放大镜图标）。 */
    public static Leaf searchField(NumberField.Text f) {
        return new Leaf(120, 16, (x, y, w, h) -> new AppleTextField(x, y, w, h, f.box).icon(AppleUi.Icon.SEARCH));
    }
    /** − 数值 + 步进器（数值是可编辑输入框）。 */
    public static Leaf stepper(NumberField f, Runnable minus, Runnable plus) {
        return new Leaf(74, 15, (x, y, w, h) -> new AppleStepper(x, y, w, h, f.box, minus, plus));
    }
    public static Leaf dropdown(AppleHost host, Supplier<List<Component>> options, IntSupplier selected, IntConsumer onSelect) {
        return new Leaf(90, t().controlH + 2, (x, y, w, h) -> new AppleDropdown(host, x, y, w, h, options, selected, onSelect));
    }
    /** 翻页胶囊 “‹ 1 / 3 ›”。page 从 0 开始；go(delta) 负责发包。只有 1 页时自动隐藏。 */
    public static Leaf pager(IntSupplier page, IntSupplier count, IntConsumer go) {
        return pager(() -> Component.literal((page.getAsInt() + 1) + " / " + count.getAsInt()),
                page, count, go).bindVisible(() -> count.getAsInt() > 1);
    }
    public static Leaf pager(Supplier<Component> label, IntSupplier page, IntSupplier count, IntConsumer go) {
        return new Leaf(t().pillW, 14, (x, y, w, h) -> new ApplePager(x, y, w, h, label,
                () -> page.getAsInt() > 0, () -> go.accept(-1),
                () -> page.getAsInt() + 1 < count.getAsInt(), () -> go.accept(1)));
    }
    public static SettingsGroup settingsGroup() { return new SettingsGroup(); }
    /** 列表（容器 / 条目行）。点击逻辑由宿主自己按行号处理，这里负责外观与空状态。 */
    public static Leaf list(int rowH, Supplier<List<AppleListRows.Row>> rows, Supplier<Component> empty) {
        return new Leaf(160, rowH * 4, (x, y, w, h) -> new AppleListRows(x, y, w, h, rowH, rows).empty(empty));
    }

    // ------------------------------------------------------------------ 进度
    public static ProgressNode progress(DoubleSupplier ratio) { return new ProgressNode(ratio); }
    public static Leaf energyBar(Supplier<Component> title, Supplier<Component> value, LongSupplier stored, LongSupplier capacity) {
        return new Leaf(120, 12 + t().energyBarH, (x, y, w, h) -> new AppleEnergyBar(x, y, w, h - 12, title, value, stored, capacity));
    }

    // ------------------------------------------------------------------ 槽位
    /** 把 Menu 中 [first, first+cols*rows) 的槽位按网格摆进这个节点（客户端重排坐标）。 */
    public static SlotsNode slotGrid(int first, int cols, int rows) { return new SlotsNode(first, cols, rows, true); }
    /** 单个槽位（输出格等）。 */
    public static SlotsNode slot(int index) { return new SlotsNode(index, 1, 1, true); }
    /** 只占位、不移动：槽位保持 Menu 里写的坐标（迁移旧界面时用）。 */
    public static SlotsNode slotsAsIs(int first, int cols, int rows) { return new SlotsNode(first, cols, rows, false); }
    /** 玩家物品栏卡片（标题 + 3×9 + 快捷栏），自动找出 Menu 中属于玩家 Inventory 的 36 个槽位。 */
    public static PlayerInventoryNode playerInventory() { return new PlayerInventoryNode(); }

    // ------------------------------------------------------------------ 自绘
    public static Leaf canvas(int w, int h, AppleCanvas.Painter p) { return new Leaf(w, h, (x, y, ww, hh) -> new AppleCanvas(x, y, ww, hh, p)); }

    // ================================================================== 节点类型
    public static final class LabelNode extends Leaf {
        private int color = -1, align; private boolean wrap;
        LabelNode(AppleLabel.Kind k, Supplier<Component> s) {
            super(0, 9, null);
            this.kind = k; this.text = s;
        }
        private final AppleLabel.Kind kind; private final Supplier<Component> text;
        public LabelNode color(int c) { color = c; return this; }
        public LabelNode right() { align = 2; return this; }
        public LabelNode centered() { align = 1; return this; }
        public LabelNode wrap() { wrap = true; return this; }
        @Override protected int prefW() { return Minecraft.getInstance().font.width(text.get()); }
        @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
            AppleLabel l = new AppleLabel(x, y, w, kind, text);
            if (color != -1) l.color(color);
            if (align == 2) l.right(); else if (align == 1) l.center();
            if (wrap) l.wrap();
            ctx.add(this, l);
        }
    }

    public static final class ButtonNode extends Leaf {
        private ApplePushButton.Kind kind = ApplePushButton.Kind.SECONDARY;
        private AppleUi.Icon icon;
        private final Supplier<Component> text; private final Runnable action;
        ButtonNode(Supplier<Component> text, Runnable action) { super(0, 0, null); this.text = text; this.action = action; }
        public ButtonNode primary() { kind = ApplePushButton.Kind.PRIMARY; return this; }
        public ButtonNode secondary() { kind = ApplePushButton.Kind.SECONDARY; return this; }
        public ButtonNode tinted() { kind = ApplePushButton.Kind.TINTED; return this; }
        public ButtonNode danger() { kind = ApplePushButton.Kind.DANGER; return this; }
        public ButtonNode plain() { kind = ApplePushButton.Kind.PLAIN; return this; }
        public ButtonNode kind(ApplePushButton.Kind k) { kind = k; return this; }
        public ButtonNode icon(AppleUi.Icon i) { icon = i; return this; }
        @Override protected int prefW() { return Minecraft.getInstance().font.width(text.get()) + 20; }
        @Override protected int prefH() { return t().buttonH; }
        @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
            ApplePushButton b = new ApplePushButton(x, y, w, h, kind, text, action);
            if (icon != null) b.icon(icon);
            ctx.add(this, b);
        }
    }

    public static final class ProgressNode extends Leaf {
        private IntSupplier color = () -> t().warning; private Supplier<Component> inner;
        private final DoubleSupplier ratio;
        ProgressNode(DoubleSupplier r) { super(60, 0, null); ratio = r; }
        public ProgressNode color(int c) { color = () -> c; return this; }
        public ProgressNode color(IntSupplier c) { color = c; return this; }
        /** 条内文字（例如 “35%”），条需要 ≥ 10 高。 */
        public ProgressNode text(Supplier<Component> s) { inner = s; return this; }
        @Override protected int prefH() { return inner != null ? 11 : t().progressH; }
        @Override protected void place(UiContext ctx, int x, int y, int w, int h) {
            AppleProgress p = new AppleProgress(x, y, w, h, ratio, color);
            if (inner != null) p.inner(inner);
            ctx.add(this, p);
        }
    }
}
