package com.sorrowmist.useless.content.stafflink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一条「锚点 × 线路」的搬运配置。
 *
 * <p>{@code anchor} 是被绑定的容器坐标；{@code route} 是线路号，只有同一张网络里
 * 线路号相同、且一个 {@link LinkFlow#RELEASE} 一个 {@link LinkFlow#ABSORB}、
 * 资源类型相同的两条配置之间才会发生搬运。</p>
 *
 * <p>构造器即校验点：所有数值都在此处夹取到合法区间，因此从存档、网络或界面传入的
 * 任何越界值都不会流进引擎。</p>
 */
public record StaffLinkRoute(
        GlobalPos anchor,
        int route,
        boolean enabled,
        LinkFlow flow,
        LinkMedium medium,
        long amount,
        int interval,
        @Nullable Direction side,
        int weight,
        List<LinkFilterSlot> filter
) {
    /** 线路总数；线路号取值 {@code 0 .. ROUTE_COUNT - 1}。 */
    public static final int ROUTE_COUNT = 9;
    /**
     * 过滤器槽位数（18 格，3 行 × 6 列）。
     *
     * <p>18 格<b>不画在主界面上</b>：主界面只留一个「过滤」按钮，点开才在覆盖层里编辑
     * （见 {@code StaffLinkScreen} 的过滤面板）。每格周围要留出「源端保留 / 接收端上限」
     * 两个输入框的位置，铺在主界面上会把面板撑得很高。</p>
     */
    public static final int FILTER_LIMIT = 18;

    /**
     * 单次搬运量的下限。
     *
     * <p><b>没有上限</b>：一次要搬多少由玩家决定，大数值可以用 {@code K / M / G / T / P / E}
     * 输入（界面走 {@code ScaledEnergyAmount}）。真要去到容器的极限，那也是玩家自己的选择。</p>
     */
    public static final long MIN_AMOUNT = 1L;

    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 1_200;
    public static final int MIN_WEIGHT = -99;
    public static final int MAX_WEIGHT = 99;

    /**
     * 应力线路上 {@link #interval} 字段承载「方向」，取值 {@link #STRESS_CLOCKWISE} /
     * {@link #STRESS_COUNTER_CLOCKWISE}。
     *
     * <p>为什么复用而不是新加一个布尔字段：{@code amount} 与 {@code interval} 在别的介质上
     * 分别表示「一轮搬多少」与「多久一轮」，而转速与方向正是应力这种连续量对应的两样东西，
     * 语义刚好对得上；复用可以让存档、网络编解码与界面控件都不必新增字段。
     * {@code interval} 的既有夹取区间 {@code [1, 1200]} 对这两个取值是安全的。</p>
     */
    public static final int STRESS_CLOCKWISE = 1;

    /** 应力线路上 {@link #interval} 字段表示逆时针。 */
    public static final int STRESS_COUNTER_CLOCKWISE = 2;

    /**
     * 应力线路上 {@link #amount} 字段承载「目标转速（RPM）」时的默认值。
     *
     * <p>取 256 是因为它是常见的最高档转速；真正可用的上限由桥在运行时按动力学配置夹取。</p>
     */
    public static final long STRESS_DEFAULT_RPM = 256L;

    /**
     * 应力线路目标转速的**粗**上限，只用于服务端清洗客户端提交的值。
     *
     * <p>真正的上限是动力学配置里的最高转速，那是可选集成侧才知道的事，因此这里先给一个
     * 绝对上界挡住离谱数值，再由桥按实际配置夹一次。</p>
     */
    public static final long STRESS_RPM_HARD_LIMIT = 1_000_000L;

    /**
     * 气压线路上 {@link #amount} 字段承载「目标气压（毫巴）」。
     *
     * <p>与应力复用 {@code amount} 承载「目标转速」同一个思路：{@code amount} 在别的介质上表示
     * 「一轮搬多少」，而气压的搬运目标本身就是「让接收端达到某个压力」，语义刚好对得上；
     * 复用可以让存档、网络编解码都不必新增字段。</p>
     *
     * <p><b>注意与 {@link #MIN_AMOUNT} 的冲突</b>：真空是负压，因此气压线路的 {@code amount}
     * 必须允许负数，构造器里对气压族单独夹取（见 {@link #StaffLinkRoute}）。</p>
     */
    public static final long PRESSURE_MIN_MBAR = -1_000L;

    /** 气压线路上 {@link #amount} 的粗上限：20 bar。真正的物理上限由气动方块自身决定。 */
    public static final long PRESSURE_MAX_MBAR = 20_000L;

    /** 气压线路的默认目标气压：2 bar（气动工艺常见的起步工作压力）。 */
    public static final long PRESSURE_DEFAULT_MBAR = 2_000L;

    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";
    private static final String TAG_ROUTE = "Route";
    private static final String TAG_ENABLED = "Enabled";
    private static final String TAG_FLOW = "Flow";
    private static final String TAG_MEDIUM = "Medium";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_INTERVAL = "Interval";
    private static final String TAG_SIDE = "Side";
    private static final String TAG_WEIGHT = "Weight";
    private static final String TAG_FILTER = "Filter";
    /** 一格标记里的物品（化学品线路的储罐也走这里）。 */
    private static final String TAG_MARKER_ITEM = "MarkerItem";
    /** 一格标记里的流体。 */
    private static final String TAG_MARKER_FLUID = "MarkerFluid";
    /** 一格标记里的模式文本（{@code #tag} 或通配符）。 */
    private static final String TAG_MARKER_PATTERN = "MarkerPattern";
    /** 源端保留量；缺省 / 0 = 不保留。 */
    private static final String TAG_KEEP_AT_SOURCE = "KeepAtSource";
    /** 接收端上限；缺省 / 0 = 不限。 */
    private static final String TAG_MAX_INTO = "MaxInto";
    /** 这一格是「排除」；缺省 / false = 包含。 */
    private static final String TAG_EXCLUDE = "Exclude";
    /** 输出端控制条件（compound，仅非 OFF 时写）。 */
    private static final String TAG_OUT_COND = "OutCond";
    /** 输入端控制条件（compound，仅非 OFF 时写）。 */
    private static final String TAG_IN_COND = "InCond";
    /** 条件内部：比较方向。 */
    private static final String TAG_COND_OP = "Op";
    /** 条件内部：控制材料物品。 */
    private static final String TAG_COND_ITEM = "Item";
    /** 条件内部：控制材料流体。 */
    private static final String TAG_COND_FLUID = "Fluid";
    /** 条件内部：阈值。 */
    private static final String TAG_COND_VALUE = "Value";

    public StaffLinkRoute {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(medium, "medium");

        route = Mth.clamp(route, 0, ROUTE_COUNT - 1);
        // 气压的 amount 是「目标气压（毫巴）」，真空是负值，因此不能套用 MIN_AMOUNT 的下限。
        amount = medium.family() == ResourceFamily.PRESSURE
                ? Mth.clamp(amount, PRESSURE_MIN_MBAR, PRESSURE_MAX_MBAR)
                : Math.max(MIN_AMOUNT, amount);
        interval = Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        weight = Mth.clamp(weight, MIN_WEIGHT, MAX_WEIGHT);

        List<LinkFilterSlot> normalized = new ArrayList<>(FILTER_LIMIT);
        for (int slot = 0; slot < FILTER_LIMIT; slot++) {
            LinkFilterSlot value = filter != null && slot < filter.size() ? filter.get(slot) : null;
            normalized.add(value == null ? LinkFilterSlot.EMPTY : value);
        }
        filter = List.copyOf(normalized);
    }

    /**
     * 过滤器适用的资源类型。
     *
     * <p>标记物按线路类型分开存：物品线路放物品本身，流体线路放<b>流体本身</b>（不是装它的桶），
     * 化学品线路放一只装有该化学品的储罐。能量、魔源与应力都没有合适的标记物，
     * 因此不参与过滤。</p>
     */
    public boolean filterApplies() {
        return switch (medium) {
            case ITEM, AE_ITEM, FLUID, AE_FLUID, CHEMICAL, AE_CHEMICAL -> true;
            // 能量、魔源、应力、气压没有合适的标记物，因此不参与过滤。
            case ENERGY, AE_ENERGY, SOURCE, AE_SOURCE, STRESS, PRESSURE, AE_PRESSURE -> false;
        };
    }

    /** 非空的过滤标记；为空表示「不限制」。 */
    public List<LinkFilterSlot> activeFilters() {
        if (!filterApplies()) {
            return List.of();
        }
        List<LinkFilterSlot> active = new ArrayList<>(filter.size());
        for (LinkFilterSlot slot : filter) {
            if (!slot.isEmpty()) {
                active.add(slot);
            }
        }
        return active;
    }

    /** 换一份过滤器，其余不变。 */
    public StaffLinkRoute withFilter(List<LinkFilterSlot> newFilter) {
        return new StaffLinkRoute(anchor, route, enabled, flow, medium, amount, interval,
                side, weight, newFilter);
    }

    /**
     * 这条线路的过滤器里有没有<b>「可用于当前资源类型」的白名单条目</b>。
     *
     * <p>注意与 {@link #activeFilters()} 的区别：那个只判「非空」，这个还要判「类型对得上」
     * ——一个只填了流体标记的物品线路，在 {@code activeFilters()} 眼里是「有过滤」，
     * 在这里却是「没有有效白名单」（它一个物品也匹配不上）。</p>
     *
     * <p><b>只为「源端是 ME 网络」那条前置判定服务</b>（见
     * {@code StaffLinkTargets#transfer}）：从网络里拿什么完全由接收端白名单决定，
     * 没有白名单就什么都不拿。所以这里的判据必须严格，否则「填了张不相干的格子」
     * 会被当成「配过了」而放行全网络。</p>
     *
     * <ul>
     *   <li>模式（{@code #tag} / 通配符）恒算命中——它本来就按类型无关的方式匹配，
     *       能不能匹配到具体资源静态判不了，交给运行时的谓词。</li>
     *   <li>物品族看 {@link LinkFilterSlot#isItem()}，流体族看 {@link LinkFilterSlot#isFluid()}，
     *       化学品看物品标记（化学品只有「储罐物品」这一种表示）。</li>
     *   <li>能量 / 魔源 / 应力不参与过滤（{@link #filterApplies()} 为 {@code false}），恒 {@code false}。</li>
     * </ul>
     */
    public boolean hasApplicableWhitelist() {
        if (!filterApplies()) {
            return false;
        }
        // 「白名单」只数**非排除**条目：排除格不构成「允许清单」。
        return switch (medium.family()) {
            case ITEM -> hasMarker(slot -> !slot.exclude() && (slot.isItem() || slot.isPattern()));
            case FLUID -> hasMarker(slot -> !slot.exclude() && (slot.isFluid() || slot.isPattern()));
            case CHEMICAL -> hasMarker(slot -> !slot.exclude() && (slot.isItem() || slot.isPattern()));
            case ENERGY, SOURCE, STRESS, PRESSURE -> false;
        };
    }

    /**
     * 这条线路的过滤器里有没有<b>任何「可用于当前资源类型」的条目</b>——包含或排除都算。
     *
     * <p>与 {@link #hasApplicableWhitelist()} 的差别就在「纯排除」这种情况：只填了一个排除格时
     * 白名单判定为 false，但这里为 true（玩家确实配置了「除了 X 都搬」）。</p>
     */
    public boolean hasApplicableFilter() {
        if (!filterApplies()) {
            return false;
        }
        return switch (medium.family()) {
            case ITEM -> hasMarker(slot -> slot.isItem() || slot.isPattern());
            case FLUID -> hasMarker(slot -> slot.isFluid() || slot.isPattern());
            case CHEMICAL -> hasMarker(slot -> slot.isItem() || slot.isPattern());
            case ENERGY, SOURCE, STRESS, PRESSURE -> false;
        };
    }

    /**
     * 「源端是 ME 网络、这条线路又拿不出白名单」是否应当<b>整条线路一个也不搬</b>。
     *
     * <p>{@link #hasApplicableWhitelist()} 判的是「有没有白名单」，这里判的是「<b>需不需要</b>
     * 白名单」——两者的差就在能量与魔源上。</p>
     *
     * <p>「没有白名单就一个也不搬」这条规则的由来是：源端是 ME 网络时，拿什么完全由接收端白名单
     * 决定，白名单为空就退化成「把整张网络灌进这个容器」——而网络里可能有几千种东西。
     * 但这条理由<b>只对「网络里能装多种资源」的族成立</b>：</p>
     *
     * <ul>
     *   <li>物品 / 流体 / 化学品：网络里能有上千种 ⇒ 需要白名单。</li>
     *   <li><b>能量 / 魔源：从网络里出来就一种</b>（FE / 魔源），「灌满」根本无从谈起；
     *       而且它们<b>没有合适的标记物</b>（{@link #filterApplies()} 恒 {@code false}，
     *       界面压根不给填）。对它们套这条规则等于把整条线路<b>锁死</b>——
     *       玩家既搬不动、也无处可填来解锁。</li>
     * </ul>
     *
     * <p>因此判据是「本族吃过滤器」∧「没填出有效白名单」。界面那条同样的警告也用它，
     * 免得能量 / 魔源线路一边能搬一边报错。</p>
     */
    public boolean blocksAeSourceWithoutWhitelist() {
        // 判据用 hasApplicableFilter（包含或排除都算）：黑名单（纯排除）在 AE 源上是
        // 「除了列出的都搬」，语义明确，不该被当成「没填过滤」而整条锁死。
        // 只有「完全没填任何本族条目」才按老规矩挡下（防误灌整网）。
        return filterApplies() && !hasApplicableFilter();
    }

    private boolean hasMarker(java.util.function.Predicate<LinkFilterSlot> predicate) {
        for (LinkFilterSlot slot : filter) {
            if (!slot.isEmpty() && predicate.test(slot)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 持久化

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, anchor.dimension().location().toString());
        tag.putLong(TAG_POS, anchor.pos().asLong());
        tag.putInt(TAG_ROUTE, route);
        tag.putBoolean(TAG_ENABLED, enabled);
        tag.putString(TAG_FLOW, flow.name());
        tag.putString(TAG_MEDIUM, medium.name());
        tag.putLong(TAG_AMOUNT, amount);
        tag.putInt(TAG_INTERVAL, interval);
        tag.putString(TAG_SIDE, side == null ? "" : side.getName());
        tag.putInt(TAG_WEIGHT, weight);

        ListTag filterTag = new ListTag();
        for (LinkFilterSlot slot : filter) {
            CompoundTag entry = new CompoundTag();
            // 空槽位写空 tag：saveOptional 对空栈返回空 CompoundTag，parseOptional 会还原成 EMPTY。
            entry.put(TAG_MARKER_ITEM, slot.item().saveOptional(registries));
            if (!slot.fluid().isEmpty()) {
                entry.put(TAG_MARKER_FLUID, slot.fluid().save(registries));
            }
            if (slot.pattern() != null) {
                entry.putString(TAG_MARKER_PATTERN, slot.pattern());
            }
            // 只在非 0（= 有限制）时写，让「老格式读出来是 0」与「没限制」天然同构。
            // 这是**向后投影**：只覆盖「输出端 ≥ self」/「输入端 ≤ self」这两种最普通的条件，
            // 好让退回旧版本时那两种仍能读出来。
            if (slot.keepAtSource() > 0L) {
                entry.putLong(TAG_KEEP_AT_SOURCE, slot.keepAtSource());
            }
            if (slot.maxInto() > 0L) {
                entry.putLong(TAG_MAX_INTO, slot.maxInto());
            }
            if (slot.exclude()) {
                entry.putBoolean(TAG_EXCLUDE, true);
            }
            // 完整条件：仅非 OFF 时写（OFF 是默认，老存档与新存档的空条件都不会有这两个键）。
            if (!slot.outCond().isOff()) {
                entry.put(TAG_OUT_COND, saveCondition(slot.outCond(), registries));
            }
            if (!slot.inCond().isOff()) {
                entry.put(TAG_IN_COND, saveCondition(slot.inCond(), registries));
            }
            filterTag.add(entry);
        }
        tag.put(TAG_FILTER, filterTag);
        return tag;
    }

    /** 读回一条配置；维度或坐标不合法时返回 {@code null}（调用方跳过该条）。 */
    @Nullable
    public static StaffLinkRoute load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        GlobalPos anchor = GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                BlockPos.of(tag.getLong(TAG_POS)));

        // 资源类型要先读：旧格式的过滤器迁移要知道这条线路搬的是什么。
        LinkMedium medium = readEnum(LinkMedium.class, tag.getString(TAG_MEDIUM), LinkMedium.ITEM);

        List<LinkFilterSlot> filter = new ArrayList<>(FILTER_LIMIT);
        ListTag filterTag = tag.getList(TAG_FILTER, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < FILTER_LIMIT; slot++) {
            filter.add(slot < filterTag.size()
                    ? readFilterSlot(filterTag.getCompound(slot), medium, registries)
                    : LinkFilterSlot.EMPTY);
        }

        return new StaffLinkRoute(
                anchor,
                tag.getInt(TAG_ROUTE),
                tag.getBoolean(TAG_ENABLED),
                readEnum(LinkFlow.class, tag.getString(TAG_FLOW), LinkFlow.ABSORB),
                medium,
                readAmount(tag),
                tag.getInt(TAG_INTERVAL),
                readSide(tag.getString(TAG_SIDE)),
                tag.getInt(TAG_WEIGHT),
                filter);
    }

    /**
     * 读一格过滤标记。
     *
     * <p><b>兼容旧格式。</b>早先这一格就是一个 ItemStack（流体线路存的是「装它的桶」），
     * 整个 CompoundTag 只有 {@code id}/{@code count}/{@code components} 这几个键。这里用
     * 「有没有新格式的标记键」来区分：没有就按旧格式读，并且对流体线路顺手把桶里的流体取出来
     * ——否则升级之后玩家配好的流体过滤会整片失效（标记全成了物品，一个流体也匹配不上）。</p>
     *
     * <p><b>条件的两级回退</b>：优先读 {@code OutCond}/{@code InCond}；没有时用旧键
     * {@code KeepAtSource}/{@code MaxInto} 合成 {@code 输出端 ≥ self} / {@code 输入端 ≤ self}，
     * 于是老存档读出来与改动前逐字节一致。</p>
     */
    private static LinkFilterSlot readFilterSlot(CompoundTag entry, LinkMedium medium,
                                                 HolderLookup.Provider registries) {
        LinkFilterSlot base = readFilterMarker(entry, medium, registries);
        if (base.isEmpty()) {
            return LinkFilterSlot.EMPTY;
        }
        boolean exclude = entry.getBoolean(TAG_EXCLUDE);
        LinkFilterCondition out = entry.contains(TAG_OUT_COND)
                ? readCondition(entry.getCompound(TAG_OUT_COND), registries)
                : legacyCondition(entry.getLong(TAG_KEEP_AT_SOURCE), LinkFilterCondition.Op.AT_LEAST);
        LinkFilterCondition in = entry.contains(TAG_IN_COND)
                ? readCondition(entry.getCompound(TAG_IN_COND), registries)
                : legacyCondition(entry.getLong(TAG_MAX_INTO), LinkFilterCondition.Op.AT_MOST);
        return base.withExclude(exclude).withConditions(out, in);
    }

    /** 老键（{@code KeepAtSource} / {@code MaxInto}）到条件的映射；{@code 0} 表示没有这条条件。 */
    private static LinkFilterCondition legacyCondition(long value, LinkFilterCondition.Op op) {
        return value > 0L ? LinkFilterCondition.self(op, value) : LinkFilterCondition.OFF;
    }

    /**
     * 只读标记（物品 / 流体 / 模式），不含排除与条件。
     *
     * <p>读不出标记（空键 / 非法模式 / 空栈）时返回 {@link LinkFilterSlot#EMPTY}。</p>
     */
    private static LinkFilterSlot readFilterMarker(CompoundTag entry, LinkMedium medium,
                                                   HolderLookup.Provider registries) {
        if (entry.contains(TAG_MARKER_ITEM) || entry.contains(TAG_MARKER_FLUID)
                || entry.contains(TAG_MARKER_PATTERN)) {
            // 模式优先：三态互斥，构造器会收敛。
            if (entry.contains(TAG_MARKER_PATTERN)) {
                LinkFilterPattern pattern = LinkFilterPattern.parse(entry.getString(TAG_MARKER_PATTERN));
                // 存档里是个非法模式（改坏了 / 旧版本写进来的）：当作空槽，不猜。
                return pattern == null ? LinkFilterSlot.EMPTY : LinkFilterSlot.ofPattern(pattern);
            }
            ItemStack item = ItemStack.parseOptional(registries, entry.getCompound(TAG_MARKER_ITEM));
            FluidStack fluid = entry.contains(TAG_MARKER_FLUID)
                    ? FluidStack.parseOptional(registries, entry.getCompound(TAG_MARKER_FLUID))
                    : FluidStack.EMPTY;
            return fluid.isEmpty() ? LinkFilterSlot.ofItem(item) : LinkFilterSlot.ofFluid(fluid);
        }

        ItemStack legacy = ItemStack.parseOptional(registries, entry);
        if (legacy.isEmpty()) {
            return LinkFilterSlot.EMPTY;
        }
        if (medium.family() == ResourceFamily.FLUID) {
            FluidStack fluid = StaffLinkFilters.fluidInItem(legacy);
            return fluid.isEmpty() ? LinkFilterSlot.EMPTY : LinkFilterSlot.ofFluid(fluid);
        }
        return LinkFilterSlot.ofItem(legacy);
    }

    /** 写一条控制条件；只对非 OFF 调用。 */
    private static CompoundTag saveCondition(LinkFilterCondition cond, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_COND_OP, cond.op().name());
        if (!cond.item().isEmpty()) {
            tag.put(TAG_COND_ITEM, cond.item().saveOptional(registries));
        }
        if (!cond.fluid().isEmpty()) {
            tag.put(TAG_COND_FLUID, cond.fluid().save(registries));
        }
        tag.putLong(TAG_COND_VALUE, cond.value());
        return tag;
    }

    /** 读一条控制条件；方向未知 / 缺失一律当 OFF（延续「未知 → 不抛」的约定）。 */
    private static LinkFilterCondition readCondition(CompoundTag tag, HolderLookup.Provider registries) {
        LinkFilterCondition.Op op = readEnum(LinkFilterCondition.Op.class,
                tag.getString(TAG_COND_OP), LinkFilterCondition.Op.OFF);
        if (op == LinkFilterCondition.Op.OFF) {
            return LinkFilterCondition.OFF;
        }
        ItemStack item = tag.contains(TAG_COND_ITEM)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_COND_ITEM)) : ItemStack.EMPTY;
        FluidStack fluid = tag.contains(TAG_COND_FLUID)
                ? FluidStack.parseOptional(registries, tag.getCompound(TAG_COND_FLUID)) : FluidStack.EMPTY;
        return new LinkFilterCondition(op, item, fluid, tag.getLong(TAG_COND_VALUE));
    }

    /** 读单次搬运量；老存档里存的是 TAG_Int，两种都要认，否则会读成 0。 */
    private static long readAmount(CompoundTag tag) {
        return tag.contains(TAG_AMOUNT, Tag.TAG_LONG)
                ? tag.getLong(TAG_AMOUNT) : tag.getInt(TAG_AMOUNT);
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String name, E fallback) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }

    @Nullable
    private static Direction readSide(String name) {
        return name == null || name.isEmpty() ? null : Direction.byName(name);
    }

    // ------------------------------------------------------------------ 网络同步

    /**
     * 一格过滤标记的编解码。
     *
     * <p>先用一个<b>状态字节</b>说清这格是哪一种形态，再按对应类型写。状态值手写
     * （{@code 0=物品 / 1=流体 / 2=模式}）而<b>刻意不用 {@code writeEnum}</b>：枚举按 ordinal
     * 编码，将来往 {@link LinkFilterSlot} 里插入一个形态就会把旧包解成别的意思。
     * 手写常量则新形态只可能是「未知值」，能显式处理。</p>
     *
     * <p>旧投影的两个数量限制（{@code keep} / {@code max}）仍然写一遍，让读端能对齐；
     * 随后才是真正的 {@code exclude} 与两条控制条件。条件里 {@code op}/{@code control}
     * 同样手写字节，且<b>无论 OFF 与否都写满三个字段</b>——这样未知 op 也能安全跳过，
     * 不会把后面的流读错位。</p>
     */
    private static final byte SLOT_STATE_ITEM = 0;
    private static final byte SLOT_STATE_FLUID = 1;
    private static final byte SLOT_STATE_PATTERN = 2;

    private static final byte COND_OP_OFF = 0;
    private static final byte COND_OP_AT_LEAST = 1;
    private static final byte COND_OP_AT_MOST = 2;

    private static final byte COND_CONTROL_SELF = 0;
    private static final byte COND_CONTROL_ITEM = 1;
    private static final byte COND_CONTROL_FLUID = 2;

    private static final StreamCodec<RegistryFriendlyByteBuf, LinkFilterSlot> FILTER_SLOT_CODEC =
            StreamCodec.of(
                    (buf, slot) -> {
                        if (slot.isPattern()) {
                            buf.writeByte(SLOT_STATE_PATTERN);
                            buf.writeUtf(slot.pattern(), LinkFilterPattern.MAX_LENGTH);
                        } else if (slot.isFluid()) {
                            buf.writeByte(SLOT_STATE_FLUID);
                            FluidStack.STREAM_CODEC.encode(buf, slot.fluid());
                        } else {
                            buf.writeByte(SLOT_STATE_ITEM);
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, slot.item());
                        }
                        // 旧投影：仅用于与旧版对齐读取，读端不采用。
                        buf.writeVarLong(slot.keepAtSource());
                        buf.writeVarLong(slot.maxInto());
                        buf.writeBoolean(slot.exclude());
                        writeCondition(buf, slot.outCond());
                        writeCondition(buf, slot.inCond());
                    },
                    buf -> {
                        byte state = buf.readByte();
                        LinkFilterSlot slot = switch (state) {
                            case SLOT_STATE_PATTERN -> LinkFilterSlot.ofPattern(buf.readUtf(LinkFilterPattern.MAX_LENGTH));
                            case SLOT_STATE_FLUID -> LinkFilterSlot.ofFluid(FluidStack.STREAM_CODEC.decode(buf));
                            case SLOT_STATE_ITEM -> LinkFilterSlot.ofItem(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                            // 未知状态（装了带新形态的版本又退回旧版本）：读掉后面的字段当空槽，
                            // 而不是抛异常——一个解不开的过滤器不该让整个界面同步失败。
                            default -> LinkFilterSlot.EMPTY;
                        };
                        buf.readVarLong();
                        buf.readVarLong();
                        boolean exclude = buf.readBoolean();
                        LinkFilterCondition out = readCondition(buf);
                        LinkFilterCondition in = readCondition(buf);
                        return slot.isEmpty()
                                ? LinkFilterSlot.EMPTY
                                : slot.withExclude(exclude).withConditions(out, in);
                    });

    /** 写一条控制条件；OFF 也写满（op + control + value），保证读端能安全对齐。 */
    private static void writeCondition(RegistryFriendlyByteBuf buf, LinkFilterCondition cond) {
        switch (cond.op()) {
            case OFF -> buf.writeByte(COND_OP_OFF);
            case AT_LEAST -> buf.writeByte(COND_OP_AT_LEAST);
            case AT_MOST -> buf.writeByte(COND_OP_AT_MOST);
        }
        if (cond.isFluidControl()) {
            buf.writeByte(COND_CONTROL_FLUID);
            FluidStack.STREAM_CODEC.encode(buf, cond.fluid());
        } else if (cond.isItemControl()) {
            buf.writeByte(COND_CONTROL_ITEM);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, cond.item());
        } else {
            buf.writeByte(COND_CONTROL_SELF);
        }
        buf.writeVarLong(cond.value());
    }

    /** 读一条控制条件；未知 op / control 一律收敛成「不启用」，不抛。 */
    private static LinkFilterCondition readCondition(RegistryFriendlyByteBuf buf) {
        byte opByte = buf.readByte();
        byte control = buf.readByte();
        ItemStack item = ItemStack.EMPTY;
        FluidStack fluid = FluidStack.EMPTY;
        if (control == COND_CONTROL_ITEM) {
            item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        } else if (control == COND_CONTROL_FLUID) {
            fluid = FluidStack.STREAM_CODEC.decode(buf);
        }
        long value = buf.readVarLong();
        LinkFilterCondition.Op op = switch (opByte) {
            case COND_OP_AT_LEAST -> LinkFilterCondition.Op.AT_LEAST;
            case COND_OP_AT_MOST -> LinkFilterCondition.Op.AT_MOST;
            default -> LinkFilterCondition.Op.OFF;
        };
        return op == LinkFilterCondition.Op.OFF
                ? LinkFilterCondition.OFF
                : new LinkFilterCondition(op, item, fluid, value);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<LinkFilterSlot>> FILTER_CODEC =
            FILTER_SLOT_CODEC.apply(ByteBufCodecs.list(FILTER_LIMIT));

    /** 锚点（维度 + 坐标）的网络编解码；手写以避开 {@code GlobalPos.STREAM_CODEC} 的泛型歧义。 */
    public static void writeAnchor(FriendlyByteBuf buf, GlobalPos anchor) {
        buf.writeResourceLocation(anchor.dimension().location());
        buf.writeBlockPos(anchor.pos());
    }

    public static GlobalPos readAnchor(FriendlyByteBuf buf) {
        return GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation()),
                buf.readBlockPos());
    }

    /**
     * 网络编解码。
     *
     * <p>过滤器里的物品与流体都依赖 {@link RegistryFriendlyByteBuf} 携带的注册表
     * （{@code OPTIONAL_STREAM_CODEC} / {@code STREAM_CODEC}），所以整条编解码器也按该类型声明。
     * 存档侧则走 {@code saveOptional}/{@code parseOptional} 与 {@code HolderLookup.Provider}。</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkRoute> STREAM_CODEC = StreamCodec.of(
            (buf, route) -> {
                writeAnchor(buf, route.anchor());
                buf.writeVarInt(route.route());
                buf.writeBoolean(route.enabled());
                buf.writeEnum(route.flow());
                buf.writeEnum(route.medium());
                buf.writeVarLong(route.amount());
                buf.writeVarInt(route.interval());
                buf.writeBoolean(route.side() != null);
                if (route.side() != null) {
                    buf.writeEnum(route.side());
                }
                buf.writeVarInt(route.weight());
                FILTER_CODEC.encode(buf, route.filter());
            },
            buf -> {
                GlobalPos anchor = readAnchor(buf);
                int route = buf.readVarInt();
                boolean enabled = buf.readBoolean();
                LinkFlow flow = buf.readEnum(LinkFlow.class);
                LinkMedium medium = buf.readEnum(LinkMedium.class);
                long amount = buf.readVarLong();
                int interval = buf.readVarInt();
                Direction side = buf.readBoolean() ? buf.readEnum(Direction.class) : null;
                int weight = buf.readVarInt();
                List<LinkFilterSlot> filter = FILTER_CODEC.decode(buf);
                return new StaffLinkRoute(anchor, route, enabled, flow, medium, amount, interval,
                        side, weight, filter);
            });
}
