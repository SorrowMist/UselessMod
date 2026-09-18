package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates exact AE amounts without materializing item or fluid stacks for numeric chunks.
 */
final class CraftingAeAmountAccumulator {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> amounts =
            new Object2ObjectLinkedOpenHashMap<>();

    static CraftingAeAmountAccumulator fromCounters(KeyCounter[] counters) {
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        accumulator.add(counters);
        return accumulator;
    }

    void add(KeyCounter[] counters) {
        if (counters == null) {
            return;
        }
        for (KeyCounter counter : counters) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    static CraftingAeAmountAccumulator fromGenericStacks(Iterable<GenericStack> stacks) {
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        for (GenericStack stack : stacks) {
            accumulator.add(stack);
        }
        return accumulator;
    }

    void add(GenericStack stack) {
        Objects.requireNonNull(stack, "Crafting AE amount");
        add(stack.what(), stack.amount());
    }

    void add(AEKey key, long amount) {
        add(key, BigInteger.valueOf(amount));
    }

    void add(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "Crafting AE key");
        Objects.requireNonNull(amount, "Crafting AE amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Crafting AE amounts must be positive");
        }
        this.amounts.merge(key, amount, BigInteger::add);
    }

    // ==================== 聚合与扣减（回网路径用） ====================

    /**
     * 合并另一本账：逐键相加，键不存在则新建。
     *
     * <p>用于回网刷新前把整条队列<b>按键聚合成一本账</b>：同一个键只投递一次，而不是每条各投一次。
     * 另一本账的余额恒为正（{@link #consume} 扣到 0 就移除键），所以直接走
     * {@link #add(AEKey, BigInteger)} 即可。</p>
     */
    void addAll(CraftingAeAmountAccumulator other) {
        Objects.requireNonNull(other, "Crafting AE amount ledger");
        for (var entry : other.amounts.object2ObjectEntrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 按键扣减余额（把「已投递量」记回账本）。
     *
     * <p>与 {@link #add(AEKey, BigInteger)} 的严格正数校验相反：这里<b>允许扣到 0</b>，
     * 因为分批回网时投递量本来就可能小于余额。扣到 0 的键会被移除，保证 {@link #isEmpty()}
     * 与持久化都不会留下零余额键。键不存在、或 {@code amount <= 0} 时返回
     * {@link BigInteger#ZERO} 且不抛异常 —— 归因是逐键遍历，必然遇到「该条目没有这个键」。</p>
     *
     * @return 实际扣减量，恒 ∈ [0, 该键当前余额]
     */
    BigInteger consume(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "Crafting AE key");
        Objects.requireNonNull(amount, "Crafting AE amount");
        if (amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = this.amounts.get(key);
        if (current == null) {
            return BigInteger.ZERO;
        }
        BigInteger consumed = current.min(amount);
        BigInteger remaining = current.subtract(consumed);
        if (remaining.signum() <= 0) {
            this.amounts.remove(key);
        } else {
            this.amounts.put(key, remaining);
        }
        return consumed;
    }

    /**
     * 按键余额的不可变快照（保持插入顺序）。
     *
     * <p>调用方可以在遍历快照的同时安全地 {@link #consume} 修改账本本身 —— 快照里的 value
     * 是不可变 {@link BigInteger}，不受影响。</p>
     *
     * <p>刻意<b>不</b>暴露内部 map：避免调用方绕过 {@link #add}/{@link #consume} 直接改值，
     * 破坏「无零/负余额」这条不变量。</p>
     */
    List<Map.Entry<AEKey, BigInteger>> snapshotEntries() {
        List<Map.Entry<AEKey, BigInteger>> snapshot = new ArrayList<>(this.amounts.size());
        for (var entry : this.amounts.object2ObjectEntrySet()) {
            snapshot.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        return snapshot;
    }

    /** 读取单键余额；键不存在返回 {@link BigInteger#ZERO}。 */
    BigInteger amount(AEKey key) {
        return this.amounts.getOrDefault(key, BigInteger.ZERO);
    }

    /**
     * 所有键余额之和（精确 BigInteger）。
     *
     * <p>回网积压度量用它：入队加总量、交付减实际交付量，两者都是精确值 ⇒ <b>零漂移</b>。</p>
     */
    BigInteger totalAmount() {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : this.amounts.values()) {
            total = total.add(amount);
        }
        return total;
    }

    /** 深拷贝。值是可变性为零的 {@link BigInteger}，逐键 putAll 即可。 */
    CraftingAeAmountAccumulator copy() {
        CraftingAeAmountAccumulator copy = new CraftingAeAmountAccumulator();
        copy.amounts.putAll(this.amounts);
        return copy;
    }

    boolean fitsWithinLongAfterMultiplying(long multiplier) {
        if (multiplier <= 0L) {
            throw new IllegalArgumentException("Crafting AE multiplier must be positive");
        }
        BigInteger factor = BigInteger.valueOf(multiplier);
        for (BigInteger amount : this.amounts.values()) {
            if (amount.multiply(factor).compareTo(MAX_LONG) > 0) {
                return false;
            }
        }
        return true;
    }

    boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    List<GenericStack> segments() {
        ArrayList<GenericStack> result = new ArrayList<>(this.amounts.size());
        for (var entry : this.amounts.object2ObjectEntrySet()) {
            BigInteger remaining = entry.getValue();
            while (remaining.compareTo(MAX_LONG) > 0) {
                result.add(new GenericStack(entry.getKey(), Long.MAX_VALUE));
                remaining = remaining.subtract(MAX_LONG);
            }
            if (remaining.signum() > 0) {
                result.add(new GenericStack(entry.getKey(), remaining.longValueExact()));
            }
        }
        return result;
    }

    // ==================== 持久化 ====================

    /**
     * 紧凑持久化：每个键写一条「键 + BigInteger 字节数组」。
     *
     * <p><b>为什么不直接写 {@link #segments()}</b>：大数产物按 {@code Long.MAX} 切段，
     * 1e22 个物品就是上千条 NBT；而回网队列的深度等于本机线程数，整条队列叠起来能到 GB 级，
     * 且每次区块存盘都要重写一遍。按键聚合后每个键只占一条，重载时再 {@link #segments()} 展开，
     * 语义完全等价。</p>
     */
    ListTag writeCompactTag(HolderLookup.Provider registries) {
        ListTag tag = new ListTag();
        for (var entry : this.amounts.object2ObjectEntrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("Key", entry.getKey().toTagGeneric(registries));
            entryTag.putByteArray("Amount", entry.getValue().toByteArray());
            tag.add(entryTag);
        }
        return tag;
    }

    /** 读取 {@link #writeCompactTag} 写出的紧凑格式。 */
    static CraftingAeAmountAccumulator readCompactTag(HolderLookup.Provider registries, ListTag tag) {
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        for (int index = 0; index < tag.size(); index++) {
            CompoundTag entryTag = tag.getCompound(index);
            byte[] encoded = entryTag.getByteArray("Amount");
            if (encoded.length == 0) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(registries, entryTag.getCompound("Key"));
            BigInteger amount = new BigInteger(encoded);
            if (key != null && amount.signum() > 0) {
                accumulator.add(key, amount);
            }
        }
        return accumulator;
    }
}
