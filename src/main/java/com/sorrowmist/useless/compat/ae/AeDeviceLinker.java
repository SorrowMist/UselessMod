package com.sorrowmist.useless.compat.ae;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.util.Platform;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.world.ae.AeConnectLinkSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * 把「拥有 AE 网格节点」的机器并进工具绑定的那张网。
 *
 * <p>走的是 AE2 自己在量子网络桥 / P2P 上用的那条路：
 * {@code GridHelper.createConnection(a, b)} 传 {@code null} 方向 ⇒ 这条连接不是「世界内连接」
 * ({@code isInWorld() == false})，而 {@code InWorldGridNode#cleanupConnections()} 明确跳过非空间连接，
 * 所以它不会被 AE2 的邻居重扫清掉；与此同时 {@code GridConnection#mergeGrids} 会把两张网格合成一张。</p>
 *
 * <p>几个必须记住的副作用：</p>
 * <ul>
 *   <li>连接按「一根线」算通道（{@code getMaxChannels() = 32 × cableCapacityFactor}）；</li>
 *   <li>AE2 不把连接写进存档，所以登记表 + {@link #ensureLinks} 是唯一的续命手段；</li>
 *   <li>同一对节点只能有一条连接，重复建会抛 {@link IllegalStateException}，判重必须遍历
 *       {@code getConnections()} 而不是自己维护标志位；</li>
 *   <li>AE2 的网格节点销毁后不可重建，所以这里只创建/销毁<b>连接</b>，绝不碰别人的节点。</li>
 *   <li>断开只认「登记表里那条 + 非空间 + 另一端正好是这台机器」的连接：
 *       世界内的线缆连接（{@code isInWorld() == true}）和其它模组建的连接一律不动。
 *       已登记的连接必然由本模组建立：机器若在登记时已带有连接，则不会进入登记表。</li>
 * </ul>
 */
public final class AeDeviceLinker {
    private AeDeviceLinker() {
    }

    /** 方块是不是「能连入 AE 网络」的机器：注册了 AE2 的网格节点宿主能力即可（本模组机器 + AE2 设备 + 其它模组 ME 设备）。 */
    public static boolean isLinkTarget(Level level, BlockPos pos) {
        if (!ModList.get().isLoaded("ae2")) {
            return false;
        }
        return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null) != null;
    }

    /**
     * 右键入口：已登记过的断开，没登记的接入。
     *
     * <p>目标必须是「当前不属于任何网络」的机器——节点已经带着连接的话说明它已经在别人网里了，
     * 这时直接拒绝，避免把两张网误并成一张。</p>
     */
    public static void toggle(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos) {
        BlockPos machinePos = pos.immutable();
        GlobalPos bound = tool.get(UComponents.WIRELESS_LINK_TARGET.get());
        if (bound == null) {
            notify(player, "gui.useless_mod.ae_connect.not_bound", ChatFormatting.YELLOW);
            return;
        }
        if (bound.dimension().equals(level.dimension()) && bound.pos().equals(machinePos)) {
            notify(player, "gui.useless_mod.ae_connect.access_point", ChatFormatting.YELLOW);
            return;
        }

        AeConnectLinkSavedData data = AeConnectLinkSavedData.get(level.getServer());
        AeConnectLinkSavedData.Link link = new AeConnectLinkSavedData.Link(
                level.dimension(), bound.dimension(), bound.pos(), machinePos);

        // 已登记过 ⇒ 断开。断开不要求访问点在线（它卸载时 AE2 已经把连接连带销毁了），
        // 而且只会拆「我们自己建的那条非空间连接」：线缆连接和其它模组的无线连接都原样保留。
        if (data.contains(link)) {
            IGridNode accessNode = resolveAccessNode(level, bound);
            IInWorldGridNodeHost host = level.getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, machinePos, null);
            IGridNode machineNode = host == null ? null : resolveNode(host);
            if (accessNode != null && machineNode != null) {
                destroyLink(accessNode, machineNode);
            }
            data.remove(link);
            display(player, level, machinePos, "gui.useless_mod.ae_connect.unlinked", ChatFormatting.GREEN);
            chime(level, machinePos, false);
            return;
        }

        IGridNode accessNode = requireAccessNode(player, level, bound);
        if (accessNode == null) {
            return;
        }

        IInWorldGridNodeHost host = level.getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, machinePos, null);
        IGridNode machineNode = host == null ? null : resolveNode(host);
        if (machineNode == null) {
            notify(player, "gui.useless_mod.ae_connect.not_ready", ChatFormatting.YELLOW);
            return;
        }

        NetworkState state = networkState(machineNode);
        if (state == NetworkState.UNKNOWN) {
            notify(player, "gui.useless_mod.ae_connect.not_ready", ChatFormatting.YELLOW);
            return;
        }
        if (state == NetworkState.ALREADY_NETWORKED) {
            display(player, level, machinePos, "gui.useless_mod.ae_connect.already_networked", ChatFormatting.RED);
            return;
        }

        if (!createLink(accessNode, machineNode)) {
            notify(player, "gui.useless_mod.ae_connect.not_ready", ChatFormatting.YELLOW);
            return;
        }
        data.add(link);
        display(player, level, machinePos, "gui.useless_mod.ae_connect.linked", ChatFormatting.GREEN);
        chime(level, machinePos, true);
    }

    /** 某个维度下、某台访问点已经连了哪些机器（客户端渲染连接提示用）。 */
    public static List<BlockPos> linkedMachines(ServerLevel level,
                                                ResourceKey<Level> accessPointDimension,
                                                BlockPos accessPoint) {
        return AeConnectLinkSavedData.get(level.getServer())
                .machinesOf(level.dimension(), accessPointDimension, accessPoint);
    }

    /**
     * 目标现在算不算「已经进了别的网」。
     *
     * <p>判据只看「这张网是不是一张真正在用的网」，不看网格里有几个节点 ——
     * <b>线缆与终端本身就是独立节点</b>（线缆的 owner 是 {@code CablePart}、终端是
     * {@code AbstractTerminalPart}，都是部件而非方块实体），所以按「有别的节点就拒绝」会把
     * 「目标身上挂了根线」也误判成「已接入别的网络」，且与供电状态无关。</p>
     *
     * <p>现行判据只有两条：① 网格中存在控制器（<b>离线亦计入</b>，并入后会产生「多控制器冲突」导致两张网同时失效）；
     * ② 整网已通电（{@code isPowered()} 即判断网格是否供电）。其余情况一律允许并入，
     * 目标身上的线缆 / 终端会一并接入 —— 这正是「连进来」的本意。</p>
     *
     * <p>历史问题：cluster 型多方块（AE2 / 高级AE 的合成 CPU、量子计算机）的部件之间天然存在连接，
     * 曾因此误报「已接入其它网络」；现行判据不再依据节点数量，该误报不再出现。</p>
     */
    private static NetworkState networkState(IGridNode machineNode) {
        IGrid grid;
        try {
            grid = machineNode.getGrid();
        } catch (Throwable notReady) {
            // 节点尚未完成初始化（getGrid 抛出 IllegalStateException），本轮无法判定。
            return NetworkState.UNKNOWN;
        }

        // ① 目标网络存在控制器 ⇒ 一律拒绝（**离线控制器亦计入**）：
        //    AE2 单个网络只允许一个控制器，并入后会产生「多控制器冲突」，导致两张网同时失效。
        if (grid.getMachineNodes(ControllerBlockEntity.class).iterator().hasNext()) {
            return NetworkState.ALREADY_NETWORKED;
        }

        // ② 整网已通电并处于运行状态 ⇒ 视为正在使用的网络，不并入。
        if (machineNode.isPowered()) {
            return NetworkState.ALREADY_NETWORKED;
        }

        // 其余情况（仅挂有线缆 / 终端 / 部件且未通电）⇒ 允许并入。
        return NetworkState.STANDALONE;
    }

    /** 静默解析访问点节点：没加载 / 方块不对 / 离线都只返回 null。 */
    @Nullable
    private static IGridNode resolveAccessNode(ServerLevel level, GlobalPos bound) {
        ServerLevel accessLevel = level.getServer().getLevel(bound.dimension());
        if (accessLevel == null || !accessLevel.isLoaded(bound.pos())
                || !(accessLevel.getBlockEntity(bound.pos()) instanceof WirelessAccessPointBlockEntity ap)) {
            return null;
        }
        IGridNode node = ap.getMainNode().getNode();
        if (node == null || !ap.getMainNode().isOnline() || ap.getGrid() == null) {
            return null;
        }
        return node;
    }

    /** 连接前必须有一个在线的访问点，失败时把原因说清楚。 */
    @Nullable
    private static IGridNode requireAccessNode(ServerPlayer player, ServerLevel level, GlobalPos bound) {
        ServerLevel accessLevel = level.getServer().getLevel(bound.dimension());
        if (accessLevel == null || !accessLevel.isLoaded(bound.pos())) {
            notify(player, "gui.useless_mod.ae_connect.access_point_unloaded", ChatFormatting.RED);
            return null;
        }
        if (!(accessLevel.getBlockEntity(bound.pos()) instanceof WirelessAccessPointBlockEntity accessPoint)) {
            notify(player, "gui.useless_mod.ae_connect.invalid_access_point", ChatFormatting.RED);
            return null;
        }
        IGridNode node = accessPoint.getMainNode().getNode();
        if (node == null || !accessPoint.getMainNode().isOnline() || accessPoint.getGrid() == null) {
            notify(player, "gui.useless_mod.ae_connect.access_point_offline", ChatFormatting.RED);
            return null;
        }
        return node;
    }

    /**
     * 低频自愈：AE2 不保存连接，区块或存档重载后要把登记过的连接重建回来。
     * 机器/访问点已不存在时，一并清除失效登记。
     */
    public static void ensureLinks(MinecraftServer server) {
        AeConnectLinkSavedData data = AeConnectLinkSavedData.get(server);
        if (!data.isEmpty()) {
            for (AeConnectLinkSavedData.Link link : data.snapshot()) {
                // 访问点与机器可能在不同维度，两边都要按各自的维度解析。
                ServerLevel accessLevel = server.getLevel(link.accessPointDimension());
                ServerLevel machineLevel = server.getLevel(link.machineDimension());
                if (accessLevel == null || machineLevel == null) {
                    continue;
                }
                if (!accessLevel.isLoaded(link.accessPoint()) || !machineLevel.isLoaded(link.machine())) {
                    continue;
                }

                BlockEntity accessEntity = accessLevel.getBlockEntity(link.accessPoint());
                if (!(accessEntity instanceof WirelessAccessPointBlockEntity accessPoint)) {
                    data.removeByAccessPoint(link.accessPointDimension(), link.accessPoint());
                    continue;
                }

                IInWorldGridNodeHost host = machineLevel.getCapability(
                        AECapabilities.IN_WORLD_GRID_NODE_HOST, link.machine(), null);
                if (host == null) {
                    data.removeByMachine(link.machineDimension(), link.machine());
                    continue;
                }

                IGridNode machineNode = resolveNode(host);
                IGridNode accessNode = accessPoint.getMainNode().getNode();
                if (machineNode == null || accessNode == null || !accessPoint.getMainNode().isOnline()) {
                    // 节点尚未就绪（AE2 将节点创建推迟到首个 tick），留待下一轮重试，避免误删登记。
                    continue;
                }

                createLink(accessNode, machineNode);
            }
        }

        // 豁免索引按节点身份查询：AE2 更换节点后旧条目无法再匹配，但会持续持有引用，
        // 且每次通道重算都会遍历一次。因此在该低频周期中一并清理失效条目。
        AeLinkChannelBypass.pruneStaleLinks();
    }

    /**
     * 取宿主的网格节点。
     *
     * <p>三条路径按优先级依次尝试，前一条拿到就返回：</p>
     *
     * <ol>
     *   <li><b>契约路径</b>：{@code getGridNode(null)} —— 方块实体自身的主节点，
     *       以及挂在 CableBus <b>中心位</b>的线缆（{@code CableBusContainer.getGridNode(null)}
     *       会回退到 {@code storage.getCenter()}）。</li>
     *   <li><b>对外朝向节点</b>：逐个方向问 {@code getGridNode(side)}。只有重写了
     *       {@code IPart.getExternalFacingNode()} 的部件才在这条路上有值 ——
     *       目前是 P2P 隧道、石英纤维、开关总线三类。</li>
     *   <li><b>侧挂部件自身节点</b>（新增）：终端、各类总线、接口这些普通部件
     *       <b>没有</b>重写 {@code getExternalFacingNode()}（接口默认返回 null），
     *       它们的真实节点只在 {@code IPart.getGridNode()} 上。这类部件通常挂在 CableBus
     *       <b>侧面</b>，若该方块又没有中心线缆，前两条路径会一路 fallthrough 返回 null，
     *       表现为「孤立终端 / 孤立总线连不上」。这里直接遍历部件补回。</li>
     * </ol>
     */
    @Nullable
    private static IGridNode resolveNode(IInWorldGridNodeHost host) {
        // ① 契约路径：方块实体主节点 / 中心线缆。
        try {
            IGridNode node = host.getGridNode(null);
            if (node != null) {
                return node;
            }
        } catch (Throwable ignored) {
        }

        // ② 对外朝向节点：P2P 隧道 / 石英纤维 / 开关总线。
        for (Direction direction : Direction.values()) {
            try {
                IGridNode node = host.getGridNode(direction);
                if (node != null) {
                    return node;
                }
            } catch (Throwable ignored) {
            }
        }

        // ③ 侧挂部件自身节点：终端 / 各类总线 / 接口。
        //    这些部件没重写 getExternalFacingNode()，CableBus 的 getGridNode(side)
        //    会在 ① 里返回 null，只能从部件本身取。
        if (host instanceof IPartHost partHost) {
            IGridNode firstAny = null;
            for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
                try {
                    IPart part = partHost.getPart(side);
                    if (part == null) {
                        continue;
                    }
                    IGridNode node = part.getGridNode();
                    if (node == null) {
                        continue;
                    }
                    // 优先返回已就绪（已挂上网格）的节点；未就绪的暂存，
                    // 若整个方块尚未完成初始化，则由 ensureLinks() 在下一轮重试。
                    if (isNodeReady(node)) {
                        return node;
                    }
                    if (firstAny == null) {
                        firstAny = node;
                    }
                } catch (Throwable ignored) {
                }
            }
            return firstAny;
        }

        return null;
    }

    /** 节点是否已经挂上网格：未 create 的节点 {@code getGrid()} 会抛异常。 */
    private static boolean isNodeReady(IGridNode node) {
        try {
            return node.getGrid() != null;
        } catch (Throwable notReady) {
            return false;
        }
    }

    static boolean createLink(IGridNode accessNode, IGridNode machineNode) {
        if (hasConnection(accessNode, machineNode)) {
            // 连接已存在（自愈重跑或重复触发）：仍需补登记通道豁免索引，避免遗漏。
            AeLinkChannelBypass.register(accessNode, machineNode);
            return true;
        }
        try {
            GridHelper.createConnection(accessNode, machineNode);
        } catch (IllegalStateException alreadyConnected) {
            boolean connected = hasConnection(accessNode, machineNode);
            if (connected) {
                AeLinkChannelBypass.register(accessNode, machineNode);
            }
            return connected;
        }
        AeLinkChannelBypass.register(accessNode, machineNode);
        return true;
    }

    /** 只拆我们自己建的非空间连接，绝不动世界内的线缆连接。 */
    static boolean destroyLink(IGridNode accessNode, IGridNode machineNode) {
        // 先注销通道豁免，再拆连接：避免重算通道时还按「这条连接在」来豁免。
        AeLinkChannelBypass.unregister(accessNode, machineNode);
        boolean destroyed = false;
        for (IGridConnection connection : List.copyOf(accessNode.getConnections())) {
            if (connection.isInWorld() || connection.getOtherSide(accessNode) != machineNode) {
                continue;
            }
            connection.destroy();
            destroyed = true;
        }
        return destroyed;
    }

    private static boolean hasConnection(IGridNode a, IGridNode b) {
        for (IGridConnection connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }

    /** 目标机器的网络归属判定结果。 */
    private enum NetworkState {
        /** 没有控制器、也未通电（至多连接线缆 / 终端）：可以并入工具绑定的网络。 */
        STANDALONE,
        /** 有控制器、或整网已经在跑：拒绝，避免把两张网误并成一张。 */
        ALREADY_NETWORKED,
        /** 节点尚未就绪，这一轮判断不了。 */
        UNKNOWN
    }

    private static void display(ServerPlayer player, ServerLevel level, BlockPos pos,
                               String key, ChatFormatting style) {
        player.displayClientMessage(
                Component.translatable(key, pos.toShortString()).withStyle(style), true);
    }

    private static void notify(ServerPlayer player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    private static void chime(ServerLevel level, BlockPos pos, boolean linked) {
        level.playSound(null, pos,
                linked ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 0.7F, linked ? 1.4F : 0.8F);
    }
}
