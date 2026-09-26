package com.sorrowmist.useless.world.stafflink;

import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 造化杖的一张无线物流网络。
 *
 * <p>网络由若干条 {@link StaffLinkRoute} 组成，每条记录「某个锚点的某条线路怎么搬运」。
 * 同一个锚点可以在多条线路上各有一条配置，所以这里是<b>扁平</b>存储而不是「锚点 → 配置」的嵌套。</p>
 *
 * <p>物品上只保存 {@link #id()}；网络本体存在 {@link StaffLinkSavedData} 里。</p>
 */
public final class StaffLinkNetwork {
    public static final int ROUTE_COUNT = StaffLinkRoute.ROUTE_COUNT;
    /** 锚点自定义名的长度上限。 */
    public static final int MAX_ANCHOR_NAME = 32;
    /** 网络名的长度上限。 */
    public static final int MAX_NAME = 32;

    private static final String TAG_ID = "Id";
    private static final String TAG_NAME = "Name";
    private static final String TAG_ROUTES = "Routes";
    private static final String TAG_ANCHOR_NAMES = "AnchorNames";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";
    private static final String TAG_LABEL = "Label";

    private static final StreamCodec<RegistryFriendlyByteBuf, List<StaffLinkRoute>> ROUTES_CODEC =
            StaffLinkRoute.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, StaffLinkNetwork> STREAM_CODEC = StreamCodec.of(
            (buf, network) -> {
                buf.writeUUID(network.id);
                buf.writeUtf(network.name);
                ROUTES_CODEC.encode(buf, network.routes);
                buf.writeVarInt(network.anchorNames.size());
                network.anchorNames.forEach((anchor, anchorName) -> {
                    StaffLinkRoute.writeAnchor(buf, anchor);
                    buf.writeUtf(anchorName);
                });
            },
            buf -> {
                UUID id = buf.readUUID();
                String name = buf.readUtf();
                List<StaffLinkRoute> routes = ROUTES_CODEC.decode(buf);
                StaffLinkNetwork network = new StaffLinkNetwork(id, name);
                network.routes.addAll(routes);
                int nameCount = buf.readVarInt();
                for (int i = 0; i < nameCount; i++) {
                    GlobalPos anchor = StaffLinkRoute.readAnchor(buf);
                    network.anchorNames.put(anchor, buf.readUtf());
                }
                return network;
            });

    private final UUID id;
    private String name;
    private final List<StaffLinkRoute> routes = new ArrayList<>();
    /** 玩家给锚点起的名字；没起名时不出现在表里，界面回落到方块名。 */
    private final Map<GlobalPos, String> anchorNames = new LinkedHashMap<>();

    public StaffLinkNetwork(UUID id) {
        this(id, "");
    }

    private StaffLinkNetwork(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? "" : name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            this.name = "";
            return;
        }
        String trimmed = name.trim();
        this.name = trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    /** 锚点的自定义名；没起过名时返回 {@code null}。 */
    @Nullable
    public String anchorName(GlobalPos anchor) {
        String value = anchorNames.get(anchor);
        return value == null || value.isEmpty() ? null : value;
    }

    public void setAnchorName(GlobalPos anchor, @Nullable String anchorName) {
        if (anchorName == null || anchorName.isBlank()) {
            anchorNames.remove(anchor);
            return;
        }
        String trimmed = anchorName.trim();
        anchorNames.put(anchor, trimmed.length() > MAX_ANCHOR_NAME
                ? trimmed.substring(0, MAX_ANCHOR_NAME) : trimmed);
    }

    public List<StaffLinkRoute> routes() {
        return List.copyOf(routes);
    }

    /** 同一线路号下的全部配置。 */
    public List<StaffLinkRoute> routesOn(int route) {
        List<StaffLinkRoute> result = new ArrayList<>();
        for (StaffLinkRoute candidate : routes) {
            if (candidate.route() == route) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** 去重后的已绑定锚点，按绑定先后排列。 */
    public List<GlobalPos> anchors() {
        Map<GlobalPos, Boolean> seen = new LinkedHashMap<>();
        for (StaffLinkRoute route : routes) {
            seen.putIfAbsent(route.anchor(), Boolean.TRUE);
        }
        return List.copyOf(seen.keySet());
    }

    public boolean isBound(GlobalPos anchor) {
        for (StaffLinkRoute route : routes) {
            if (route.anchor().equals(anchor)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public StaffLinkRoute routeAt(GlobalPos anchor, int route) {
        for (StaffLinkRoute candidate : routes) {
            if (candidate.route() == route && candidate.anchor().equals(anchor)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 写入/覆盖「该锚点的该线路」配置。
     *
     * <p><b>原地替换</b>而不是先删后加：锚点列表是按线路出现顺序去重出来的，若把编辑过的线路挪到
     * 末尾，界面上这个容器就会「跳」到列表最下面。</p>
     */
    public void putRoute(StaffLinkRoute route) {
        for (int index = 0; index < routes.size(); index++) {
            StaffLinkRoute existing = routes.get(index);
            if (existing.route() == route.route() && existing.anchor().equals(route.anchor())) {
                routes.set(index, route);
                return;
            }
        }
        routes.add(route);
    }

    /** 解绑：移除该锚点的全部线路配置与自定义名。 */
    public void detach(GlobalPos anchor) {
        routes.removeIf(candidate -> candidate.anchor().equals(anchor));
        anchorNames.remove(anchor);
    }

    public boolean isEmpty() {
        return routes.isEmpty();
    }

    /** 该网络里是否已经有释放端——绑定新锚点时用它决定默认流向。 */
    public boolean hasReleaseRoute() {
        for (StaffLinkRoute route : routes) {
            if (route.flow() == LinkFlow.RELEASE) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 持久化

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_NAME, name);

        ListTag routeList = new ListTag();
        for (StaffLinkRoute route : routes) {
            routeList.add(route.save(registries));
        }
        tag.put(TAG_ROUTES, routeList);

        ListTag nameList = new ListTag();
        anchorNames.forEach((anchor, anchorName) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_DIMENSION, anchor.dimension().location().toString());
            entry.putLong(TAG_POS, anchor.pos().asLong());
            entry.putString(TAG_LABEL, anchorName);
            nameList.add(entry);
        });
        tag.put(TAG_ANCHOR_NAMES, nameList);
        return tag;
    }

    public static StaffLinkNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffLinkNetwork network = new StaffLinkNetwork(
                tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : UUID.randomUUID(),
                tag.getString(TAG_NAME));

        ListTag routeList = tag.getList(TAG_ROUTES, Tag.TAG_COMPOUND);
        for (int i = 0; i < routeList.size(); i++) {
            StaffLinkRoute route = StaffLinkRoute.load(routeList.getCompound(i), registries);
            if (route != null) {
                network.routes.add(route);
            }
        }

        ListTag nameList = tag.getList(TAG_ANCHOR_NAMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < nameList.size(); i++) {
            CompoundTag entry = nameList.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString(TAG_DIMENSION));
            String anchorName = entry.getString(TAG_LABEL);
            if (dimensionId == null || anchorName.isEmpty()) {
                continue;
            }
            network.anchorNames.put(
                    GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimensionId),
                            BlockPos.of(entry.getLong(TAG_POS))),
                    anchorName);
        }
        return network;
    }
}
