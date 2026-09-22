package com.sorrowmist.useless.content.blockentities.multiblock;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.PagedMenuPageMemory;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTask;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PassivePatternInputTransaction;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.core.component.MultiblockPartData;
import com.sorrowmist.useless.core.component.PassiveHatchSettings;
import com.sorrowmist.useless.world.inventory.ExternalInventoryStore;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModItems;
import com.sorrowmist.useless.network.PassiveCraftingStatusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Pattern inventory and independent passive task owner for an optional multiblock casing hatch. */
public final class PassiveCraftingHatchBlockEntity extends BlockEntity
        implements MenuProvider, CraftingTaskContext {
    public static final int MAX_PATTERN_SLOTS = RecoverableItemStackHandler.MAX_SLOTS;
    public static final int PATTERN_SLOTS = MAX_PATTERN_SLOTS;
    public static final int MENU_DATA_COUNT = 10;
    public static final int MIN_INTERVAL_TICKS = 1;
    public static final int MAX_INTERVAL_TICKS = 72_000;
    public static final int DEFAULT_INTERVAL_TICKS = 1_200;

    private static final ItemStackHandler EMPTY_ITEMS = new ItemStackHandler(0);
    private static final FluidTank[] EMPTY_TANKS = new FluidTank[0];

    private final RecoverableItemStackHandler patterns = new RecoverableItemStackHandler(
            MAX_PATTERN_SLOTS, 0, this::getActivePatternSlots,
            stack -> stack.is(ModItems.OMNIVERSAL_PATTERN.get()), this::inventoryChanged);
    private final PagedMenuPageMemory pageMemory = new PagedMenuPageMemory(this::setChanged);
    private final Map<Integer, CraftingTask> activeTasks = new HashMap<>();
    /**
     * Sparse per-slot batch sizes. A missing entry means the slot follows {@link #multiplier},
     * the global default, so clearing an override never has to guess what the default was.
     */
    private final Map<Integer, Long> slotMultiplierOverrides = new HashMap<>();
    private int statusCapacity = configuredStatusCapacity();
    private SlotState[] idleStates = new SlotState[statusCapacity];
    private String[] idleDetails = new String[statusCapacity];
    private OmniversalPatternDetails[] decodedPatterns = new OmniversalPatternDetails[statusCapacity];
    private boolean[] patternDecodeCached = new boolean[statusCapacity];
    private final ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> taskProgress =
            new ConcurrentHashMap<>();
    private final AtomicInteger totalProgress = new AtomicInteger();
    private final AtomicInteger totalMaxProgress = new AtomicInteger();
    private final ReentrantLock craftingLock = new ReentrantLock();
    private final List<GenericStack> localUnreturnedInputs = new ArrayList<>();
    private final IEnergyManager fallbackEnergy = EnergyManager.builder()
            .capacity(1L).maxReceive(0L).maxExtract(0L).build();

    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private int intervalTicks = DEFAULT_INTERVAL_TICKS;
    private int countdownTicks = DEFAULT_INTERVAL_TICKS;
    private long multiplier = 1L;
    private long recipeCatalogGeneration = -1L;
    @Nullable
    private CompoundTag deferredTasksTag;
    private boolean loading;
    private boolean unloading;
    private boolean statusDirty = true;
    private int statusSyncTimer;
    private int observedActivePatternSlots = -1;
    private long patternStorageRevision;
    @Nullable
    private ExternalInventoryReference inventoryReference;
    @Nullable
    private CompoundTag pendingLegacyInventory;
    private boolean externalInventoryLoaded;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
            return switch (index) {
                case 0 -> controller == null ? 0 : 1;
                case 1 -> controller == null ? 0 : controller.getCoilTier();
                case 2 -> getActivePatternSlots();
                case 3 -> intervalTicks;
                case 4 -> countdownTicks;
                case 5 -> (int) multiplier;
                case 6 -> (int) (multiplier >>> 32);
                case 7 -> (int) getCurrentMaxParallel();
                case 8 -> (int) (getCurrentMaxParallel() >>> 32);
                case 9 -> getConfiguredPatternSlots();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return MENU_DATA_COUNT;
        }
    };

    public PassiveCraftingHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PASSIVE_CRAFTING_HATCH.get(), pos, state);
        for (int slot = 0; slot < statusCapacity; slot++) {
            idleStates[slot] = SlotState.EMPTY;
            idleDetails[slot] = "";
        }
    }

    public RecoverableItemStackHandler getPatterns() {
        ensureExternalInventory();
        return patterns;
    }

    public PagedMenuPageMemory getPageMemory() {
        return pageMemory;
    }

    public long getPatternStorageRevision() {
        return patternStorageRevision;
    }

    public MultiblockPartData createItemData(HolderLookup.Provider registries) {
        return MultiblockPartData.passiveHatch(
                patterns, registries, intervalTicks, multiplier);
    }

    public MultiblockPartData createSettingsItemData() {
        return new MultiblockPartData(MultiblockPartData.CURRENT_VERSION,
                new CompoundTag(), intervalTicks, multiplier);
    }

    public void restoreItemData(MultiblockPartData data, HolderLookup.Provider registries) {
        if (data == null) return;
        loading = true;
        try {
            data.restoreInventory(patterns, registries);
        } finally {
            loading = false;
        }
        patternStorageRevision++;
        intervalTicks = data.intervalTicks() > 0
                ? Math.max(MIN_INTERVAL_TICKS, Math.min(MAX_INTERVAL_TICKS, data.intervalTicks()))
                : DEFAULT_INTERVAL_TICKS;
        multiplier = Math.max(1L, data.multiplier());
        // MultiblockPartData predates per-slot multipliers, so a full restore starts clean.
        slotMultiplierOverrides.clear();
        countdownTicks = intervalTicks;
        deferredTasksTag = null;
        localUnreturnedInputs.clear();
        observedActivePatternSlots = -1;
        clearPatternDecodeCache();
        resetIdleStates();
        statusDirty = true;
        setChanged();
    }

    public void restoreSettings(MultiblockPartData data) {
        if (data == null) return;
        restoreSettings(data.intervalTicks(), data.multiplier(), null);
    }

    public void restoreSettings(PassiveHatchSettings settings) {
        if (settings == null) return;
        restoreSettings(settings.intervalTicks(), settings.multiplier(), settings.slotMultipliers());
    }

    private void restoreSettings(int requestedInterval, long requestedMultiplier,
                                 @Nullable List<PassiveHatchSettings.SlotMultiplier> requestedOverrides) {
        intervalTicks = requestedInterval > 0
                ? Math.max(MIN_INTERVAL_TICKS, Math.min(MAX_INTERVAL_TICKS, requestedInterval))
                : DEFAULT_INTERVAL_TICKS;
        multiplier = Math.max(1L, requestedMultiplier);
        slotMultiplierOverrides.clear();
        if (requestedOverrides != null) {
            for (PassiveHatchSettings.SlotMultiplier entry : requestedOverrides) {
                if (entry == null || !entry.valid() || entry.slot() >= PATTERN_SLOTS) {
                    continue;
                }
                slotMultiplierOverrides.put(entry.slot(), entry.multiplier());
            }
        }
        countdownTicks = intervalTicks;
        statusDirty = true;
        setChanged();
    }

    public void bindExternalInventory(@Nullable ExternalInventoryReference requested,
                                      @Nullable CompoundTag legacyInventory,
                                      HolderLookup.Provider registries) {
        if (level == null || level.isClientSide) return;
        if (externalInventoryLoaded) {
            // 放置方块时 vanilla 会先调 clearRemoved()、后调 setPlacedBy()，前者可能在物品上的引用
            // 尚未写入 BE 组件字段时抢先绑定了临时占位。此处必须用物品携带的引用覆盖它，
            // 否则原数据不会被加载（拆除后重新放置将表现为内容物丢失）。
            if (requested == null || requested.equals(inventoryReference)) return;
            ExternalInventoryStore.release(level, worldPosition, inventoryReference);
        }
        inventoryReference = ExternalInventoryStore.bindAt(
                level, ExternalInventoryKind.PASSIVE_HATCH, requested, worldPosition,
                patterns, legacyInventory, registries);
        ExternalInventoryStore.setReference(this, inventoryReference);
        pendingLegacyInventory = null;
        externalInventoryLoaded = true;
        inventoryChanged();
    }

    private void ensureExternalInventory() {
        if (externalInventoryLoaded || level == null || level.isClientSide) return;
        bindExternalInventory(
                ExternalInventoryStore.getReference(this),
                pendingLegacyInventory,
                level.registryAccess());
    }

    @Nullable
    public ExternalInventoryReference getExternalInventoryReference() {
        ensureExternalInventory();
        return inventoryReference;
    }

    public void releaseExternalInventory() {
        if (level != null) {
            ExternalInventoryStore.save(level, inventoryReference, patterns, level.registryAccess());
            ExternalInventoryStore.release(level, worldPosition, inventoryReference);
        }
    }

    private static int configuredStatusCapacity() {
        return Math.max(1, Math.min(MAX_PATTERN_SLOTS,
                ConfigManager.getOmniversalPassivePatternSlots()));
    }

    private void ensureStatusCapacity() {
        int configured = configuredStatusCapacity();
        if (configured <= statusCapacity) return;
        int oldCapacity = statusCapacity;
        statusCapacity = configured;
        idleStates = java.util.Arrays.copyOf(idleStates, statusCapacity);
        idleDetails = java.util.Arrays.copyOf(idleDetails, statusCapacity);
        decodedPatterns = java.util.Arrays.copyOf(decodedPatterns, statusCapacity);
        patternDecodeCached = java.util.Arrays.copyOf(patternDecodeCached, statusCapacity);
        java.util.Arrays.fill(idleStates, oldCapacity, statusCapacity, SlotState.EMPTY);
        java.util.Arrays.fill(idleDetails, oldCapacity, statusCapacity, "");
    }

    public ContainerData getMenuData() {
        return menuData;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public int getCountdownTicks() {
        return countdownTicks;
    }

    public long getMultiplier() {
        return multiplier;
    }

    /** Batch size a slot actually runs at: its own override, otherwise the global default. */
    public long getSlotMultiplier(int slot) {
        long override = getSlotMultiplierOverride(slot);
        return override > 0L ? override : multiplier;
    }

    /** {@code 0} means "follow the global multiplier"; any other value is an explicit override. */
    public long getSlotMultiplierOverride(int slot) {
        if (slot < 0 || slot >= PATTERN_SLOTS) {
            return 0L;
        }
        Long value = slotMultiplierOverrides.get(slot);
        return value == null ? 0L : Math.max(0L, value);
    }

    public boolean hasSlotMultiplierOverride(int slot) {
        return getSlotMultiplierOverride(slot) > 0L;
    }

    /** Sorted snapshot used for item data, so drops stay byte-for-byte stable. */
    public List<PassiveHatchSettings.SlotMultiplier> slotMultiplierSettings() {
        List<PassiveHatchSettings.SlotMultiplier> result = new ArrayList<>(slotMultiplierOverrides.size());
        for (Map.Entry<Integer, Long> entry : new java.util.TreeMap<>(slotMultiplierOverrides).entrySet()) {
            long value = entry.getValue() == null ? 0L : entry.getValue();
            if (value > 0L) {
                result.add(new PassiveHatchSettings.SlotMultiplier(entry.getKey(), value));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Pins one slot to its own batch size. {@code requested <= 0} clears the override so the slot
     * returns to following the global multiplier. The value is clamped to the coil-tier parallel
     * limit, because a passive batch larger than the machine could ever run is never useful.
     */
    public void setSlotMultiplier(int slot, long requested) {
        if (slot < 0 || slot >= PATTERN_SLOTS) {
            return;
        }
        long clamped = requested <= 0L
                ? 0L : Math.max(1L, Math.min(getCurrentMaxParallel(), requested));
        long previous = getSlotMultiplierOverride(slot);
        if (clamped == previous) {
            return;
        }
        if (clamped <= 0L) {
            slotMultiplierOverrides.remove(slot);
        } else {
            slotMultiplierOverrides.put(slot, clamped);
        }
        statusDirty = true;
        setChanged();
    }

    /** Drops every override, making all slots follow the global multiplier again. */
    public void clearSlotMultipliers() {
        if (slotMultiplierOverrides.isEmpty()) {
            return;
        }
        slotMultiplierOverrides.clear();
        statusDirty = true;
        setChanged();
    }

    public int getActivePatternSlots() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null ? 0 : activeSlotsForCoilTier(controller.getCoilTier());
    }

    public static int activeSlotsForCoilTier(int coilTier) {
        return activeSlotsForCoilTier(coilTier, ConfigManager.getOmniversalPassivePatternSlots());
    }

    static int activeSlotsForCoilTier(int coilTier, int configuredSlots) {
        int tier = Math.max(0, Math.min(UselessCoilBlock.MAX_TIER, coilTier));
        int capacity = Math.max(1, Math.min(MAX_PATTERN_SLOTS, configuredSlots));
        return (capacity * tier + UselessCoilBlock.MAX_TIER - 1) / UselessCoilBlock.MAX_TIER;
    }

    public int getConfiguredPatternSlots() {
        return ConfigManager.getOmniversalPassivePatternSlots();
    }

    public long getCurrentMaxParallel() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null ? 1L : Math.max(1L, controller.getPassiveCraftingMaxParallel());
    }

    @Override
    public long getCraftingPatternCapacity() {
        return getCurrentMaxParallel();
    }

    public void applySettings(int requestedInterval, long requestedMultiplier) {
        if (getController() == null) {
            return;
        }
        int newInterval = Math.max(MIN_INTERVAL_TICKS,
                Math.min(MAX_INTERVAL_TICKS, requestedInterval));
        long newMultiplier = Math.max(1L, Math.min(getCurrentMaxParallel(), requestedMultiplier));
        if (newInterval == intervalTicks && newMultiplier == multiplier) {
            return;
        }
        intervalTicks = newInterval;
        multiplier = newMultiplier;
        countdownTicks = intervalTicks;
        statusDirty = true;
        setChanged();
    }

    public void linkController(@Nullable BlockPos newControllerPos, long generation) {
        if (unloading || isRemoved()) {
            return;
        }
        BlockPos immutable = newControllerPos == null ? null : newControllerPos.immutable();
        if (Objects.equals(controllerPos, immutable) && structureGeneration == generation) {
            clampMultiplier();
            return;
        }

        if (controllerPos != null && immutable != null && !controllerPos.equals(immutable)) {
            MultiblockAlloyFurnaceCoreBlockEntity previousController = getRawController();
            cancelAllTasks();
            if (previousController != null) {
                flushLocalUnreturnedInputs(previousController);
            }
        }
        controllerPos = immutable;
        structureGeneration = generation;
        clampMultiplier();
        statusDirty = true;
        setChanged();
    }

    /** True when this hatch's raw association belongs to the given core. */
    public boolean isLinkedToController(BlockPos controllerPos) {
        return Objects.equals(this.controllerPos, controllerPos);
    }

    /** Passive hatches can only execute work for one formed controller. */
    public boolean isClaimedByOtherController(BlockPos controllerPos) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && !controller.getBlockPos().equals(controllerPos);
    }

    @Nullable
    public MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (level == null || controllerPos == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        if (level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isFormed()
                && core.isPassiveHatchLinked(worldPosition, structureGeneration)) {
            return core;
        }
        return null;
    }

    @Nullable
    private MultiblockAlloyFurnaceCoreBlockEntity getRawController() {
        if (level == null || controllerPos == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                ? core : null;
    }

    /** Called by the linked controller after normal AE tasks have consumed their tick. */
    public boolean serverTickFromController(MultiblockAlloyFurnaceCoreBlockEntity controller) {
        if (unloading || isRemoved() || level == null || level.isClientSide || controller != getController()) {
            return false;
        }
        loadDeferredTasks();
        reconcileActivePatternSlots();
        flushLocalUnreturnedInputs(controller);

        long currentCatalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (recipeCatalogGeneration != currentCatalogGeneration) {
            recipeCatalogGeneration = currentCatalogGeneration;
            clearPatternDecodeCache();
            resetIdleStates();
        }

        if (!controller.isTaskExecutionEnabled()) {
            flushStatusUpdates();
            return false;
        }

        boolean progressed = tickActiveTasks();
        if (--countdownTicks <= 0) {
            countdownTicks = intervalTicks;
            runPassiveCycle(controller);
            setChanged();
        }
        flushStatusUpdates();
        return progressed;
    }

    /** Keeps viewer status current while the structure is unavailable without advancing work. */
    public void serverTickStandalone() {
        if (unloading || isRemoved() || level == null || level.isClientSide || getController() != null) {
            return;
        }
        loadDeferredTasks();
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        if (controller != null) {
            flushLocalUnreturnedInputs(controller);
        }
        flushStatusUpdates();
    }

    private boolean tickActiveTasks() {
        boolean progressed = false;
        List<Integer> slots = new ArrayList<>(activeTasks.keySet());
        slots.sort(Integer::compareTo);
        for (int slot : slots) {
            CraftingTask task = activeTasks.get(slot);
            if (task == null) {
                continue;
            }
            task.tick();
            progressed |= task.progressedLastTick();
            if (task.isProcessingComplete()) {
                activeTasks.remove(slot);
                setIdleState(slot, patterns.getStackInSlot(slot).isEmpty()
                        ? SlotState.EMPTY : SlotState.READY, "");
                setChanged();
            }
        }
        return progressed;
    }

    public boolean hasWork() {
        if (!activeTasks.isEmpty() || deferredTasksTag != null) {
            return true;
        }
        int activeSlots = getActivePatternSlots();
        for (int slot = 0; slot < activeSlots; slot++) {
            if (!patterns.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void runPassiveCycle(MultiblockAlloyFurnaceCoreBlockEntity controller) {
        List<Candidate> candidates = new ArrayList<>();
        int activeSlots = getActivePatternSlots();
        for (int slot = 0; slot < activeSlots; slot++) {
            if (activeTasks.containsKey(slot)) {
                continue;
            }
            ItemStack stack = patterns.getStackInSlot(slot);
            if (stack.isEmpty()) {
                setIdleState(slot, SlotState.EMPTY, "");
                continue;
            }

            OmniversalPatternDetails omniversal = decodePattern(slot, stack);
            if (omniversal == null) {
                setIdleState(slot, SlotState.INVALID_PATTERN, "");
                continue;
            }
            long slotMultiplier = getSlotMultiplier(slot);
            if (!amountsFit(omniversal, slotMultiplier)) {
                setIdleState(slot, SlotState.INVALID_PATTERN, "amount_overflow");
                continue;
            }
            if (!controller.isTaskRecipeAvailable(omniversal.recipe())) {
                setIdleState(slot, SlotState.MISSING_MOLD, "");
                continue;
            }
            candidates.add(new Candidate(slot, omniversal, slotMultiplier));
        }

        if (candidates.isEmpty()) {
            return;
        }
        MultiblockAlloyFurnaceCoreBlockEntity.AeNetworkAccess access = controller.getAeNetworkAccess();
        if (access == null) {
            for (Candidate candidate : candidates) {
                setIdleState(candidate.slot, SlotState.AE_OFFLINE, "");
            }
            return;
        }

        List<PassivePatternInputTransaction.Result> extractions =
                PassivePatternInputTransaction.extractAll(
                        candidates.stream().map(Candidate::pattern).toList(),
                        candidates.stream().mapToLong(Candidate::multiplier).toArray(),
                        level, access.storage(), access::cachedInventory, access.source(),
                        this::stashUnreturnedInput);
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            PassivePatternInputTransaction.Result extraction = extractions.get(index);
            if (!extraction.successful()) {
                SlotState state = extraction.failure() == PassivePatternInputTransaction.Failure.MISSING_INPUT
                        ? SlotState.MISSING_INPUT
                        : extraction.failure() == PassivePatternInputTransaction.Failure.AMOUNT_OVERFLOW
                        ? SlotState.INVALID_PATTERN : SlotState.AE_OFFLINE;
                String detail = extraction.missingKey() == null
                        ? "" : extraction.missingKey().getDisplayName().getString();
                setIdleState(candidate.slot(), state, detail);
                continue;
            }

            CraftingTask task = new CraftingTask(
                    candidate.slot(), candidate.pattern(), extraction.inputs(), candidate.multiplier(), this);
            if (!task.canStartNow()) {
                CraftingTask.returnInputsToAE(Collections.singletonList(extraction.inputs()), this);
                setIdleState(candidate.slot(), SlotState.MISSING_MOLD, task.getWaitingDetail());
                continue;
            }
            activeTasks.put(candidate.slot(), task);
            setIdleState(candidate.slot(), SlotState.RUNNING, "");
            setChanged();
        }
    }

    private static boolean amountsFit(OmniversalPatternDetails pattern, long operations) {
        try {
            Map<AEKey, Long> totals = new HashMap<>();
            for (ItemStack output : pattern.recipe().outputs()) {
                addScaledAmount(totals, AEItemKey.of(output), output.getCount(), operations);
            }
            for (FluidStack output : pattern.recipe().outputFluids()) {
                addScaledAmount(totals, AEFluidKey.of(output), output.getAmount(), operations);
            }
            for (GenericStack output : pattern.recipe().keyOutputs()) {
                addScaledAmount(totals, output.what(), output.amount(), operations);
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static void addScaledAmount(
            Map<AEKey, Long> totals, @Nullable AEKey key, long amount, long operations) {
        if (key == null || amount <= 0L) {
            return;
        }
        long scaled = Math.multiplyExact(amount, operations);
        totals.merge(key, scaled, Math::addExact);
    }

    public void prepareForRemoval() {
        loadDeferredTasks();
        cancelAllTasks();
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        if (controller != null) {
            flushLocalUnreturnedInputs(controller);
        }
    }

    public void prepareForControllerRemoval(MultiblockAlloyFurnaceCoreBlockEntity controller) {
        if (controller == null || level == null || controller.getLevel() != level
                || getRawController() != controller) {
            return;
        }
        loadDeferredTasks();
        cancelAllTasks();
        flushLocalUnreturnedInputs(controller);
    }

    public void cancelAllTasks() {
        for (CraftingTask task : new ArrayList<>(activeTasks.values())) {
            task.cancel();
        }
        activeTasks.clear();
        taskProgress.clear();
        totalProgress.set(0);
        totalMaxProgress.set(0);
        deferredTasksTag = null;
        resetIdleStates();
        statusDirty = true;
        setChanged();
    }

    private void loadDeferredTasks() {
        CompoundTag tasksTag = deferredTasksTag;
        deferredTasksTag = null;
        if (tasksTag == null || level == null) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();
        ListTag tasks = tasksTag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int index = 0; index < tasks.size(); index++) {
            CompoundTag entry = tasks.getCompound(index);
            int slot = entry.getInt("Slot");
            CompoundTag taskTag = entry.getCompound("Task");
            if (slot < 0 || slot >= PATTERN_SLOTS || activeTasks.containsKey(slot)) {
                CraftingTask.returnSavedMaterials(taskTag, this, registries);
                continue;
            }
            CraftingTask task = CraftingTask.load(taskTag, level, this, registries);
            if (task == null) {
                CraftingTask.returnSavedMaterials(taskTag, this, registries);
            } else {
                activeTasks.put(slot, task);
            }
        }
        statusDirty = true;
    }

    /**
     * A server-config reduction turns excess pattern slots into recovery-only
     * storage. Any task already using such a slot must return its materials
     * before the slot becomes inactive.
     */
    private void reconcileActivePatternSlots() {
        int activeSlots = getActivePatternSlots();
        if (observedActivePatternSlots == activeSlots) {
            return;
        }
        observedActivePatternSlots = activeSlots;
        boolean changed = false;
        for (int slot : new ArrayList<>(activeTasks.keySet())) {
            if (slot < activeSlots) {
                continue;
            }
            CraftingTask task = activeTasks.remove(slot);
            if (task != null) {
                task.cancel();
                taskProgress.remove(slot);
                changed = true;
            }
        }
        resetIdleStates();
        statusDirty = true;
        if (changed) {
            setChanged();
        }
    }

    private CompoundTag saveTasks(HolderLookup.Provider registries) {
        if (deferredTasksTag != null) {
            return deferredTasksTag.copy();
        }
        CompoundTag result = new CompoundTag();
        ListTag tasks = new ListTag();
        activeTasks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag task = new CompoundTag();
                    task.putInt("Slot", entry.getKey());
                    task.put("Task", entry.getValue().save(registries));
                    tasks.add(task);
                });
        result.put("Tasks", tasks);
        return result;
    }

    private void inventoryChanged() {
        if (loading || unloading || isRemoved()) {
            return;
        }
        patternStorageRevision++;
        if (externalInventoryLoaded && level != null && !level.isClientSide) {
            ExternalInventoryStore.save(level, inventoryReference, patterns, level.registryAccess());
        }
        clearPatternDecodeCache();
        countdownTicks = intervalTicks;
        resetIdleStates();
        statusDirty = true;
        setChanged();
    }

    @Nullable
    private OmniversalPatternDetails decodePattern(int slot, ItemStack stack) {
        ensureStatusCapacity();
        if (slot < 0 || slot >= statusCapacity) return null;
        if (!patternDecodeCached[slot]) {
            IPatternDetails decoded = AdvancedAlloyFurnacePatternResolver.decode(stack, level);
            decodedPatterns[slot] = decoded instanceof OmniversalPatternDetails omniversal
                    ? omniversal : null;
            patternDecodeCached[slot] = true;
        }
        return decodedPatterns[slot];
    }

    private void clearPatternDecodeCache() {
        java.util.Arrays.fill(decodedPatterns, null);
        java.util.Arrays.fill(patternDecodeCached, false);
    }

    private void resetIdleStates() {
        ensureStatusCapacity();
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            if (slot >= statusCapacity) break;
            if (!activeTasks.containsKey(slot)) {
                setIdleState(slot, patterns.getStackInSlot(slot).isEmpty()
                        ? SlotState.EMPTY : SlotState.READY, "");
            }
        }
    }

    private void setIdleState(int slot, SlotState state, String detail) {
        ensureStatusCapacity();
        if (slot < 0 || slot >= PATTERN_SLOTS) {
            return;
        }
        if (slot >= statusCapacity) return;
        String safeDetail = detail == null ? "" : detail;
        if (idleStates[slot] != state || !Objects.equals(idleDetails[slot], safeDetail)) {
            idleStates[slot] = state;
            idleDetails[slot] = safeDetail;
            statusDirty = true;
        }
    }

    private List<SlotStatus> getSlotStatusSnapshot(int firstSlot, int count) {
        ensureStatusCapacity();
        int start = Math.max(0, Math.min(PATTERN_SLOTS, firstSlot));
        int end = Math.max(start, Math.min(PATTERN_SLOTS, start + Math.max(0, count)));
        List<SlotStatus> result = new ArrayList<>(end - start);
        boolean enabled = isTaskExecutionEnabled();
        for (int slot = start; slot < end; slot++) {
            CraftingTask task = activeTasks.get(slot);
            long slotMultiplier = getSlotMultiplierOverride(slot);
            if (task == null) {
                if (slot >= statusCapacity) {
                    result.add(new SlotStatus(slot, SlotState.EMPTY, 0, 0, "", slotMultiplier));
                    continue;
                }
                result.add(new SlotStatus(slot, idleStates[slot], 0, 0, idleDetails[slot], slotMultiplier));
                continue;
            }
            AdvancedAlloyFurnaceAeManager.AETaskProgress progress = taskProgress.get(slot);
            SlotState state;
            if (task.isAwaitingOutputFlush()) {
                state = SlotState.WAITING_OUTPUT;
            } else if (!enabled) {
                state = SlotState.PAUSED;
            } else if (progress != null && isWaitingForMold(progress.getStatusKey())) {
                state = SlotState.MISSING_MOLD;
            } else {
                state = SlotState.RUNNING;
            }
            result.add(new SlotStatus(slot, state,
                    progress == null ? 0 : progress.getProgress(),
                    progress == null ? 1 : progress.getMaxProgress(),
                    progress == null ? "" : progress.getStatusDetail(),
                    slotMultiplier));
        }
        return List.copyOf(result);
    }

    private static boolean isWaitingForMold(String statusKey) {
        return "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold".equals(statusKey)
                || "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold".equals(statusKey)
                || "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold_hub".equals(statusKey);
    }

    public void requestStatusSync() {
        statusDirty = true;
        statusSyncTimer = 20;
    }

    private void flushStatusUpdates() {
        if (!statusDirty && ++statusSyncTimer < 20) {
            return;
        }
        statusDirty = false;
        statusSyncTimer = 0;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (var player : serverLevel.players()) {
            if (player.containerMenu instanceof PassiveCraftingHatchMenu menu
                    && menu.getBlockPos().equals(worldPosition)) {
                PacketDistributor.sendToPlayer(player,
                        new PassiveCraftingStatusPacket(menu.containerId, worldPosition,
                                getSlotStatusSnapshot(menu.getPage() * PagedRecoverableMenu.SLOTS_PER_PAGE,
                                        PagedRecoverableMenu.SLOTS_PER_PAGE)));
            }
        }
    }

    private void clampMultiplier() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        if (controller == null) {
            return;
        }
        long limit = Math.max(1L, controller.getPassiveCraftingMaxParallel());
        boolean changed = false;
        if (multiplier > limit) {
            multiplier = limit;
            changed = true;
        }
        for (var iterator = slotMultiplierOverrides.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Integer, Long> entry = iterator.next();
            long value = entry.getValue() == null ? 0L : entry.getValue();
            if (value <= 0L) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (value > limit) {
                entry.setValue(limit);
                changed = true;
            }
        }
        if (changed) {
            statusDirty = true;
            setChanged();
        }
    }

    private void flushLocalUnreturnedInputs(MultiblockAlloyFurnaceCoreBlockEntity controller) {
        if (localUnreturnedInputs.isEmpty()) {
            return;
        }
        for (GenericStack stack : localUnreturnedInputs) {
            controller.stashUnreturnedInput(stack.what(), stack.amount());
        }
        localUnreturnedInputs.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.passive_crafting_hatch");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        statusDirty = true;
        return new PassiveCraftingHatchMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loading = true;
        pendingLegacyInventory = tag.contains("Patterns")
                ? tag.getCompound("Patterns").copy() : null;
        loading = false;
        patternStorageRevision++;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        intervalTicks = Math.max(MIN_INTERVAL_TICKS,
                Math.min(MAX_INTERVAL_TICKS, tag.getInt("IntervalTicks")));
        if (!tag.contains("IntervalTicks")) {
            intervalTicks = DEFAULT_INTERVAL_TICKS;
        }
        multiplier = Math.max(1L, tag.getLong("Multiplier"));
        slotMultiplierOverrides.clear();
        ListTag slotMultipliers = tag.getList("SlotMultipliers", Tag.TAG_COMPOUND);
        for (int index = 0; index < slotMultipliers.size(); index++) {
            CompoundTag entry = slotMultipliers.getCompound(index);
            int slot = entry.getInt("Slot");
            long value = entry.getLong("Multiplier");
            if (slot >= 0 && slot < PATTERN_SLOTS && value > 0L) {
                slotMultiplierOverrides.put(slot, value);
            }
        }
        countdownTicks = intervalTicks;
        deferredTasksTag = tag.contains("PassiveTasks") ? tag.getCompound("PassiveTasks") : null;
        localUnreturnedInputs.clear();
        ListTag unreturned = tag.getList("UnreturnedInputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < unreturned.size(); index++) {
            GenericStack stack = GenericStack.readTag(registries, unreturned.getCompound(index));
            if (stack != null) {
                localUnreturnedInputs.add(stack);
            }
        }
        observedActivePatternSlots = -1;
        clearPatternDecodeCache();
        resetIdleStates();
        pageMemory.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataVersion", 1);
        if (controllerPos != null) {
            tag.putLong("Controller", controllerPos.asLong());
        }
        tag.putLong("StructureGeneration", structureGeneration);
        tag.putInt("IntervalTicks", intervalTicks);
        tag.putLong("Multiplier", multiplier);
        ListTag slotMultipliers = new ListTag();
        if (!slotMultiplierOverrides.isEmpty()) {
            for (Map.Entry<Integer, Long> entry : new java.util.TreeMap<>(slotMultiplierOverrides).entrySet()) {
                long value = entry.getValue() == null ? 0L : entry.getValue();
                if (value <= 0L) {
                    continue;
                }
                CompoundTag slotEntry = new CompoundTag();
                slotEntry.putInt("Slot", entry.getKey());
                slotEntry.putLong("Multiplier", value);
                slotMultipliers.add(slotEntry);
            }
        }
        tag.put("SlotMultipliers", slotMultipliers);
        tag.put("PassiveTasks", saveTasks(registries));
        ListTag unreturned = new ListTag();
        for (GenericStack stack : localUnreturnedInputs) {
            unreturned.add(GenericStack.writeTag(registries, stack));
        }
        tag.put("UnreturnedInputs", unreturned);
        pageMemory.save(tag);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        countdownTicks = intervalTicks;
        statusDirty = true;
        observedActivePatternSlots = -1;
        clearPatternDecodeCache();
        // 这里**不要**调 ensureExternalInventory()：放置方块时 vanilla 的调用顺序是
        // Level.setBlock → clearRemoved() → updateBlockEntityComponents → setPlacedBy()，
        // 本方法执行时 BE 组件还是空的（物品上的引用要等 updateBlockEntityComponents 才搬进来），
        // 提前 bindAt(null) 会抢一个空 UUID 并置 externalInventoryLoaded，让 setPlacedBy 的
        // adopt 早退 —— 结果就是拆掉重放后内容物丢失。绑定交给 setPlacedBy / onLoad 即可。
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureExternalInventory();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
    }

    @Override
    public int getInputSlotsStart() {
        return 0;
    }

    @Override
    public int getInputSlotsCount() {
        return 0;
    }

    @Override
    public int getOutputSlotsStart() {
        return 0;
    }

    @Override
    public int getOutputSlotsCount() {
        return 0;
    }

    @Override
    public int getCatalystSlot() {
        return 0;
    }

    @Override
    public int getMoldSlot() {
        return 0;
    }

    @Override
    public int getFluidTankCount() {
        return 0;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public ItemStackHandler getItemHandler() {
        return EMPTY_ITEMS;
    }

    @Override
    public IEnergyManager getEnergyManager() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? fallbackEnergy : controller.getEnergyManager();
    }

    @Override
    public void markChanged() {
        setChanged();
    }

    @Override
    public void sendAETaskProgressToClients() {
        statusDirty = true;
    }

    @Override
    public int getCatalystMaxParallel() {
        return (int) Math.min(Integer.MAX_VALUE, getCurrentMaxParallel());
    }

    @Override
    public long tryOutputToAE(ItemStack stack) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? 0L : controller.tryOutputToAE(stack);
    }

    @Override
    public long tryOutputFluidToAE(FluidStack stack) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? 0L : controller.tryOutputFluidToAE(stack);
    }

    @Override
    public long tryOutputKeyToAE(AEKey key, long amount) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? 0L : controller.tryOutputKeyToAE(key, amount);
    }

    @Override
    public boolean isReturnOutputToAe() {
        return true;
    }

    @Override
    public void stashUnreturnedInput(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        if (controller != null) {
            controller.stashUnreturnedInput(key, amount);
        } else {
            localUnreturnedInputs.add(new GenericStack(key, amount));
            setChanged();
        }
    }

    @Override
    public ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap() {
        return taskProgress;
    }

    @Override
    public AtomicInteger getTotalAEMaxProgressAtomic() {
        return totalMaxProgress;
    }

    @Override
    public AtomicInteger getTotalAEProgressAtomic() {
        return totalProgress;
    }

    @Override
    public ReentrantLock getCraftingLock() {
        return craftingLock;
    }

    @Override
    public FluidTank[] getInputFluidTanks() {
        return EMPTY_TANKS;
    }

    @Override
    public FluidTank[] getOutputFluidTanks() {
        return EMPTY_TANKS;
    }

    @Override
    @Nullable
    public IGrid getAeGrid() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? null : controller.getAeGrid();
    }

    @Override
    public AdvancedAlloyFurnaceRecipe resolveTaskRecipe(
            IPatternDetails pattern, List<ItemStack> items, List<FluidStack> fluids,
            List<GenericStack> keys, long operations) {
        IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
        return original instanceof OmniversalPatternDetails omniversal ? omniversal.recipe() : null;
    }

    @Override
    public boolean isTaskRecipeAvailable(AdvancedAlloyFurnaceRecipe recipe) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && controller.isTaskRecipeAvailable(recipe);
    }

    @Override
    public CraftingTaskContext.TaskAvailability getTaskAvailability(AdvancedAlloyFurnaceRecipe recipe) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller == null
                ? CraftingTaskContext.TaskAvailability.unavailable(
                        "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_structure", "")
                : controller.getTaskAvailability(recipe);
    }

    @Override
    public ResolvedCatalystEffect resolveTaskEffect(AdvancedAlloyFurnaceRecipe recipe) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null
                ? CraftingTaskContext.super.resolveTaskEffect(recipe)
                : controller.resolveTaskEffect(recipe);
    }

    @Override
    public long getTaskParallel(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getRawController();
        return controller == null ? 1 : controller.getTaskParallel(recipe, effect);
    }

    @Override
    public boolean supportsLongAeAmounts() {
        return true;
    }

    @Override
    public boolean isTaskExecutionEnabled() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && controller.isTaskExecutionEnabled();
    }

    @Override
    public void handleUnreturnedItem(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key != null) {
            stashUnreturnedInput(key, stack.getCount());
        }
    }

    @Override
    public void handleUnreturnedFluid(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        if (key != null) {
            stashUnreturnedInput(key, stack.getAmount());
        }
    }

    public enum SlotState {
        EMPTY,
        READY,
        RUNNING,
        PAUSED,
        MISSING_INPUT,
        MISSING_MOLD,
        AE_OFFLINE,
        INVALID_PATTERN,
        WAITING_OUTPUT
    }

    public record SlotStatus(int slot, SlotState state, int progress, int maxProgress, String detail,
                             long multiplier) {
        public SlotStatus {
            state = Objects.requireNonNull(state, "state");
            progress = Math.max(0, progress);
            maxProgress = Math.max(0, maxProgress);
            detail = detail == null ? "" : detail;
            multiplier = Math.max(0L, multiplier);
        }

        public SlotStatus(int slot, SlotState state, int progress, int maxProgress, String detail) {
            this(slot, state, progress, maxProgress, detail, 0L);
        }

        /** True when this slot is pinned to its own batch size instead of the global default. */
        public boolean hasOwnMultiplier() {
            return multiplier > 0L;
        }
    }

    private record Candidate(int slot, OmniversalPatternDetails pattern, long multiplier) {
    }
}
