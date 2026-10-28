package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

final class AlloyFurnaceControlButton extends Button {
    private final Supplier<RedstoneControlMode> redstoneMode;
    private final boolean cancel;
    private boolean pressed;

    private AlloyFurnaceControlButton(int x, int y, Component message, OnPress onPress,
                                      Supplier<RedstoneControlMode> redstoneMode, boolean cancel) {
        super(x, y, AlloyFurnaceControlIcons.WIDTH, AlloyFurnaceControlIcons.HEIGHT,
                message, onPress, DEFAULT_NARRATION);
        this.redstoneMode = redstoneMode;
        this.cancel = cancel;
    }

    static AlloyFurnaceControlButton redstone(int x, int y,
                                              Supplier<RedstoneControlMode> mode,
                                              OnPress onPress) {
        return new AlloyFurnaceControlButton(x, y,
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.redstone_control"),
                onPress, mode, false);
    }

    static AlloyFurnaceControlButton cancel(int x, int y, OnPress onPress) {
        return new AlloyFurnaceControlButton(x, y,
                Component.translatable("gui.useless_mod.advanced_alloy_furnace.cancel_ae_tasks"),
                onPress, () -> RedstoneControlMode.DISABLED, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && cancel) {
            pressed = true;
        }
        return handled;
    }

    void releaseVisualState() {
        pressed = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Apple 风：圆角按钮底 + 自绘几何图标（不再使用原贴图像素图标）
        boolean hot = isHoveredOrFocused();
        if (cancel) {
            AppleUi.buttonSurface(graphics, getX(), getY(), getWidth(), getHeight(),
                    AppleUi.ButtonStyle.DESTRUCTIVE, hot, pressed, active);
            AppleUi.icon(graphics, AppleUi.Icon.XMARK, getX() + 3, getY() + 3, 9, AppleUi.RED);
        } else {
            RedstoneControlMode mode = redstoneMode.get();
            AppleUi.buttonSurface(graphics, getX(), getY(), getWidth(), getHeight(),
                    AppleUi.ButtonStyle.GRAY, hot, false, active);
            float cx = getX() + getWidth() / 2f, cy = getY() + getHeight() / 2f;
            switch (mode) {
                case HIGH_ACTIVE -> AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.RED);
                case LOW_ACTIVE -> {
                    AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.RED);
                    AppleUi.fillRound(graphics, cx - 2f, cy - 2f, 4, 4, 2f, AppleUi.CONTROL);
                }
                default -> {
                    AppleUi.fillRound(graphics, cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, AppleUi.QUATERNARY_LABEL);
                    AppleUi.fillRound(graphics, cx - 3.5f, cy - 0.5f, 7, 1, 0.5f, AppleUi.CONTROL);
                }
            }
        }
    }
}
