package com.sorrowmist.useless.content.blockentities.multiblock;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTask;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PassivePatternInputTransaction;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.ConfigManager;
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
    private final Map<Integer, CraftingTask> activeTasks = new HashMap<>();
    private final SlotState[] idleStates = new SlotState[PATTERN_SLOTS];
    private final String[] idleDetails = new String[PATTERN_SLOTS];
    private final OmniversalPatternDetails[] decodedPatterns = new OmniversalPatternDetails[PATTERN_SLOTS];
    private final boolean[] patternDecodeCached = new boolean[PATTERN_SLOTS];
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
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            idleStates[slot] = SlotState.EMPTY;
            idleDetails[slot] = "";
        }
    }

    public RecoverableItemStackHandler getPatterns() {
        return patterns;
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
    public void serverTickFromController(MultiblockAlloyFurnaceCoreBlockEntity controller) {
        if (unloading || isRemoved() || level == null || level.isClientSide || controller != getController()) {
            return;
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
            return;
        }

        tickActiveTasks();
        if (--countdownTicks <= 0) {
            countdownTicks = intervalTicks;
            runPassiveCycle(controller);
            setChanged();
        }
        flushStatusUpdates();
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

    private void tickActiveTasks() {
        List<Integer> slots = new ArrayList<>(activeTasks.keySet());
        slots.sort(Integer::compareTo);
        for (int slot : slots) {
            CraftingTask task = activeTasks.get(slot);
            if (task == null) {
                continue;
            }
            task.tick();
            if (task.isProcessingComplete()) {
                activeTasks.remove(slot);
                setIdleState(slot, patterns.getStackInSlot(slot).isEmpty()
                        ? SlotState.EMPTY : SlotState.READY, "");
                setChanged();
            }
        }
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
            if (!amountsFit(omniversal, multiplier)) {
                setIdleState(slot, SlotState.INVALID_PATTERN, "amount_overflow");
                continue;
            }
            if (!controller.isTaskRecipeAvailable(omniversal.recipe())) {
                setIdleState(slot, SlotState.MISSING_MOLD, "");
                continue;
            }
            candidates.add(new Candidate(slot, omniversal));
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
                        candidates.stream().map(Candidate::pattern).toList(), multiplier, level,
                        access.storage(), access::cachedInventory, access.source(),
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
                setIdleState(candidate.slot, state, detail);
                continue;
            }

            CraftingTask task = new CraftingTask(
                    candidate.slot, candidate.pattern, extraction.inputs(), multiplier, this);
            if (!task.canStartNow()) {
                CraftingTask.returnInputsToAE(Collections.singletonList(extraction.inputs()), this);
                setIdleState(candidate.slot, SlotState.MISSING_MOLD, task.getWaitingDetail());
                continue;
            }
            activeTasks.put(candidate.slot, task);
            setIdleState(candidate.slot, SlotState.RUNNING, "");
            setChanged();
        }
    }

    private static boolean amountsFit(OmniversalPatternDetails pattern, long operations) {
        try {
            Map<AEKey, Long> totals = new HashMap<>();
            if (pattern.usesDynamicOutputs()) {
                for (ItemStack output : pattern.recipe().outputs()) {
                    addScaledAmount(totals, AEItemKey.of(output), output.getCount(), operations);
                }
                for (FluidStack output : pattern.recipe().outputFluids()) {
                    addScaledAmount(totals, AEFluidKey.of(output), output.getAmount(), operations);
                }
                for (GenericStack output : pattern.recipe().keyOutputs()) {
                    addScaledAmount(totals, output.what(), output.amount(), operations);
                }
            } else {
                for (GenericStack output : pattern.getOutputs()) {
                    addScaledAmount(totals, output.what(), output.amount(), operations);
                }
                for (GenericStack output : pattern.recipe().keyOutputs()) {
                    boolean alreadyInPattern = pattern.getOutputs().stream()
                            .anyMatch(patternOutput -> output.what().equals(patternOutput.what()));
                    if (!alreadyInPattern) {
                        addScaledAmount(totals, output.what(), output.amount(), operations);
                    }
                }
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
        clearPatternDecodeCache();
        countdownTicks = intervalTicks;
        resetIdleStates();
        statusDirty = true;
        setChanged();
    }

    @Nullable
    private OmniversalPatternDetails decodePattern(int slot, ItemStack stack) {
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
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            if (!activeTasks.containsKey(slot)) {
                setIdleState(slot, patterns.getStackInSlot(slot).isEmpty()
                        ? SlotState.EMPTY : SlotState.READY, "");
            }
        }
    }

    private void setIdleState(int slot, SlotState state, String detail) {
        if (slot < 0 || slot >= PATTERN_SLOTS) {
            return;
        }
        String safeDetail = detail == null ? "" : detail;
        if (idleStates[slot] != state || !Objects.equals(idleDetails[slot], safeDetail)) {
            idleStates[slot] = state;
            idleDetails[slot] = safeDetail;
            statusDirty = true;
        }
    }

    private List<SlotStatus> getSlotStatusSnapshot(int firstSlot, int count) {
        int start = Math.max(0, Math.min(PATTERN_SLOTS, firstSlot));
        int end = Math.max(start, Math.min(PATTERN_SLOTS, start + Math.max(0, count)));
        List<SlotStatus> result = new ArrayList<>(end - start);
        boolean enabled = isTaskExecutionEnabled();
        for (int slot = start; slot < end; slot++) {
            CraftingTask task = activeTasks.get(slot);
            if (task == null) {
                result.add(new SlotStatus(slot, idleStates[slot], 0, 0, idleDetails[slot]));
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
                    progress == null ? "" : progress.getStatusDetail()));
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
        long clamped = Math.max(1L,
                Math.min(multiplier, controller.getPassiveCraftingMaxParallel()));
        if (clamped != multiplier) {
            multiplier = clamped;
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
        patterns.deserializeNBT(registries, tag.getCompound("Patterns"));
        loading = false;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        intervalTicks = Math.max(MIN_INTERVAL_TICKS,
                Math.min(MAX_INTERVAL_TICKS, tag.getInt("IntervalTicks")));
        if (!tag.contains("IntervalTicks")) {
            intervalTicks = DEFAULT_INTERVAL_TICKS;
        }
        multiplier = Math.max(1L, tag.getLong("Multiplier"));
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataVersion", 1);
        tag.put("Patterns", patterns.serializeNBT(registries));
        if (controllerPos != null) {
            tag.putLong("Controller", controllerPos.asLong());
        }
        tag.putLong("StructureGeneration", structureGeneration);
        tag.putInt("IntervalTicks", intervalTicks);
        tag.putLong("Multiplier", multiplier);
        tag.put("PassiveTasks", saveTasks(registries));
        ListTag unreturned = new ListTag();
        for (GenericStack stack : localUnreturnedInputs) {
            unreturned.add(GenericStack.writeTag(registries, stack));
        }
        tag.put("UnreturnedInputs", unreturned);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        countdownTicks = intervalTicks;
        statusDirty = true;
        observedActivePatternSlots = -1;
        clearPatternDecodeCache();
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
    public AdvancedAlloyFurnaceRecipe resolveTaskRecipe(
            IPatternDetails pattern, List<ItemStack> items, List<FluidStack> fluids,
            List<GenericStack> keys, long operations) {
        return pattern instanceof OmniversalPatternDetails omniversal ? omniversal.recipe() : null;
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

    public record SlotStatus(int slot, SlotState state, int progress, int maxProgress, String detail) {
        public SlotStatus {
            state = Objects.requireNonNull(state, "state");
            progress = Math.max(0, progress);
            maxProgress = Math.max(0, maxProgress);
            detail = detail == null ? "" : detail;
        }
    }

    private record Candidate(int slot, OmniversalPatternDetails pattern) {
    }
}
