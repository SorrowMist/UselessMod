package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import com.sorrowmist.useless.client.gui.apple.core.WidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 弹出面板：白色圆角卡片 + 大投影，挂在 {@link AppleHost} 的覆盖层，绘制在物品/槽位之上。
 * 子控件坐标相对于弹层左上角。modal=true 时点外部关闭并吞掉点击。
 */
public class ApplePopover extends WidgetGroup {
    private Component title;
    private Runnable onClose;
    private boolean dimBackground;

    public ApplePopover dimBackground() { dimBackground = true; return this; }
    public boolean dimsBackground() { return dimBackground; }

    public ApplePopover(int x, int y, int w, int h) {
        super(x, y, w, h);
        setClientSideWidget();
    }

    public ApplePopover title(Component t) { title = t; return this; }
    public ApplePopover onClose(Runnable r) { onClose = r; return this; }
    void fireClose() { if (onClose != null) onClose.run(); }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        DesignAssets.draw(g, "ninepatch_popup", x, y, w, h);
        if (title != null) {
            g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    title.copy().withStyle(s -> s.withBold(true)), x + AppleStyle.PAD, y + 7, AppleStyle.LABEL, false);
        }
        super.drawInBackground(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isMouseOverElement(double mx, double my) {
        return mx >= getPositionX() && mx < getPositionX() + getSizeWidth()
                && my >= getPositionY() && my < getPositionY() + getSizeHeight();
    }
}
