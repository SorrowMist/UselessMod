package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import org.jetbrains.annotations.Nullable;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One grid service forwarding the compact fleet's patterns to native ECO buses. */
public final class CompactPatternProvider implements ICraftingProvider, IECOPatternStorage,
        ECOJobAwareCraftingProvider, ECOFastPathDispatchProvider, ECOParallelCraftingProvider {
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
        Set<IPatternDetails> union = new LinkedHashSet<>();
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            // 20.5 defers decoding after insertion. The buses have no grid node to notify
            // this host when decoding finishes, so publish a current native snapshot here.
            bus.refreshPatternDetailsForCatalog();
            union.addAll(bus.getAvailablePatterns());
        }
        return List.copyOf(union);
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        return pushPattern(details, inputs, null);
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs, @Nullable UUID jobId) {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            if (bus.pushPattern(details, inputs, jobId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            Preparation prepared = bus.eco$prepareFastPath(context);
            if (prepared != null) {
                return prepared;
            }
        }
        return null;
    }

    @Override
    public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ExactPreparation prepared = bus.eco$prepareExactFastPath(context, requested);
            if (prepared != null) {
                return prepared;
            }
        }
        return null;
    }

    // The native CPU uses this ordinary-path contract to retain the job ID for non-bus providers.
    // Larger atomic batches use native FastPath preparation rather than splitting owned inputs here.
    @Override
    public int eco$getAvailableParallelSlots() {
        return isBusy() ? 0 : 1;
    }

    @Override
    public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, long craftCount,
                                       @Nullable UUID jobId) {
        return craftCount == 1 && pushPattern(pattern, inputs, jobId);
    }

    @Override
    public boolean isBusy() {
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
    public boolean insertPattern(ItemStack stack) {
        return insertPatternWithResult(stack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack stack) {
        return insertPattern(stack, false);
    }

    @Override
    public ECOPatternInsertionResult insertPatternKnownUnique(ItemStack stack) {
        return insertPattern(stack, true);
    }

    private ECOPatternInsertionResult insertPattern(ItemStack stack, boolean knownUnique) {
        if (buses.isEmpty()) {
            return ECOPatternInsertionResult.NO_TARGET;
        }
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPreparedPattern prepared = bus.preparePattern(stack);
            if (prepared != null) {
                return insertPrepared(prepared, knownUnique);
            }
        }
        return ECOPatternInsertionResult.INCOMPATIBLE;
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertPrepared(prepared, false);
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertPrepared(prepared, true);
    }

    private ECOPatternInsertionResult insertPrepared(ECOPreparedPattern prepared, boolean knownUnique) {
        ECOPatternInsertionResult last = ECOPatternInsertionResult.NO_TARGET;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPatternInsertionResult result = knownUnique
                    ? bus.insertPreparedPatternKnownUnique(prepared) : bus.insertPreparedPattern(prepared);
            if (result == ECOPatternInsertionResult.INSERTED || result == ECOPatternInsertionResult.ALREADY_PRESENT) {
                return result;
            }
            // A valid native insertion checks the whole logical domain before checking space.
            // Once it reports NO_SPACE, later buses can reuse both the decode and uniqueness check.
            if (result == ECOPatternInsertionResult.NO_SPACE) {
                knownUnique = true;
                last = result;
            } else if (last != ECOPatternInsertionResult.NO_SPACE) {
                last = result;
            }
        }
        return last;
    }

    @Override
    public boolean checksLogicalDomainForDuplicates() {
        return true;
    }
}
