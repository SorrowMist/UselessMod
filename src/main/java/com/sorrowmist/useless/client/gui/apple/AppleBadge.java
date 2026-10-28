package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 状态徽标：有留白的浅色胶囊，长文字先适度缩小；宽度不超出分配区域。 */
public class AppleBadge extends AppleWidget {
    private final Supplier<Component> text;
    private final IntSupplier accent;
    private boolean right, solid;

    public AppleBadge(int x, int y, int w, Supplier<Component> text, IntSupplier accent) {
        super(x, y, w, 14);
        this.text = text; this.accent = accent;
    }

    public AppleBadge right() { right = true; return this; }
    /** 实心：彩色底白字。 */
    public AppleBadge solid() { solid = true; return this; }

    /** 非实心使用烘焙好的 7 色徽标贴图；indigo 没有专属资源，归入 purple。 */
    static String badgeKey(int accent) {
        String color = DesignAssets.colorName(accent);
        if ("indigo".equals(color)) color = "purple";
        return "badge_" + color + "_64";
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        Component c = text.get();
        if (c == null || c.getString().isEmpty()) return;
        int a = accent.getAsInt();
        int w = Math.min(getSizeWidth(), font().width(c) + 16);
        int x = right ? getPositionX() + getSizeWidth() - w : getPositionX();
        int y = getPositionY(), h = getSizeHeight();
        if (solid) {
            AppleTex.capsule(a).draw(g, mouseX, mouseY, x, y, w, h);
        } else {
            DesignAssets.draw(g, badgeKey(a), x, y, w, h);
        }
        AppleTextLayout.drawCentered(g, font(), c, x, y, w, h, 8, 3, null,
                solid ? AppleStyle.ON_ACCENT : a);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
