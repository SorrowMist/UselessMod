package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.compat.mekanism.MekanismChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AeChemicalBridge} 的 Applied Mekanistics 实现。
 *
 * <p>只有 AE2 与 appmek 同时在场时，{@link AeChemicalCompatLoader} 才会加载本类。</p>
 *
 * <h2>为什么是「一个端点」而不是「一次插入 / 一次抽出」</h2>
 *
 * <p>搬运侧走的是「先模拟后提交、余量退回源」，这套时序要求模拟与执行面对同一个对象。
 * 把整个 ME 网络里的化学品包成一个 {@link ChemicalHandlerView}，搬运逻辑就与方块容器
 * 完全同源，不存在两套代码走样的问题。</p>
 *
 * <h2>槽位快照</h2>
 *
 * <p>ME 网络没有「第几号储罐」，储罐号是搬运侧遍历内容时的临时编号。因此在第一次被问到
 * 内容时抓一份快照（网络里现有的化学品 key 与存量），本次搬运全程沿用，避免边抽边变导致
 * 编号指向另一种化学品。</p>
 *
 * <h2>数量</h2>
 *
 * <p>全程 long：{@code ChemicalStack} 的 amount 与 AE2 的 {@code insert}/{@code extract}
 * 都是 long 签名，中间不需要任何 int 中转。</p>
 */
public final class AeChemicalCompat implements AeChemicalBridge {

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
    public ChemicalHandlerView chemicalEndpoint(Level level, BlockPos pos) {
        return AeNetworks.isEndpoint(level, pos) ? new Endpoint(level, pos) : null;
    }

    /** 一次搬运期间固定的「网络里有哪些化学品」快照条目。 */
    private static final class Entry {
        private final AEKey key;
        private long amount;

        Entry(AEKey key, long amount) {
            this.key = key;
            this.amount = amount;
        }
    }

    private static final class Endpoint implements ChemicalHandlerView {
        private final Level level;
        private final BlockPos pos;
        private List<Entry> snapshot;

        Endpoint(Level level, BlockPos pos) {
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
                        if (amount > 0L && key instanceof MekanismKey) {
                            local.add(new Entry(key, amount));
                        }
                    }
                }
                snapshot = local;
            }
            return local;
        }

        @Override
        public ChemicalStackView insertChemical(ChemicalStackView stack, boolean simulate) {
            if (stack == null || stack.isEmpty() || stack.amount() <= 0L) {
                return ChemicalStackView.EMPTY;
            }
            if (!(stack instanceof MekanismChemicalStackView view)) {
                // 不是 Mekanism 支撑的视图，说明这个化学品根本不是本环境能表达的，原样退回。
                return stack;
            }
            ChemicalStack chemical = view.stack();
            MekanismKey key = MekanismKey.of(chemical);
            if (key == null) {
                return stack;
            }
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return stack;
            }
            long inserted = storage.insert(key, stack.amount(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            long remaining = stack.amount() - Math.max(0L, inserted);
            return remaining <= 0L ? ChemicalStackView.EMPTY : stack.copyWithAmount(remaining);
        }

        @Override
        public ChemicalStackView extractChemical(long amount, boolean simulate) {
            if (amount <= 0L) {
                return ChemicalStackView.EMPTY;
            }
            List<Entry> entries = snapshot();
            for (Entry entry : entries) {
                if (entry.amount <= 0L) {
                    continue;
                }
                long taken = extract(entry, amount, simulate);
                if (taken > 0L) {
                    return asView(entry, taken);
                }
            }
            return ChemicalStackView.EMPTY;
        }

        @Override
        public ChemicalStackView extractChemical(ChemicalStackView stack, long amount, boolean simulate) {
            if (stack == null || stack.isEmpty() || amount <= 0L) {
                return ChemicalStackView.EMPTY;
            }
            List<Entry> entries = snapshot();
            for (Entry entry : entries) {
                if (entry.amount <= 0L) {
                    continue;
                }
                ChemicalStackView candidate = asView(entry, entry.amount);
                if (!stack.isSameType(candidate)) {
                    continue;
                }
                long taken = extract(entry, amount, simulate);
                return taken > 0L ? asView(entry, taken) : ChemicalStackView.EMPTY;
            }
            return ChemicalStackView.EMPTY;
        }

        private long extract(Entry entry, long amount, boolean simulate) {
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0L;
            }
            long request = Math.min(amount, entry.amount);
            long taken = storage.extract(entry.key, request,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (taken > 0L && !simulate) {
                // 同步扣减快照：同一次搬运里若又轮到这一条，不会按旧数字重复抽。
                entry.amount -= taken;
            }
            return Math.max(0L, taken);
        }

        /** 把快照条目还原成本模组的化学品视图。 */
        private static ChemicalStackView asView(Entry entry, long amount) {
            if (entry.key instanceof MekanismKey key) {
                ChemicalStack chemical = key.getStack();
                if (chemical == null || chemical.isEmpty()) {
                    return ChemicalStackView.EMPTY;
                }
                return new MekanismChemicalStackView(chemical.copyWithAmount(amount));
            }
            return ChemicalStackView.EMPTY;
        }
    }
}
