package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

/**
 * 灰色圆角无边框输入框。文字编辑（光标、选区、剪贴板、IME）沿用原版 {@link EditBox}（原逻辑/提交时机不变），
 * 本 Widget 负责外观并每帧把 EditBox 摆进自己的内容区。
 */
public class AppleTextField extends AppleWidget {
    private final EditBox box;
    private final int padX;
    private int bg = AppleTex.FILL;
    private boolean rightAlign;
    private boolean grayWhenDisabled;

    /** Preserve a visible gray field even when there is no editable selection. */
    public AppleTextField grayWhenDisabled() { grayWhenDisabled = true; return this; }
    private int trailingInset;
    private java.util.function.BooleanSupplier invalid = () -> false;

    /** Validation belongs to the screen; this only decorates its current result. */
    public AppleTextField invalidWhen(java.util.function.BooleanSupplier invalid) {
        this.invalid = java.util.Objects.requireNonNull(invalid);
        return this;
    }

    /** Reserve room for a unit/accessory while retaining the full decorated click target. */
    public AppleTextField trailingInset(int pixels) { trailingInset = Math.max(0, pixels); return this; }
    private com.sorrowmist.useless.client.gui.AppleUi.Icon icon;

    public AppleTextField icon(com.sorrowmist.useless.client.gui.AppleUi.Icon i) { icon = i; return this; }

    public AppleTextField(int x, int y, int w, int h, EditBox box) {
        this(x, y, w, h, box, 6);
    }

    public AppleTextField(int x, int y, int w, int h, EditBox box, int padX) {
        super(x, y, w, h);
        this.box = box; this.padX = padX;
        box.setBordered(false);
    }

    public AppleTextField background(int c) { bg = c; return this; }
    public AppleTextField rightAlign() { rightAlign = true; return this; }
    public EditBox box() { return box; }

    @Override
    public void updateLayout() {
        if (!isVisible() || !box.visible) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        int leadingInset = icon == null ? padX : Math.max(padX, 17);
        int inner = Math.max(1, w - leadingInset - padX - trailingInset);
        float textScale = box instanceof AppleEditBox input ? input.textScale() : 1f;
        int tw = rightAlign ? Math.round(font().width(box.getValue()) * textScale) : inner;
        boolean fit = rightAlign && !box.isFocused() && tw < inner;
        box.setX(fit ? x + w - padX - trailingInset - tw : x + leadingInset);
        box.setY(y + Math.round((h - 8 * textScale) / 2) + 1);
        box.setWidth(fit ? tw + 1 : inner);
        if (box instanceof AppleEditBox input) input.setDecorationBounds(x, y, w, h);
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        boolean vis = isVisible() && box.visible;
        if (!vis) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean error = invalid.getAsBoolean();
        boolean highlighted = box.active && (box.isFocused() || error);
        if (bg != 0) {
            String state = !box.active && !grayWhenDisabled ? "disabled" : highlighted ? (error ? "error" : "focus") : "default";
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g, "field_" + state + "_200", x, y, w, h);
        }
        if (icon != null) com.sorrowmist.useless.client.gui.AppleUi.icon(g, icon, x + 5, y + (h - 8) / 2, 8, AppleTex.SECONDARY);
        updateLayout();
    }
}
