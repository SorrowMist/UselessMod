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
        List<ItemStack> filter
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

    public StaffLinkRoute {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(trigger, "trigger");

        route = Mth.clamp(route, 0, ROUTE_COUNT - 1);
        amount = Math.max(MIN_AMOUNT, amount);
        interval = Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        weight = Mth.clamp(weight, MIN_WEIGHT, MAX_WEIGHT);

        List<ItemStack> normalized = new ArrayList<>(FILTER_LIMIT);
        for (int slot = 0; slot < FILTER_LIMIT; slot++) {
            ItemStack stack = filter != null && slot < filter.size() ? filter.get(slot) : null;
            normalized.add(stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        filter = List.copyOf(normalized);
    }

    /**
     * 过滤器适用的资源类型。
     *
     * <p>物品比物品本身；流体/化学品比「标记物里装的东西」——放一个水桶就是「只搬水」，
     * 放一个化学品罐就是「只搬那种化学品」。能量与魔源没有合适的标记物，因此不参与过滤。</p>
     */
    public boolean filterApplies() {
        return medium == LinkMedium.ITEM || medium == LinkMedium.FLUID || medium == LinkMedium.CHEMICAL;
    }

    /** 非空的过滤标记；为空表示「不限制」。 */
    public List<ItemStack> activeFilters() {
        if (!filterApplies()) {
            return List.of();
        }
        List<ItemStack> active = new ArrayList<>(filter.size());
        for (ItemStack stack : filter) {
            if (!stack.isEmpty()) {
                active.add(stack);
            }
        }
        return active;
    }

    /** 换一份过滤器，其余不变。 */
    public StaffLinkRoute withFilter(List<ItemStack> newFilter) {
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
        for (ItemStack stack : filter) {
            // 空槽位写空 tag：saveOptional 对空栈返回空 CompoundTag，parseOptional 会还原成 EMPTY。
            filterTag.add(stack.saveOptional(registries));
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

        List<ItemStack> filter = new ArrayList<>(FILTER_LIMIT);
        ListTag filterTag = tag.getList(TAG_FILTER, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < FILTER_LIMIT; slot++) {
            filter.add(slot < filterTag.size()
                    ? ItemStack.parseOptional(registries, filterTag.getCompound(slot))
                    : ItemStack.EMPTY);
        }

        return new StaffLinkRoute(
                anchor,
                tag.getInt(TAG_ROUTE),
                tag.getBoolean(TAG_ENABLED),
                readEnum(LinkFlow.class, tag.getString(TAG_FLOW), LinkFlow.ABSORB),
                readEnum(LinkMedium.class, tag.getString(TAG_MEDIUM), LinkMedium.ITEM),
                readAmount(tag),
                tag.getInt(TAG_INTERVAL),
                readSide(tag.getString(TAG_SIDE)),
                readEnum(LinkTrigger.class, tag.getString(TAG_TRIGGER), LinkTrigger.ALWAYS),
                tag.getInt(TAG_WEIGHT),
                filter);
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

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> FILTER_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(FILTER_LIMIT));

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
     * <p>过滤器的 {@link ItemStack} 只能走 {@code OPTIONAL_STREAM_CODEC}——它依赖
     * {@link RegistryFriendlyByteBuf} 携带的物品注册表，所以整条编解码器也按该类型声明。
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
                List<ItemStack> filter = FILTER_CODEC.decode(buf);
                return new StaffLinkRoute(anchor, route, enabled, flow, medium, amount, interval,
                        side, trigger, weight, filter);
            });
}
