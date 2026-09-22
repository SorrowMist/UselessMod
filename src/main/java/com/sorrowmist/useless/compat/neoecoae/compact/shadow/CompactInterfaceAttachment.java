package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.IManaged;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactInterfaceAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

/**
 * 紧凑方块的「通讯接口界面」全部接线，由三个宿主方块实体各持有一个实例。
 *
 * <p>它把 {@link ShadowInterfaceBlockEntity} 的生命周期、菜单分派、LDLib2 包转发与
 * 存档桥接收在一处；宿主只需要覆写几个入口并把调用转过来。</p>
 *
 * <p><b>包为什么要宿主转发</b>：LDLib2 的两类包都按 {@code (pos, BlockEntityType)} 投递，
 * 而世界在宿主坐标上只会给出宿主自己，所以接收端拿到包之后必须再交给影子。</p>
 */
public final class CompactInterfaceAttachment<C extends NECluster<C>> {

    /** 影子 @Persisted 状态在宿主存档里的子标签名。 */
    private static final String PERSIST_KEY = "useless_compact_interface";

    private final BlockEntity host;
    private final IManaged hostManaged;
    private final NEClusterCalculator.Factory<C> calculator;
    private final ShadowNodeLink nodeLink;

    @Nullable
    private ShadowInterfaceBlockEntity<C> shadow;
    @Nullable
    private C joinedCluster;

    public CompactInterfaceAttachment(BlockEntity host, NEClusterCalculator.Factory<C> calculator) {
        this.host = host;
        this.hostManaged = (IManaged) host;
        this.calculator = calculator;
        this.nodeLink = new ShadowNodeLink(host);
    }

    // ---------------------------------------------------------------- 菜单分派

    /**
     * 伪装分支可用时返回通讯接口界面；否则返回 null，由宿主回退主机面板。
     *
     * <p>分支依据只有 {@code holder.blockState} 这一个两端都一样的值，所以两端 UI 树必然同构。</p>
     */
    @Nullable
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (!CompactInterfaceAccess.requested(holder.blockState)) {
            return null;
        }
        ShadowInterfaceBlockEntity<C> target = shadow();
        return target.supportsInterfaceUi() ? target.createUI(holder) : null;
    }

    // ---------------------------------------------------------------- 成形 / 拆除

    /** 服务端成形后调用：让影子认宿主所在的集群，并把它接上宿主的网格节点。 */
    public void attach(ServerLevel serverLevel, C cluster, IManagedGridNode node) {
        ShadowInterfaceBlockEntity<C> target = shadow();
        if (target.compactCluster() != cluster) {
            target.updateCluster(cluster);
        }
        // 集群里那些「按类型找接口」的逻辑（存储集群的 theInterface）只认成员表，
        // 而影子是手工造的，得自己进这张表。
        if (joinedCluster != cluster) {
            cluster.addBlockEntity(target);
            joinedCluster = cluster;
        }
        nodeLink.attach(target.getMainNode(), serverLevel, node);
    }

    /**
     * 重新装配前调用：只解除与旧集群的登记。
     *
     * <p>影子对象必须留着——它的 {@code @Persisted} 状态（接口模式、模糊过滤器）在存档恢复时
     * 就灌进去了，而 AE2 的网格节点一旦销毁就<b>不能重建</b>（{@code ManagedGridNode} 的初始化数据
     * 已被消费）。重新装配用的还是同一个宿主节点，连接原封不动即可。</p>
     */
    public void release() {
        joinedCluster = null;
    }

    /** 拆方块 / 卸载区块时调用：连影子节点一起摘掉，避免留下幽灵机器。 */
    public void dispose() {
        nodeLink.detach();
        shadow = null;
        joinedCluster = null;
    }

    /** 宿主每 tick 驱动一次：补建网格节点、把影子的脏字段推给客户端、再跑影子自己的 tick。 */
    public void tick() {
        ShadowInterfaceBlockEntity<C> target = shadow;
        if (target == null) {
            return;
        }
        nodeLink.tick();
        target.getRootStorage().requireInit();
        if (target.getRootStorage().hasDirtySyncFields()) {
            target.sync(false);
        }
        target.tick();
    }

    // ---------------------------------------------------------------- LDLib2 包转发

    /** @return true 表示这一包属于影子接口、已由它处理，宿主不要再处理 */
    public boolean forwardRpc(RPCSender sender, byte[] data) {
        ShadowInterfaceBlockEntity<C> target = shadow;
        if (target == null) {
            return false;
        }
        String method = rpcMethodName(data);
        // 包体里只有方法名，所以「谁的方法名」就是唯一的判据。宿主自己的 RPC 只有 L9 的
        // setEcoMegaFilter，与接口界面的五个方法不相交（已按运行时 21.2.0-beta1 逐个核对），
        // 因此这里不会把接口界面的交互错投给主机面板。
        if (hasRpc(hostManaged, method) || !hasRpc(target, method)) {
            return false;
        }
        target.handleRPCPacket(sender, data);
        return true;
    }

    /** @return true 表示这一包是影子发出的字段同步，宿主不要再处理（下标空间不同，错处理会写坏字段） */
    public boolean forwardSync(RegistryAccess access, BitSet changed, byte[] data, CompoundTag extra) {
        if (!extra.getBoolean(ShadowInterfaceBlockEntity.SYNC_MARKER)) {
            return false;
        }
        shadow().handleSyncPacket(access, changed, data, extra);
        return true;
    }

    /** RPC 包体 = {@code varInt(managed 下标) + utf(方法名) + 参数}，这里只需要方法名。 */
    private static String rpcMethodName(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            buf.readVarInt();
            return buf.readUtf();
        } finally {
            buf.release();
        }
    }

    private static boolean hasRpc(IManaged owner, String method) {
        return owner.getFieldHolder().getRpcMethodMap().containsKey(method);
    }

    // ---------------------------------------------------------------- 存档桥接

    /** 影子的 @Persisted 状态（存储接口模式、63 格模糊过滤器）代存在宿主自己的存档里。 */
    public void save(HolderLookup.Provider provider, CompoundTag tag) {
        if (shadow == null) {
            return;
        }
        CompoundTag sub = new CompoundTag();
        shadow.saveManagedPersistentData(provider, sub, false);
        tag.put(PERSIST_KEY, sub);
    }

    public void load(HolderLookup.Provider provider, CompoundTag tag) {
        if (!tag.contains(PERSIST_KEY)) {
            return;
        }
        shadow().loadManagedPersistentData(provider, tag.getCompound(PERSIST_KEY));
    }

    // ---------------------------------------------------------------- 影子对象

    /**
     * 双端都按需建同构的影子对象：服务端在成形时建（之后由 attach 认集群、tick 建节点），
     * 客户端在收到第一包或第一次开界面时建。
     */
    private ShadowInterfaceBlockEntity<C> shadow() {
        if (shadow == null) {
            shadow = new ShadowInterfaceBlockEntity<>(host.getType(), host.getBlockPos(),
                    host.getBlockState(), calculator);
        }
        // 存档恢复（loadTag）早于 setLevel，客户端则不存在 attach 过程：两条时序都需要取得真实世界，
        // 影子实体才能识别菜单 viewer（isViewer 会比对 level），服务端也才能在正确的维度中 tick。
        if (shadow.getLevel() != host.getLevel()) {
            shadow.setLevel(host.getLevel());
        }
        return shadow;
    }
}
