package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把一整个紧凑 F9 里所有影子样板总线聚合成的单一网格服务。
 *
 * <p>AE2 与 ECO 的网格每个服务类只认一个实例，而紧凑 F9 内部有 176 条真实样板总线；
 * 这个聚合器把它们的样板集合、派单与插入接口按 ECO 原生实现逐条转发，不做任何自己的样板逻辑。</p>
 */
public final class CompactPatternProvider implements ECOJobAwareCraftingProvider, IECOPatternStorage,
        ECOFastPathDispatchProvider {

    private final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
    private Map<IPatternDetails, ECOCraftingPatternBusBlockEntity> patternOwners = Map.of();

    public void bind(List<ECOCraftingPatternBusBlockEntity> newBuses) {
        buses.clear();
        buses.addAll(newBuses);
        patternOwners = Map.of();
    }

    public boolean isEmpty() {
        return buses.isEmpty();
    }

    public List<ECOCraftingPatternBusBlockEntity> buses() {
        return List.copyOf(buses);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (buses.isEmpty()) {
            patternOwners = Map.of();
            return List.of();
        }
        Set<IPatternDetails> union = new LinkedHashSet<>();
        Map<IPatternDetails, ECOCraftingPatternBusBlockEntity> owners = new HashMap<>();
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            for (IPatternDetails pattern : bus.getAvailablePatterns()) {
                union.add(pattern);
                owners.putIfAbsent(pattern, bus);
            }
        }
        patternOwners = Map.copyOf(owners);
        return List.copyOf(union);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushPattern(patternDetails, inputHolder, null);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, @Nullable UUID craftingJobId) {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            if (bus.pushPattern(patternDetails, inputHolder, craftingJobId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        if (buses.isEmpty()) {
            return true;
        }
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            if (!bus.isBusy()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        Set<AEKey> union = new LinkedHashSet<>();
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            union.addAll(bus.getEmitableItems());
        }
        return union;
    }

    /**
     * 保留 ECO 样板总线的原生批量/虚拟批次派单；普通 ICraftingProvider 转发不会进入这条路径。
     */
    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        ECOCraftingPatternBusBlockEntity owner = patternOwners.get(context.pattern());
        return owner == null ? null : owner.eco$prepareFastPath(context);
    }

    @Override
    public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        ECOCraftingPatternBusBlockEntity owner = patternOwners.get(context.pattern());
        return owner == null ? null : owner.eco$prepareExactFastPath(context, requested);
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        if (itemStack.isEmpty() || buses.isEmpty()) {
            return ECOPatternInsertionResult.NO_TARGET;
        }
        // 只解码一次，再交给各总线的「已知唯一」快路径，避免每条总线都重扫整个逻辑域。
        ECOPatternInsertionResult last = ECOPatternInsertionResult.NO_TARGET;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPatternInsertionResult result = bus.insertPatternWithResult(itemStack);
            if (result == ECOPatternInsertionResult.INSERTED) {
                return result;
            }
            last = result;
        }
        return last;
    }

    @Override
    public boolean checksLogicalDomainForDuplicates() {
        return false;
    }
}
