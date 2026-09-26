package com.sorrowmist.useless.content.stafflink;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * 一条线路搬运的资源类型。
 *
 * <p>带 {@code AE_} 前缀的项表示这一端直接接在 <b>AE 网络</b>上：不解析方块自身的容器能力，
 * 而是从网格的 ME 存储里取/存。它们与对应的方块版本属于同一个 {@link ResourceFamily}，
 * 因此「AE 网络 ↔ 箱子」可以正常配对。</p>
 *
 * <p>每种资源能否走 AE 取决于「谁把这种资源带进了 ME 网络」：物品与流体是 AE2 自带，
 * 化学品需要 Applied Mekanistics，魔源需要 Ars Énergistique。因此 {@code AE_CHEMICAL}
 * 的可用条件比 {@link #CHEMICAL} 更严（还要 AE2 与 appmek）。</p>
 */
public enum LinkMedium {
    ITEM("gui.useless_mod.wireless_logistics.medium.item", ResourceFamily.ITEM),
    AE_ITEM("gui.useless_mod.wireless_logistics.medium.ae_item", ResourceFamily.ITEM),
    FLUID("gui.useless_mod.wireless_logistics.medium.fluid", ResourceFamily.FLUID),
    AE_FLUID("gui.useless_mod.wireless_logistics.medium.ae_fluid", ResourceFamily.FLUID),
    ENERGY("gui.useless_mod.wireless_logistics.medium.energy", ResourceFamily.ENERGY),
    CHEMICAL("gui.useless_mod.wireless_logistics.medium.chemical", ResourceFamily.CHEMICAL),
    AE_CHEMICAL("gui.useless_mod.wireless_logistics.medium.ae_chemical", ResourceFamily.CHEMICAL),
    SOURCE("gui.useless_mod.wireless_logistics.medium.source", ResourceFamily.SOURCE),
    AE_SOURCE("gui.useless_mod.wireless_logistics.medium.ae_source", ResourceFamily.SOURCE);

    private static final String MEKANISM = "mekanism";
    private static final String ARS_NOUVEAU = "ars_nouveau";
    private static final String AE2 = "ae2";
    private static final String APPLIED_MEKANISTICS = "appmek";
    private static final String ARS_ENERGISTIQUE = "arseng";

    private final String translationKey;
    private final ResourceFamily family;

    LinkMedium(String translationKey, ResourceFamily family) {
        this.translationKey = translationKey;
        this.family = family;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    /** 搬运语义上的粗分类；配对时比较它，见 {@link ResourceFamily}。 */
    public ResourceFamily family() {
        return family;
    }

    /**
     * 这一端是否走 AE 网络。
     *
     * <p>走网络的一端有一处特殊规则：<b>从 AE 取出时只认接收端的白名单</b>。AE 网络里动辄上千种
     * 物品，让玩家在源端（网络侧）列白名单既繁琐又容易漏；「要从网里拿什么」由接收端说了算更自然。</p>
     */
    public boolean isAe() {
        return this == AE_ITEM || this == AE_FLUID || this == AE_CHEMICAL || this == AE_SOURCE;
    }

    /** 当前环境是否支持这种资源。可选集成缺失时界面跳过、服务端拒绝。 */
    public boolean isSupported() {
        return switch (this) {
            case CHEMICAL -> ModList.get().isLoaded(MEKANISM);
            case SOURCE -> ModList.get().isLoaded(ARS_NOUVEAU);
            case AE_ITEM, AE_FLUID -> ModList.get().isLoaded(AE2);
            // 化学品/魔源要进 ME 网络，除了 AE2 还得有把它们带进网络的那个附属。
            case AE_CHEMICAL -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(APPLIED_MEKANISTICS);
            case AE_SOURCE -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(ARS_ENERGISTIQUE);
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