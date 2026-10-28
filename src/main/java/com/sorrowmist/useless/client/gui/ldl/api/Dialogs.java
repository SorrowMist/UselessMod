package com.sorrowmist.useless.client.gui.ldl.api;

import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.client.gui.apple.ApplePopover;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static com.sorrowmist.useless.client.gui.ldl.api.Ui.*;

/** 弹层：任意节点放进 popover；确认对话框（取消 / 危险操作）。 */
public final class Dialogs {
    private Dialogs() {}

    /** 在窗口坐标 (x,y) 打开内容为 content 的弹层；modal=true 点外部关闭并吞点击。 */
    public static ApplePopover popover(AppleHost host, Component title, int x, int y, UiNode content, boolean modal) {
        int top = title == null ? 6 : 22;
        int w = content.measureW() + 16, h = content.measureH() + top + 8;
        ApplePopover p = new ApplePopover(x, y, w, h);
        if (title != null) p.title(title);
        UiContext.layout(host, null, p::addWidget, content, 8, top, w - 16, h - top - 8);
        host.openPopover(p, modal, content);
        return p;
    }

    /** 居中确认框：消息 + 取消 / 确认（danger=true 时确认按钮为红色）。 */
    public static ApplePopover confirm(AppleHost host, int windowW, int windowH, Component title, Supplier<Component> message,
                                       Supplier<Component> confirmText, boolean danger, Runnable onConfirm) {
        ButtonNode ok = button(confirmText, () -> { host.closePopover(); onConfirm.run(); }).id("dialog_ok");
        if (danger) ok.danger(); else ok.primary();
        UiNode content = column(
                footnote(message).wrap().width(150).height(20),
                row(spacer(), button(tr("gui.cancel"), host::closePopover).id("dialog_cancel"), ok).gap(6)
        ).gap(8);
        int w = content.measureW() + 16, h = content.measureH() + 30;
        return popover(host, title, (windowW - w) / 2, (windowH - h) / 2, content, true);
    }
}
