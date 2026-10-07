package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.LinkFilterCondition;
import com.sorrowmist.useless.content.stafflink.LinkFilterSlot;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.ResourceFamily;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.network.StaffLinkConfigurePacket;
import com.sorrowmist.useless.network.StaffLinkCyclePacket;
import com.sorrowmist.useless.network.StaffLinkDetachPacket;
import com.sorrowmist.useless.network.StaffLinkNetworkPacket;
import com.sorrowmist.useless.network.StaffLinkRenamePacket;
import com.sorrowmist.useless.network.StaffLinkReorderPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Objects;
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
    /**
     * 背包第一行的 y。
     *
     * <p>过滤槽不画在主界面上（改成覆盖层里的面板），所以面板高度维持原值，
     * 背包位置也就不必下移。</p>
     */
    private static final int PLAYER_INVENTORY_Y = 254;
    private static final int PLAYER_HOTBAR_Y = 312;

    private UUID networkId;
    private final boolean clientSide;

    @Nullable
    private StaffLinkNetwork snapshot;
    /** 当前网络在归属者列表里的位置（由同步包带来，客户端没有那份列表）。 */
    private int networkIndex;
    private int networkCount = 1;
    private List<LinkFilterSlot> filterMirror = emptyFilter();

    @Nullable
    private GlobalPos selectedAnchor;
    private int selectedRoute;

    /**
     * 多选出来的锚点（批量编辑用）。
     *
     * <p>它和 {@link #selectedAnchor} 是两套东西：单选锚点决定右边配置区显示谁的配置，
     * 多选集合决定「一次改动要写到哪些容器上」。集合为空时只改单选那一个。</p>
     */
    private final List<GlobalPos> multiSelection = new ArrayList<>();

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

    private static List<LinkFilterSlot> emptyFilter() {
        List<LinkFilterSlot> filter = new ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        for (int i = 0; i < StaffLinkRoute.FILTER_LIMIT; i++) {
            filter.add(LinkFilterSlot.EMPTY);
        }
        return filter;
    }

    // ------------------------------------------------------------------ 状态

    public UUID getNetworkId() {
        return networkId;
    }

    /**
     * 服务端专用：把界面绑定的网络换成另一张。
     *
     * <p>玩家在界面里切网络（{@code <}/{@code >}、新建、解散）改的是<b>归属者</b>（玩家或队伍）
     * 名下的「当前网络」，而后续所有编辑包都靠服务端菜单里的这个 ID 寻址。不同步它，
     * 切到 B 之后的编辑仍会打到 A 上——表现就是「在 B 里点一下，界面跳回 A」。</p>
     */
    public void setNetworkId(@Nullable UUID networkId) {
        if (networkId != null && !networkId.equals(this.networkId)) {
            this.networkId = networkId;
            this.selectedAnchor = null;
            this.snapshot = null;
            this.filterMirror = emptyFilter();
        }
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
    public List<LinkFilterSlot> getFilterMirror() {
        return filterMirror;
    }

    /** 当前选中线路的资源类型；没有选中配置时返回 {@code null}。 */
    @Nullable
    public LinkMedium getSelectedMedium() {
        StaffLinkRoute config = getSelectedConfig();
        return config == null ? null : config.medium();
    }

    public boolean isFilterActive() {
        StaffLinkRoute config = getSelectedConfig();
        return config != null && config.filterApplies();
    }

    /** 客户端收到服务端快照。 */
    public void receiveSync(StaffLinkNetwork network, int index, int count) {
        // 服务端可能刚切到另一张网络（新建 / 解散），界面跟着走。
        this.networkId = network.id();
        this.snapshot = network;
        this.networkIndex = Math.max(0, index);
        this.networkCount = Math.max(1, count);
        if (selectedAnchor != null && !network.isBound(selectedAnchor)) {
            // 选中的锚点已经不在新快照里了。走 setSelection 而不是直接清字段：它顺手把过滤器
            // 镜像一起刷新了。
            setSelection(null, selectedRoute);
        } else {
            refreshFilterMirror();
        }
    }

    /** 当前网络在该归属者网络列表里的下标（0 起）。 */
    public int getNetworkIndex() {
        return networkIndex;
    }

    /** 该归属者名下共有几张网络。 */
    public int getNetworkCount() {
        return networkCount;
    }

    /**
     * 界面切换选中锚点 / 线路。
     *
     * <p>纯客户端状态：右边配置区显示谁的配置，以及过滤槽镜像的是谁，都由它决定。
     * 服务端不需要知道——搬运引擎按存档里的网络配置自己跑，与玩家在看哪个容器无关。</p>
     */
    public void setSelection(@Nullable GlobalPos anchor, int route) {
        int clamped = Math.max(0, Math.min(route, StaffLinkNetwork.ROUTE_COUNT - 1));
        this.selectedAnchor = anchor;
        this.selectedRoute = clamped;
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
        // 应力线路的两个数值是「目标转速 / 旋转方向」，语义与别的介质不同，初值也要跟着换；
        // 沿用 16 / 5 的话，那个 5 会被服务端当成非法方向收敛成 1，玩家会看到配置被悄悄改掉。
        boolean stress = medium.family() == ResourceFamily.STRESS;
        // 气压线路的「数量」是「目标气压（毫巴）」：默认 2 bar，同样不能沿用 16（那是 0.016 bar）。
        boolean pressure = medium.family() == ResourceFamily.PRESSURE;
        // 新线路默认关闭，由玩家显式打开。
        return new StaffLinkRoute(anchor, route, false, flow, medium,
                stress ? StaffLinkRoute.STRESS_DEFAULT_RPM
                        : pressure ? StaffLinkRoute.PRESSURE_DEFAULT_MBAR : 16,
                stress ? StaffLinkRoute.STRESS_CLOCKWISE : 5,
                null, 0, List.of());
    }

    /** 当前网络名（没起过名时为空串）。 */
    public String getNetworkName() {
        return snapshot == null ? "" : snapshot.name();
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

    /**
     * 写入/覆盖配置，并提交给服务端。
     *
     * <p>多选为空时只写 {@code route} 自己那一条；多选非空时写到所有被选锚点的同号线路上。
     * 具体写法见 {@link #applyRouteTo}。</p>
     */
    public void applyRoute(StaffLinkRoute route) {
        applyRouteTo(getEditTargets(), route);
    }

    /**
     * 把一条配置写到给定的一批锚点上。
     *
     * <p>目标已有该线路配置时走 {@link #patch}，没有时用 {@link #copyFor} 整条补上。</p>
     *
     * <p><b>语义是整条覆盖</b>：{@code patch(base, edited)} 逐字段取「与 edited 不同就采用 edited」，
     * 而相等的字段取谁都一样，所以结果恒等于 {@code edited} 的字段——即目标的介质、方向、
     * 过滤器等都会被刷成 {@code edited} 的值。批量改「流量」时，各机原本不同的介质/方向
     * 会一起被抹平，这是有意的：批量编辑的期望就是「让这批机器一致」。</p>
     *
     * <p>本方法<b>不读取也不修改</b> {@link #multiSelection}，所以「应用到全部」可以传一份
     * 临时目标列表进来，不会改变玩家当前的多选状态。</p>
     */
    public void applyRouteTo(List<GlobalPos> targets, StaffLinkRoute route) {
        for (GlobalPos target : targets) {
            StaffLinkRoute existing = snapshot == null ? null : snapshot.routeAt(target, route.route());
            StaffLinkRoute written = existing == null
                    ? copyFor(target, route)
                    : patch(existing, route);
            if (snapshot != null) {
                snapshot.putRoute(written);
            }
            if (clientSide) {
                PacketDistributor.sendToServer(new StaffLinkConfigurePacket(
                        target, written.route(), written));
            }
        }
        refreshFilterMirror();
    }

    /** 把一条配置原样搬到另一个锚点上（用于给还没有配置的目标补默认值）。 */
    private static StaffLinkRoute copyFor(GlobalPos anchor, StaffLinkRoute route) {
        return new StaffLinkRoute(anchor, route.route(), route.enabled(), route.flow(), route.medium(),
                route.amount(), route.interval(), route.side(), route.weight(),
                route.filter());
    }

    /**
     * 逐字段取「与 {@code edited} 不同的那个值」。
     *
     * <p>注意：相等的字段取 {@code base} 还是 {@code edited} 结果一样，所以本方法实际上
     * 等价于<b>整条覆盖</b>（只保留 {@code base} 的锚点与线路号）。保留这个写法是为了
     * 与 {@link #copyFor} 共用同一个「逐字段构造」的形状，别指望它能实现字段级 diff。</p>
     */
    private static StaffLinkRoute patch(StaffLinkRoute base, StaffLinkRoute edited) {
        return new StaffLinkRoute(
                base.anchor(), base.route(),
                base.enabled() != edited.enabled() ? edited.enabled() : base.enabled(),
                base.flow() != edited.flow() ? edited.flow() : base.flow(),
                base.medium() != edited.medium() ? edited.medium() : base.medium(),
                base.amount() != edited.amount() ? edited.amount() : base.amount(),
                base.interval() != edited.interval() ? edited.interval() : base.interval(),
                base.side() != edited.side() ? edited.side() : base.side(),
                base.weight() != edited.weight() ? edited.weight() : base.weight(),
                base.filter().equals(edited.filter()) ? base.filter() : edited.filter());
    }

    /**
     * 只写一个锚点的配置，不参与批量。
     *
     * <p>给「刚选中一台还没配过的机器、顺手补一条默认配置」用。这种补默认值的动作必须
     * 限定在单选那一台上：否则多选期间点一下别的机器，就会把一堆默认值糊到所有选中的机器上。</p>
     */
    public void applyRouteSingle(GlobalPos anchor, StaffLinkRoute route) {
        if (snapshot != null) {
            snapshot.putRoute(route);
        }
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkConfigurePacket(anchor, route.route(), route));
        }
    }

    // ------------------------------------------------------------------ 多选

    /** 当前多选出来的锚点（只读视图）。 */
    public List<GlobalPos> getMultiSelection() {
        return List.copyOf(multiSelection);
    }

    public boolean isMultiSelected(GlobalPos anchor) {
        return multiSelection.contains(anchor);
    }

    /** 点一下加/减一个锚点；被减掉的正好是单选那个时，把单选让给集合里的下一个。 */
    public void toggleMultiSelection(GlobalPos anchor) {
        if (!multiSelection.remove(anchor)) {
            multiSelection.add(anchor);
        }
        if (selectedAnchor != null && !multiSelection.isEmpty()
                && !multiSelection.contains(selectedAnchor)) {
            selectedAnchor = multiSelection.get(0);
            refreshFilterMirror();
        }
    }

    public void clearMultiSelection() {
        multiSelection.clear();
    }

    /**
     * 直接把批量选择集合替换成给定的一组锚点（「全选」/ Shift 范围选择用）。
     *
     * <p>去重；若当前单选锚点不在新集合里（或还没有单选），把单选让给集合的第一个，
     * 保证配置区显示的永远是集合内的机器。</p>
     */
    public void setMultiSelection(List<GlobalPos> anchors) {
        multiSelection.clear();
        for (GlobalPos anchor : anchors) {
            if (anchor != null && !multiSelection.contains(anchor)) {
                multiSelection.add(anchor);
            }
        }
        if (!multiSelection.isEmpty()
                && (selectedAnchor == null || !multiSelection.contains(selectedAnchor))) {
            selectedAnchor = multiSelection.get(0);
            refreshFilterMirror();
        }
    }

    /** 解绑时把它从多选里一并摘掉，免得集合里留着一个不存在的锚点。 */
    private void forgetMultiSelection(GlobalPos anchor) {
        multiSelection.remove(anchor);
    }

    /**
     * 一次编辑要落到哪些锚点上。
     *
     * <p>多选为空 = 只改单选那个；多选非空 = 改所有被选的。多选里如果没包含单选锚点，
     * 单选那个不参与批量（界面上的配置区显示的是它，但改动只发给被选中的容器）。</p>
     */
    public List<GlobalPos> getEditTargets() {
        if (!multiSelection.isEmpty()) {
            return List.copyOf(multiSelection);
        }
        return selectedAnchor == null ? List.of() : List.of(selectedAnchor);
    }

    /** 改一个过滤器槽；没有选中配置或该线路不用过滤器时忽略。 */
    public void setFilterSlot(int index, LinkFilterSlot slot) {
        StaffLinkRoute config = getSelectedConfig();
        if (config == null || !config.filterApplies()) {
            return;
        }
        if (index < 0 || index >= StaffLinkRoute.FILTER_LIMIT) {
            return;
        }
        List<LinkFilterSlot> filter = new ArrayList<>(config.filter());
        filter.set(index, slot == null ? LinkFilterSlot.EMPTY : slot);
        applyRoute(config.withFilter(filter));
    }

    /**
     * 改一个过滤器槽的「包含 / 排除」方向，标记与条件不动。
     *
     * <p>排除 = 黑名单：这一格标记命中的资源<b>不搬</b>，其余照搬。</p>
     */
    public void setFilterSlotExclude(int index, boolean exclude) {
        editFilterSlot(index, slot -> slot.withExclude(exclude));
    }

    /**
     * 改一个过滤器槽的一条控制条件。
     *
     * @param input {@code true} = 输入端条件（在目标容器上测量）；{@code false} = 输出端条件（在源容器上测量）
     */
    public void setFilterSlotCondition(int index, boolean input, LinkFilterCondition condition) {
        editFilterSlot(index, slot -> input
                ? slot.withConditions(slot.outCond(), condition)
                : slot.withConditions(condition, slot.inCond()));
    }

    /**
     * 改一条控制条件的「控制材料 B」。
     *
     * <p>传一格的标记：物品 / 流体直接当 B；空标记 = 清掉 B（回到「测 A 自身」）。方向与数值保留。
     * 模式（{@code #tag}）不能当控制材料，忽略。</p>
     */
    public void setFilterSlotConditionControl(int index, boolean input, LinkFilterSlot marker) {
        editFilterSlot(index, slot -> {
            LinkFilterCondition current = input ? slot.inCond() : slot.outCond();
            LinkFilterCondition updated = applyControl(current, marker);
            return input
                    ? slot.withConditions(slot.outCond(), updated)
                    : slot.withConditions(updated, slot.inCond());
        });
    }

    /** 通用：改一个过滤格；没有选中配置或该线路不用过滤器时忽略。 */
    private void editFilterSlot(int index, java.util.function.UnaryOperator<LinkFilterSlot> change) {
        StaffLinkRoute config = getSelectedConfig();
        if (config == null || !config.filterApplies()) {
            return;
        }
        if (index < 0 || index >= StaffLinkRoute.FILTER_LIMIT) {
            return;
        }
        List<LinkFilterSlot> filter = new ArrayList<>(config.filter());
        LinkFilterSlot current = index < filter.size() ? filter.get(index) : LinkFilterSlot.EMPTY;
        filter.set(index, change.apply(current));
        applyRoute(config.withFilter(filter));
    }

    /** 把控制材料 B 换成一格标记里的物品 / 流体；标记为空 ⇒ 清掉 B。 */
    private static LinkFilterCondition applyControl(LinkFilterCondition cond, LinkFilterSlot marker) {
        if (marker == null || marker.isEmpty()) {
            return cond.withoutControl();
        }
        if (marker.isFluid()) {
            return cond.withControl(marker.fluid());
        }
        if (marker.isItem()) {
            return cond.withControl(marker.item());
        }
        // 模式不支持当控制材料：保持原样。
        return cond;
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
            // 走 setSelection 而不是直接改字段：它顺手把过滤器镜像一起刷新了。
            setSelection(null, selectedRoute);
        } else {
            refreshFilterMirror();
        }
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkDetachPacket(anchor));
        }
    }

    /**
     * 客户端：把某个锚点挪到列表里的指定位置。
     *
     * <p>本地不先改：服务端改完会回发整网快照，以它为准，避免两边次序短暂不一致。</p>
     *
     * @param targetIndex 目标位置（在完整锚点列表里的下标）
     */
    public void moveAnchorTo(GlobalPos anchor, int targetIndex) {
        if (clientSide) {
            PacketDistributor.sendToServer(new StaffLinkReorderPacket(anchor, targetIndex));
        }
    }

    private void refreshFilterMirror() {
        StaffLinkRoute config = getSelectedConfig();
        if (config == null) {
            filterMirror = emptyFilter();
            return;
        }
        List<LinkFilterSlot> filter = new ArrayList<>(StaffLinkRoute.FILTER_LIMIT);
        List<LinkFilterSlot> source = config.filter();
        for (int index = 0; index < StaffLinkRoute.FILTER_LIMIT; index++) {
            filter.add(index < source.size() ? source.get(index) : LinkFilterSlot.EMPTY);
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
        // 网络必须还在，而且必须挂在这名玩家（或他队伍）名下；否则界面该关掉。
        return player instanceof ServerPlayer serverPlayer
                && StaffLinkManager.canAccess(server, serverPlayer, networkId);
    }
}
