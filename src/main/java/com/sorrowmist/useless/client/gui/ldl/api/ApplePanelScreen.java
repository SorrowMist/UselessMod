package com.sorrowmist.useless.client.gui.ldl.api;

import net.minecraft.client.Minecraft;
import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.client.gui.apple.AppleWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * 没有 Menu 的 Apple 风面板（方块设置弹窗：恒温器、配置页 …）。和 {@link AppleScreen} 共用同一套 DSL，
 * 区别只是没有槽位、背后是半透明遮罩、默认不暂停游戏。
 */
public abstract class ApplePanelScreen extends Screen {
    protected final Fields fields = new Fields();
    protected AppleHost host;
    protected int leftPos, topPos, panelW, panelH;
    private final int fixedW, fixedH;
    /** 遮罩颜色（与旧恒温器一致）。 */
    protected int scrim = 0x66000000;

    protected ApplePanelScreen(Component title) { this(title, 0, 0); }
    protected ApplePanelScreen(Component title, int w, int h) { super(title); fixedW = w; fixedH = h; }

    protected abstract UiNode body();
    protected UiNode titleAccessory() { return null; }
    protected void afterLayout(AppleHost h) {}
    protected AppleWindow createWindow() { return new AppleWindow(panelW, panelH, () -> title); }
    protected int bodyX() { return 6; }
    protected int bodyY() { return AppleTheme.get().titleBar + 6; }

    protected NumberField numberField(LongSupplier server, LongConsumer commit) { return fields.add(new NumberField(server, commit)); }

    @Override
    protected void init() {
        super.init();
        this.font = Minecraft.getInstance().font;
        AppleTheme t = AppleTheme.get();
        UiNode root = body(), acc = titleAccessory();
        panelW = fixedW > 0 ? fixedW : root.measureW() + 12;
        panelH = fixedH > 0 ? fixedH : t.titleBar + 6 + root.measureH() + 6;
        leftPos = (width - panelW) / 2;
        topPos = (height - panelH) / 2;
        fields.create(leftPos, topPos, this::addRenderableWidget);
        AppleHost h = host = new AppleHost(panelW, panelH);
        h.add(createWindow());
        if (acc != null) {
            int aw = acc.measureW(), ah = acc.measureH();
            UiContext.layout(h, null, h::add, acc, panelW - 6 - aw, (t.titleBar - ah) / 2, aw, ah);
        }
        int bx = bodyX(), by = bodyY();
        UiContext.layout(h, null, h::add, root, bx, by, panelW - bx * 2, panelH - by - 6);
        afterLayout(h);
        h.layout(width, height, leftPos, topPos);
    }

    public AppleHost appleHost() { return host; }

    @Override
    public void tick() { super.tick(); fields.tick(); if (host != null) host.tick(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        host.layout(width, height, leftPos, topPos);
        host.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
        host.renderForeground(g, mx, my, pt);
        host.renderOverlay(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g) { g.fill(0, 0, width, height, scrim); }

    private void defocusUnless(double mx, double my) {
        for (EditBox b : fields.boxes()) if (!b.isMouseOver(mx, my)) { setFocused(null); b.setFocused(false); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (host.mouseClicked(mx, my, b)) { defocusUnless(mx, my); return true; }
        defocusUnless(mx, my);
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        return host.mouseDragged(mx, my, b, dx, dy) || super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) { host.mouseReleased(mx, my, b); return super.mouseReleased(mx, my, b); }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) { return host.mouseScrolled(mx, my, d) || super.mouseScrolled(mx, my, d); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (fields.keyPressed(key, scan, mods, () -> setFocused(null))) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { fields.onClose(); super.onClose(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
