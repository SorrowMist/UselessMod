package com.sorrowmist.useless.content.stafflink;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * 一条线路搬运的资源类型。
 *
 * <p>{@link #CHEMICAL} 与 {@link #SOURCE} 是可选集成：只有在对应模组加载时才可用，
 * 界面里循环选择时会自动跳过。</p>
 */
public enum LinkMedium {
    ITEM("gui.useless_mod.wireless_logistics.medium.item"),
    FLUID("gui.useless_mod.wireless_logistics.medium.fluid"),
    ENERGY("gui.useless_mod.wireless_logistics.medium.energy"),
    CHEMICAL("gui.useless_mod.wireless_logistics.medium.chemical"),
    SOURCE("gui.useless_mod.wireless_logistics.medium.source");

    private static final String MEKANISM = "mekanism";
    private static final String ARS_NOUVEAU = "ars_nouveau";

    private final String translationKey;

    LinkMedium(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /** 当前环境是否支持这种资源。可选集成缺失时界面跳过、服务端拒绝。 */
    public boolean isSupported() {
        return switch (this) {
            case CHEMICAL -> ModList.get().isLoaded(MEKANISM);
            case SOURCE -> ModList.get().isLoaded(ARS_NOUVEAU);
            default -> true;
        };
    }

    /** 循环到下一个当前环境支持的资源类型。 */
    public LinkMedium nextSupported() {
        LinkMedium[] values = values();
        LinkMedium candidate = this;
        for (int i = 0; i < values.length; i++) {
            candidate = values[(candidate.ordinal() + 1) % values.length];
            if (candidate.isSupported()) {
                return candidate;
            }
        }
        return ITEM;
    }
}
