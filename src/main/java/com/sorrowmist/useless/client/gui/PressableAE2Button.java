package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.compat.forge1201.client.PortButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class PressableAE2Button extends PortButton {
    private boolean pressed;

    PressableAE2Button(int x, int y, int width, int height,
                       Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            pressed = true;
        }
        return handled;
    }

    /**
     * 触发点击并复位按下状态。
     *
     * <p>按下态若只依赖屏幕的 {@code mouseReleased} 复位，在鼠标移出界面后松开等路径下不会触发，
     * 按钮将停留在按下态。此处以点击回调作为复位兜底，保证任何一次成功点击后都会弹起。</p>
     */
    @Override
    public void onPress() {
        releaseVisualState();
        super.onPress();
    }

    void releaseVisualState() {
        pressed = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderSurface(graphics,pressed || isHoveredOrFocused());
        renderLabel(graphics,!active?0x888894:pressed?0xAED4ED:0xF2F2F2);
    }

    @Override public boolean mouseReleased(double mouseX,double mouseY,int button) {
        releaseVisualState();
        return super.mouseReleased(mouseX,mouseY,button);
    }
}
