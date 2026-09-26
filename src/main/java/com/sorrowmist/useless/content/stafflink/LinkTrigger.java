package com.sorrowmist.useless.content.stafflink;

import net.minecraft.network.chat.Component;

/** 一条线路的红石触发条件。 */
public enum LinkTrigger {
    /** 忽略红石，始终生效。 */
    ALWAYS("gui.useless_mod.wireless_logistics.trigger.always"),
    /** 无红石信号时生效。 */
    LOW("gui.useless_mod.wireless_logistics.trigger.low"),
    /** 有红石信号时生效。 */
    HIGH("gui.useless_mod.wireless_logistics.trigger.high");

    private final String translationKey;

    LinkTrigger(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /** 给定的红石强度是否满足本条件。 */
    public boolean allows(int signal) {
        return switch (this) {
            case ALWAYS -> true;
            case LOW -> signal == 0;
            case HIGH -> signal > 0;
        };
    }

    public LinkTrigger next() {
        LinkTrigger[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
