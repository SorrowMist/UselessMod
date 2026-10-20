package com.sorrowmist.useless.content.stafflink;

import net.minecraft.network.chat.Component;

/**
 * 一条线路的流向。
 *
 * <p>{@link #RELEASE} 端负责把资源推出去，{@link #ABSORB} 端负责接住。
 * 引擎只在「同一张网络 + 同一线路号 + RELEASE → ABSORB + 同一资源类型」之间搬运。</p>
 */
public enum LinkFlow {
    /** 吸收：接收同线路 RELEASE 端推来的资源。 */
    ABSORB("gui.useless_mod.wireless_logistics.flow.absorb"),
    /** 释放：把自身库存推给同线路的 ABSORB 端。 */
    RELEASE("gui.useless_mod.wireless_logistics.flow.release");

    private final String translationKey;

    LinkFlow(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public LinkFlow next() {
        return this == ABSORB ? RELEASE : ABSORB;
    }
}
