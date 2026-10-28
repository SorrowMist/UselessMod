package com.sorrowmist.useless.client.gui.apple.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * 极简控件基类（纯原版）：相对父容器的坐标 + 尺寸 + 可见/激活 + 事件钩子。
 * 只实现 Apple 控件库实际用到的部分，接口命名沿用 LDLib 版，便于两套实现共用布局代码。
 */
public class Widget {
    public static final class Pos { public final int x, y; Pos(int x, int y) { this.x = x; this.y = y; } }

    private int selfX, selfY, w, h;
    private Widget parent;
    private boolean visible = true, active = true;
    private String id = "";

    public Widget(int x, int y, int w, int h) { selfX = x; selfY = y; this.w = w; this.h = h; }

    public void setClientSideWidget() { }
    public Widget setId(String id) { this.id = id == null ? "" : id; return this; }
    public String getId() { return id; }

    public Widget getParent() { return parent; }
    void setParent(Widget p) { parent = p; }

    public Pos getSelfPosition() { return new Pos(selfX, selfY); }
    public void setSelfPosition(int x, int y) { selfX = x; selfY = y; }
    public int getPositionX() { return (parent == null ? 0 : parent.getPositionX()) + selfX; }
    public int getPositionY() { return (parent == null ? 0 : parent.getPositionY()) + selfY; }
    public int getSizeWidth() { return w; }
    public int getSizeHeight() { return h; }
    public void setSize(int w, int h) { this.w = w; this.h = h; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { active = a; }

    public boolean isMouseOverElement(double mx, double my) {
        int x = getPositionX(), y = getPositionY();
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void drawInBackground(GuiGraphics g, int mouseX, int mouseY, float pt) { }
    public void drawInForeground(GuiGraphics g, int mouseX, int mouseY, float pt) { }

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    public boolean mouseWheelMove(double mx, double my, double delta) { return false; }
    public boolean keyPressed(int key, int scan, int mods) { return false; }
    /** 在渲染/输入前同步嵌入的原版控件；隐藏控件也会收到此钩子。 */
    public void updateLayout() { }
    public void updateScreen() { }

    public void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
