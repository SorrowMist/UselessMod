package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class AlloyFurnaceBatchScaling {
    private AlloyFurnaceBatchScaling() {
    }

    /** Returns the largest safe count for one physical scaled-pattern submission. */
    public static long maximumBatchCount(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        if (requestedCount <= 0L) return 0L;
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        long maximumCount = SmartDoublingPatterns.maximumSafeMultiplier(execution.pattern())
                / execution.operationsPerPush();
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("Crafting input amounts must not be negative");
                }
                if (amount > 0L) {
                    maximumCount = Math.min(maximumCount, Long.MAX_VALUE / amount);
                }
            }
        }
        return Math.min(requestedCount, maximumCount);
    }

    public static KeyCounter[] scaleInputs(KeyCounter[] prototype, long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("Counted crafting batch size must be positive");
        }
        KeyCounter[] scaled = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = prototype[index];
            KeyCounter targetCounter = new KeyCounter();
            for (var entry : source) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("Crafting input amounts must not be negative");
                }
                targetCounter.add(entry.getKey(), Math.multiplyExact(amount, count));
            }
            scaled[index] = targetCounter;
        }
        return scaled;
    }

    public static CapacityLimits recipeCapacity(
            @Nullable CraftingTaskContext context,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long arithmeticMaximum) {
        if (arithmeticMaximum == 0L) {
            return CapacityLimits.EMPTY;
        }
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        if (execution.pattern() instanceof IMolecularAssemblerSupportedPattern) {
            // 合成样板由本机在虚拟 3×3 上自执行，单次推送即可折叠整批，不存在配方侧并行概念。
            return new CapacityLimits(arithmeticMaximum, arithmeticMaximum);
        }
        if (context == null) {
            return new CapacityLimits(arithmeticMaximum, arithmeticMaximum);
        }
        AdvancedAlloyFurnaceRecipe recipe = context.resolveTaskRecipe(
                patternDetails,
                List.of(),
                List.of(),
                compactInputs(prototype),
                execution.operationsPerPush());
        if (recipe == null) {
            return CapacityLimits.EMPTY;
        }

        if (execution.pattern() instanceof OmniversalPatternDetails
                && AlloyFurnaceBigIntegerCrafting.supports(context)) {
            // 安全阀：本机一旦吃不下（档次/模具/能量任一不满足），必须发布「零容量」而不是带
            // machine identity 的容量 —— DE 的 exact 分支一旦接管就不会再回落长版路径，
            // 先发布容量再在提交时拒绝会把该 provider 卡死。
            if (!context.isTaskRecipeAvailable(recipe)) {
                return CapacityLimits.EMPTY;
            }
            BigInteger energyCap = AlloyFurnaceBigIntegerCrafting.maximumCountForEnergy(context, recipe);
            if (energyCap != null && energyCap.signum() <= 0) {
                return CapacityLimits.EMPTY;
            }
        }

        long manualOperations = SmartDoublingPatterns.manualOperationsPerPattern(
                recipe, execution.pattern());
        if (manualOperations == 0L) {
            return CapacityLimits.EMPTY;
        }
        BigInteger wrapperOperations = BigInteger.valueOf(execution.operationsPerPush());
        BigInteger operations = wrapperOperations
                .multiply(BigInteger.valueOf(manualOperations));
        long maximum;
        if (usesRecipeOutputs(execution.pattern())) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
            recipe.outputs().forEach(output -> mergeOutput(outputs, GenericStack.fromItemStack(output)));
            recipe.outputFluids().forEach(output -> mergeOutput(outputs, GenericStack.fromFluidStack(output)));
            recipe.keyOutputs().forEach(output -> mergeOutput(outputs, output));
            maximum = limitByOutputs(arithmeticMaximum, outputs, operations);
        } else {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> declaredOutputs = new Object2ObjectLinkedOpenHashMap<>();
            execution.pattern().getOutputs().forEach(output -> mergeOutput(declaredOutputs, output));
            maximum = limitByOutputs(arithmeticMaximum, declaredOutputs, wrapperOperations);
            if (maximum == 0L) {
                return CapacityLimits.EMPTY;
            }

            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> hiddenOutputs = new Object2ObjectLinkedOpenHashMap<>();
            for (GenericStack output : recipe.keyOutputs()) {
                boolean declared = execution.pattern().getOutputs().stream()
                        .anyMatch(patternOutput -> output.what().equals(patternOutput.what()));
                if (!declared) {
                    mergeOutput(hiddenOutputs, output);
                }
            }
            maximum = limitByOutputs(maximum, hiddenOutputs, operations);
        }

        if (maximum == 0L) {
            return CapacityLimits.EMPTY;
        }
        long parallel = Math.max(1L, context.getTaskParallel(recipe, context.resolveTaskEffect(recipe)));
        long singleThreadMaximum = divideByBigInteger(parallel, operations);
        return singleThreadMaximum == 0L
                ? CapacityLimits.EMPTY
                : new CapacityLimits(maximum, singleThreadMaximum);
    }

    private static long limitByOutputs(
            long currentMaximum,
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs,
            BigInteger operationMultiplier) {
        BigInteger maximumLong = BigInteger.valueOf(Long.MAX_VALUE);
        long maximum = currentMaximum;
        for (BigInteger amount : outputs.values()) {
            BigInteger perLogicalCraft = amount.multiply(operationMultiplier);
            if (perLogicalCraft.compareTo(maximumLong) > 0) {
                return 0L;
            }
            maximum = Math.min(maximum, maximumLong.divide(perLogicalCraft).longValueExact());
        }
        return maximum;
    }

    private static boolean usesRecipeOutputs(IPatternDetails pattern) {
        return pattern instanceof OmniversalPatternDetails
                || pattern instanceof DynamicComponentPattern dynamic && dynamic.usesDynamicOutputs();
    }

    private static List<GenericStack> compactInputs(KeyCounter[] prototype) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                inputs.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        BigInteger maximumLong = BigInteger.valueOf(Long.MAX_VALUE);
        ArrayList<GenericStack> result = new ArrayList<>(inputs.size());
        for (var entry : inputs.object2ObjectEntrySet()) {
            BigInteger remaining = entry.getValue();
            while (remaining.compareTo(maximumLong) > 0) {
                result.add(new GenericStack(entry.getKey(), Long.MAX_VALUE));
                remaining = remaining.subtract(maximumLong);
            }
            if (remaining.signum() > 0) {
                result.add(new GenericStack(entry.getKey(), remaining.longValueExact()));
            }
        }
        return result;
    }

    private static void mergeOutput(
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs,
            @Nullable GenericStack output) {
        if (output == null || output.amount() <= 0L) {
            return;
        }
        outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
    }

    private static long divideByBigInteger(long dividend, BigInteger divisor) {
        if (dividend <= 0L || divisor.signum() <= 0) {
            return 0L;
        }
        BigInteger result = BigInteger.valueOf(dividend).divide(divisor);
        return result.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : result.longValueExact();
    }

    public record CapacityLimits(long logicalMaximum, long singleThreadMaximum) {
        private static final CapacityLimits EMPTY = new CapacityLimits(0L, 0L);
    }

}
