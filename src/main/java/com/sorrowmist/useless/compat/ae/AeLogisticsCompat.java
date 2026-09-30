package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AeLogisticsBridge} 的 AE2 实现。
 *
 * <p><b>只有本类（以及它引用的 AE2 类）会在装了 AE2 时被加载</b>：常驻代码碰到的永远是
 * {@link AeLogisticsBridge} 那个不含 AE2 类型的接口。</p>
 *
 * <h2>为什么交付的是「端点」而不是「一次插入」</h2>
 *
 * <p>无线物流的搬运讲究「先模拟后提交」：先问目标能接多少，再按那个量抽源、塞目标，
 * 余量退回源。若桥只提供「插一次、抽一次」的动作，调用方就得在 AE 这条路上重写一遍
 * 这套时序，两套代码迟早走样。</p>
 *
 * <p>所以这里把整个 ME 网络包成一个标准的 {@link LongItemHandler} / {@link LongFluidHandler}：
 * {@code moveItems} / {@code moveFluid} 完全不知道自己面对的是 AE，模拟与执行天然同源，
 * 也不存在「超 int 要分多次」——AE2 的 {@code insert} / {@code extract} 本身就是 long 签名。</p>
 *
 * <h2>哪些方块算端点</h2>
 *
 * <p>判定交给 {@link AeNetworks#isEndpoint}：只要方块注册了 AE2 的网格节点宿主能力即可，
 * 因此无线访问点、ME 接口、终端、总线都能直接绑进无线物流。早先只认无线访问点，
 * 玩家绑别的东西再选 {@code AE_*} 就会「过几秒被自动删掉」，见 {@link AeNetworks} 的说明。</p>
 *
 * <h2>槽位快照</h2>
 *
 * <p>AE 网络没有「第几号槽」这种概念，槽位是常驻代码遍历内容时用的临时编号。端点因此在
 * <b>第一次被问到内容时</b>抓一份快照（网络里现有的 key 与存量），本次搬运全程沿用这一份：
 * 否则边抽边变，{@code slot} 会在遍历途中指向另一个 key。</p>
 *
 * <p>快照是每次 {@code resolve} 新建端点时重新抓的，所以跨 tick 不会读到陈旧数据；
 * 端点只在一次搬运内被使用，不存在长期持有的问题。</p>
 *
 * <p><b>但过滤器驱动的搬运根本不走快照。</b>那时「要搬什么」已经完全由过滤器确定，
 * 不需要知道网络里有什么，因此调用方走的是
 * {@link LongItemHandler#extractMatching(ItemStack, long, boolean)} /
 * {@link LongFluidHandler#drainMatching(FluidStack, long, boolean)}
 * —— 本类把它们覆写成「拿模板直接构造资源键、问一次 {@code MEStorage.extract}」。
 * 这一步是必须的：默认实现要先 {@code findSlot}，而它必须先抓整网快照，
 * 于是每次搬运都要把 ME 网络里的每一种资源遍历一遍（实测占无线物流总耗时的 32%）。
 * 快照只在<b>没有过滤器</b>的扫描路径上才会被用到。</p>
 *
 * <h2>离线端点</h2>
 *
 * <p>判定「是不是端点」只看方块<b>类型</b>，不看是否在线：掉电、掉线都是暂时的，
 * 若据此认定线路失效，自愈逻辑会把玩家配好的线整条删掉。网络取不到时端点表现为「空容器」
 * （抽不出、塞不进），搬运自然失败，等网络回来又自动恢复。</p>
 */
public final class AeLogisticsCompat implements AeLogisticsBridge {

    /** AE2 的动作源：无线物流没有玩家或机器作为发起者，用空源即可。 */
    private static volatile IActionSource cachedSource;

    private static IActionSource source() {
        IActionSource local = cachedSource;
        if (local == null) {
            local = IActionSource.empty();
            cachedSource = local;
        }
        return local;
    }

    @Override
    public boolean isEndpoint(Level level, BlockPos pos) {
        return AeNetworks.isEndpoint(level, pos);
    }

    @Override
    @Nullable
    public LongItemHandler itemEndpoint(Level level, BlockPos pos) {
        return isEndpoint(level, pos) ? new ItemEndpoint(level, pos) : null;
    }

    @Override
    @Nullable
    public LongFluidHandler fluidEndpoint(Level level, BlockPos pos) {
        return isEndpoint(level, pos) ? new FluidEndpoint(level, pos) : null;
    }

    /**
     * 写入被拒时的限流警告：每 {@link #WARN_INTERVAL_MS} 毫秒最多一条。
     *
     * <p>AE 端点「接受不了」有两类完全不同的原因，而界面上的 {@code TARGET_REJECTED}
     * 分不出来：一是<b>网络根本取不到</b>（端点没挂上网 / 节点尚未就绪），二是
     * <b>网络取到了但不收</b>——最常见的是 ME 网络里压根没有能存这种资源的存储
     * （AE2 的物品与流体各有各的存储元件，只放物品元件的话流体无处可去）。
     * 这两类的处理方式完全不同，所以这里把具体原因写进日志。</p>
     *
     * <p>限流是必须的：搬运每秒都在重试，不限流会把日志刷爆。</p>
     */
    private static final long WARN_INTERVAL_MS = 5000L;
    private static final AtomicLong LAST_WARN_AT = new AtomicLong();

    private static void warnRejected(BlockPos pos, String resource, String reason) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_AT.get();
        if (now - last < WARN_INTERVAL_MS || !LAST_WARN_AT.compareAndSet(last, now)) {
            return;
        }
        UselessMod.LOGGER.warn(
                "无线物流：AE 端点 {} 无法接收{}——{}", pos.toShortString(), resource, reason);
    }

    /** 一次搬运期间固定的「网络里有什么」快照条目。 */
    private static final class Entry {
        private final AEKey key;
        private long amount;

        Entry(AEKey key, long amount) {
            this.key = key;
            this.amount = amount;
        }
    }

    // ------------------------------------------------------------------ 物品端点

    /** ME 网络的物品视角。 */
    private static final class ItemEndpoint implements LongItemHandler {
        private final Level level;
        private final BlockPos pos;
        @Nullable
        private List<Entry> snapshot;
        /** 本次使用的网络句柄；见 {@link #storage()}。 */
        @Nullable
        private MEStorage storage;
        private boolean storageResolved;

        ItemEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        /**
         * 取 ME 网络句柄，<b>一个端点实例只解析一次</b>。
         *
         * <p>{@code AeNetworks.storage} 每次都要走「取方块实体 → 取能力」。早先这里每次用
         * 都现调一遍，于是一次搬运里要付三四次；而端点在同一轮分配里还会被多个接收端共用
         * （见 {@code StaffLinkEngine.distribute}），次数会再乘上接收端个数。</p>
         *
         * <p>句柄在网格存续期内是稳定的，缓存不会读到陈旧对象；{@code storageResolved} 单独
         * 记录「查过了但没查到」，避免网络离线时反复重查。端点寿命只有一轮分配，
         * 网络恢复后下一个周期就会拿到新的句柄。</p>
         */
        @Nullable
        private MEStorage storage() {
            if (!storageResolved) {
                storage = AeNetworks.storage(level, pos);
                storageResolved = true;
            }
            return storage;
        }

        private List<Entry> snapshot() {
            List<Entry> local = snapshot;
            if (local == null) {
                local = new ArrayList<>();
                MEStorage storage = storage();
                if (storage != null) {
                    for (var entry : storage.getAvailableStacks()) {
                        AEKey key = entry.getKey();
                        long amount = entry.getLongValue();
                        if (amount > 0L && key instanceof AEItemKey) {
                            local.add(new Entry(key, amount));
                        }
                    }
                }
                snapshot = local;
            }
            return local;
        }

        @Override
        public int getSlots() {
            return snapshot().size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<Entry> entries = snapshot();
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            return entries.get(slot).key instanceof AEItemKey itemKey
                    ? itemKey.toStack(1)
                    : ItemStack.EMPTY;
        }

        @Override
        public long amountIn(int slot) {
            List<Entry> entries = snapshot();
            return slot < 0 || slot >= entries.size() ? 0L : entries.get(slot).amount;
        }

        /**
         * 按资源键定位，不经过 {@link ItemStack}。
         *
         * <p>扫描上千条时这一步不能有额外分配——默认实现每条都要 {@code toStack(1)} 造一个栈。</p>
         *
         * <p><b>但它仍然要先抓整网快照，所以过滤器驱动的搬运不再走这里</b>（走
         * {@link #extractMatching(ItemStack, long, boolean)}）。保留它是为了<b>没有过滤器</b>的
         * 扫描路径：那里本来就必须知道网络里有什么，绕不开快照；而删掉它会让默认实现退化成
         * 「逐条 {@code toStack(1)} 再比较」，比现在更慢。</p>
         */
        @Override
        public int findSlot(ItemStack template) {
            if (template == null || template.isEmpty()) {
                return -1;
            }
            AEItemKey wanted = AEItemKey.of(template);
            if (wanted == null) {
                return -1;
            }
            List<Entry> entries = snapshot();
            for (int slot = 0; slot < entries.size(); slot++) {
                Entry entry = entries.get(slot);
                if (entry.amount > 0L && entry.key.equals(wanted)) {
                    return slot;
                }
            }
            return -1;
        }

        /**
         * 按资源键直接抽取，<b>完全不碰 {@link #snapshot()}</b>。
         *
         * <p>这是过滤器驱动路径的入口（见类注释「槽位快照」一节）。默认实现要先
         * {@code findSlot}，而它对 ME 网络意味着先抓一份整网快照 —— 每次搬运遍历一遍网络里
         * 每一种资源。AE2 的 {@code extract} 本来就是 long 签名、按 key 的哈希查找，
         * 直接问它即可，「先模拟后提交」的时序与 {@link #extract(int, long, boolean)} 完全一致。</p>
         *
         * <p>提交后把已抓的快照作废：万一同一轮分配里另一个接收端走的是无过滤器的扫描路径，
         * 它必须重新抓一份反映当前存量的快照，不能沿用抽取之前的旧数字。</p>
         */
        @Override
        public long extractMatching(ItemStack template, long amount, boolean simulate) {
            if (template == null || template.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEItemKey key = AEItemKey.of(template);
            if (key == null) {
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                return 0L;
            }
            long extracted = storage.extract(key, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (extracted > 0L && !simulate) {
                snapshot = null;
            }
            return Math.max(0L, extracted);
        }

        /**
         * 网络里这一种物品有多少。
         *
         * <p><b>要抓一份整网快照</b>（{@code getAvailableStacks()} 遍历网络里每一种资源），
         * 所以只有玩家的过滤器真的勾了「源端保留」或「接收端上限」时才会被调用；
         * 没勾就一次都不进来（见 {@code StaffLinkTargets}）。</p>
         *
         * <p>默认实现（{@code findSlot} + {@code amountIn}）在这里等价但要抓两次快照——
         * 一次 {@code findSlot}、一次 {@code amountIn}。直接查 {@code snapshot()} 的缓存
         * 只抓一次。ME 网络的 {@code getAvailableStacks} 对同一种 key 只报一个条目
         * （{@code KeyCounter} 按 key 聚拢），所以「第一个命中的条目」就是全部存量。</p>
         */
        @Override
        public long amountOf(ItemStack template) {
            if (template == null || template.isEmpty()) {
                return 0L;
            }
            AEItemKey wanted = AEItemKey.of(template);
            if (wanted == null) {
                return 0L;
            }
            for (Entry entry : snapshot()) {
                if (entry.key.equals(wanted)) {
                    return Math.max(0L, entry.amount);
                }
            }
            return 0L;
        }

        @Override
        public long extract(int slot, long amount, boolean simulate) {
            List<Entry> entries = snapshot();
            if (slot < 0 || slot >= entries.size() || amount <= 0L) {
                return 0L;
            }
            Entry entry = entries.get(slot);
            if (entry.amount <= 0L) {
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                return 0L;
            }
            long request = Math.min(amount, entry.amount);
            long extracted = storage.extract(entry.key, request,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (extracted > 0L && !simulate) {
                // 同步扣减快照：同一次搬运里若又轮到这一条，不会按旧数字重复抽。
                entry.amount -= extracted;
            }
            return Math.max(0L, extracted);
        }

        @Override
        public long insert(ItemStack template, long amount, boolean simulate) {
            if (template == null || template.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEItemKey key = AEItemKey.of(template);
            if (key == null) {
                warnRejected(pos, "物品", "这个物品无法转成 AE 的资源键");
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                warnRejected(pos, "物品", "该方块当前取不到 ME 网络（没有挂上网格，或节点尚未就绪）");
                return 0L;
            }
            long inserted = storage.insert(key, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (inserted <= 0L) {
                warnRejected(pos, "物品", "ME 网络没有收下它：要么网里没有物品存储"
                        + "（物品存储元件，或用存储总线接入的容器），要么这些存储已经满了");
            }
            // 刻意<b>不</b>在这里作废快照：无过滤器的扫描路径正靠「一次搬运沿用同一份快照」
            // 保证 slot 在遍历途中始终指向同一个 key（见类注释）。作废它会让扫描循环每插一次
            // 就重抓一遍整网。多出来的存量下次搬运自然可见。
            return Math.max(0L, inserted);
        }
    }

    // ------------------------------------------------------------------ 流体端点

    /** ME 网络的流体视角。 */
    private static final class FluidEndpoint implements LongFluidHandler {
        private final Level level;
        private final BlockPos pos;
        @Nullable
        private List<Entry> snapshot;
        /** 本次使用的网络句柄；见 {@link #storage()}。 */
        @Nullable
        private MEStorage storage;
        private boolean storageResolved;

        FluidEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        /** 理由同 {@link ItemEndpoint#storage()}。 */
        @Nullable
        private MEStorage storage() {
            if (!storageResolved) {
                storage = AeNetworks.storage(level, pos);
                storageResolved = true;
            }
            return storage;
        }

        private List<Entry> snapshot() {
            List<Entry> local = snapshot;
            if (local == null) {
                local = new ArrayList<>();
                MEStorage storage = storage();
                if (storage != null) {
                    for (var entry : storage.getAvailableStacks()) {
                        AEKey key = entry.getKey();
                        long amount = entry.getLongValue();
                        if (amount > 0L && key instanceof AEFluidKey) {
                            local.add(new Entry(key, amount));
                        }
                    }
                }
                snapshot = local;
            }
            return local;
        }

        @Override
        public int getTanks() {
            return snapshot().size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<Entry> entries = snapshot();
            if (tank < 0 || tank >= entries.size()) {
                return FluidStack.EMPTY;
            }
            return entries.get(tank).key instanceof AEFluidKey fluidKey
                    ? fluidKey.toStack(1)
                    : FluidStack.EMPTY;
        }

        @Override
        public long amountIn(int tank) {
            List<Entry> entries = snapshot();
            return tank < 0 || tank >= entries.size() ? 0L : entries.get(tank).amount;
        }

        /** 按资源键定位，不经过 {@link FluidStack}；理由同物品端点的 {@code findSlot}。 */
        @Override
        public int findTank(FluidStack type) {
            if (type == null || type.isEmpty()) {
                return -1;
            }
            AEFluidKey wanted = AEFluidKey.of(type);
            if (wanted == null) {
                return -1;
            }
            List<Entry> entries = snapshot();
            for (int tank = 0; tank < entries.size(); tank++) {
                Entry entry = entries.get(tank);
                if (entry.amount > 0L && entry.key.equals(wanted)) {
                    return tank;
                }
            }
            return -1;
        }

        @Override
        public long capacityOf(int tank) {
            // AE 网络对每一种资源都没有「储罐容量」这个概念，取 long 上限表示不设限。
            return Long.MAX_VALUE;
        }

        /**
         * 按资源键直接抽取，<b>完全不碰 {@link #snapshot()}</b>；理由同
         * {@link ItemEndpoint#extractMatching(ItemStack, long, boolean)}。
         */
        @Override
        public long drainMatching(FluidStack type, long amount, boolean simulate) {
            if (type == null || type.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEFluidKey key = AEFluidKey.of(type);
            if (key == null) {
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                return 0L;
            }
            long drained = storage.extract(key, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (drained > 0L && !simulate) {
                snapshot = null;
            }
            return Math.max(0L, drained);
        }

        /**
         * 网络里这一种流体有多少；理由与代价同 {@link ItemEndpoint#amountOf(ItemStack)}。
         */
        @Override
        public long amountOf(FluidStack type) {
            if (type == null || type.isEmpty()) {
                return 0L;
            }
            AEFluidKey wanted = AEFluidKey.of(type);
            if (wanted == null) {
                return 0L;
            }
            for (Entry entry : snapshot()) {
                if (entry.key.equals(wanted)) {
                    return Math.max(0L, entry.amount);
                }
            }
            return 0L;
        }

        @Override
        public long drain(int tank, long amount, boolean simulate) {
            List<Entry> entries = snapshot();
            if (tank < 0 || tank >= entries.size() || amount <= 0L) {
                return 0L;
            }
            Entry entry = entries.get(tank);
            if (entry.amount <= 0L) {
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                return 0L;
            }
            long request = Math.min(amount, entry.amount);
            long drained = storage.extract(entry.key, request,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (drained > 0L && !simulate) {
                entry.amount -= drained;
            }
            return Math.max(0L, drained);
        }

        @Override
        public long fill(FluidStack type, long amount, boolean simulate) {
            if (type == null || type.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEFluidKey key = AEFluidKey.of(type);
            if (key == null) {
                warnRejected(pos, "流体", "这种流体无法转成 AE 的资源键（多半是未正确注册的流体）");
                return 0L;
            }
            MEStorage storage = storage();
            if (storage == null) {
                warnRejected(pos, "流体", "该方块当前取不到 ME 网络（没有挂上网格，或节点尚未就绪）");
                return 0L;
            }
            long inserted = storage.insert(key, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (inserted <= 0L) {
                warnRejected(pos, "流体", "ME 网络没有收下它：要么网里没有流体存储（AE2 的物品与"
                        + "流体存储是分开的，需要流体存储元件放进 ME 驱动器，或用存储总线接入一个"
                        + "流体容器），要么这些存储已经满了");
            }
            // 理由同 ItemEndpoint.insert：刻意不作废快照。
            return Math.max(0L, inserted);
        }
    }
}
