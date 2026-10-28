package com.sorrowmist.useless.client.gui.apple;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 嵌入 Apple 控件树（纯原版）的容器界面基类。
 * <ul>
 *   <li>Menu / Slot / 网络包 / JEI 全部沿用原版 {@link AbstractContainerScreen}；</li>
 *   <li>子类在 {@link #initControls()} 里创建原逻辑模型（按钮对象只当状态模型，不加入 renderables），
 *       在 {@link #buildApple(AppleHost)} 里把控件挂进 Widget 树；</li>
 *   <li>渲染：Widget 树（背景层）→ 原版槽位物品 → EditBox → 弹层 → tooltip。</li>
 * </ul>
 */
public abstract class AppleContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected AppleHost host;

    protected AppleContainerScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected final void init() {
        preInit();
        super.init();
        initControls();
        host = new AppleHost(imageWidth, imageHeight);
        buildApple(host);
        afterBuild();
        host.layout(width, height, leftPos, topPos);
    }

    /** Measure declarative layouts before vanilla computes container position. */
    protected void preInit() {}

    protected void initControls() {}
    protected abstract void buildApple(AppleHost h);
    protected void afterBuild() {}

    /** 通用：物品栏卡（标题 + 3×9 + 快捷栏底格由 SlotGrid 统一画）。x/y 为槽位左上（Menu 坐标）。 */
    protected void inventoryCard(AppleHost h, int slotX, int slotY) {
        h.add(new AppleCard(slotX - 6, slotY - 17, 174, 101));
        h.add(new AppleLabel(slotX, slotY - 11, 160, AppleLabel.Kind.CAPTION, () -> playerInventoryTitle));
    }

    protected AppleSlotGrid slotGrid() {
        return new AppleSlotGrid(menu, () -> leftPos, () -> topPos, s -> true);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        host.layout(width, height, leftPos, topPos);
        host.render(g, mx, my, pt);
        renderExtraBg(g, pt, mx, my);
    }

    /** 子类可在 Widget 树之后、槽位物品之前补画（屏幕坐标）。 */
    protected void renderExtraBg(GuiGraphics g, float pt, int mx, int my) {}

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // 标题与所有文字由 Widget 树绘制。
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // AbstractContainerScreen does not draw Screen's world-dimming background.
        // Keep it before container/JEI rendering so panels and drag previews stay bright.
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderAboveSlots(g, mx, my, pt);
        host.renderOverlay(g, mx, my, pt);
        if (!host.isPopoverOpen()) {
            renderTooltip(g, mx, my);
            renderExtraTooltips(g, mx, my);
        }
    }

    /** 槽位物品之后、弹层与 tooltip 之前（屏幕坐标），例如搜索高亮。 */
    protected void renderAboveSlots(GuiGraphics g, int mx, int my, float pt) {}

    protected void renderExtraTooltips(GuiGraphics g, int mx, int my) {}

    @Override
    protected void containerTick() {
        super.containerTick();
        host.tick();
        tickControls();
    }

    protected void tickControls() {}

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (host.mouseClicked(mx, my, b)) {
            setFocused(null);
            for (var c : children()) if (c instanceof net.minecraft.client.gui.components.EditBox e) e.setFocused(false);
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && host.isPopoverOpen()) {
            host.closePopover();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        return host.mouseReleased(mx, my, b) || super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (host.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (host.mouseScrolled(mx, my, d)) return true;
        return super.mouseScrolled(mx, my, d);
    }

    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        if (host != null && host.isOverPopover(mx, my)) return false;
        return super.isHovering(x, y, w, h, mx, my);
    }

    public AppleHost appleHost() { return host; }
}
