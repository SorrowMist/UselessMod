package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Bridges one alloy-furnace AE provider to Data Energistics counted Trinity dispatch.
 *
 * <p>The adapter converts one accepted logical batch into a single scaled pattern push. This preserves the
 * furnace's native queueing and task-splitting behavior while avoiding per-craft provider calls.</p>
 */
final class AlloyFurnaceCountedCraftingAdapter implements CountedCraftingProviderAdapter {
    private final ICraftingProvider provider;
    private final BooleanSupplier online;
    private final CountedCraftingTarget target;
    private final Supplier<@Nullable CraftingTaskContext> taskContext;
    private final ToIntFunction<Boolean> remainingThreads;

    AlloyFurnaceCountedCraftingAdapter(
            @NotNull ICraftingProvider provider,
            @NotNull BooleanSupplier online,
            @NotNull CountedCraftingTarget target) {
        this(provider, online, target, () -> null, ignored -> Integer.MAX_VALUE);
    }

    AlloyFurnaceCountedCraftingAdapter(
            @NotNull ICraftingProvider provider,
            @NotNull BooleanSupplier online,
            @NotNull CountedCraftingTarget target,
            @NotNull Supplier<@Nullable CraftingTaskContext> taskContext,
            @NotNull ToIntFunction<Boolean> remainingThreads) {
        this.provider = provider;
        this.online = online;
        this.target = target;
        this.taskContext = taskContext;
        this.remainingThreads = remainingThreads;
    }

