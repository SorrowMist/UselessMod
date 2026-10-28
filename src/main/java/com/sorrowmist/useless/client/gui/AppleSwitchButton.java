package com.sorrowmist.useless.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** v5 Apple 风格开关：点击行为与原按钮一致，仅以开关外观呈现当前状态；message 保留供旁白与测试读取。 */
public final class AppleSwitchButton extends com.sorrowmist.useless.compat.forge1201.client.PortButton {
    private final BooleanSupplier state;

    public AppleSwitchButton(int x, int y, int w, int h, Component message, OnPress onPress, BooleanSupplier state) {
        super(x, y, w, h, message, onPress);
        this.state = state;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AppleUi.toggle(graphics, getX(), getY(), getWidth(), getHeight(), state.getAsBoolean());
    }
}
