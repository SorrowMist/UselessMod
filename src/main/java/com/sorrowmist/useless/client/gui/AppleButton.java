package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.compat.forge1201.client.PortButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Apple 风胶囊按钮；行为与 {@link PressableAE2Button} 完全一致（按下态与复位），仅绘制不同。 */
final class AppleButton extends PortButton {
    private boolean pressed;
    private AppleUi.ButtonStyle style = AppleUi.ButtonStyle.GRAY;
    private boolean selected;

    AppleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    AppleButton style(AppleUi.ButtonStyle style) { this.style = style; return this; }
    /** 选中态（如分段/页签当前项）显示为浅蓝着色。 */
    void setSelected(boolean selected) { this.selected = selected; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) pressed = true;
        return handled;
    }

    @Override
    public void onPress() {
        releaseVisualState();
        super.onPress();
    }

    void releaseVisualState() { pressed = false; }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        releaseVisualState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AppleUi.ButtonStyle s = selected && style == AppleUi.ButtonStyle.GRAY ? AppleUi.ButtonStyle.TINTED : style;
        AppleUi.buttonSurface(graphics, getX(), getY(), getWidth(), getHeight(), s, isHoveredOrFocused(), pressed, active);
        drawLabel(graphics, this, AppleUi.buttonTextColor(s, active));
    }

    /** 按文案推断按钮样式：确认类为蓝色主按钮，破坏性操作为红色，其余为灰色。 */
    static AppleUi.ButtonStyle autoStyle(String label) {
        for (String k : new String[]{"删除", "解散", "移除", "清空", "取消"})
            if (label.contains(k)) return AppleUi.ButtonStyle.DESTRUCTIVE;
        for (String k : new String[]{"应用", "完成", "添加", "转换", "新建", "保存", "确认", "创建"})
            if (label.contains(k)) return AppleUi.ButtonStyle.PRIMARY;
        return AppleUi.ButtonStyle.GRAY;
    }

    /** 无阴影标签；保留内边距，先适度缩小，仍放不下时尾部截断为“…”。 */
    static void drawLabel(GuiGraphics graphics, net.minecraft.client.gui.components.AbstractWidget w, int color) {
        drawLabel(graphics, w, color, 0);
    }

    /** @param reservedRight 右侧为内嵌图标（如齿轮）预留的宽度，标签在剩余区域内居中。 */
    static void drawLabel(GuiGraphics graphics, net.minecraft.client.gui.components.AbstractWidget w, int color, int reservedRight) {
        var font = Minecraft.getInstance().font;
        net.minecraft.network.chat.Component message = appleTint(w.getMessage());
        int areaW = Math.max(0, w.getWidth() - reservedRight);
        com.sorrowmist.useless.client.gui.apple.AppleTextLayout.drawCentered(graphics, font, message,
                w.getX(), w.getY(), areaW, w.getHeight(),
                Math.min(7, Math.max(3, areaW / 8)), 3, null, 0xFF000000 | (color & 0xFFFFFF));
    }

    /** 原版亮色（为深底设计的 §e/§a/§c…）在浅色胶囊上看不清，绘制时映射为 Apple 深色变体。 */
    static net.minecraft.network.chat.Component appleTint(net.minecraft.network.chat.Component src) {
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        src.visit((style, text) -> {
            net.minecraft.network.chat.TextColor tc = style.getColor();
            if (tc != null) style = style.withColor(AppleUi.readable(tc.getValue()));
            out.append(net.minecraft.network.chat.Component.literal(text).withStyle(style));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }
}
