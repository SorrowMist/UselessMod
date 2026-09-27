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
        LinkTrigger trigger,
        int weight,
        List<LinkFilterSlot> filter
) {
    /** 线路总数；线路号取值 {@code 0 .. ROUTE_COUNT - 1}。 */
    public static final int ROUTE_COUNT = 9;
    /** 过滤器槽位数；仅 {@link LinkMedium#ITEM} 线路使用。 */
    public static final int FILTER_LIMIT = 6;

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
    private static final String TAG_TRIGGER = "Trigger";
    private static final String TAG_WEIGHT = "Weight";
    private static final String TAG_FILTER = "Filter";
    /** 一格标记里的物品（化学品线路的储罐也走这里）。 */
    private static final String TAG_MARKER_ITEM = "MarkerItem";
    /** 一格标记里的流体。 */
    private static final String TAG_MARKER_FLUID = "MarkerFluid";

    public StaffLinkRoute {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(trigger, "trigger");

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
                side, trigger, weight, newFilter);
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
        tag.putString(TAG_TRIGGER, trigger.name());
        tag.putInt(TAG_WEIGHT, weight);

        ListTag filterTag = new ListTag();
        for (LinkFilterSlot slot : filter) {
            CompoundTag entry = new CompoundTag();
            // 空槽位写空 tag：saveOptional 对空栈返回空 CompoundTag，parseOptional 会还原成 EMPTY。
            entry.put(TAG_MARKER_ITEM, slot.item().saveOptional(registries));
            if (!slot.fluid().isEmpty()) {
                entry.put(TAG_MARKER_FLUID, slot.fluid().save(registries));
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
                readEnum(LinkTrigger.class, tag.getString(TAG_TRIGGER), LinkTrigger.ALWAYS),
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
     */
    private static LinkFilterSlot readFilterSlot(CompoundTag entry, LinkMedium medium,
                                                 HolderLookup.Provider registries) {
        if (entry.contains(TAG_MARKER_ITEM) || entry.contains(TAG_MARKER_FLUID)) {
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

    /** 读单次搬运量；老存档里存的是 TAG_Int，两种都要认，否则会读成 0。 */
    private static long readAmount(CompoundTag tag) {
        return tag.contains(TAG_AMOUNT, Tag.TAG_LONG)
                ? tag.getLong(TAG_AMOUNT) : tag.getInt(TAG_AMOUNT);
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String name, E fallback) {        for (E value : type.getEnumConstants()) {
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
     * <p>先用一个布尔说清这格是流体还是物品，再按对应类型写——这样不必给两种栈各写一个
     * 「是否为空」，也就不会出现「流体的空栈被当成物品」这种含糊状态。</p>
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, LinkFilterSlot> FILTER_SLOT_CODEC =
            StreamCodec.of(
                    (buf, slot) -> {
                        buf.writeBoolean(slot.isFluid());
                        if (slot.isFluid()) {
                            FluidStack.STREAM_CODEC.encode(buf, slot.fluid());
                        } else {
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, slot.item());
                        }
                    },
                    buf -> buf.readBoolean()
                            ? LinkFilterSlot.ofFluid(FluidStack.STREAM_CODEC.decode(buf))
                            : LinkFilterSlot.ofItem(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

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
                buf.writeEnum(route.trigger());
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
                LinkTrigger trigger = buf.readEnum(LinkTrigger.class);
                int weight = buf.readVarInt();
                List<LinkFilterSlot> filter = FILTER_CODEC.decode(buf);
                return new StaffLinkRoute(anchor, route, enabled, flow, medium, amount, interval,
                        side, trigger, weight, filter);
            });
}
