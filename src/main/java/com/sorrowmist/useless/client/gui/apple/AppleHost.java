package com.sorrowmist.useless.client.gui.apple;

import com.sorrowmist.useless.client.gui.apple.core.Widget;
import com.sorrowmist.useless.client.gui.apple.core.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 将客户端皮肤控件通过 LDLib WidgetGroup 嵌入现有 AbstractContainerScreen；缺少 LDLib 时保留兼容后端：
 * 原 Menu / 槽位 / 网络包不变，Screen 把渲染与鼠标键盘事件转发给这里。
 * 所有 Apple 控件都是 clientSide widget，回调直接调用 Screen 原有逻辑方法。
 */
public final class AppleHost {
    private final WidgetGroup root;
    private final AppleWidgetRuntime runtime;
    private boolean overlayPress;
    private int left = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
    /** 覆盖层：弹出菜单 / 弹出面板。作为 root 最后一个子组（点击最先收到），但主渲染时隐藏，
     *  由宿主在 render() 末尾调用 {@link #renderOverlay} 画到物品之上。 */
    private final WidgetGroup overlay;
    private ApplePopover popover;
    private boolean modal;
    private Object owner;

    public AppleHost(int w, int h) {
        this(w, h, AppleWidgetRuntime.create());
    }

    AppleHost(int w, int h, AppleWidgetRuntime runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime);
        root = new WidgetGroup(0, 0, w, h);
        root.setClientSideWidget();
        overlay = new WidgetGroup(0, 0, w, h);
        overlay.setClientSideWidget();
        root.addWidget(overlay);
    }

    public void openPopover(ApplePopover p, boolean modal, Object owner) {
        closePopover();
        popover = p; this.modal = modal; this.owner = owner;
        overlay.addWidget(p);
    }

    public void closePopover() {
        if (popover == null) return;
        ApplePopover p = popover;
        popover = null; owner = null;
        overlay.clearAllWidgets();
        p.fireClose();
    }

    public boolean isPopoverOpen() { return popover != null; }
    public boolean isOpenFor(Object o) { return popover != null && owner == o; }
    public ApplePopover popover() { return popover; }

    /** 鼠标是否在弹层上（宿主用来屏蔽 tooltip / 槽位悬停）。 */
    public boolean isOverPopover(double mx, double my) { return popover != null && popover.isMouseOverElement(mx, my); }

    /** 在 Screen.render() 末尾调用：弹层画在槽位物品与 tooltip 之下的最上层。 */
    public void renderOverlay(GuiGraphics g, int mx, int my, float pt) {
        if (popover == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        if (popover.dimsBackground()) {
            com.sorrowmist.useless.client.gui.AppleUi.fillRound(g, root.getPositionX(), root.getPositionY(),
                    root.getSizeWidth(), root.getSizeHeight(), AppleStyle.R_WINDOW, 0x59FFFFFF);
        }
        overlay.setVisible(true);
        runtime.background(overlay, g, mx, my, pt);
        runtime.foreground(overlay, g, mx, my, pt);
        g.flush();
        g.pose().popPose();
    }

    public <T extends Widget> T add(T w) {
        root.addWidget(root.widgets.indexOf(overlay), w);
        return w;
    }

    public void clear() {
        closePopover();
        for (Widget w : new java.util.ArrayList<>(root.widgets)) if (w != overlay) root.removeWidget(w);
    }

    /** 在 init() 中调用，左上角与 leftPos/topPos 对齐。 */
    public void layout(int screenW, int screenH, int leftPos, int topPos) {
        root.setSelfPosition(leftPos, topPos);
        left = leftPos; top = topPos;
        root.updateLayout();
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        overlay.setVisible(false);
        runtime.background(root, g, mx, my, pt);
        overlay.setVisible(true);
    }

    public void renderForeground(GuiGraphics g, int mx, int my, float pt) {
        overlay.setVisible(false);
        runtime.foreground(root, g, mx, my, pt);
        overlay.setVisible(true);
    }

    public boolean mouseClicked(double mx, double my, int b) {
        root.updateLayout();
        if (popover != null) {
            overlayPress = true;
            if (runtime.click(overlay, mx, my, b)) return true;
            if (!popover.isMouseOverElement(mx, my)) {
                boolean m = modal;
                closePopover();
                if (m) {
                    // 点回下拉本身 = 仅关闭（不重新打开）
                    return true;
                }
                return false;
            }
            return true;
        }
        return runtime.click(root, mx, my, b);
    }
    public boolean mouseReleased(double mx, double my, int b) {
        boolean captured = overlayPress;
        overlayPress = false;
        if (captured && popover == null) return true;
        return runtime.release(popover != null ? overlay : root, mx, my, b) || captured || (popover != null && modal);
    }
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (overlayPress && popover == null) return true;
        return runtime.drag(popover != null ? overlay : root, mx, my, b, dx, dy) || overlayPress || (popover != null && modal);
    }
    public boolean mouseScrolled(double mx, double my, double d) {
        if (popover != null) {
            if (popover.isMouseOverElement(mx, my)) { runtime.scroll(overlay, mx, my, d); return true; }
            if (modal) return true;
        }
        return runtime.scroll(root, mx, my, d);
    }
    public void tick() { runtime.tick(root); }

    public WidgetGroup root() { return root; }
}
