package com.sorrowmist.useless.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Apple 风输入框 v2：白底圆角 + 聚焦蓝色光环；正文/提示/光标/选区全部自绘且<b>无文字阴影</b>。
 * 输入、光标移动、选择、剪贴板等行为仍完全由原版 {@link EditBox} 处理；仅通过公开 API 读取状态。
 */
public class AppleEditBox extends EditBox {
    private final Font appleFont;
    private int textColor = 0x1D1D1F;
    private int textColorUneditable = 0x8E8E93;
    private boolean editable = true;
    private boolean bordered = true;
    private String hint = "";
    private String suggestion;
    private int scroll;
    private float textScale = 1f;

    public AppleEditBox textScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid text scale");
        textScale = scale;
        return this;
    }

    public float textScale() { return textScale; }
    private int selectionAnchor;
    private int dragAnchor;
    private boolean decorated;
    private int decorationX, decorationY, decorationWidth, decorationHeight;

    public AppleEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.appleFont = font;
    }

    @Override public void setTextColor(int c) { super.setTextColor(c); textColor = AppleUi.readable(c); }
    @Override public void setTextColorUneditable(int c) { super.setTextColorUneditable(c); textColorUneditable = 0x8E8E93; }
    @Override public void setEditable(boolean e) { super.setEditable(e); editable = e; }
    @Override public void setBordered(boolean b) { super.setBordered(b); bordered = b; }
    @Override public void setHint(Component h) { super.setHint(h); hint = h.getString(); }
    @Override public void setSuggestion(String s) { super.setSuggestion(s); suggestion = s; }

    /** 文字可右对齐，但整块输入框（包括留白）都必须能聚焦。 */
    public void setDecorationBounds(int x, int y, int width, int height) {
        decorated = true;
        decorationX = x; decorationY = y;
        decorationWidth = width; decorationHeight = height;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!decorated) return super.isMouseOver(mouseX, mouseY);
        return visible && mouseX >= decorationX && mouseX < decorationX + decorationWidth
                && mouseY >= decorationY && mouseY < decorationY + decorationHeight;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return active && isMouseOver(mouseX, mouseY);
    }

    @Override
    public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        selectionAnchor = Math.max(0, Math.min(position, getValue().length()));
    }

    public int selectionAnchor() { return selectionAnchor; }

    /** 自绘窗口与原版私有 displayPos 不同，鼠标定位也必须使用同一个 scroll。 */
    private int cursorAt(double mouseX) {
        int tx = getX() + (bordered ? 4 : 0);
        int inner = Math.max(1, (int) (getInnerWidth() / textScale));
        String value = getValue();
        scroll = Math.min(scroll, value.length());
        String visibleText = appleFont.plainSubstrByWidth(value.substring(scroll), inner);
        int pixels = Math.max(0, Math.min(inner, (int) Math.floor((mouseX - tx) / textScale)));
        return scroll + appleFont.plainSubstrByWidth(visibleText, pixels).length();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        moveCursorTo(cursorAt(mouseX));
        dragAnchor = selectionAnchor;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (!isFocused() || !canConsumeInput()) return;
        setCursorPosition(cursorAt(mouseX));
        setHighlightPos(dragAnchor);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        int x = getX(), y = getY();
        int tx = x, ty = y, inner = getWidth();
        if (bordered) {
            AppleUi.field(g, x, y, getWidth(), getHeight(), isFocused());
            tx = x + 4; ty = y + Math.round((getHeight() - 8 * textScale) / 2); inner = getWidth() - 8;
        }
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(textScale, textScale, 1);
        tx = 0; ty = 0;
        inner = Math.max(1, (int) (inner / textScale));
        String value = getValue();
        int cursor = Math.min(getCursorPosition(), value.length());
        // 维护自己的可见窗口，保证光标始终可见
        if (scroll > value.length()) scroll = value.length();
        if (cursor < scroll) scroll = cursor;
        while (scroll < cursor && appleFont.width(value.substring(scroll, cursor)) > inner) scroll++;
        String visible = appleFont.plainSubstrByWidth(value.substring(scroll), inner);
        int color = editable ? textColor : textColorUneditable;
        if (value.isEmpty() && !hint.isEmpty() && !isFocused()) {
            g.drawString(appleFont, appleFont.plainSubstrByWidth(hint, inner), tx, ty, 0x8E8E93, false);
        }
        // 选区
        int s0 = Math.min(cursor, selectionAnchor), s1 = Math.max(cursor, selectionAnchor);
        if (s0 != s1) {
            int a = Math.max(s0, scroll) - scroll, b = Math.min(s1 - scroll, visible.length());
            if (b > a) {
                int ax = tx + appleFont.width(visible.substring(0, a));
                int bx = tx + appleFont.width(visible.substring(0, b));
                AppleUi.fillRound(g, ax, ty - 1, bx - ax, 10, 1.5f, 0x55007AFF);
            }
        }
        g.drawString(appleFont, visible, tx, ty, 0xFF000000 | color, false);
        if (suggestion != null && cursor == value.length() && !value.isEmpty()) {
            g.drawString(appleFont, suggestion, tx + appleFont.width(visible), ty, 0xFF8E8E93, false);
        }
        if (isFocused() && editable && (Util.getMillis() / 500L) % 2L == 0L) {
            int rel = cursor - scroll;
            if (rel >= 0 && rel <= visible.length()) {
                int cx = tx + appleFont.width(visible.substring(0, rel));
                AppleUi.fillRound(g, cx, ty - 1, 1, 10, 0.5f, AppleUi.BLUE);
            }
        }
        g.pose().popPose();
    }
}
