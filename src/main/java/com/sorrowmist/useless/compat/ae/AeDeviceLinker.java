package com.sorrowmist.useless.compat.ae;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
 *       已登记的连接必然是我们建的——机器当时若已经带着连接，压根不会被登记。</li>
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
     * <p><b>不能直接看 {@code getConnections()} 是否为空</b>：cluster 型多方块（AE2 / 高级AE 的合成 CPU、
     * 量子计算机、空间塔）的部件<b>本身</b>就带着节点连接（结构内部部件之间），所以一座完全孤立的多方块
     * 也会「有连接」——这正是「无论是否已接入网络都提示已接入其它网络」的根因。</p>
     *
     * <p>改成看整张网：用 AE2 自己的 {@link IGridMultiblock} 服务取出「本节点所属结构的全部节点」，
     * 网格里还存在不属于这个集合的节点，才算真的接进了别人的网（线缆、控制器、存储、别的机器……）。</p>
     */
    private static NetworkState networkState(IGridNode machineNode) {
        IGrid grid;
        Set<IGridNode> own;
        try {
            grid = machineNode.getGrid();
            own = structureNodes(machineNode);
        } catch (Throwable notReady) {
            // 节点还没初始化完（getGrid 会抛 IllegalStateException），这一轮判断不了。
            return NetworkState.UNKNOWN;
        }
        Object owner = machineNode.getOwner();
        for (IGridNode other : grid.getNodes()) {
            // 同一个方块实体自己的其它节点（有些机器不止一个节点）也算「自带」，不算别人。
            if (other == machineNode || other.getOwner() == owner || own.contains(other)) {
                continue;
            }
            return NetworkState.ALREADY_NETWORKED;
        }
        return NetworkState.STANDALONE;
    }

    /** 本体「自带」的节点：多方块结构返回整座结构的所有节点，普通机器就是它自己。 */
    private static Set<IGridNode> structureNodes(IGridNode machineNode) {
        Set<IGridNode> own = Collections.newSetFromMap(new IdentityHashMap<>());
        own.add(machineNode);
        IGridMultiblock multiblock = machineNode.getService(IGridMultiblock.class);
        if (multiblock != null) {
            Iterator<IGridNode> nodes = multiblock.getMultiblockNodes();
            while (nodes.hasNext()) {
                own.add(nodes.next());
            }
        }
        return own;
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
     * 机器/访问点已经不存在时顺手把死登记清掉。
     */
    public static void ensureLinks(MinecraftServer server) {
        AeConnectLinkSavedData data = AeConnectLinkSavedData.get(server);
        if (data.isEmpty()) {
            return;
        }

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
                // 节点还没就绪（AE2 会把建节点推迟到首个 tick），等下一轮再试，别误删登记。
                continue;
            }

            createLink(accessNode, machineNode);
        }
    }

    /**
     * 取宿主的网格节点。契约允许 {@code getGridNode(null)}，但第三方实现未必守规矩，
     * 所以退回逐个方向问一遍。
     */
    @Nullable
    private static IGridNode resolveNode(IInWorldGridNodeHost host) {
        try {
            IGridNode node = host.getGridNode(null);
            if (node != null) {
                return node;
            }
        } catch (Throwable ignored) {
        }
        for (Direction direction : Direction.values()) {
            try {
                IGridNode node = host.getGridNode(direction);
                if (node != null) {
                    return node;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static boolean createLink(IGridNode accessNode, IGridNode machineNode) {
        if (hasConnection(accessNode, machineNode)) {
            // 连接已经在了（自愈重跑 / 重复触发）：通道豁免索引也要补登记，别漏。
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
        /** 孤立节点 / 孤立多方块：可以并入工具绑定的网络。 */
        STANDALONE,
        /** 网格里已经有本结构之外的东西：拒绝，避免把两张网误并成一张。 */
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
