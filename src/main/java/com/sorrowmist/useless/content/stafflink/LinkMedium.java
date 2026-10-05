package com.sorrowmist.useless.content.stafflink;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条线路搬运的资源类型。
 *
 * <p>带 {@code AE_} 前缀的项表示这一端直接接在 <b>AE 网络</b>上：不解析方块自身的容器能力，
 * 而是从网格的 ME 存储里取/存。它们与对应的方块版本属于同一个 {@link ResourceFamily}，
 * 因此「AE 网络 ↔ 箱子」可以正常配对。</p>
 *
 * <p>每种资源能否走 AE 取决于「谁把这种资源带进了 ME 网络」：物品与流体是 AE2 自带，
 * 化学品需要 Applied Mekanistics，魔源需要 Ars Énergistique，<b>能量需要 AppliedFlux</b>
 * （FE 以通量元件的形式存进网络）。因此 {@code AE_CHEMICAL} 的可用条件比 {@link #CHEMICAL}
 * 更严（还要 AE2 与 appmek）。</p>
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
    AE_SOURCE("gui.useless_mod.wireless_logistics.medium.ae_source", ResourceFamily.SOURCE),
    /**
     * 接在 ME 网络上的 FE（AppliedFlux 通量元件）。
     *
     * <p><b>必须追加在末尾</b>：{@code StaffLinkRoute.STREAM_CODEC} 用 {@code writeEnum} 按
     * ordinal 编码，往中间插值会让新旧版本之间把「能量」读成别的资源。</p>
     */
    AE_ENERGY("gui.useless_mod.wireless_logistics.medium.ae_energy", ResourceFamily.ENERGY),
    /**
     * 机械动力的转速与应力容量。
     *
     * <p>与能量、魔源一样，是「源头出来只有一种」的连续量，因此同样<b>不参与过滤</b>
     * （{@link StaffLinkRoute#filterApplies()} 恒 {@code false}，界面不给填标记）。</p>
     *
     * <p><b>同样必须追加在末尾</b>，理由见 {@link #AE_ENERGY}。</p>
     */
    STRESS("gui.useless_mod.wireless_logistics.medium.stress", ResourceFamily.STRESS),
    /**
     * 气动工艺的气压（空气）。
     *
     * <p>与能量、魔源、应力一样是「源头出来只有一种」的连续量，因此同样<b>不参与过滤</b>
     * （{@link StaffLinkRoute#filterApplies()} 恒 {@code false}，界面不给填标记）。</p>
     *
     * <p>它是<b>状态量</b>：搬运的目标是让接收端达到某个气压，而不是每轮固定搬一批，
     * 因此走 {@link StaffLinkEngine} 的专用分支，不经过逐对 {@code transfer}。</p>
     *
     * <p><b>同样必须追加在末尾</b>，理由见 {@link #AE_ENERGY}。</p>
     */
    PRESSURE("gui.useless_mod.wireless_logistics.medium.pressure", ResourceFamily.PRESSURE),
    /**
     * 存进 ME 网络的空气（Applied Pneumatics 的 {@code AirKey}）。
     *
     * <p>网络里只有一堆空气、没有体积，因此没有压力概念；目标气压对它无意义，
     * 它只作为「气源 / 气库」参与搬运。</p>
     *
     * <p><b>同样必须追加在末尾</b>，理由见 {@link #AE_ENERGY}。</p>
     */
    AE_PRESSURE("gui.useless_mod.wireless_logistics.medium.ae_pressure", ResourceFamily.PRESSURE);

    private static final String MEKANISM = "mekanism";
    private static final String ARS_NOUVEAU = "ars_nouveau";
    private static final String AE2 = "ae2";
    private static final String APPLIED_MEKANISTICS = "appmek";
    private static final String ARS_ENERGISTIQUE = "arseng";
    private static final String APPLIED_FLUX = "appflux";
    private static final String CREATE = "create";
    private static final String PNEUMATICRAFT = "pneumaticcraft";
    private static final String APPLIED_PNEUMATICS = "appliedpneumatics";

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
        return this == AE_ITEM || this == AE_FLUID || this == AE_CHEMICAL || this == AE_SOURCE
                || this == AE_ENERGY || this == AE_PRESSURE;
    }

    /** 当前环境是否支持这种资源。可选集成缺失时界面跳过、服务端拒绝。 */
    public boolean isSupported() {
        return switch (this) {
            case CHEMICAL -> ModList.get().isLoaded(MEKANISM);
            case SOURCE -> ModList.get().isLoaded(ARS_NOUVEAU);
            case AE_ITEM, AE_FLUID -> ModList.get().isLoaded(AE2);
            // 化学品/魔源/能量要进 ME 网络，除了 AE2 还得有把它们带进网络的那个附属。
            case AE_CHEMICAL -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(APPLIED_MEKANISTICS);
            case AE_SOURCE -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(ARS_ENERGISTIQUE);
            case AE_ENERGY -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(APPLIED_FLUX);
            case STRESS -> ModList.get().isLoaded(CREATE);
            case PRESSURE -> ModList.get().isLoaded(PNEUMATICRAFT);
            // 空气要进 ME 网络得靠 Applied Pneumatics，只有 AE2 是不够的。
            case AE_PRESSURE -> ModList.get().isLoaded(AE2) && ModList.get().isLoaded(APPLIED_PNEUMATICS);
            default -> true;
        };
    }

    /**
     * 下拉菜单里的排列次序：同一个资源族的普通形态与 AE 形态相邻。
     *
     * <p>刻意不直接用 {@code values()}：{@link #AE_ENERGY} 是为了保持网络编解码的 ordinal
     * 稳定才追加在末尾的，直接枚举会把它排到「AE 魔源」后面，读起来莫名其妙。</p>
     */
    private static final LinkMedium[] MENU_ORDER = {
            ITEM, AE_ITEM, FLUID, AE_FLUID, ENERGY, AE_ENERGY, PRESSURE, AE_PRESSURE,
            CHEMICAL, AE_CHEMICAL, SOURCE, AE_SOURCE, STRESS
    };

    /**
     * 当前环境支持的类型，按下拉菜单的次序。
     *
     * <p>界面拿它铺下拉列表：装了化学品 / 魔源 / 通量之后类型能到十种，靠「点一下换一个」
     * 轮换太费手。</p>
     */
    public static List<LinkMedium> supported() {
        List<LinkMedium> result = new ArrayList<>(MENU_ORDER.length);
        for (LinkMedium medium : MENU_ORDER) {
            if (medium.isSupported()) {
                result.add(medium);
            }
        }
        return List.copyOf(result);
    }
}