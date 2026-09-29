package com.sorrowmist.useless.compat.ae;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AeGenericInvBridge} 的 AE2 实现。
 *
 * <p><b>只有本类（以及它引用的 AE2 类）会在装了 AE2 时被加载</b>：常驻代码碰到的永远是
 * {@link AeGenericInvBridge} 那个不含 AE2 类型的接口。</p>
 *
 * <h2>这个端点是「方块本地那一小格库存」</h2>
 *
 * <p>拿的是 {@code AECapabilities.GENERIC_INTERNAL_INV}。对 ME 接口，它是
 * {@code InterfaceLogic#getStorage()} —— 那 9 格<b>缓冲</b>，不是整张网络
 * （整张网络挂在 {@code ME_STORAGE} 上，归 {@link AeLogisticsCompat} 管）。因此它天然是个
 * 固定 9 槽、可以「一格一个 key」的普通容器，槽位编号稳定，不需要快照。</p>
 *
 * <h2>收益在哪</h2>
 *
 * <p>{@code GenericInternalInventory} 的 {@code getAmount} / {@code insert} / {@code extract}
 * 全是 long 签名，而且<b>完全不碰 {@code ItemStack}</b>。原先这条路走的是 AE2 顺手注册的
 * {@code GenericStackItemStorage}（{@code Capabilities.ItemHandler.BLOCK} 投影），
 * 每读一个槽就要 {@code AEKey → ItemStack} 物化一次（连带复制整份 DataComponent 映射），
 * 每抽一次同样要物化。走 native 接口把这一整类开销删掉。</p>
 *
 * <h2>两条硬约束</h2>
 *
 * <ol>
 *   <li><b>{@code beginBatch} / {@code endBatch} 只在真正写入时开，且必须 {@code try/finally}。</b>
 *       {@code GenericStackInv} 用 {@code Preconditions.checkState} 守两侧：
 *       嵌套会崩；而一旦漏掉 {@code endBatch}，{@code suppressOnChange} 就<b>永久为真</b>，
 *       这个接口从此<b>再也不会刷新</b>——比崩溃更糟，因为它静默地把机器弄坏。</li>
 *   <li><b>{@code insert} 的多槽循环不能提前 {@code break}</b>。
 *       "收得比给得少就说明满了" 这条推理在这里不成立：某一槽可能只是 key 不符或被
 *       过滤器挡下，后面的槽还有空间。代价是最多 9 次调用，可忽略。</li>
 * </ol>
 */
public final class AeGenericInvCompat implements AeGenericInvBridge {

    /**
     * 写入被拒时的限流警告：每 {@link #WARN_INTERVAL_MS} 毫秒最多一条。
     *
     * <p>独立于 {@link AeLogisticsCompat} 里的那份（那边是「网络不收」，这边是「本地槽不收」，
     * 原因不同，混在一起会互相盖掉）。</p>
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
                "无线物流：AE 本地库存 {} 无法接收{}——{}", pos.toShortString(), resource, reason);
    }

    /**
     * 取该坐标的局部通用库存；不是这种方块、或正处于替换的中间态时返回 {@code null}。
     *
     * <p>{@code side} 直接透传。AE2 的注册 lambda 忽略 context，所以任何面拿到的都是同一个
     * 库存；透传只是为了万一以后有方块按面区分。</p>
     *
     * <p>{@code catch (Throwable)} 是刻意的：能力查询本身不该抛，但方块实体可能正被替换/卸载
     * （与 {@code AeNetworks#host} 同款防御）。但<b>只在这里</b>吞——常驻代码里不许有。</p>
     */
    @Nullable
    private static GenericInternalInventory inventory(Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        try {
            return level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, side);
        } catch (Throwable notReady) {
            return null;
        }
    }

    @Override
    public boolean isGenericInv(Level level, BlockPos pos) {
        return inventory(level, pos, null) != null;
    }

    @Override
    @Nullable
    public LongItemHandler itemEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        GenericInternalInventory inv = inventory(level, pos, side);
        // 只接「已挂上网格」的方块：节点未 create 时 getStorage() 会是 null，
        // 这里自然就退回了。附带的过滤是 isSupportedType，见端点里的说明。
        return inv == null ? null : new GenericInvItemEndpoint(inv, pos);
    }

    @Override
    @Nullable
    public LongFluidHandler fluidEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        GenericInternalInventory inv = inventory(level, pos, side);
        return inv == null ? null : new GenericInvFluidEndpoint(inv, pos);
    }

    // ------------------------------------------------------------------ 物品端点

    /**
     * 局部通用库存的物品视角。
     *
     * <p><b>没有快照。</b> 与整网端点不同，这里的槽位是真实且稳定的（ME 接口恒为 9 格），
     * 直接按 {@code inv.getKey(slot)} 读即可。这也是它比走 {@code ItemHandler.BLOCK} 快的原因之一。</p>
     */
    private static final class GenericInvItemEndpoint implements LongItemHandler {
        private final GenericInternalInventory inv;
        private final BlockPos pos;
        /** 本次端点寿命内「这个库存支持物品」的判定结果；一次调用里不会变。 */
        private final boolean supported;

        GenericInvItemEndpoint(GenericInternalInventory inv, BlockPos pos) {
            this.inv = inv;
            this.pos = pos;
            this.supported = safeSupportedType(AEKeyType.items());
        }

        private boolean safeSupportedType(AEKeyType type) {
            try {
                return inv.isSupportedType(type);
            } catch (Throwable notReady) {
                return false;
            }
        }

        @Override
        public int getSlots() {
            return inv.size();
        }

        /**
         * 这一格的 key；不是物品（或空）时返回 {@code null}。
         *
         * <p>判 {@code AEItemKey} 而不是靠 {@code supported}：接口的 `storage` 是「通用」库存，
         * 同一个接口的不同槽可能装着不同类型的 key（升级卡装流体时尤其明显），
         * 逐槽判类型才是对的。</p>
         */
        @Nullable
        private AEItemKey keyAt(int slot) {
            if (slot < 0 || slot >= inv.size()) {
                return null;
            }
            AEKey key = inv.getKey(slot);
            return key instanceof AEItemKey itemKey && inv.getAmount(slot) > 0L ? itemKey : null;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            AEItemKey key = keyAt(slot);
            // toStack 每次新建对象，无需再 copy。
            return key == null ? ItemStack.EMPTY : key.toStack(1);
        }

        @Override
        public ItemStack peekStack(int slot) {
            // 同 getStackInSlot：底层本来就是新对象，没有「引用被就地清空」的问题。
            return getStackInSlot(slot);
        }

        /**
         * 槽内实际数量，<b>纯 long，零物化</b>——这是本端点最主要的收益来源。
         *
         * <p>对比：走 {@code GenericStackItemStorage.amountIn} 要先 {@code toStack(1)} 造一个
         * {@code ItemStack}，只为读它的 {@code getCount()}。</p>
         */
        @Override
        public long amountIn(int slot) {
            if (slot < 0 || slot >= inv.size()) {
                return 0L;
            }
            AEKey key = inv.getKey(slot);
            if (!(key instanceof AEItemKey)) {
                return 0L;
            }
            return Math.max(0L, inv.getAmount(slot));
        }

        @Override
        public long amountIn(int slot, ItemStack stackFromSlot) {
            // 模板的 count 恒为 1（见 getStackInSlot），不代表真实数量，所以忽略它。
            return amountIn(slot);
        }

        /**
         * 跨槽累加同一 key 的存量。
         *
         * <p>ME 接口的 9 格可以同时装同一种物品的多个槽（配置成同一种、或模糊卡的
         * {@code IGNORE_ALL} 模式下不同组件落进不同槽）。过滤器的「源端保留 8 个」
         * 要按总存量算，所以这里遍历求和——本来就只有 9 次 {@code getKey}，纯 long。</p>
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
            long total = 0L;
            for (int slot = 0; slot < inv.size(); slot++) {
                if (wanted.equals(inv.getKey(slot))) {
                    total += Math.max(0L, inv.getAmount(slot));
                }
            }
            return total;
        }

        /**
         * 按 key 定位，<b>不物化任何 {@code ItemStack}</b>。
         *
         * <p>默认实现要逐槽 {@code getStackInSlot} 再 {@code ItemStack.isSameItemSameComponents}
         * ——9 次物化 + 9 次组件映射比较。</p>
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
            for (int slot = 0; slot < inv.size(); slot++) {
                if (inv.getAmount(slot) > 0L && wanted.equals(inv.getKey(slot))) {
                    return slot;
                }
            }
            return -1;
        }

        @Override
        public long extractMatching(ItemStack template, long amount, boolean simulate) {
            if (template == null || template.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEItemKey key = AEItemKey.of(template);
            if (key == null) {
                return 0L;
            }
            long remaining = amount;
            long moved = 0L;
            for (int slot = 0; slot < inv.size() && remaining > 0L; slot++) {
                if (!key.equals(inv.getKey(slot))) {
                    continue;
                }
                long taken = extractFromSlot(slot, key, remaining, simulate);
                moved += taken;
                remaining -= taken;
            }
            return moved;
        }

        /**
         * 抽走某槽里指定 key 的 {@code amount} 个。
         *
         * <p><b>零物化</b>：{@code inv.extract} 收 {@code AEKey} + long，返回值也是 long，
         * 全程不需要 {@code ItemStack}。这是相对 {@code GenericStackItemStorage.extractItem}
         * 最大的差异（后者要 {@code toStack(extracted)} 才能报出抽了多少）。</p>
         *
         * <p>{@code canExtract()} 必须问：样板供应器的 {@code PatternProviderReturnInventory}
         * 是「只进不出」的（{@code canExtract() = false}），当源端时不能搬空它。</p>
         */
        private long extractFromSlot(int slot, AEKey key, long amount, boolean simulate) {
            if (slot < 0 || slot >= inv.size() || amount <= 0L) {
                return 0L;
            }
            if (simulate) {
                try {
                    return Math.max(0L, inv.extract(slot, key, amount, Actionable.SIMULATE));
                } catch (Throwable notReady) {
                    return 0L;
                }
            }
            boolean batch = false;
            try {
                inv.beginBatch();
                batch = true;
                return Math.max(0L, inv.extract(slot, key, amount, Actionable.MODULATE));
            } catch (Throwable notReady) {
                return 0L;
            } finally {
                // 必须 endBatch，否则 suppressOnChange 永久为真、接口再不刷新。见类注释。
                if (batch) {
                    inv.endBatch();
                }
            }
        }

        @Override
        public long extract(int slot, long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            AEItemKey key = keyAt(slot);
            if (key == null) {
                return 0L;
            }
            // 不能抽超过槽内存量：AE2 侧会自己夹，但显式夹一次能让返回值与
            // 「快照式实现」的语义完全一致，调用方不必区分。
            long request = Math.min(amount, Math.max(0L, inv.getAmount(slot)));
            return request <= 0L ? 0L : extractFromSlot(slot, key, request, simulate);
        }

        /**
         * 向库存里塞东西。
         *
         * <p>逐槽试 {@code isAllowedIn}（那是配置/过滤卡的判定），但<b>不因某一槽少收就退出</b>
         * ——某个槽可能只是 key 不符，后面的槽还有空间。见类注释第二条硬约束。</p>
         */
        @Override
        public long insert(ItemStack template, long amount, boolean simulate) {
            if (template == null || template.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEItemKey key = AEItemKey.of(template);
            if (key == null) {
                return 0L;
            }
            if (!supported) {
                warnRejected(pos, "物品", "这个接口的本地库存不接受物品类型（配置里可能只允许流体等）");
                return 0L;
            }
            long remaining = amount;
            long inserted = 0L;
            // 只在提交时才开 batch：模拟不改状态、不该牵动通知；而且 endBatch 一旦漏掉
            // 后果严重，能不碰就不碰。
            boolean batch = false;
            try {
                if (!simulate) {
                    inv.beginBatch();
                    batch = true;
                }
                for (int slot = 0; slot < inv.size() && remaining > 0L; slot++) {
                    if (!inv.isAllowedIn(slot, key)) {
                        continue;
                    }
                    long accepted = inv.insert(slot, key, remaining,
                            simulate ? Actionable.SIMULATE : Actionable.MODULATE);
                    if (accepted <= 0L) {
                        continue;
                    }
                    inserted += accepted;
                    remaining -= accepted;
                }
            } catch (Throwable notReady) {
                // 恰好在这里出错时 insert 可能已经部分生效，返回已计到的量即可（不谎报）。
                return inserted;
            } finally {
                if (batch) {
                    inv.endBatch();
                }
            }
            if (inserted <= 0L && !simulate) {
                warnRejected(pos, "物品", "本地库存没有收下它：可能是这 9 个槽都满了、"
                        + "或配置/过滤卡不允许这种物品进任何槽");
            }
            return inserted;
        }
    }

    // ------------------------------------------------------------------ 流体端点

    /** 局部通用库存的流体视角；与物品端点严格对称，说明见那边。 */
    private static final class GenericInvFluidEndpoint implements LongFluidHandler {
        private final GenericInternalInventory inv;
        private final BlockPos pos;

        GenericInvFluidEndpoint(GenericInternalInventory inv, BlockPos pos) {
            this.inv = inv;
            this.pos = pos;
        }

        @Nullable
        private AEFluidKey keyAt(int tank) {
            if (tank < 0 || tank >= inv.size()) {
                return null;
            }
            AEKey key = inv.getKey(tank);
            return key instanceof AEFluidKey fluidKey && inv.getAmount(tank) > 0L ? fluidKey : null;
        }

        @Override
        public int getTanks() {
            return inv.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            AEFluidKey key = keyAt(tank);
            return key == null ? FluidStack.EMPTY : key.toStack(1);
        }

        @Override
        public long amountIn(int tank) {
            if (tank < 0 || tank >= inv.size()) {
                return 0L;
            }
            AEKey key = inv.getKey(tank);
            return key instanceof AEFluidKey ? Math.max(0L, inv.getAmount(tank)) : 0L;
        }

        /**
         * 这一格的容量。
         *
         * <p>空槽时没有 key 可问 {@code getMaxAmount}，回落到「这种 key 类型的通用容量」
         * （{@code getCapacity}）。<b>不返回 {@code Long.MAX_VALUE}</b>——那会让调用方以为
         * 可以无限注入；AE2 自己的 {@code GenericStackFluidStorage.getTankCapacity} 也是这个口径。</p>
         *
         * <p>注意 {@code getCapacity(AEKeyType)} 的文档：它可能只是 estimate。
         * 当前无线物流没有调用方，按契约给个合理值即可。</p>
         */
        @Override
        public long capacityOf(int tank) {
            if (tank < 0 || tank >= inv.size()) {
                return 0L;
            }
            AEKey key = inv.getKey(tank);
            if (key != null) {
                return Math.max(0L, inv.getMaxAmount(key));
            }
            try {
                return Math.max(0L, inv.getCapacity(AEKeyType.fluids()));
            } catch (Throwable notReady) {
                return 0L;
            }
        }

        @Override
        public long amountOf(FluidStack type) {
            if (type == null || type.isEmpty()) {
                return 0L;
            }
            AEFluidKey wanted = AEFluidKey.of(type);
            if (wanted == null) {
                return 0L;
            }
            long total = 0L;
            for (int tank = 0; tank < inv.size(); tank++) {
                if (wanted.equals(inv.getKey(tank))) {
                    total += Math.max(0L, inv.getAmount(tank));
                }
            }
            return total;
        }

        @Override
        public int findTank(FluidStack type) {
            if (type == null || type.isEmpty()) {
                return -1;
            }
            AEFluidKey wanted = AEFluidKey.of(type);
            if (wanted == null) {
                return -1;
            }
            for (int tank = 0; tank < inv.size(); tank++) {
                if (inv.getAmount(tank) > 0L && wanted.equals(inv.getKey(tank))) {
                    return tank;
                }
            }
            return -1;
        }

        @Override
        public long drainMatching(FluidStack type, long amount, boolean simulate) {
            if (type == null || type.isEmpty() || amount <= 0L) {
                return 0L;
            }
            AEFluidKey key = AEFluidKey.of(type);
            if (key == null) {
                return 0L;
            }
            long remaining = amount;
            long moved = 0L;
            for (int tank = 0; tank < inv.size() && remaining > 0L; tank++) {
                if (!key.equals(inv.getKey(tank))) {
                    continue;
                }
                long drained = drainFromTank(tank, key, remaining, simulate);
                moved += drained;
                remaining -= drained;
            }
            return moved;
        }

        private long drainFromTank(int tank, AEKey key, long amount, boolean simulate) {
            if (tank < 0 || tank >= inv.size() || amount <= 0L) {
                return 0L;
            }
            if (simulate) {
                try {
                    return Math.max(0L, inv.extract(tank, key, amount, Actionable.SIMULATE));
                } catch (Throwable notReady) {
                    return 0L;
                }
            }
            boolean batch = false;
            try {
                inv.beginBatch();
                batch = true;
                return Math.max(0L, inv.extract(tank, key, amount, Actionable.MODULATE));
            } catch (Throwable notReady) {
                return 0L;
            } finally {
                if (batch) {
                    inv.endBatch();
                }
            }
        }

        @Override
        public long drain(int tank, long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            AEFluidKey key = keyAt(tank);
            if (key == null) {
                return 0L;
            }
            long request = Math.min(amount, Math.max(0L, inv.getAmount(tank)));
            return request <= 0L ? 0L : drainFromTank(tank, key, request, simulate);
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
            long remaining = amount;
            long filled = 0L;
            boolean batch = false;
            try {
                if (!simulate) {
                    inv.beginBatch();
                    batch = true;
                }
                for (int tank = 0; tank < inv.size() && remaining > 0L; tank++) {
                    if (!inv.isAllowedIn(tank, key)) {
                        continue;
                    }
                    long accepted = inv.insert(tank, key, remaining,
                            simulate ? Actionable.SIMULATE : Actionable.MODULATE);
                    if (accepted <= 0L) {
                        continue;
                    }
                    filled += accepted;
                    remaining -= accepted;
                }
            } catch (Throwable notReady) {
                return filled;
            } finally {
                if (batch) {
                    inv.endBatch();
                }
            }
            if (filled <= 0L && !simulate) {
                warnRejected(pos, "流体", "本地库存没有收下它：可能是这 9 个槽都满了、"
                        + "或配置/过滤卡不允许这种流体进任何槽");
            }
            return filled;
        }
    }
}
