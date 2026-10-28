package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;

/** iOS 绿色开关：绿/灰轨道 + 带阴影白色圆钮，状态切换有补间动画。 */
public class AppleSwitch extends AppleWidget {
    private final BooleanSupplier value;
    private final Runnable toggle;

    public AppleSwitch(int x, int y, int w, int h, BooleanSupplier value, Runnable toggle) {
        super(x, y, w, h);
        this.value = value; this.toggle = toggle;
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean v = value.getAsBoolean();
        // The original artwork already contains the knob and its shadow.
        DesignAssets.draw(g, switchKey(v, isEnabled()), x, y, w, h);
    }

    /** 设计资源只有开/关与禁用四种轨道，补间值决定用哪一张，圆钮仍连续滑动。 */
    static String switchKey(boolean on, boolean enabled) {
        return "switch_" + (on ? "on" : "off") + (enabled ? "" : "_disabled");
    }

    @Override
    protected boolean onClick(double mx, double my) { toggle.run(); return true; }
}
