package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.AppleEditBox;
import com.sorrowmist.useless.client.gui.ScaledEnergyAmount;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.*;

/**
 * 与服务端同步的数字输入框（产出速率 / 温度 / 数量 …）。统一处理了所有旧界面里手写的那套逻辑：
 * <ul><li>服务端值变化时刷新，但玩家正在输入（聚焦）或改过（dirty）时不打断；</li>
 * <li>吃 K/M/G/T/P/E 后缀（{@link ScaledEnergyAmount}）；</li>
 * <li>回车提交、关界面时提交未保存的修改（由 AppleScreen / ApplePanelScreen 自动调用）。</li></ul>
 */
public class NumberField {
    private final LongSupplier server;
    private final LongConsumer commit;
    private long min = 0, max = Long.MAX_VALUE;
    private LongFunction<String> format = ScaledEnergyAmount::format;
    private BooleanSupplier editable = () -> true;
    private Component hint;
    private int boxH = 14;
    EditBox box;
    private long lastSynced = Long.MIN_VALUE;
    private String syncedText = "";
    private boolean updating, dirty;

    /** @param server 服务端同步值（Menu 数据槽 / 方块实体）  @param commit 提交新值（发包） */
    public NumberField(LongSupplier server, LongConsumer commit) { this.server = server; this.commit = commit; }

    public NumberField min(long v) { min = v; return this; }
    public NumberField max(long v) { max = v; return this; }
    /** 显示格式；默认 {@code 1.5M} 这种缩写，温度这类要原样显示的用 {@code String::valueOf}。 */
    public NumberField format(LongFunction<String> f) { format = f; return this; }
    public NumberField editableWhen(BooleanSupplier s) { editable = s; return this; }
    public NumberField hint(Component c) { hint = c; return this; }
    /** EditBox 的点击高度（输入框外观尺寸由布局决定，这里只影响命中区域）。 */
    public NumberField boxHeight(int h) { boxH = h; return this; }

    /** 由宿主在 init 时调用。 */
    EditBox create(int x, int y, int w, Component narration) {
        return create(Minecraft.getInstance().font, x, y, w, narration);
    }

    EditBox create(net.minecraft.client.gui.Font font, int x, int y, int w, Component narration) {
        box = new AppleEditBox(font, x, y, w, boxH, narration);
        box.setMaxLength(24);
        box.setFilter(ScaledEnergyAmount::isValidInput);
        if (hint != null) box.setHint(hint);
        box.setResponder(v -> { if (!updating) dirty = !v.equals(syncedText); });
        box.setTextColor(AppleStyle.LABEL);
        box.setTextColorUneditable(AppleStyle.TERTIARY);
        lastSynced = Long.MIN_VALUE;
        sync(true);
        return box;
    }

    public EditBox box() { return box; }
    public boolean dirty() { return dirty; }

    /** 每 tick：服务端值同步进输入框。 */
    public void sync(boolean force) {
        if (box == null) return;
        box.setEditable(editable.getAsBoolean());
        long v = Math.max(min, server.getAsLong());
        if ((force || (!box.isFocused() && !dirty)) && v != lastSynced) set(v);
    }

    /** 程序改值（不提交）。 */
    public void set(long v) {
        v = Math.max(min, Math.min(max, v));
        syncedText = format.apply(v);
        lastSynced = v;
        updating = true;
        try { if (box != null) box.setValue(syncedText); }
        finally { updating = false; }
        dirty = false;
    }

    /** 当前值：没改过就用同步值（避免缩写格式解析损失精度），否则解析输入，失败回落到服务端值。 */
    public long value() {
        if (box == null) return server.getAsLong();
        if (!dirty && box.getValue().equals(syncedText) && lastSynced >= min && lastSynced != Long.MIN_VALUE) return lastSynced;
        return ScaledEnergyAmount.parse(box.getValue(), max).orElse(server.getAsLong());
    }

    /** 提交：钳制到 [min,max]、回写输入框、调用 commit。 */
    public void submit() { submit(value()); }
    public void submit(long v) {
        if (!editable.getAsBoolean()) return;
        v = Math.max(min, Math.min(max, v));
        set(v);
        commit.accept(v);
    }

    /** 加减（带溢出保护），然后提交。 */
    public void add(long delta) {
        long cur = value();
        long next;
        try { next = Math.addExact(cur, delta); }
        catch (ArithmeticException overflow) { next = delta > 0 ? max : min; }
        submit(next);
    }

    /** 关界面时：有未提交修改就提交。 */
    public void commitIfDirty() { if (dirty) submit(); }

    /** 纯文本输入框（搜索框）。 */
    public static class Text {
        private final Consumer<String> onChange; EditBox box; private Component hint;
        public Text(Consumer<String> onChange) { this.onChange = onChange; }
        public Text hint(Component c) { hint = c; return this; }
        EditBox create(int x, int y, int w, Component narration) {
            box = new AppleEditBox(Minecraft.getInstance().font, x, y, w, 12, narration);
            box.setMaxLength(64);
            if (hint != null) box.setHint(hint);
            box.setResponder(onChange);
            box.setTextColor(AppleStyle.LABEL);
            return box;
        }
        public EditBox box() { return box; }
        public String value() { return box == null ? "" : box.getValue(); }
    }
}
