package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.LinkTrigger;
import com.sorrowmist.useless.content.stafflink.StaffLinkEngine;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.content.stafflink.StaffLinkTargets;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.network.StaffLinkConfigurePacket;
import com.sorrowmist.useless.network.StaffLinkCyclePacket;
import com.sorrowmist.useless.network.StaffLinkDetachPacket;
import com.sorrowmist.useless.network.StaffLinkNetworkPacket;
import com.sorrowmist.useless.network.StaffLinkRenamePacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 无线物流的配置界面容器。
 *
 * <p>它背后没有方块实体：网络状态存在服务端的 {@code StaffLinkSavedData} 里，客户端拿到的是
 * {@link #receiveSync} 下发的一份快照。界面上所有编辑都发成命令包，服务端校验后回发新快照，
 * 因此<b>服务端永远是唯一权威</b>。</p>
 *
 * <p>真实槽位只有玩家背包。过滤器槽与锚点列表都是纯绘制控件（由 {@code StaffLinkScreen}
 * 负责绘制与命中检测），不走原版的槽位点击管线——它们只是「编辑某个字段的入口」，
 * 不需要参与物品搬运。</p>
 */
public final class StaffLinkMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_X = 44;
    private static final int PLAYER_INVENTORY_Y = 254;
    private static final int PLAYER_HOTBAR_Y = 312;

    private UUID networkId;
    private final boolean clientSide;

    @Nullable
    private StaffLinkNetwork snapshot;
    private List<ItemStack> filterMirror = emptyFilter();

    @Nullable
    private GlobalPos selectedAnchor;
    private int selectedRoute;

    /** 服务端每秒推一次「上次搬运」读数，界面上直接显示，省得靠猜。 */
    private StaffLinkEngine.TransferStats lastStats = StaffLinkEngine.TransferStats.NONE;

    /** 服务端构造：直接绑定到存档里的那张网络。 */
    public StaffLinkMenu(int containerId, Inventory inventory, UUID networkId) {
        super(ModMenuType.STAFF_LINK_MENU.get(), containerId);
        this.networkId = networkId;
        this.clientSide = inventory.player.level().isClientSide();
        MinecraftServer server = inventory.player.level().getServer();
        this.snapshot = server == null ? null : StaffLinkManager.networkById(server, networkId);
        addPlayerInventory(inventory);
    }

    /** 客户端构造：网络 ID 随开界面的上下文一起下发，快照由同步包补上。 */
    public StaffLinkMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readUUID());
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INVENTORY_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    private static List<ItemStack> emptyFilter() {
        List<ItemStack> filter = new ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        for (int i = 0; i < StaffLinkRoute.FILTER_LIMIT; i++) {
            filter.add(ItemStack.EMPTY);
        }
        return filter;
    }

    // ------------------------------------------------------------------ 状态

    public UUID getNetworkId() {
        return networkId;
    }

    @Nullable
    public StaffLinkNetwork getSnapshot() {
        return snapshot;
    }

    /** 已绑定的锚点列表（快照为空时为空表）。 */
    public List<GlobalPos> getAnchors() {
        return snapshot == null ? List.of() : snapshot.anchors();
    }

    @Nullable
    public GlobalPos getSelectedAnchor() {
        return selectedAnchor;
    }

    public int getSelectedRoute() {
        return selectedRoute;
    }

    /** 当前选中锚点 + 线路对应的配置；未选中或该线路还没有配置时返回 {@code null}。 */
    @Nullable
    public StaffLinkRoute getSelectedConfig() {
        if (snapshot == null || selectedAnchor == null) {
            return null;
        }
        return snapshot.routeAt(selectedAnchor, selectedRoute);
    }

    /** 任意「锚点 × 线路」的配置；列表里给每个容器标出流向用。 */
    @Nullable
    public StaffLinkRoute getConfig(GlobalPos anchor, int route) {
        return snapshot == null ? null : snapshot.routeAt(anchor, route);
    }

    public boolean isAnchorBound(GlobalPos anchor) {
        return snapshot != null && snapshot.isBound(anchor);
    }

    /** 过滤器显示用的镜像（长度固定为 {@link StaffLinkRoute#FILTER_LIMIT}）。 */
    public List<ItemStack> getFilterMirror() {
        return filterMirror;
    }

    public boolean isFilterActive() {
        StaffLinkRoute config = getSelectedConfig();
        return config != null && config.filterApplies();
    }

    /** 客户端收到服务端快照。 */
    public void receiveSync(StaffLinkNetwork network) {
        // 服务端可能刚切到另一张网络（新建 / 解散），界面跟着走。
        this.networkId = network.id();
        this.snapshot = network;
        if (selectedAnchor != null && !network.isBound(selectedAnchor)) {
            selectedAnchor = null;
        }
        refreshFilterMirror();
    }

    /** 界面切换选中锚点/线路。 */
    public void setSelection(@Nullable GlobalPos anchor, int route) {
        this.selectedAnchor = anchor;
        this.selectedRoute = Math.max(0, Math.min(route, StaffLinkNetwork.ROUTE_COUNT - 1));
        refreshFilterMirror();
    }

    /** 为某个还没有配置的「锚点 × 线路」生成一条默认配置。 */
    public StaffLinkRoute defaultRouteFor(GlobalPos anchor, int route) {
        LinkMedium medium = LinkMedium.ITEM;
        if (snapshot != null) {
            for (StaffLinkRoute existing : snapshot.routes()) {
                if (existing.anchor().equals(anchor)) {
                    medium = existing.medium();
                    break;
                }
            }
        }
        LinkFlow flow = snapshot != null && snapshot.hasReleaseRoute() ? LinkFlow.ABSORB : LinkFlow.RELEASE;
        // 新线路默认关闭，由玩家显式打开。
        return new StaffLinkRoute(anchor, route, false, flow, medium, 16, 5, null,
                LinkTrigger.ALWAYS, 0, List.of());
    }

    /** 当前网络名（没起过名时为空串）。 */
    public String getNetworkName() {
        return snapshot == null ? "" : snapshot.name();
    }

    /** 最近一次搬运读数（请求量 / 实际搬走量 / 输出个数）。 */
    public StaffLinkEngine.TransferStats getLastStats() {
        return lastStats;
    }

    /** 客户端收到服务端推来的搬运读数。 */
    public void receiveStatus(long tick, long requested, long moved, int targets,
                              StaffLinkTargets.TransferBlocker blocker) {
        lastStats = new StaffLinkEngine.TransferStats(tick, requested, moved, targets, blocker);
    }

    /** 给当前网络改名。 */
    public void renameNetwork(String name) {
        String safe = name == null ? "" : name;
        if (snapshot != null) {
            snapshot.setName(safe);
        }
        if (clientSide) {
            PacketDistributor.sendToServer(
                    new StaffLinkNetworkPacket(StaffLinkNetworkPacket.Action.RENAME, safe));
        }
    }

    /** 新建一张网络并切过去；服务端会回发新网络的快照。 */
    public void createNetwork() {
        if (clientSide) {
            PacketDistributor.sendToServer(
                    new StaffLinkNetworkPacket(StaffLinkNetworkPacket.Action.NEW, ""));
        }
    }

    /** 切换当前网络（界面上的 &lt; / &gt; 按钮）；服务端会回发新网络的快照。 */
    public void cycleNetwork(int delta) {
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkCyclePacket(delta));
        }
    }

    /** 解散当前网络；服务端会切到相邻的一张并回发快照，一张不剩时会关掉界面。 */
    public void dissolveNetwork() {
        if (clientSide) {
            PacketDistributor.sendToServer(
                    new StaffLinkNetworkPacket(StaffLinkNetworkPacket.Action.DISSOLVE, ""));
        }
    }

    /** 写入/覆盖当前选中线路的配置，并提交给服务端。 */
    public void applyRoute(StaffLinkRoute route) {
        if (snapshot != null) {
            snapshot.putRoute(route);
        }
        refreshFilterMirror();
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkConfigurePacket(
                    route.anchor(), route.route(), route));
        }
    }

    /** 改一个过滤器槽；没有选中配置或该线路不用过滤器时忽略。 */
    public void setFilterSlot(int index, ItemStack stack) {
        StaffLinkRoute config = getSelectedConfig();
        if (config == null || !config.filterApplies()) {
            return;
        }
        if (index < 0 || index >= StaffLinkRoute.FILTER_LIMIT) {
            return;
        }
        List<ItemStack> filter = new ArrayList<>(config.filter());
        filter.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        applyRoute(config.withFilter(filter));
    }

    /** 该锚点的自定义名；没起过名时返回 {@code null}，界面回落到方块本名。 */
    @Nullable
    public String getAnchorName(GlobalPos anchor) {
        return snapshot == null ? null : snapshot.anchorName(anchor);
    }

    /** 给锚点改名（空串表示恢复方块本名）。 */
    public void renameAnchor(GlobalPos anchor, String name) {
        if (snapshot != null) {
            snapshot.setAnchorName(anchor, name);
        }
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkRenamePacket(anchor, name == null ? "" : name));
        }
    }

    /** 解绑一个锚点。 */
    public void detach(GlobalPos anchor) {
        if (snapshot != null) {
            snapshot.detach(anchor);
        }
        if (anchor.equals(selectedAnchor)) {
            selectedAnchor = null;
        }
        refreshFilterMirror();
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkDetachPacket(anchor));
        }
    }

    private void refreshFilterMirror() {        StaffLinkRoute config = getSelectedConfig();
        if (config == null) {
            filterMirror = emptyFilter();
            return;
        }
        List<ItemStack> filter = new ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        List<ItemStack> source = config.filter();
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            filter.add(index < source.size() ? source.get(index) : ItemStack.EMPTY);
        }
        filterMirror = List.copyOf(filter);
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (!player.isAlive()) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            // 客户端不做权威判定，交给服务端关闭。
            return true;
        }
        return StaffLinkManager.networkById(server, networkId) != null;
    }
}
