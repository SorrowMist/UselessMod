package com.sorrowmist.useless.world.stafflink;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.teams.TeamOwners;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无线物流网络的门面。
 *
 * <h2>归属跟着玩家/队伍，不跟着杖</h2>
 *
 * <p>「谁拥有哪些网络、当前用的是哪一张」存在 {@link StaffLinkSavedData} 的归属登记里，
 * 键是<b>归属者 ID</b>：玩家组了队就是队伍 UUID，没组队就是他自己的 UUID
 * （见 {@link TeamOwners}）。</p>
 *
 * <p>早先这些信息挂在杖的物品组件上（{@code STAFF_LINK_NETWORKS} / {@code STAFF_LINK_ACTIVE}），
 * 于是换一把杖、把杖放进箱子，网络就跟着丢了或停摆。现在网络跟着归属者走：</p>
 *
 * <ul>
 *   <li>队友之间天然共享同一批网络（同一个队伍 ID）；</li>
 *   <li>归属者只要有成员在线，网络就照常被引擎遍历，与「杖在哪」无关。</li>
 * </ul>
 *
 * <h2>组队时的并入</h2>
 *
 * <p>组队之前各人已经建好的网络，会在解析出队伍之后<b>并入队伍</b>
 * （见 {@link #mergePersonalIntoTeam}）——你把人拉进队伍，第一眼就该看见彼此的机器。
 * 这是<b>不可逆</b>的：并进去之后网络属于队伍，退队不会带走。</p>
 *
 * <p>杖仍然是<b>操作工具</b>：绑定容器、切网络、看高亮、开配置界面都要手持它。
 * 杖上残留的旧登记只用于一次性迁移，见 {@link #migrateLegacyRegistrations}。</p>
 *
 * <h2>权限</h2>
 *
 * <p>判据只有一条：{@link #canAccess} —— 玩家当前解析出的归属 ID 必须与该网络的归属 ID 相同。
 * 退队自然失去访问权，入队自然获得，不需要额外的成员表。</p>
 */
public final class StaffLinkManager {
    /**
     * 本局「有归属者在线」的网络。
     *
     * <p><b>这是防孤儿网络的关键。</b>存档里的网络不会因为没人引用就消失——比如早期版本用单
     * UUID 组件，改成列表组件之后，旧网络就成了没人引用的孤儿，可它仍然躺在
     * {@link StaffLinkSavedData} 里、仍然会被引擎遍历到。孤儿的配置可能停在
     * 「数量 65536 / 周期 1」，于是它会把箱子瞬间抽干，而玩家正在编辑的那张网络只看到
     * 「源已空」，完全无从判断。</p>
     *
     * <p>只让「归属者本局有在线成员」的网络参与搬运，孤儿自然就停摆了。</p>
     */
    private static final Set<UUID> LIVE_NETWORKS = ConcurrentHashMap.newKeySet();

    private StaffLinkManager() {
    }

    // ------------------------------------------------------------------ 归属

    /** 该玩家当前的归属 ID：有队伍就是队伍，否则是他自己。 */
    public static UUID ownerIdOf(ServerPlayer player) {
        return TeamOwners.ownerIdOf(player);
    }

    /** 一张网络的归属者；没有归属登记时返回 {@code null}。 */
    @Nullable
    public static UUID ownerOf(MinecraftServer server, UUID networkId) {
        return server == null || networkId == null
                ? null : StaffLinkSavedData.get(server).ownerOfNetwork(networkId);
    }

    /** 该玩家有没有权限操作这张网络。 */
    public static boolean canAccess(MinecraftServer server, ServerPlayer player, UUID networkId) {
        UUID owner = ownerOf(server, networkId);
        return owner != null && owner.equals(ownerIdOf(player));
    }

    // ------------------------------------------------------------------ 活跃闸门

    /** 标记某张网络本局活跃。 */
    public static void markLive(UUID networkId) {
        if (networkId != null) {
            LIVE_NETWORKS.add(networkId);
        }
    }

    public static boolean isLive(UUID networkId) {
        return networkId != null && LIVE_NETWORKS.contains(networkId);
    }

    public static void clearLive() {
        LIVE_NETWORKS.clear();
    }

    /**
     * 把「在线玩家所属归属者名下的网络」标记为活跃。
     *
     * <p>不再靠扫背包找杖：网络跟着归属者走，只要归属者有成员在线就该跑。每隔一会儿跑一次即可。</p>
     *
     * <p>顺带做两件收尾的事：</p>
     * <ul>
     *   <li><b>并入队伍</b>（{@link #mergePersonalIntoTeam}）：玩家刚组队时把他个人名下的网络
     *       交给队伍。放在这里而不是只挂在组队事件上，是因为「组队」可能发生在他离线的时候
     *       （被管理员拉进队伍），下次登录才解析得出队伍，没有事件可听。</li>
     *   <li><b>迁移旧登记</b>：玩家可能在本局中途才从箱子里翻出一把老存档留下的杖，
     *       只在登录时迁一次会漏掉这种。没有残留时就是每格一次组件查询，代价可以忽略。</li>
     * </ul>
     */
    public static void refreshLiveNetworks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            migrateLegacyRegistrations(server, player);
            mergePersonalIntoTeam(server, player);
            LIVE_NETWORKS.addAll(networkIds(server, ownerIdOf(player)));
        }
    }

    /**
     * 玩家在队伍里、而个人名下还留着网络时，把这些网络<b>并入队伍</b>。
     *
     * <p><b>这是不可逆的</b>：并进去之后网络就属于队伍了，退队不会带走。设计取舍是
     * 「组队共享」优先——你把人拉进队伍，第一眼就该看见彼此的机器，而不是对着空列表发呆。</p>
     *
     * <p>判据只有一个：<b>个人登记非空，且当前归属不是他自己</b>。它同时覆盖「在线组队」与
     * 「离线期间被拉进队伍、下次登录才解析出队伍」；而队→队、队→个人这两种切换下个人登记
     * 本来就是空的，自然不会误触发。</p>
     *
     * <p>并入时把个人登记清空：不清的话下次刷新会再并一遍，而且他退队后又会「复活」一批
     * 早就交给队伍的网络。</p>
     */
    public static void mergePersonalIntoTeam(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        UUID personalId = player.getUUID();
        UUID ownerId = ownerIdOf(player);
        if (personalId.equals(ownerId)) {
            // 没队伍：个人登记就是他的当前登记，不用动。
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        StaffLinkSavedData.OwnerRegistration personal = data.ownerOrNull(personalId);
        if (personal == null || personal.isEmpty()) {
            return;
        }

        List<UUID> merged = new ArrayList<>(networkIds(server, ownerId));
        boolean teamAlreadyHad = !merged.isEmpty();
        int moved = 0;
        for (UUID id : personal.networks()) {
            if (data.get(id) != null && !merged.contains(id)) {
                merged.add(id);
                moved++;
            }
        }
        if (!merged.isEmpty()) {
            // 队伍本来就有网络时保留队伍的当前项；第一次并进来才沿用个人那边的下标。
            int active = teamAlreadyHad
                    ? Math.min(activeIndex(server, ownerId), merged.size() - 1)
                    : Math.min(personal.activeIndex(), merged.size() - 1);
            data.setOwnerNetworks(ownerId, merged, Math.max(0, active));
        }
        data.setOwnerNetworks(personalId, List.of(), 0);
        data.dropOwnerIfEmpty(personalId);

        if (moved > 0) {
            UselessMod.LOGGER.info("无线物流：把 {} 名下的 {} 张网络并入队伍 {}",
                    player.getGameProfile().getName(), moved, ownerId);
        }
    }

    // ------------------------------------------------------------------ 归属登记读写

    /**
     * 该归属者名下的网络，按登记顺序。
     *
     * <p>顺手剔掉「已经被解散」的 id，避免登记里攒死链接；只有真的剔掉了才写回。</p>
     */
    public static List<UUID> networkIds(MinecraftServer server, UUID ownerId) {
        if (server == null || ownerId == null) {
            return List.of();
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        StaffLinkSavedData.OwnerRegistration registration = data.ownerOrNull(ownerId);
        if (registration == null) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(registration.networks());
        if (ids.removeIf(id -> data.get(id) == null)) {
            data.setOwnerNetworks(ownerId, ids, registration.activeIndex());
        }
        return List.copyOf(ids);
    }

    public static int activeIndex(MinecraftServer server, UUID ownerId) {
        if (server == null || ownerId == null) {
            return 0;
        }
        StaffLinkSavedData.OwnerRegistration registration =
                StaffLinkSavedData.get(server).ownerOrNull(ownerId);
        return registration == null ? 0 : registration.activeIndex();
    }

    /** 当前生效的网络；该归属者名下一张都没有时返回 {@code null}。 */
    @Nullable
    public static StaffLinkNetwork activeNetwork(MinecraftServer server, UUID ownerId) {
        return activeNetwork(server, ownerId, false);
    }

    /**
     * 取当前生效的网络。
     *
     * @param createIfMissing 该归属者一张网络都没有时是否就地新建一张并切过去
     */
    @Nullable
    public static StaffLinkNetwork activeNetwork(MinecraftServer server, UUID ownerId, boolean createIfMissing) {
        if (server == null || ownerId == null) {
            return null;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(server, ownerId));

        if (ids.isEmpty()) {
            if (!createIfMissing) {
                return null;
            }
            StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
            data.setOwnerNetworks(ownerId, List.of(created.id()), 0);
            markLive(created.id());
            return created;
        }

        int raw = activeIndex(server, ownerId);
        int clamped = Math.floorMod(raw, ids.size());
        if (clamped != raw) {
            // 只在真的越界时才写回，否则每 tick 都会把存档标脏。
            data.setOwnerNetworks(ownerId, ids, clamped);
        }
        markLive(ids.get(clamped));
        return data.get(ids.get(clamped));
    }

    /**
     * 在当前网络之间循环（手持杖 Shift+滚轮，或界面上的 {@code <} / {@code >}）。
     *
     * @param delta +1 下一张，-1 上一张
     * @return 是否成功切换（一张都没有时会顺手建一张）
     */
    public static boolean cycleActive(MinecraftServer server, UUID ownerId, int delta) {
        if (server == null || ownerId == null) {
            return false;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(server, ownerId));

        if (ids.isEmpty()) {
            StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
            data.setOwnerNetworks(ownerId, List.of(created.id()), 0);
            markLive(created.id());
            return true;
        }

        int next = Math.floorMod(activeIndex(server, ownerId) + delta, ids.size());
        data.setOwnerNetworks(ownerId, ids, next);
        markLive(ids.get(next));
        return true;
    }

    /** 新建一张空网络并切过去。 */
    @Nullable
    public static StaffLinkNetwork createNetwork(MinecraftServer server, UUID ownerId) {
        if (server == null || ownerId == null) {
            return null;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
        List<UUID> ids = new ArrayList<>(networkIds(server, ownerId));
        ids.add(created.id());
        data.setOwnerNetworks(ownerId, ids, ids.size() - 1);
        markLive(created.id());
        return created;
    }

    /** 解散当前网络：从存档删掉，并从归属登记里摘掉、切到相邻的一张。 */
    public static void dissolveActive(MinecraftServer server, UUID ownerId) {
        if (server == null || ownerId == null) {
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(server, ownerId));
        if (ids.isEmpty()) {
            return;
        }
        int index = Math.floorMod(activeIndex(server, ownerId), ids.size());
        data.remove(ids.remove(index));

        if (ids.isEmpty()) {
            data.setOwnerNetworks(ownerId, List.of(), 0);
            data.dropOwnerIfEmpty(ownerId);
            return;
        }
        data.setOwnerNetworks(ownerId, ids, Math.floorMod(index, ids.size()));
    }

    @Nullable
    public static StaffLinkNetwork networkById(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : StaffLinkSavedData.get(server).get(id);
    }

    // ------------------------------------------------------------------ 旧存档迁移

    /**
     * 把该玩家身上所有造化杖登记的旧网络迁到他的归属名下，并清空杖上的组件。
     *
     * <p>登录时跑一次即可；迁移后组件就清掉了，所以重复调用是幂等的。</p>
     */
    public static void migrateLegacyRegistrations(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        UUID ownerId = ownerIdOf(player);
        Inventory inventory = player.getInventory();
        // 背包 41 格包含主手与副手，所以不用再单独看双手。
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            migrateStaff(server, ownerId, inventory.getItem(slot));
        }
    }

    private static void migrateStaff(MinecraftServer server, UUID ownerId, ItemStack staff) {
        List<UUID> legacy = legacyNetworkIds(staff);
        if (legacy.isEmpty()) {
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(server, ownerId));
        boolean alreadyHad = !ids.isEmpty();
        for (UUID id : legacy) {
            if (data.get(id) != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (!ids.isEmpty()) {
            // 本来就有网络时保留原来的当前项；第一次迁移才沿用杖上的下标。
            int active = alreadyHad
                    ? Math.min(activeIndex(server, ownerId), ids.size() - 1)
                    : Math.min(legacyActiveIndex(staff), ids.size() - 1);
            data.setOwnerNetworks(ownerId, ids, Math.max(0, active));
        }
        clearLegacyRegistration(staff);
    }

    /** 旧存档：杖上登记的网络 ID（只用于迁移）。 */
    public static List<UUID> legacyNetworkIds(ItemStack staff) {
        if (staff == null || staff.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = staff.get(UComponents.STAFF_LINK_NETWORKS.get());
        return ids == null ? List.of() : ids;
    }

    /** 旧存档：杖上的当前下标（只用于迁移）。 */
    public static int legacyActiveIndex(ItemStack staff) {
        Integer index = staff == null || staff.isEmpty()
                ? null : staff.get(UComponents.STAFF_LINK_ACTIVE.get());
        return index == null ? 0 : index;
    }

    /** 迁移完成后把杖上的登记清掉，免得下次登录又迁一遍。 */
    private static void clearLegacyRegistration(ItemStack staff) {
        staff.remove(UComponents.STAFF_LINK_NETWORKS.get());
        staff.remove(UComponents.STAFF_LINK_ACTIVE.get());
    }
}
