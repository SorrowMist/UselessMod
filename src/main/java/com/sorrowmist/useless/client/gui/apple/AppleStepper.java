package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.AppleEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 步进器：灰色胶囊「− 数值 +」。数值可为只读文字（value）或可编辑的原版 EditBox（box，居中摆放，
 * 编辑/提交逻辑沿用宿主）。−/+ 点击回调宿主原逻辑；Shift 等修饰键由宿主在回调里自行读取。
 */
public class AppleStepper extends AppleWidget {
    private final Runnable minus, plus;
    private Supplier<Component> value;
    private EditBox box;
    private BooleanSupplier canMinus = () -> true, canPlus = () -> true;
    private static final int BTN = 15;
    private boolean trailing;
    private boolean grayWhenDisabled;

    /** Disabled inputs keep their field silhouette, but never become editable/clickable. */
    public AppleStepper grayWhenDisabled() { grayWhenDisabled = true; return this; }

    /** Reference layout: editable field, 4px gap, then the original 36px −/+ control. */
    public AppleStepper trailingButtons() { trailing = true; return this; }

    public AppleStepper(int x, int y, int w, int h, Supplier<Component> value, Runnable minus, Runnable plus) {
        super(x, y, w, h);
        this.value = value; this.minus = minus; this.plus = plus;
    }

    public AppleStepper(int x, int y, int w, int h, EditBox box, Runnable minus, Runnable plus) {
        super(x, y, w, h);
        this.box = box; this.minus = minus; this.plus = plus;
        box.setBordered(false);
    }

    public AppleStepper limits(BooleanSupplier canMinus, BooleanSupplier canPlus) {
        this.canMinus = canMinus; this.canPlus = canPlus; return this;
    }

    @Override
    public void updateLayout() {
        if (box == null) return;
        box.visible = isVisible();
        if (!box.visible) { box.setFocused(false); return; }
        boolean on = isEnabled();
        box.active = on;
        box.setEditable(on);
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        if (trailing) {
            int fieldWidth = Math.max(1, w - 40);
            box.setX(x + 6);
            box.setY(cy(y, h));
            box.setWidth(Math.max(1, fieldWidth - 12));
            if (box instanceof AppleEditBox input) input.setDecorationBounds(x, y, fieldWidth, h);
            return;
        }
        int inner = Math.max(1, w - BTN * 2 - 4);
        int tw = font().width(box.getValue());
        boolean fit = !box.isFocused() && tw < inner;
        box.setX(fit ? x + (w - tw) / 2 : x + BTN + 2);
        box.setY(cy(y, h));
        box.setWidth(fit ? tw + 1 : inner);
        if (box instanceof AppleEditBox input) input.setDecorationBounds(x + BTN, y, w - BTN * 2, h);
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        updateLayout();
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean on = isEnabled();
        if (trailing) {
            int fw = Math.max(1, w - 40), bx = x + w - 36;
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g,
                    box != null && on && box.isFocused() ? "field_focus_200"
                            : on || grayWhenDisabled ? "field_default_200" : "field_disabled_200", x, y, fw, h);
            com.sorrowmist.useless.client.gui.DesignAssets.draw(g, "stepper_72x32", bx, y, 36, h);
            if (on && mouseY >= y && mouseY < y + h && mouseX >= bx && mouseX < x + w) {
                boolean plusSide = mouseX >= bx + 18;
                if ((plusSide ? canPlus : canMinus).getAsBoolean())
                    AppleUi2.round(g, plusSide ? bx + 18 : bx, y, 18, h, 4, AppleStyle.HOVER_ROW);
            }
            if (box == null && value != null)
                AppleTextLayout.drawCentered(g, font(), value.get(), x, y, fw, h, 4, 2, null, dim(AppleStyle.LABEL, on));
            return;
        }
        if (box != null && box.isFocused()) AppleTex.round(AppleStyle.FOCUS_RING, AppleStyle.R_FIELD + 1).draw(g, mouseX, mouseY, x - 1.5f, y - 1.5f, w + 3, h + 3);
        AppleTex.round(AppleStyle.FILL, AppleStyle.R_FIELD).draw(g, mouseX, mouseY, x, y, w, h);
        boolean hm = on && canMinus.getAsBoolean() && mouseX >= x && mouseX < x + BTN && mouseY >= y && mouseY < y + h;
        boolean hp = on && canPlus.getAsBoolean() && mouseX >= x + w - BTN && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hm) AppleUi2.round(g, x, y, BTN, h, AppleStyle.R_FIELD, AppleStyle.FILL_PRESSED);
        if (hp) AppleUi2.round(g, x + w - BTN, y, BTN, h, AppleStyle.R_FIELD, AppleStyle.FILL_PRESSED);
        g.fill(x + BTN, y + 3, x + BTN + 1, y + h - 3, AppleStyle.SEPARATOR);
        g.fill(x + w - BTN - 1, y + 3, x + w - BTN, y + h - 3, AppleStyle.SEPARATOR);
        int mc = dim(AppleStyle.LABEL, on && canMinus.getAsBoolean());
        int pc = dim(AppleStyle.LABEL, on && canPlus.getAsBoolean());
        int my = y + h / 2;
        g.fill(x + BTN / 2 - 3, my, x + BTN / 2 + 3, my + 1, mc);
        int px = x + w - BTN + BTN / 2;
        g.fill(px - 3, my, px + 3, my + 1, pc);
        g.fill(px, my - 3, px + 1, my + 4, pc);
        int inner = w - BTN * 2 - 4;
        if (box == null && value != null) {
            Component c = value.get();
            text(g, c, x + (w - font().width(c)) / 2, cy(y, h), dim(AppleStyle.LABEL, on));
        }
    }

    @Override
    protected boolean onClick(double mx, double my) {
        int x = getPositionX(), w = getSizeWidth();
        if (trailing) {
            if (mx < x + w - 36) return false;
            if (mx < x + w - 18) { if (canMinus.getAsBoolean()) minus.run(); }
            else if (canPlus.getAsBoolean()) plus.run();
            return true;
        }
        if (mx < x + BTN) { if (canMinus.getAsBoolean()) minus.run(); return true; }
        if (mx >= x + w - BTN) { if (canPlus.getAsBoolean()) plus.run(); return true; }
        return false; // 中间交给 EditBox（宿主 super.mouseClicked）
    }
}
