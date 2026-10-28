package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Apple 控件库的公共基类：自研轻量 Widget（纯客户端，无第三方库）。 */
public abstract class AppleWidget extends Widget {
    protected BooleanSupplier enabled = () -> true;
    protected BooleanSupplier shown = () -> true;

    protected AppleWidget(int x, int y, int w, int h) {
        super(x, y, w, h);
        setClientSideWidget();
    }

    @SuppressWarnings("unchecked")
    public <T extends AppleWidget> T enabledWhen(BooleanSupplier s) { this.enabled = s; return (T) this; }
    @SuppressWarnings("unchecked")
    public <T extends AppleWidget> T shownWhen(BooleanSupplier s) { this.shown = s; return (T) this; }

    /** 测试/查找用 id（UiShotHook 通过 id 找控件并按其屏幕坐标真实点击）。 */
    @SuppressWarnings("unchecked")
    public <T extends AppleWidget> T id(String id) { setId(id); return (T) this; }

    public boolean isEnabled() { return enabled.getAsBoolean(); }

    @Override
    public boolean isVisible() { return super.isVisible() && shown.getAsBoolean(); }

    protected static Font font() { return Minecraft.getInstance().font; }

    protected static void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, false);
    }

    protected static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }

    /** 正常字号，可选加粗；紧凑控件由 AppleTextLayout 单独处理缩放与留白。 */
    protected static void text(GuiGraphics g, Component c, int x, int y, int color, boolean bold) {
        g.drawString(font(), bold ? c.copy().withStyle(s -> s.withBold(true)) : c, x, y, color, false);
    }

    /** 整数倍放大（仅用于大数字），任何 GUI 缩放下都不糊。 */
    protected static void big(GuiGraphics g, Component c, int x, int y, int scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font(), c, 0, 0, color, false);
        g.pose().popPose();
    }

    protected static int cy(int y, int h) { return y + (h - 9) / 2; }

    protected static int dim(int argb, boolean on) {
        if (on) return argb;
        int a = ((argb >>> 24) & 0xFF) * 45 / 100;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    protected static Supplier<Component> lit(Component c) { return () -> c; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive()) return false;
        if (isMouseOverElement(mouseX, mouseY) && isEnabled() && button == 0) {
            if (onClick(mouseX, mouseY)) {
                playButtonClickSound();
                return true;
            }
        }
        return false;
    }

    /** 子类实现点击；返回 true 表示消费。 */
    protected boolean onClick(double mouseX, double mouseY) { return false; }
}
