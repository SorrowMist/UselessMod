package com.sorrowmist.useless.world.stafflink;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 无线物流的全局存档：网络本体 + 归属登记。
 *
 * <p>与 {@code AeConnectLinkSavedData} 同一套路：存在主世界的 {@code DimensionDataStorage} 上，
 * 与「谁此刻拿着哪把杖」无关。</p>
 *
 * <h2>归属登记（{@link #owners}）</h2>
 *
 * <p><b>「这些网络是谁的」记在这里，而不是记在杖的物品组件上。</b>早先网络列表挂在杖上，
 * 于是换一把杖、把杖放进箱子，网络就跟着丢了或停摆。现在网络跟着<b>归属者</b>走：
 * 归属者通常是玩家本人，玩家在队伍里时则是队伍 —— 队友之间因此天然共享同一批网络。</p>
 *
 * <p>网络本体与归属分开存，所以删网络时两边都要清：{@link #remove} 会顺手把 id 从所有
 * 归属登记里摘掉，免得留下「登记了但不存在」的死链接。</p>
 */
public final class StaffLinkSavedData extends SavedData {
    private static final String DATA_NAME = "useless_mod_staff_link";
    private static final int CURRENT_VERSION = 2;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_NETWORKS = "Networks";
    private static final String TAG_OWNERS = "Owners";
    private static final String TAG_OWNER_ID = "Owner";
    private static final String TAG_OWNER_ACTIVE = "Active";
    private static final String TAG_OWNER_REGISTERED = "Registered";
    private static final String TAG_OWNER_ENTRY_ID = "Id";

    private final Map<UUID, StaffLinkNetwork> networks = new LinkedHashMap<>();
    /** 归属者 UUID → 它登记的网络。 */
    private final Map<UUID, OwnerRegistration> owners = new LinkedHashMap<>();

    /**
     * 一个归属者登记的网络。
     *
     * <p>列表顺序就是 Shift+滚轮的切换顺序；{@link #activeIndex} 是「当前用的是第几张」。</p>
     */
    public static final class OwnerRegistration {
        private final List<UUID> networks = new ArrayList<>();
        private int activeIndex;

        public List<UUID> networks() {
            return List.copyOf(networks);
        }

        public int activeIndex() {
            return activeIndex;
        }

        public void setActiveIndex(int index) {
            activeIndex = Math.max(0, index);
        }

        public boolean isEmpty() {
            return networks.isEmpty();
        }

        boolean register(UUID id) {
            return !networks.contains(id) && networks.add(id);
        }

        boolean unregister(UUID id) {
            return networks.remove(id);
        }
    }

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

        ListTag ownerList = tag.getList(TAG_OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < ownerList.size(); i++) {
            CompoundTag entry = ownerList.getCompound(i);
            UUID ownerId = readUuid(entry.getString(TAG_OWNER_ID));
            if (ownerId == null) {
                continue;
            }
            OwnerRegistration registration = new OwnerRegistration();
            ListTag registered = entry.getList(TAG_OWNER_REGISTERED, Tag.TAG_COMPOUND);
            for (int n = 0; n < registered.size(); n++) {
                UUID networkId = readUuid(registered.getCompound(n).getString(TAG_OWNER_ENTRY_ID));
                if (networkId != null && !registration.networks.contains(networkId)) {
                    registration.networks.add(networkId);
                }
            }
            registration.activeIndex = Math.max(0, entry.getInt(TAG_OWNER_ACTIVE));
            data.owners.put(ownerId, registration);
        }
        return data;
    }

    // ------------------------------------------------------------------ 网络本体

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

    /** 删一张网络，并从所有归属登记里摘掉它。 */
    public void remove(UUID id) {
        boolean changed = networks.remove(id) != null;
        for (OwnerRegistration registration : owners.values()) {
            changed |= registration.unregister(id);
        }
        if (changed) {
            setDirty();
        }
    }

    /** 网络内容被改动后调用（网络对象本身是可变类，脏标记由它这里统一打）。 */
    public void markDirty() {
        setDirty();
    }

    // ------------------------------------------------------------------ 归属登记

    /** 该归属者的登记；没有则返回 {@code null}（只读，不会顺手建一条）。 */
    @Nullable
    public OwnerRegistration ownerOrNull(UUID ownerId) {
        return owners.get(ownerId);
    }

    /** 该归属者的登记，没有就建一条空的。 */
    public OwnerRegistration owner(UUID ownerId) {
        return owners.computeIfAbsent(ownerId, ignored -> new OwnerRegistration());
    }

    /** 整份覆盖某个归属者的登记（写回时用）。 */
    public void setOwnerNetworks(UUID ownerId, List<UUID> networkIds, int activeIndex) {
        OwnerRegistration registration = owner(ownerId);
        registration.networks.clear();
        for (UUID id : networkIds) {
            if (id != null && !registration.networks.contains(id)) {
                registration.networks.add(id);
            }
        }
        registration.setActiveIndex(activeIndex);
        setDirty();
    }

    /** 把一张网络登记到该归属者名下（已在列表里则什么都不做）。 */
    public void registerNetwork(UUID ownerId, UUID networkId) {
        if (networkId != null && owner(ownerId).register(networkId)) {
            setDirty();
        }
    }

    /**
     * 反查一张网络的归属者。
     *
     * <p>归属者数量很少（在线玩家/队伍级别），遍历足够；维护反向索引反而容易和
     * {@link #remove} / {@link #setOwnerNetworks} 走岔。</p>
     */
    @Nullable
    public UUID ownerOfNetwork(UUID networkId) {
        if (networkId == null) {
            return null;
        }
        for (Map.Entry<UUID, OwnerRegistration> entry : owners.entrySet()) {
            if (entry.getValue().networks.contains(networkId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 某归属者名下的一张都不剩时，把这条空登记删掉，别让存档里攒空壳。 */
    public void dropOwnerIfEmpty(UUID ownerId) {
        OwnerRegistration registration = owners.get(ownerId);
        if (registration != null && registration.isEmpty()) {
            owners.remove(ownerId);
            setDirty();
        }
    }

    // ------------------------------------------------------------------ 持久化

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);

        ListTag list = new ListTag();
        for (StaffLinkNetwork network : networks.values()) {
            list.add(network.save(registries));
        }
        tag.put(TAG_NETWORKS, list);

        ListTag ownerList = new ListTag();
        owners.forEach((ownerId, registration) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_OWNER_ID, ownerId.toString());
            entry.putInt(TAG_OWNER_ACTIVE, registration.activeIndex);
            ListTag registered = new ListTag();
            for (UUID networkId : registration.networks) {
                CompoundTag item = new CompoundTag();
                item.putString(TAG_OWNER_ENTRY_ID, networkId.toString());
                registered.add(item);
            }
            entry.put(TAG_OWNER_REGISTERED, registered);
            ownerList.add(entry);
        });
        tag.put(TAG_OWNERS, ownerList);
        return tag;
    }

    @Nullable
    private static UUID readUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
