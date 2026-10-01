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

    public StaffLinkRoute {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(medium, "medium");

        route = Mth.clamp(route, 0, ROUTE_COUNT - 1);
        amount = Math.max(MIN_AMOUNT, amount);
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
     * 化学品线路放一只装有该化学品的储罐。能量与魔源没有合适的标记物，因此不参与过滤。</p>
     */
    public boolean filterApplies() {
        return switch (medium) {
            case ITEM, AE_ITEM, FLUID, AE_FLUID, CHEMICAL, AE_CHEMICAL -> true;
            // 能量与魔源没有合适的标记物，因此不参与过滤。
            case ENERGY, AE_ENERGY, SOURCE, AE_SOURCE -> false;
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
     *   <li>能量 / 魔源不参与过滤（{@link #filterApplies()} 为 {@code false}），恒 {@code false}。</li>
     * </ul>
     */
    public boolean hasApplicableWhitelist() {
        if (!filterApplies()) {
            return false;
        }
        return switch (medium.family()) {
            case ITEM -> hasMarker(slot -> slot.isItem() || slot.isPattern());
            case FLUID -> hasMarker(slot -> slot.isFluid() || slot.isPattern());
            case CHEMICAL -> hasMarker(slot -> slot.isItem() || slot.isPattern());
            case ENERGY, SOURCE -> false;
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
        return filterApplies() && !hasApplicableWhitelist();
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
            if (slot.keepAtSource() > 0L) {
                entry.putLong(TAG_KEEP_AT_SOURCE, slot.keepAtSource());
            }
            if (slot.maxInto() > 0L) {
                entry.putLong(TAG_MAX_INTO, slot.maxInto());
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
     * <p>第九轮新增的 {@code MarkerPattern} / {@code KeepAtSource} / {@code MaxInto} 也算
     * 新格式的判据：老存档三个键都不会有，读出来就是「没有模式、两个限制都是 0」，
     * 行为与改动前逐字节一致。</p>
     */
    private static LinkFilterSlot readFilterSlot(CompoundTag entry, LinkMedium medium,
                                                 HolderLookup.Provider registries) {
        long keepAtSource = entry.getLong(TAG_KEEP_AT_SOURCE);
        long maxInto = entry.getLong(TAG_MAX_INTO);
        if (entry.contains(TAG_MARKER_ITEM) || entry.contains(TAG_MARKER_FLUID)
                || entry.contains(TAG_MARKER_PATTERN)) {
            // 模式优先：三态互斥，构造器会收敛。
            if (entry.contains(TAG_MARKER_PATTERN)) {
                LinkFilterPattern pattern = LinkFilterPattern.parse(entry.getString(TAG_MARKER_PATTERN));
                if (pattern != null) {
                    return LinkFilterSlot.ofPattern(pattern).withLimits(keepAtSource, maxInto);
                }
                // 存档里是个非法模式（改坏了 / 旧版本写进来的）：当作空槽，不猜。
                return LinkFilterSlot.EMPTY;
            }
            ItemStack item = ItemStack.parseOptional(registries, entry.getCompound(TAG_MARKER_ITEM));
            FluidStack fluid = entry.contains(TAG_MARKER_FLUID)
                    ? FluidStack.parseOptional(registries, entry.getCompound(TAG_MARKER_FLUID))
                    : FluidStack.EMPTY;
            LinkFilterSlot base = fluid.isEmpty() ? LinkFilterSlot.ofItem(item) : LinkFilterSlot.ofFluid(fluid);
            return base.isEmpty() ? LinkFilterSlot.EMPTY : base.withLimits(keepAtSource, maxInto);
        }

        ItemStack legacy = ItemStack.parseOptional(registries, entry);
        if (legacy.isEmpty()) {
            return LinkFilterSlot.EMPTY;
        }
        LinkFilterSlot migrated;
        if (medium.family() == ResourceFamily.FLUID) {
            FluidStack fluid = StaffLinkFilters.fluidInItem(legacy);
            migrated = fluid.isEmpty() ? LinkFilterSlot.EMPTY : LinkFilterSlot.ofFluid(fluid);
        } else {
            migrated = LinkFilterSlot.ofItem(legacy);
        }
        return migrated.isEmpty() ? LinkFilterSlot.EMPTY : migrated.withLimits(keepAtSource, maxInto);
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
     * <p>两个数量限制用 {@code writeVarLong}：绝大多数格是 0，只占一个字节。</p>
     */
    private static final byte SLOT_STATE_ITEM = 0;
    private static final byte SLOT_STATE_FLUID = 1;
    private static final byte SLOT_STATE_PATTERN = 2;

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
                        buf.writeVarLong(slot.keepAtSource());
                        buf.writeVarLong(slot.maxInto());
                    },
                    buf -> {
                        byte state = buf.readByte();
                        LinkFilterSlot slot = switch (state) {
                            case SLOT_STATE_PATTERN -> LinkFilterSlot.ofPattern(buf.readUtf(LinkFilterPattern.MAX_LENGTH));
                            case SLOT_STATE_FLUID -> LinkFilterSlot.ofFluid(FluidStack.STREAM_CODEC.decode(buf));
                            case SLOT_STATE_ITEM -> LinkFilterSlot.ofItem(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                            // 未知状态（装了带新形态的版本又退回旧版本）：读掉两个数字后当空槽，
                            // 而不是抛异常——一个解不开的过滤器不该让整个界面同步失败。
                            default -> LinkFilterSlot.EMPTY;
                        };
                        long keepAtSource = buf.readVarLong();
                        long maxInto = buf.readVarLong();
                        return slot.isEmpty() ? LinkFilterSlot.EMPTY : slot.withLimits(keepAtSource, maxInto);
                    });

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
