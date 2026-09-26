package com.sorrowmist.useless.world.stafflink;

import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 造化杖 ↔ 无线物流网络的门面。
 *
 * <p>一把杖可以同时挂多张互相独立的网络：物品上存 {@link UComponents#STAFF_LINK_NETWORKS}
 * （UUID 列表）与 {@link UComponents#STAFF_LINK_ACTIVE}（当前生效的下标），
 * 网络本体存在 {@link StaffLinkSavedData}。Shift+滚轮就是改那个下标。</p>
 *
 * <p>所有读取都会顺手清掉「已经被解散」的 UUID，避免杖上攒一堆死链接。</p>
 */
public final class StaffLinkManager {
    /**
     * 本次服务器会话里「被某把造化杖引用过」的网络。
     *
     * <p><b>这是防孤儿网络的关键。</b>存档里的网络不会因为杖丢了引用就消失——比如早期版本用单
     * UUID 组件，改成列表组件之后，旧网络就成了没人引用的孤儿，可它仍然躺在
     * {@link StaffLinkSavedData} 里、仍然会被引擎遍历到。孤儿的配置可能停在
     * 「数量 65536 / 周期 1」，于是它会把箱子瞬间抽干，而玩家正在编辑的那张网络只看到
     * 「源已空」，完全无从判断。</p>
     *
     * <p>只让「本局被杖引用过」的网络参与搬运，孤儿自然就停摆了。杖放进箱子不影响——
     * 玩家带着它走过一次就进了这个集合。</p>
     */
    private static final Set<UUID> LIVE_NETWORKS = ConcurrentHashMap.newKeySet();

    private StaffLinkManager() {
    }

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
     * 扫描在线玩家身上的造化杖，把它们引用的网络标记为活跃。
     *
     * <p>每隔一会儿跑一次即可：玩家的杖一直在身上，扫 41 个格子乘以在线人数，代价可以忽略。</p>
     */
    public static void refreshLiveNetworks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                LIVE_NETWORKS.addAll(networkIds(stack));
            }
        }
    }

    /** 杖上登记的网络 ID（原样返回，不校验是否还存在）。 */
    public static List<UUID> networkIds(ItemStack staff) {
        if (staff == null || staff.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = staff.get(UComponents.STAFF_LINK_NETWORKS.get());
        return ids == null ? List.of() : ids;
    }

    public static int activeIndex(ItemStack staff) {
        Integer index = staff == null || staff.isEmpty() ? null : staff.get(UComponents.STAFF_LINK_ACTIVE.get());
        return index == null ? 0 : index;
    }

    /** 当前生效的网络；杖上还没有网络时返回 {@code null}。 */
    @Nullable
    public static StaffLinkNetwork activeNetwork(MinecraftServer server, ItemStack staff) {
        return activeNetwork(server, staff, false);
    }

    /**
     * 取当前生效的网络。
     *
     * @param createIfMissing 杖上一张网络都没有时是否就地新建一张并切过去
     */
    @Nullable
    public static StaffLinkNetwork activeNetwork(MinecraftServer server, ItemStack staff, boolean createIfMissing) {
        if (server == null || staff == null || staff.isEmpty()) {
            return null;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(staff));
        boolean pruned = ids.removeIf(id -> data.get(id) == null);

        if (ids.isEmpty()) {
            if (!createIfMissing) {
                if (pruned) {
                    writeBack(staff, ids, 0);
                }
                return null;
            }
            StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
            writeBack(staff, List.of(created.id()), 0);
            return created;
        }

        int index = activeIndex(staff);
        int clamped = Math.floorMod(index, ids.size());
        if (pruned || clamped != index) {
            writeBack(staff, ids, clamped);
        }
        // 杖正在用这张网络 ⇒ 标记活跃（哪怕这次没有写回组件）。
        markLive(ids.get(clamped));
        return data.get(ids.get(clamped));
    }

    /**
     * 在当前网络之间循环（Shift+滚轮）。
     *
     * @param delta +1 下一张，-1 上一张
     * @return 是否成功切换（杖上一张网络都没有时会顺手建一张）
     */
    public static boolean cycleActive(MinecraftServer server, ItemStack staff, int delta) {
        if (server == null || staff == null || staff.isEmpty()) {
            return false;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(staff));
        ids.removeIf(id -> data.get(id) == null);

        if (ids.isEmpty()) {
            StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
            writeBack(staff, List.of(created.id()), 0);
            return true;
        }

        writeBack(staff, ids, Math.floorMod(activeIndex(staff) + delta, ids.size()));
        return true;
    }

    /** 新建一张空网络并切过去。 */
    @Nullable
    public static StaffLinkNetwork createNetwork(MinecraftServer server, ItemStack staff) {
        if (server == null || staff == null || staff.isEmpty()) {
            return null;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        StaffLinkNetwork created = data.getOrCreate(UUID.randomUUID());
        List<UUID> ids = new ArrayList<>(networkIds(staff));
        ids.removeIf(id -> data.get(id) == null);
        ids.add(created.id());
        writeBack(staff, ids, ids.size() - 1);
        return created;
    }

    /** 解散当前网络：从存档删掉，并从杖的列表里摘掉、切到相邻的一张。 */
    public static void dissolveActive(MinecraftServer server, ItemStack staff) {
        if (server == null || staff == null || staff.isEmpty()) {
            return;
        }
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<UUID> ids = new ArrayList<>(networkIds(staff));
        if (ids.isEmpty()) {
            return;
        }
        int index = Math.floorMod(activeIndex(staff), ids.size());
        data.remove(ids.remove(index));

        if (ids.isEmpty()) {
            staff.remove(UComponents.STAFF_LINK_NETWORKS.get());
            staff.remove(UComponents.STAFF_LINK_ACTIVE.get());
            return;
        }
        writeBack(staff, ids, Math.floorMod(index, ids.size()));
    }

    @Nullable
    public static StaffLinkNetwork networkById(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : StaffLinkSavedData.get(server).get(id);
    }

    /**
     * 在玩家身上找「携带了指定网络」的造化杖：先手上，再背包。
     *
     * <p>界面开着时玩家可能把杖换到别的格子，所以不能只看手持。</p>
     */
    public static ItemStack findStaffWithNetwork(Player player, UUID networkId) {
        if (player == null || networkId == null) {
            return ItemStack.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (carries(stack, networkId)) {
                return stack;
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (carries(stack, networkId)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean carries(ItemStack stack, UUID networkId) {
        return !stack.isEmpty() && networkIds(stack).contains(networkId);
    }

    private static void writeBack(ItemStack staff, List<UUID> ids, int index) {
        staff.set(UComponents.STAFF_LINK_NETWORKS.get(), List.copyOf(ids));
        staff.set(UComponents.STAFF_LINK_ACTIVE.get(), index);
        // 杖正在被使用 ⇒ 这些网络本局活跃。
        for (UUID id : ids) {
            markLive(id);
        }
    }
}
