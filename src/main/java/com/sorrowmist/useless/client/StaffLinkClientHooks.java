package com.sorrowmist.useless.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * 无线物流里需要客户端状态的判断。
 *
 * <p>单独放一个客户端类，是为了让公共代码（{@code EventHandler}）不直接引用
 * {@code net.minecraft.client.*}：专用服务器上没有这些类，common 类里出现它们的
 * 符号会在类加载阶段炸掉。这里由调用方保证只在客户端分支里调用。</p>
 */
public final class StaffLinkClientHooks {

    private StaffLinkClientHooks() {
    }

    /** 按住 Ctrl：批量绑定 / 批量编辑。 */
    public static boolean isBatchModifierDown() {
        return Screen.hasControlDown();
    }
}