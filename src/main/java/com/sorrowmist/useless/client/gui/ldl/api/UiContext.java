package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.core.Widget;
import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.client.gui.apple.AppleWidget;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 一次布局的上下文：控件落点（宿主根 / 弹层）、可选 Menu（槽位节点用）、生成的矩形（JEI 排除区 / 调试）。 */
public final class UiContext {
    public final AppleHost host;
    @Nullable public final AbstractContainerMenu menu;
    private final Consumer<Widget> sink;
    final List<int[]> rects = new ArrayList<>();

    UiContext(AppleHost host, @Nullable AbstractContainerMenu menu, Consumer<Widget> sink) {
        this.host = host; this.menu = menu; this.sink = sink;
    }

    /** 把整棵树布局到 (x,y,w,h)。 */
    public static UiContext layout(AppleHost host, @Nullable AbstractContainerMenu menu, Consumer<Widget> sink,
                                   UiNode root, int x, int y, int w, int h) {
        UiContext c = new UiContext(host, menu, sink);
        root.layoutAt(c, x, y, w, h);
        return c;
    }

    /** 把控件挂进树，并套上节点上声明的 id / 可见 / 可用绑定。 */
    public <W extends Widget> W add(UiNode n, W w) {
        if (w == null) return null;
        if (w instanceof AppleWidget a) {
            if (n.id != null) a.id(n.id);
            if (n.shown != null) a.shownWhen(n.shown);
            if (n.enabled != null) a.enabledWhen(n.enabled);
        }
        sink.accept(w);
        rects.add(new int[]{w.getSelfPosition().x, w.getSelfPosition().y, w.getSizeWidth(), w.getSizeHeight()});
        return w;
    }

    public <W extends Widget> W add(W w) { sink.accept(w); return w; }
}