    /** Creates the live adapter used by one standalone advanced alloy furnace. */
    static AlloyFurnaceCountedCraftingAdapter forAdvancedAlloyFurnace(
            @NotNull AdvancedAlloyFurnaceBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                () -> isAdvancedAlloyFurnaceOnline(provider),
                targetFor(identity),
                () -> provider,
                provider::getRemainingAETaskCount);
    }

    /** Creates the live adapter used by one ME pattern assembly and its linked multiblock controller. */
    static AlloyFurnaceCountedCraftingAdapter forMePatternAssembly(
            @NotNull MePatternAssemblyBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                () -> isMePatternAssemblyOnline(provider),
                targetFor(identity),
                provider::getController,
                craftingPattern -> {
                    var controller = provider.getController();
                    return controller == null ? 0 : controller.getRemainingAETaskCount(craftingPattern);
                });
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatch(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        return prepareAdmission(patternDetails, prototype, requestedCount);
    }

    @Override
    public @NotNull List<@NotNull CountedCraftingCapacity> captureCapacity(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        AvailableCapacity capacity = availableCapacity(patternDetails, prototype, requestedCount);
        if (capacity.logicalCrafts() == 0L) {
            return List.of();
        }
        return List.of(new CountedCraftingCapacity(
                target,
                CountedCraftingRoutingMode.TARGETED,
                OptionalLong.of(capacity.logicalCrafts()),
                OptionalLong.of(capacity.maximumSingleBatch())));
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatchForTarget(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount,
            @NotNull CountedCraftingTarget requestedTarget) {
        return target.equals(requestedTarget)
                ? prepareAdmission(patternDetails, prototype, requestedCount)
                : null;
    }

    /** Returns the largest safe count for one physical scaled-pattern submission. */
    static long maximumBatchCount(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        validateRequestedCount(requestedCount);
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

    /** Creates a deep, exact scaled input snapshot without changing the caller-owned prototype. */
    static KeyCounter[] scalePrototype(KeyCounter @NotNull [] prototype, long count) {
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

    private @Nullable CountedCraftingAdmission prepareAdmission(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        long acceptedCount = availableCount(patternDetails, prototype, requestedCount);
        return acceptedCount == 0L
                ? null
                : new AlloyFurnaceCountedCraftingAdmission(this, patternDetails, prototype, acceptedCount);
    }

    private long availableCount(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        return availableCapacity(patternDetails, prototype, requestedCount).logicalCrafts();
    }

    private AvailableCapacity availableCapacity(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        validateRequestedCount(requestedCount);
        if (!online.getAsBoolean()) {
            return AvailableCapacity.EMPTY;
        }
        IPatternDetails original = SmartDoublingPatterns.unwrap(patternDetails);
        if (!provider.getAvailablePatterns().contains(original)) {
            return AvailableCapacity.EMPTY;
        }
        long arithmeticMaximum = maximumBatchCount(
                patternDetails,
                prototype,
                requestedCount);
        CapacityLimits limits = maximumRecipeBatchCount(patternDetails, prototype, arithmeticMaximum);
        if (limits.logicalMaximum() == 0L) {
            return AvailableCapacity.EMPTY;
        }

        IPatternDetails executionPattern = SmartDoublingPatterns.unwrap(patternDetails);
        boolean craftingPattern = executionPattern instanceof IMolecularAssemblerSupportedPattern;
        int availableThreads = Math.max(0, this.remainingThreads.applyAsInt(craftingPattern));
        if (craftingPattern) {
            CraftingTaskContext context = this.taskContext.get();
            long perThreadCapacity = context == null
                    ? 1L : Math.max(1L, context.getCraftingPatternCapacity());
            long aggregateCapacity = saturatingMultiply(perThreadCapacity, availableThreads);
            long logicalCrafts = Math.min(limits.logicalMaximum(), aggregateCapacity);
            long maximumSingleBatch = logicalCrafts;
            return logicalCrafts == 0L || maximumSingleBatch == 0L
                    ? AvailableCapacity.EMPTY
                    : new AvailableCapacity(logicalCrafts, maximumSingleBatch);
        }
        if (limits.singleThreadMaximum() == 0L) {
            return AvailableCapacity.EMPTY;
        }
        long aggregateMaximum = saturatingMultiply(limits.singleThreadMaximum(), availableThreads);
        long logicalCrafts = Math.min(limits.logicalMaximum(), aggregateMaximum);
        long maximumSingleBatch = Math.min(limits.logicalMaximum(), limits.singleThreadMaximum());
        return logicalCrafts == 0L || maximumSingleBatch == 0L
                ? AvailableCapacity.EMPTY
                : new AvailableCapacity(logicalCrafts, maximumSingleBatch);
    }

    private CapacityLimits maximumRecipeBatchCount(
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long arithmeticMaximum) {
        CraftingTaskContext context = this.taskContext.get();
        if (arithmeticMaximum == 0L) {
            return CapacityLimits.EMPTY;
        }
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        if (execution.pattern() instanceof IMolecularAssemblerSupportedPattern) {
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

    private boolean dispatch(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long count) {
        if (availableCount(patternDetails, prototype, count) < count) {
            return false;
        }
        // 统一走 SmartDoublingPatterns.scale：合成样板必须保留 IMolecularAssemblerSupportedPattern 身份，
        // 否则接收方会把它当成处理样板去查合金炉配方。
        IPatternDetails scaledPattern = SmartDoublingPatterns.scale(patternDetails, count);
        KeyCounter[] scaledPrototype = scalePrototype(prototype, count);
        return provider.pushPattern(scaledPattern, scaledPrototype);
    }

    static CountedCraftingTarget targetFor(@NotNull PatternProviderIdentity identity) {
        String digest = identity.digest();
        return CountedCraftingTarget.machine(digest, digest);
    }

    private static boolean isAdvancedAlloyFurnaceOnline(@NotNull AdvancedAlloyFurnaceBlockEntity provider) {
        Level level = provider.getLevel();
        return level != null
                && !level.isClientSide
                && !provider.isRemoved()
                && provider.getMainNode().isActive();
    }

    private static boolean isMePatternAssemblyOnline(@NotNull MePatternAssemblyBlockEntity provider) {
        Level level = provider.getLevel();
        return level != null
                && !level.isClientSide
                && !provider.isRemoved()
                && provider.getMainNode().isActive()
                && provider.getController() != null;
    }

    private static void validateRequestedCount(long requestedCount) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting amount must be positive");
        }
    }

    private static long saturatingMultiply(long left, int right) {
        if (left <= 0L || right <= 0) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long divideByBigInteger(long dividend, BigInteger divisor) {
        if (dividend <= 0L || divisor.signum() <= 0) {
            return 0L;
        }
        BigInteger result = BigInteger.valueOf(dividend).divide(divisor);
        return result.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : result.longValueExact();
    }

    private record CapacityLimits(long logicalMaximum, long singleThreadMaximum) {
        private static final CapacityLimits EMPTY = new CapacityLimits(0L, 0L);
    }

    private record AvailableCapacity(long logicalCrafts, long maximumSingleBatch) {
        private static final AvailableCapacity EMPTY = new AvailableCapacity(0L, 0L);
    }

    /** One-shot admission that owns only temporary dispatch references until it is committed. */
    private static final class AlloyFurnaceCountedCraftingAdmission implements CountedCraftingAdmission {
        private AdmissionState state;
        private final long count;
        private boolean transferredInputOwnership;

        private AlloyFurnaceCountedCraftingAdmission(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] preparedPrototype,
                long count) {
            this.state = new PreparedAdmissionState(adapter, patternDetails, preparedPrototype);
            this.count = count;
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (!(state instanceof PreparedAdmissionState(
                    AlloyFurnaceCountedCraftingAdapter adapter,
                    IPatternDetails patternDetails,
                    KeyCounter[] preparedPrototype))) {
                throw new IllegalStateException("Admission has already been committed");
            }
            if (prototype != preparedPrototype) {
                throw new IllegalArgumentException("Admission must be committed with its prepared prototype");
            }
            state = ReleasedAdmissionState.RELEASED;
            boolean accepted = adapter.dispatch(patternDetails, prototype, count);
            transferredInputOwnership = accepted;
            return accepted;
        }

        /** The admission's retained state is either dispatchable once or already released. */
        private sealed interface AdmissionState permits PreparedAdmissionState, ReleasedAdmissionState {}

        /** Retains the server-thread data required to execute exactly one provider dispatch. */
        private record PreparedAdmissionState(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype) implements AdmissionState {}

        /** Drops all temporary references as soon as the only commit attempt begins. */
        private enum ReleasedAdmissionState implements AdmissionState {
            RELEASED
        }
    }
}
