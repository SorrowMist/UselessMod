package com.sorrowmist.useless.world.stafflink;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 造化杖无线物流网络的全局登记表。
 *
 * <p>与 {@code AeConnectLinkSavedData} 同一套路：存在主世界的
 * {@code DimensionDataStorage} 上，按网络 UUID 索引。这样物流的归属与「杖此刻在哪」
 * 无关——杖被放进箱子、所在区块卸载，网络照常在服务端被遍历。</p>
 */
public final class StaffLinkSavedData extends SavedData {
    private static final String DATA_NAME = "useless_mod_staff_link";
    private static final int CURRENT_VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_NETWORKS = "Networks";

    private final Map<UUID, StaffLinkNetwork> networks = new LinkedHashMap<>();

    public static StaffLinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(StaffLinkSavedData::new,
                        (tag, registries) -> load(tag, registries)),
                DATA_NAME);
    }

    private static StaffLinkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffLinkSavedData data = new StaffLinkSavedData();
        ListTag list = tag.getList(TAG_NETWORKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StaffLinkNetwork network = StaffLinkNetwork.load(list.getCompound(i), registries);
            data.networks.put(network.id(), network);
        }
        return data;
    }

    public StaffLinkNetwork getOrCreate(UUID id) {
        StaffLinkNetwork existing = networks.get(id);
        if (existing != null) {
            return existing;
        }
        StaffLinkNetwork created = new StaffLinkNetwork(id);
        networks.put(id, created);
        setDirty();
        return created;
    }

    @Nullable
    public StaffLinkNetwork get(UUID id) {
        return networks.get(id);
    }

    public Collection<StaffLinkNetwork> all() {
        return List.copyOf(networks.values());
    }

    public void remove(UUID id) {
        if (networks.remove(id) != null) {
            setDirty();
        }
    }

    /** 网络内容被改动后调用（网络对象本身是可变类，脏标记由它这里统一打）。 */
    public void markDirty() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (StaffLinkNetwork network : networks.values()) {
            list.add(network.save(registries));
        }
        tag.put(TAG_NETWORKS, list);
        return tag;
    }
}
