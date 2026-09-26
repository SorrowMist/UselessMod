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

        ItemEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        private List<Entry> snapshot() {
            List<Entry> local = snapshot;
            if (local == null) {
                local = new ArrayList<>();
                MEStorage storage = AeNetworks.storage(level, pos);
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
            MEStorage storage = AeNetworks.storage(level, pos);
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
            MEStorage storage = AeNetworks.storage(level, pos);
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

        FluidEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        private List<Entry> snapshot() {
            List<Entry> local = snapshot;
            if (local == null) {
                local = new ArrayList<>();
                MEStorage storage = AeNetworks.storage(level, pos);
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

        @Override
        public long capacityOf(int tank) {
            // AE 网络对每一种资源都没有「储罐容量」这个概念，取 long 上限表示不设限。
            return Long.MAX_VALUE;
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
            MEStorage storage = AeNetworks.storage(level, pos);
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
            MEStorage storage = AeNetworks.storage(level, pos);
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
            return Math.max(0L, inserted);
        }
    }
}
