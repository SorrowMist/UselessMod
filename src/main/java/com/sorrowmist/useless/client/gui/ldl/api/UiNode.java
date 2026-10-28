package com.sorrowmist.useless.client.gui.ldl.api;

import java.util.function.BooleanSupplier;

/**
 * 声明式布局树的节点。坐标一律相对窗口左上角（= Menu 槽位坐标系），不需要手写屏幕像素。
 * <p>尺寸规则：{@link #width}/{@link #height} 固定；否则取 {@link #prefW()}/{@link #prefH()}；
 * 父容器在主轴上把剩余空间按 {@link #grow} 分给子节点，交叉轴默认拉伸。</p>
 */
public abstract class UiNode {
    int fixedW = -1, fixedH = -1;
    float grow;
    int dx, dy;
    String id;
    BooleanSupplier shown, enabled;

    /** 内容决定的首选宽高。 */
    protected abstract int prefW();
    protected abstract int prefH();

    /** 在 (x,y,w,h) 处生成控件并挂进宿主。 */
    protected abstract void place(UiContext ctx, int x, int y, int w, int h);

    public final int measureW() { return fixedW >= 0 ? fixedW : prefW(); }
    public final int measureH() { return fixedH >= 0 ? fixedH : prefH(); }

    final void layoutAt(UiContext ctx, int x, int y, int w, int h) { place(ctx, x + dx, y + dy, w, h); }

    @SuppressWarnings("unchecked") protected <T extends UiNode> T self() { return (T) this; }

    public <T extends UiNode> T width(int w) { fixedW = w; return self(); }
    public <T extends UiNode> T height(int h) { fixedH = h; return self(); }
    public <T extends UiNode> T size(int w, int h) { fixedW = w; fixedH = h; return self(); }
    /** 在父容器主轴上吃剩余空间（类似 flex-grow）。 */
    public <T extends UiNode> T grow() { return grow(1f); }
    public <T extends UiNode> T grow(float g) { grow = g; return self(); }
    /** 布局完成后的微调偏移（像素级对齐旧界面时用，新界面一般不需要）。 */
    public <T extends UiNode> T nudge(int dx, int dy) { this.dx += dx; this.dy += dy; return self(); }
    /** 测试 / 查找 id（UiShotHook 用 id 找控件真实点击）。 */
    public <T extends UiNode> T id(String id) { this.id = id; return self(); }
    /** 只读绑定：可见性。 */
    public <T extends UiNode> T bindVisible(BooleanSupplier s) { shown = s; return self(); }
    /** 只读绑定：可用性（禁用时控件变灰且不响应点击）。 */
    public <T extends UiNode> T bindEnabled(BooleanSupplier s) { enabled = s; return self(); }
}
