package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.DesignAssets;
import com.sorrowmist.useless.client.gui.apple.core.IGuiTexture;
import com.sorrowmist.useless.client.gui.AppleUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** 胶囊按钮：主（蓝底白字，可带图标）/ 次（灰底）/ 浅色（浅蓝底蓝字）/ 危险（浅红底红字）。 */
public class ApplePushButton extends AppleWidget {
    public enum Kind { PRIMARY, SECONDARY, TINTED, DANGER, PLAIN }

    private final Supplier<Component> label;
    private final Kind kind;
    private final Runnable action;
    private AppleUi.Icon icon;
    private IGuiTexture base, hover;
    /** 本次按下状态：只在 click 时执行一次 action，松手清除高亮。 */
    private boolean pressed;

    public ApplePushButton(int x, int y, int w, int h, Kind kind, Supplier<Component> label, Runnable action) {
        super(x, y, w, h);
        this.kind = kind; this.label = label; this.action = action;
        int bg = switch (kind) {
            case PRIMARY -> AppleTex.BLUE;
            case SECONDARY -> AppleTex.FILL;
            case TINTED -> AppleStyle.BTN_TINTED;
            case DANGER -> AppleStyle.BTN_DANGER;
            case PLAIN -> 0x00000000;
        };
        int bgHover = switch (kind) {
            case PRIMARY -> AppleStyle.BTN_PRIMARY_HOVER;
            case SECONDARY -> AppleTex.FILL_HOVER;
            case TINTED -> AppleStyle.BTN_TINTED_HOVER;
            case DANGER -> AppleStyle.BTN_DANGER_HOVER;
            case PLAIN -> AppleStyle.BTN_PLAIN_HOVER;
        };
        base = kind == Kind.PRIMARY
                ? new com.sorrowmist.useless.client.gui.apple.core.GuiTextureGroup(new AppleTex.Shadow(4, 1.5f, AppleStyle.BTN_PRIMARY_GLOW), AppleTex.capsule(bg))
                : AppleTex.capsule(bg);
        hover = kind == Kind.PRIMARY
                ? new com.sorrowmist.useless.client.gui.apple.core.GuiTextureGroup(new AppleTex.Shadow(5, 1.5f, AppleStyle.BTN_PRIMARY_GLOW_HOVER), AppleTex.capsule(bgHover))
                : AppleTex.capsule(bgHover);
    }

    /** DesignAssets 的按钮种类；PLAIN 没有设计资源，继续走原来的胶囊/悬停贴图。 */
    private String designKind() {
        return switch (kind) {
            case PRIMARY -> "primary";
            case SECONDARY -> "secondary";
            case TINTED -> "tint";
            case DANGER -> "danger";
            case PLAIN -> null;
        };
    }

    public ApplePushButton icon(AppleUi.Icon icon) { this.icon = icon; return this; }

    private int fg() {
        return switch (kind) {
            case PRIMARY -> AppleStyle.ON_ACCENT;
            case SECONDARY, PLAIN -> AppleTex.LABEL;
            case TINTED -> AppleTex.BLUE;
            case DANGER -> AppleTex.RED;
        };
    }

    @Override
    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!isVisible()) return;
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        boolean on = isEnabled();
        boolean hov = on && isMouseOverElement(mouseX, mouseY);
        if (designKind() == null) {
            if (!on) g.setColor(1, 1, 1, 0.45f);
            (hov ? hover : base).draw(g, mouseX, mouseY, x, y, w, h);
            g.setColor(1, 1, 1, 1);
        } else {
            DesignAssets.button(g, x, y, w, h, designKind(), hov, pressed && hov, on);
        }
        AppleTextLayout.drawCentered(g, font(), label.get(), x, y, w, h,
                Math.min(8, Math.max(3, w / 8)), 3, icon, dim(fg(), on));
    }

    @Override
    protected boolean onClick(double mx, double my) { pressed = true; action.run(); return true; }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) pressed = false;
        // The native container still owns release/double-click inventory mechanics.
        return false;
    }
}
