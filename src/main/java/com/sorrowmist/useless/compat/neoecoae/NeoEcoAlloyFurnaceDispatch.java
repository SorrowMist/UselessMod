package com.sorrowmist.useless.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceBatchScaling;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.function.BooleanSupplier;

record NeoEcoAlloyFurnaceDispatch(ICraftingProvider provider) implements ECOFastPathDispatchProvider {
    @Override
    public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        CraftingTaskContext machine = eligibleMachine(context);
        if (machine == null) return null;
        var pattern = context.pattern();
        KeyCounter[] prototype = context.inputCounters();
        var target = exactTarget(context, machine);
        if (target != null) {
            long capacity = target.capacity(pattern, prototype, BigInteger.valueOf(Long.MAX_VALUE))
                    .accepted().longValueExact();
            if (capacity > 0L) {
                return new Preparation(capacity, null, false, batch -> {
                    if (liveMachine(context) != machine || provider.isBusy()) return false;
                    BigInteger count = BigInteger.valueOf(batch.craftCount());
                    return commitWholeBatch(target, pattern, prototype, count);
                }, true);
            }
        }

        long capacity = scaledCapacity(context, machine, prototype);
        if (capacity <= 1L) return null;
        return new Preparation(capacity, null, false, batch -> {
            if (liveMachine(context) != machine || provider.isBusy()) return false;
            long count = batch.craftCount();
            if (count <= 1L) return false;
            var scaled = SmartDoublingPatterns.scale(pattern, count, context.level());
            var inputs = AlloyFurnaceBatchScaling.scaleInputs(prototype, count);
            return transfer(() -> provider.pushPattern(scaled, inputs));
        });
    }

    @Override
    public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        CraftingTaskContext machine = eligibleMachine(context);
        if (machine == null || requested.signum() <= 0) return null;
        var target = exactTarget(context, machine);
        if (target == null) return null;
        KeyCounter[] prototype = context.inputCounters();
        var ticket = target.admit(context.pattern(), prototype, requested, null);
        if (ticket == null) return null;
        return new ExactPreparation(ticket.count(), () -> liveMachine(context) == machine
                && !provider.isBusy() && commit(ticket, prototype));
    }

    private @Nullable CraftingTaskContext eligibleMachine(ECOBatchDispatchContext context) {
        if (!DynamicPatternCpuStateManager.INSTANCE.hasEcoJob(context.craftingJobId())
                || !context.containerItems().isEmpty()) return null;
        CraftingTaskContext machine = liveMachine(context);
        IPatternDetails original = SmartDoublingPatterns.unwrap(context.pattern());
        return machine != null && !provider.isBusy() && machine.isTaskExecutionEnabled()
                && provider.getAvailablePatterns().contains(original)
                && SmartDoublingPatterns.reusableInputSlots(original, context.level()).isEmpty() ? machine : null;
    }

    private @Nullable CraftingTaskContext liveMachine(ECOBatchDispatchContext context) {
        if (provider instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            return furnace.getLevel() == context.level() && !context.level().isClientSide
                    && !furnace.isRemoved() && furnace.getMainNode().isActive() ? furnace : null;
        }
        if (provider instanceof MePatternAssemblyBlockEntity assembly) {
            return assembly.getLevel() == context.level() && !context.level().isClientSide
                    && !assembly.isRemoved() && assembly.getMainNode().isActive() ? assembly.getController() : null;
        }
        return null;
    }

    private @Nullable AlloyFurnaceBigIntegerTarget exactTarget(ECOBatchDispatchContext context,
            CraftingTaskContext machine) {
        var pattern = context.pattern();
        var dynamic = DynamicPatternExecution.resolve(pattern);
        if (!(provider instanceof MePatternAssemblyBlockEntity assembly)
                || SmartDoublingPatterns.unwrap(pattern) != pattern
                || (dynamic != null && dynamic.pattern().usesDynamicOutputs())) return null;
        return assembly.getController() == machine ? assembly.bigIntegerTarget() : null;
    }

    private static long scaledCapacity(ECOBatchDispatchContext context, CraftingTaskContext machine,
            KeyCounter[] prototype) {
        var pattern = context.pattern();
        if (!SmartDoublingPatterns.canScale(pattern)) return 0L;
        long maximum = AlloyFurnaceBatchScaling.maximumBatchCount(pattern, prototype, dynamicOutputLimit(pattern));
        var capacity = AlloyFurnaceBatchScaling.recipeCapacity(machine, pattern, prototype, maximum);
        if (SmartDoublingPatterns.unwrap(pattern) instanceof IMolecularAssemblerSupportedPattern) {
            return Math.min(capacity.logicalMaximum(), machine.getCraftingPatternCapacity());
        }
        return Math.min(capacity.logicalMaximum(), capacity.singleThreadMaximum());
    }

    private static long dynamicOutputLimit(IPatternDetails pattern) {
        var dynamic = DynamicPatternExecution.resolve(pattern);
        if (dynamic == null || !dynamic.pattern().usesDynamicOutputs()) return Long.MAX_VALUE;
        var outputs = dynamic.pattern().getOutputs();
        var itemIds = new HashSet<ResourceLocation>();
        long limit = Long.MAX_VALUE / dynamic.copies();
        for (int slot = 0; slot < outputs.size(); slot++) {
            if (!dynamic.pattern().isItemIdOutput(slot)) continue;
            var output = outputs.get(slot);
            if (!(output.what() instanceof AEItemKey key) || !itemIds.add(key.getId()) || output.amount() <= 0L) {
                return 0L;
            }
            limit = Math.min(limit, Long.MAX_VALUE / dynamic.copies() / output.amount());
        }
        return limit;
    }

    private static boolean commit(AlloyFurnaceBigIntegerBatch ticket, KeyCounter[] prototype) {
        return transfer(() -> ticket.commit(prototype));
    }

    static boolean commitWholeBatch(AlloyFurnaceBigIntegerTarget target, IPatternDetails pattern,
            KeyCounter[] prototype, BigInteger count) {
        var ticket = target.admit(pattern, prototype, count, null);
        return ticket != null && count.equals(ticket.count()) && commit(ticket, prototype);
    }

    private static boolean transfer(BooleanSupplier dispatch) {
        try {
            return dispatch.getAsBoolean();
        } catch (RuntimeException | Error failure) {
            throw new ECOIndeterminateBatchException("Alloy furnace batch acceptance is uncertain", failure);
        }
    }
}
