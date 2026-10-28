package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.AppleContainerScreen;
import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.client.gui.apple.AppleWindow;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Apple 风容器界面基类（对应 AE2 的 AEBaseScreen + 布局）。子类只写 {@link #body()}：
 * <pre>
 * protected UiNode body() { return column(row(slotGrid(0,3,3), progress(..), slot(9)), playerInventory()); }
 * </pre>
 * 自动获得：原设计切片窗口 + 标题栏 + 原版字体 + 槽位底格 + 物品栏 + 输入框同步/回车/关闭提交 +
 * 弹层感知 tooltip + JEI 排除区。动画和自定义字体由宿主扩展提供。窗口尺寸默认由内容测量（也可在构造里固定）。
 */
public abstract class AppleScreen<M extends AbstractContainerMenu> extends AppleContainerScreen<M> {
    protected final Fields fields = new Fields();
    protected final AppleNet net;
    private final int fixedW, fixedH;
    private UiNode root, accessory;

    protected AppleScreen(M menu, Inventory inv, Component title) { this(menu, inv, title, 0, 0); }

    /** w/h &gt; 0 时固定窗口尺寸（迁移旧界面保持像素一致时用）。 */
    protected AppleScreen(M menu, Inventory inv, Component title, int w, int h) {
        super(menu, inv, title);
        fixedW = w; fixedH = h;
        net = new AppleNet(menu);
    }

    /** 窗口主体（标题栏下方）。 */
    protected abstract UiNode body();

    /** 父类已有无参 slotGrid()，会遮住 Ui.slotGrid 的静态导入；这里转发一次，子类可直接写 slotGrid(0,3,3)。 */
    protected static SlotsNode slotGrid(int first, int cols, int rows) { return Ui.slotGrid(first, cols, rows); }
    /** 标题栏右侧：状态胶囊 / 翻页 / 按钮。 */
    protected UiNode titleAccessory() { return null; }
    /** 布局完成后（可再手动 add 特殊控件）。 */
    protected void afterLayout(AppleHost h) {}

    /** Keep screen-specific title icons while sharing the declarative lifecycle. */
    protected AppleWindow createWindow() { return new AppleWindow(imageWidth, imageHeight, () -> title); }
    protected int bodyX() { return 6; }
    protected int bodyY() { return AppleTheme.get().titleBar + 6; }

    protected NumberField numberField(LongSupplier server, LongConsumer commit) { return fields.add(new NumberField(server, commit)); }
    protected NumberField.Text textField(java.util.function.Consumer<String> onChange) { return fields.add(new NumberField.Text(onChange)); }

    @Override
    protected void preInit() {
        AppleTheme t = AppleTheme.get();
        root = body();
        accessory = titleAccessory();
        imageWidth = fixedW > 0 ? fixedW : root.measureW() + t.pad * 2 - 4;
        imageHeight = fixedH > 0 ? fixedH : t.titleBar + 6 + root.measureH() + 6;
    }

    @Override
    protected void initControls() { fields.create(leftPos, topPos, this::addRenderableWidget); }

    /** Re-measure state-dependent panels without recreating focused/dirty native inputs. */
    protected final void rebuildLayout() {
        preInit();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        host = new AppleHost(imageWidth, imageHeight);
        buildApple(host);
        host.layout(width, height, leftPos, topPos);
    }

    private final List<int[]> rects = new ArrayList<>();

    @Override
    protected void buildApple(AppleHost h) {
        AppleTheme t = AppleTheme.get();
        h.add(createWindow());
        rects.clear();
        if (accessory != null) {
            int aw = accessory.measureW(), ah = accessory.measureH();
            rects.addAll(UiContext.layout(h, menu, h::add, accessory, imageWidth - 6 - aw, (t.titleBar - ah) / 2, aw, ah).rects);
        }
        int bx = bodyX(), by = bodyY();
        rects.addAll(UiContext.layout(h, menu, h::add, root, bx, by, imageWidth - bx * 2, imageHeight - by - 6).rects);
        h.add(slotGrid());
        afterLayout(h);
    }

    @Override protected void tickControls() { fields.tick(); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (fields.keyPressed(key, scan, mods, () -> setFocused(null))) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { fields.onClose(); super.onClose(); }

    /** JEI/EMI 排除区：窗口本身由 JEI 按 imageWidth/Height 处理；这里补上越出窗口的弹层。 */
    public List<Rect2i> appleExclusionAreas() {
        List<Rect2i> l = new ArrayList<>();
        if (host != null && host.popover() != null) {
            var p = host.popover();
            l.add(new Rect2i(p.getPositionX(), p.getPositionY(), p.getSizeWidth(), p.getSizeHeight()));
        }
        return l;
    }

    /** 布局生成的控件矩形（窗口坐标，调试 / 截图测试用）。 */
    public List<int[]> layoutRects() { return rects; }
}
