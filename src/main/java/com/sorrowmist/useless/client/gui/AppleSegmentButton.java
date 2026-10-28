package com.sorrowmist.useless.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** v5 分段控件：点击某一段时以该段序号回调；当前选中段由 supplier 提供。 */
public final class AppleSegmentButton extends com.sorrowmist.useless.compat.forge1201.client.PortButton {
    private final Supplier<String[]> labels;
    private final IntSupplier selected;
    private final IntConsumer onSelect;

    public AppleSegmentButton(int x, int y, int w, int h, Component message,
                              Supplier<String[]> labels, IntSupplier selected, IntConsumer onSelect) {
        super(x, y, w, h, message, b -> { });
        this.labels = labels;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int n = labels.get().length;
        int idx = (int) ((mouseX - getX()) * n / getWidth());
        onSelect.accept(Math.max(0, Math.min(n - 1, idx)));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AppleUi.segmented(graphics, Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(),
                labels.get(), selected.getAsInt());
    }
}
