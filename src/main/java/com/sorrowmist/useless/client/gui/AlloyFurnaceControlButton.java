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
        if (cancel) {
            AlloyFurnaceControlIcons.drawCancel(graphics, getX(), getY(), pressed);
        } else {
            AlloyFurnaceControlIcons.drawRedstone(graphics, getX(), getY(), redstoneMode.get());
        }
    }
}
