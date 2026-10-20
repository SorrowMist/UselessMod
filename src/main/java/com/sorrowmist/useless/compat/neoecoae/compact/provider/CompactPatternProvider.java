package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把一整个紧凑 F9 里所有影子样板总线聚合成的单一网格服务。
 *
 * <p>AE2 与 ECO 的网格每个服务类只认一个实例，而紧凑 F9 内部有 176 条真实样板总线；
 * 这个聚合器把它们的样板集合、派单与插入接口按 ECO 原生实现逐条转发，不做任何自己的样板逻辑。</p>
 */
public final class CompactPatternProvider implements ICraftingProvider, IECOPatternStorage {

    private final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();

    public void bind(List<ECOCraftingPatternBusBlockEntity> newBuses) {
        buses.clear();
        buses.addAll(newBuses);
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
            return List.of();
        }
        Set<IPatternDetails> union = new LinkedHashSet<>();
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            union.addAll(bus.getAvailablePatterns());
        }
        return List.copyOf(union);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            if (bus.pushPattern(patternDetails, inputHolder)) {
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

    @Override
    public ECOPatternInsertionResult insertPattern(ItemStack itemStack) {
        if (itemStack.isEmpty() || buses.isEmpty()) {
            return ECOPatternInsertionResult.NO_TARGET;
        }
        // ECO 20.4.x exposes insertion as a result-returning operation on each native bus.
        ECOPatternInsertionResult last = ECOPatternInsertionResult.NO_TARGET;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPatternInsertionResult result = bus.insertPattern(itemStack);
            if (result == ECOPatternInsertionResult.INSERTED) {
                return result;
            }
            last = result;
        }
        return last;
    }

}
