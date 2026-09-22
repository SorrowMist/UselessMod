package com.sorrowmist.useless.world.ae;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 造化杖「AE 连接模式」建立的跨空间连接登记表。
 *
 * <p>AE2 <b>不会</b>把这种连接写进存档：网格的持久化数据里只有节点与机器，没有连接，
 * 连接完全是运行时对象（原版量子网络桥同样只在两侧均存活时重建）。
 * 因此连接的持久化由建立方负责——这里按「访问点维度+坐标 / 机器维度+坐标」四条信息登记，
 * 配合 {@code AeDeviceLinker} 的低频自愈在区块/存档重载后重建连接。</p>
 *
 * <p><b>两个维度都必须记录</b>：连接允许跨维度（访问点与机器分处不同维度），
 * 若只记录机器维度，自愈会在机器所在维度查找访问点：既可能查找失败，也可能命中同坐标的
 * 另一台访问点（不同维度坐标对齐在整合包中很常见）。</p>
 */
public final class AeConnectLinkSavedData extends SavedData {
    private static final String DATA_NAME = "useless_mod_ae_connect_links";
    private static final int CURRENT_VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_LINKS = "Links";
    private static final String TAG_MACHINE_DIMENSION = "MachineDimension";
    private static final String TAG_ACCESS_POINT_DIMENSION = "AccessPointDimension";
    private static final String TAG_ACCESS_POINT = "AccessPoint";
    private static final String TAG_MACHINE = "Machine";

    private final Set<Link> links = new LinkedHashSet<>();

    public static AeConnectLinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AeConnectLinkSavedData::new,
                        (tag, registries) -> load(tag)),
                DATA_NAME);
    }

    private static AeConnectLinkSavedData load(CompoundTag tag) {
        AeConnectLinkSavedData data = new AeConnectLinkSavedData();
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Link link = readLink(list.getCompound(i));
            if (link != null) {
                data.links.add(link);
            }
        }
        return data;
    }

    @Nullable
    private static Link readLink(CompoundTag entry) {
        ResourceKey<Level> machineDimension = readDimension(entry.getString(TAG_MACHINE_DIMENSION));
        ResourceKey<Level> accessPointDimension = readDimension(entry.getString(TAG_ACCESS_POINT_DIMENSION));
        if (machineDimension == null || accessPointDimension == null) {
            return null;
        }
        return new Link(machineDimension, accessPointDimension,
                BlockPos.of(entry.getLong(TAG_ACCESS_POINT)),
                BlockPos.of(entry.getLong(TAG_MACHINE)));
    }

    @Nullable
    private static ResourceKey<Level> readDimension(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    public boolean contains(Link link) {
        return links.contains(link);
    }

    public void add(Link link) {
        if (links.add(link)) {
            setDirty();
        }
    }

    public void remove(Link link) {
        if (links.remove(link)) {
            setDirty();
        }
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    /** 自愈遍历用的快照：避免遍历过程中改集合。 */
    public List<Link> snapshot() {
        return new ArrayList<>(links);
    }

    /** 某台访问点在某个维度下连了哪些机器——客户端渲染连接提示用。 */
    public List<BlockPos> machinesOf(ResourceKey<Level> machineDimension,
                                     ResourceKey<Level> accessPointDimension,
                                     BlockPos accessPoint) {
        List<BlockPos> result = new ArrayList<>();
        for (Link link : links) {
            if (link.machineDimension().equals(machineDimension)
                    && link.accessPointDimension().equals(accessPointDimension)
                    && link.accessPoint().equals(accessPoint)) {
                result.add(link.machine());
            }
        }
        return result;
    }

    /** 机器方块没了：把指向它的登记一并解除，避免存档里越攒越多死链接。 */
    public void removeByMachine(ResourceKey<Level> machineDimension, BlockPos machine) {
        if (links.removeIf(link -> link.machineDimension().equals(machineDimension)
                && link.machine().equals(machine))) {
            setDirty();
        }
    }

    /** 访问点没了：绑定的那张网已经不存在，指向它的登记全部作废。 */
    public void removeByAccessPoint(ResourceKey<Level> accessPointDimension, BlockPos accessPoint) {
        if (links.removeIf(link -> link.accessPointDimension().equals(accessPointDimension)
                && link.accessPoint().equals(accessPoint))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (Link link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_MACHINE_DIMENSION, link.machineDimension().location().toString());
            entry.putString(TAG_ACCESS_POINT_DIMENSION, link.accessPointDimension().location().toString());
            entry.putLong(TAG_ACCESS_POINT, link.accessPoint().asLong());
            entry.putLong(TAG_MACHINE, link.machine().asLong());
            list.add(entry);
        }
        tag.put(TAG_LINKS, list);
        return tag;
    }

    /** 一条「访问点 ↔ 机器」的连接登记（两个维度都要记，见类注释）。 */
    public record Link(ResourceKey<Level> machineDimension,
                       ResourceKey<Level> accessPointDimension,
                       BlockPos accessPoint,
                       BlockPos machine) {
    }
}
