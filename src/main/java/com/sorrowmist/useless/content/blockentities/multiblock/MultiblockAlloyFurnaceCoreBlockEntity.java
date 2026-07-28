package com.sorrowmist.useless.content.blockentities.multiblock;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.compat.AppFluxCompat;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceAeHost;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.multiblock.OmniversalCoilStats;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.core.component.MultiblockRecoveryData;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.network.chat.Component;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Controller, energy store and persistent task owner for the multiblock furnace. */
public final class MultiblockAlloyFurnaceCoreBlockEntity extends BlockEntity implements AlloyFurnaceAeHost, MenuProvider {
    private static final int SAFETY_REVALIDATION_TICKS = 40;
    public static final int MENU_DATA_COUNT = 11;
    private static final ItemStackHandler EMPTY_ITEMS = new ItemStackHandler(0);
    private static final FluidTank[] EMPTY_TANKS = new FluidTank[0];

    private final IEnergyManager energy = EnergyManager.builder()
            .capacity(1L).maxReceive(1L).maxExtract(0L).onChange(this::setChanged).build();
    private final AdvancedAlloyFurnaceAeManager aeManager = new AdvancedAlloyFurnaceAeManager(this);
    private boolean structureDirty = true;
    private boolean formed;
    private int coilTier;
    @Nullable
    private BlockPos passiveHatchPos;
    private int validationTimer;
    private long structureGeneration;
    private boolean deferredTasksLoaded;
    private long recipeCatalogGeneration = -1L;
    private boolean unloading;
    private RedstoneControlMode redstoneControlMode = RedstoneControlMode.DISABLED;
    private long automaticEnergyLimit = Long.MAX_VALUE;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            long stored = energy.getEnergyStoredLong();
            long capacity = energy.getMaxEnergyStoredLong();
            long energyLimit = getAutomaticEnergyLimit();
            return switch (index) {
                case 0 -> formed ? 1 : 0;
                case 1 -> coilTier;
                case 2 -> (int) stored;
                case 3 -> (int) (stored >>> 32);
                case 4 -> (int) capacity;
                case 5 -> (int) (capacity >>> 32);
                case 6 -> aeManager.getActiveAETaskCount();
                case 7 -> redstoneControlMode.ordinal();
                case 8 -> getMaxAETaskCount();
                case 9 -> (int) energyLimit;
                case 10 -> (int) (energyLimit >>> 32);
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return MENU_DATA_COUNT; }
    };

    public MultiblockAlloyFurnaceCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_ALLOY_FURNACE_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (unloading || isRemoved() || level == null || level.isClientSide) return;
        if (structureDirty || ++validationTimer >= SAFETY_REVALIDATION_TICKS) {
            validationTimer = 0;
            validateStructure();
        }
        if (!deferredTasksLoaded) {
            deferredTasksLoaded = true;
            aeManager.loadDeferredTasks();
        }
        long currentCatalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (recipeCatalogGeneration != currentCatalogGeneration) {
            recipeCatalogGeneration = currentCatalogGeneration;
            updatePatterns();
        }
        // The assembly node may have mounted before the controller was linked
        // or while the AE grid was still booting.  Retry the provider refresh
        // after every validation/load transition without rebuilding patterns.
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly != null) assembly.refreshProviderIfReady();
        if (isTaskExecutionEnabled()) {
            drawEnergyFromAeNetwork();
            aeManager.flushAEBatches();
            aeManager.tickAETasks();
            aeManager.tickUnreturnedInputs();
        }
        PassiveCraftingHatchBlockEntity passiveHatch = getPassiveHatch();
        if (passiveHatch != null) {
            passiveHatch.serverTickFromController(this);
        }
    }

    public void requestStructureValidation() {
        if (unloading || isRemoved()) return;
        structureDirty = true;
    }

    public void validateStructure() {
        if (unloading || isRemoved() || level == null) return;
        structureDirty = false;
        Direction facing = getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING);
        OmniversalAlloyFurnaceStructure.ValidationResult result =
                OmniversalAlloyFurnaceStructure.validate(level, worldPosition, facing);
        int validatedCoilTier = result.valid() ? result.coilTier() : 0;
        BlockPos validatedHatchPos = result.valid() ? result.passiveHatchPos() : null;
        boolean stateChanged = formed != result.valid()
                || coilTier != validatedCoilTier
                || !java.util.Objects.equals(passiveHatchPos, validatedHatchPos);
        formed = result.valid();
        coilTier = validatedCoilTier;
        passiveHatchPos = validatedHatchPos == null ? null : validatedHatchPos.immutable();
        if (stateChanged) {
            structureGeneration++;
            if (formed) {
                OmniversalCoilStats stats = OmniversalCoilStats.forTier(coilTier);
                energy.setMaxEnergyStored(stats.energyCapacity());
                energy.setMaxReceive(stats.maxReceive());
            }
            BlockState state = getBlockState();
            if (state.getValue(MultiblockAlloyFurnaceCoreBlock.FORMED) != formed) {
                level.setBlock(worldPosition, state.setValue(MultiblockAlloyFurnaceCoreBlock.FORMED, formed), 3);
            }
            setChanged();
        }
        linkFunctionalParts(facing);
        if (stateChanged) updatePatterns();
    }

    private void linkFunctionalParts(Direction facing) {
        if (unloading || isRemoved() || level == null) return;
        MePatternAssemblyBlockEntity assembly = getAssembly(facing);
        if (assembly != null) assembly.linkController(formed ? worldPosition : null, structureGeneration);
        OmniversalMoldHubBlockEntity hub = getMoldHub(facing);
        if (hub != null) hub.linkController(formed ? worldPosition : null, structureGeneration);
        for (OmniversalAlloyFurnaceStructure.Entry entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() != OmniversalAlloyFurnaceStructure.Part.CASING) continue;
            BlockPos pos = entry.worldPos(worldPosition, facing);
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch) {
                // Keep the raw controller association while invalid so removal can still
                // recover buffered materials. getController() performs the strict link check.
                hatch.linkController(worldPosition, structureGeneration);
            }
        }
    }

    @Nullable
    private PassiveCraftingHatchBlockEntity getPassiveHatch() {
        if (!formed || passiveHatchPos == null || level == null || !level.isLoaded(passiveHatchPos)) {
            return null;
        }
        return level.getBlockEntity(passiveHatchPos) instanceof PassiveCraftingHatchBlockEntity hatch
                ? hatch : null;
    }

    @Nullable
    private MePatternAssemblyBlockEntity getAssembly() {
        return getAssembly(getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING));
    }

    @Nullable
    private MePatternAssemblyBlockEntity getAssembly(Direction facing) {
        if (unloading || isRemoved() || level == null) return null;
        BlockPos pos = OmniversalAlloyFurnaceStructure.toWorld(worldPosition, facing, new BlockPos(-1, 0, 0));
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly ? assembly : null;
    }

    @Nullable
    private OmniversalMoldHubBlockEntity getMoldHub() {
        return getMoldHub(getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING));
    }

    @Nullable
    private OmniversalMoldHubBlockEntity getMoldHub(Direction facing) {
        if (unloading || isRemoved() || level == null) return null;
        BlockPos pos = OmniversalAlloyFurnaceStructure.toWorld(worldPosition, facing, new BlockPos(1, 0, 0));
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof OmniversalMoldHubBlockEntity hub ? hub : null;
    }

    public void onAeGridChanged() {
        if (unloading || isRemoved()) return;
        setChanged();
    }

    public void updatePatterns() {
        if (unloading || isRemoved()) return;
        aeManager.rebuildPatterns();
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly != null) assembly.requestProviderRefresh();
    }

    public List<IPatternDetails> getAvailablePatterns() {
        return aeManager.getAvailablePatterns();
    }

    public int getPatternPriority() {
        return aeManager.getPatternPriority();
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return aeManager.pushPattern(pattern, inputs);
    }

    public boolean isBusy() {
        return aeManager.isBusy();
    }

    public void cancelAllAETasks() {
        aeManager.cancelAllTasks();
    }

    /** Cancels every task owned by this structure before recovery data is captured. */
    public void cancelAllTasksForRemoval() {
        aeManager.cancelAllTasks();
        if (level == null) return;
        Direction facing = getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING);
        for (OmniversalAlloyFurnaceStructure.Entry entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() != OmniversalAlloyFurnaceStructure.Part.CASING) continue;
            BlockPos pos = entry.worldPos(worldPosition, facing);
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch) {
                hatch.prepareForControllerRemoval(this);
            }
        }
    }

    public MultiblockRecoveryData createRecoveryData() {
        return new MultiblockRecoveryData(
                MultiblockRecoveryData.CURRENT_VERSION,
                energy.getEnergyStoredLong(),
                aeManager.getUnreturnedInputsSnapshot());
    }

    public void restoreRecoveryData(MultiblockRecoveryData data) {
        if (data == null) return;
        energy.setMaxEnergyStored(Math.max(energy.getMaxEnergyStoredLong(), data.energy()));
        energy.setEnergyStored(data.energy());
        aeManager.addUnreturnedInputs(data.contents());
        setChanged();
    }

    public Collection<AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressList() {
        return aeManager.getAETaskProgressList();
    }

    public void updateClientTaskProgress(List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData> tasks) {
        aeManager.updateClientTaskProgress(tasks);
    }

    public boolean isFormed() {
        return formed;
    }

    public int getCoilTier() {
        return coilTier;
    }

    public long getStructureGeneration() {
        return structureGeneration;
    }

    public int getPassiveCraftingMaxParallel() {
        return formed ? OmniversalCoilStats.forTier(coilTier).singleTaskParallel() : 1;
    }

    public boolean isPassiveHatchLinked(BlockPos pos, long generation) {
        return formed && passiveHatchPos != null && passiveHatchPos.equals(pos)
                && structureGeneration == generation;
    }

    public RedstoneControlMode getRedstoneControlMode() {
        return redstoneControlMode;
    }

    public ContainerData getMenuData() {
        return menuData;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.multiblock_alloy_furnace_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (level != null && !level.isClientSide) {
            aeManager.sendAETaskProgressToClients();
        }
        return new MultiblockAlloyFurnaceMenu(containerId, inventory, worldPosition);
    }

    public void cycleRedstoneControlMode() {
        redstoneControlMode = redstoneControlMode.next();
        setChanged();
    }

    @Override
    public @Nullable IManagedGridNode getMainNode() {
        MePatternAssemblyBlockEntity assembly = getAssembly();
        return assembly == null ? null : assembly.getMainNode();
    }

    @Override
    public int getMaxAETaskCount() {
        return formed ? OmniversalCoilStats.forTier(coilTier).threads() : 0;
    }

    @Override
    public Iterable<ItemStack> getPatternStacks() {
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly == null || !formed) return List.of();
        List<ItemStack> result = new ArrayList<>(assembly.getPatterns().getActiveSlots());
        for (int slot = 0; slot < assembly.getPatterns().getActiveSlots(); slot++) {
            result.add(assembly.getPatterns().getStackInSlot(slot));
        }
        return result;
    }

    @Override
    public boolean canPublishPatterns() {
        return formed;
    }

    @Override
    public boolean acceptsPattern(IPatternDetails pattern) {
        return SmartDoublingPatterns.unwrap(pattern) instanceof OmniversalPatternDetails;
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
        OmniversalMoldHubBlockEntity hub = getMoldHub();
        return formed && hub != null && hub.containsMold(recipe.mold());
    }

    @Override
    public ResolvedCatalystEffect resolveTaskEffect(AdvancedAlloyFurnaceRecipe recipe) {
        return OmniversalCoilStats.forTier(Math.max(1, coilTier)).resolveEffect(recipe);
    }

    @Override
    public int getTaskParallel(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return AlloyFurnaceParallelCalculator.calculateAeTaskParallel(recipe, effect);
    }

    @Override
    public boolean isTaskExecutionEnabled() {
        return formed && level != null
                && redstoneControlMode.shouldRun(level.hasNeighborSignal(worldPosition));
    }

    @Override
    public int getInputSlotsStart() { return 0; }
    @Override
    public int getInputSlotsCount() { return 0; }
    @Override
    public int getOutputSlotsStart() { return 0; }
    @Override
    public int getOutputSlotsCount() { return 0; }
    @Override
    public int getCatalystSlot() { return 0; }
    @Override
    public int getMoldSlot() { return 0; }
    @Override
    public int getFluidTankCount() { return 0; }
    @Override
    public Level getLevel() { return level; }
    @Override
    public ItemStackHandler getItemHandler() { return EMPTY_ITEMS; }
    @Override
    public IEnergyManager getEnergyManager() { return energy; }
    public IEnergyManager getEnergyStorage() { return energy; }
    public long getAutomaticEnergyLimit() {
        return clampAutomaticEnergyLimit(automaticEnergyLimit, energy.getMaxEnergyStoredLong());
    }

    public void setAutomaticEnergyLimit(long limit) {
        if (limit < 0L) return;
        long clampedLimit = clampAutomaticEnergyLimit(limit, energy.getMaxEnergyStoredLong());
        if (automaticEnergyLimit == clampedLimit) return;
        automaticEnergyLimit = clampedLimit;
        setChanged();
    }
    @Override
    public void markChanged() { setChanged(); }
    @Override
    public void sendAETaskProgressToClients() { aeManager.sendAETaskProgressToClients(); }
    @Override
    public int getCatalystMaxParallel() { return getMaxAETaskCount(); }
    @Override
    public boolean isReturnOutputToAe() { return true; }
    @Override
    public void stashUnreturnedInput(AEKey key, long amount) { aeManager.stashUnreturnedInput(key, amount); }
    @Override
    public ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap() {
        return aeManager.getAETaskProgressMap();
    }
    @Override
    public AtomicInteger getTotalAEMaxProgressAtomic() { return aeManager.getTotalAEMaxProgressAtomic(); }
    @Override
    public AtomicInteger getTotalAEProgressAtomic() { return aeManager.getTotalAEProgressAtomic(); }
    @Override
    public ReentrantLock getCraftingLock() { return aeManager.getCraftingLock(); }
    @Override
    public FluidTank[] getInputFluidTanks() { return EMPTY_TANKS; }
    @Override
    public FluidTank[] getOutputFluidTanks() { return EMPTY_TANKS; }

    @Override
    public void handleUnreturnedItem(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key != null) stashUnreturnedInput(key, stack.getCount());
    }

    @Override
    public void handleUnreturnedFluid(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        if (key != null) stashUnreturnedInput(key, stack.getAmount());
    }

    @Override
    public long tryOutputToAE(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null ? 0L : tryOutputKeyToAE(key, stack.getCount());
    }

    @Override
    public long tryOutputFluidToAE(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null ? 0L : tryOutputKeyToAE(key, stack.getAmount());
    }

    @Override
    public long tryOutputKeyToAE(AEKey key, long amount) {
        if (key == null || amount <= 0) return 0L;
        AeNetworkAccess access = getAeNetworkAccess();
        return access == null ? 0L
                : access.storage().insert(key, amount, Actionable.MODULATE, access.source());
    }

    @Nullable
    AeNetworkAccess getAeNetworkAccess() {
        if (!formed) return null;
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly == null || !assembly.getMainNode().isActive()) return null;
        IGrid grid = assembly.getMainNode().getGrid();
        if (grid == null) return null;
        return new AeNetworkAccess(
                grid.getStorageService().getInventory(), IActionSource.ofMachine(assembly));
    }

    record AeNetworkAccess(MEStorage storage, IActionSource source) {
    }

    private void drawEnergyFromAeNetwork() {
        boolean drawAppflux = AppFluxCompat.isLoaded() && ConfigManager.isFurnaceDrawAppfluxEnergyEnabled();
        boolean drawAe = ConfigManager.isFurnaceDrawAeEnergyEnabled();
        if (!drawAppflux && !drawAe) return;
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly == null || !assembly.getMainNode().isActive()) return;
        long wanted = calculateAutomaticEnergyRequest(
                energy.getEnergyStoredLong(), energy.getMaxEnergyStoredLong(),
                energy.getMaxReceiveLong(), automaticEnergyLimit);
        IGrid grid = assembly.getMainNode().getGrid();
        if (wanted <= 0 || grid == null) return;
        IActionSource source = IActionSource.ofMachine(assembly);
        if (drawAppflux) {
            long received = AppFluxCompat.extractFe(grid, wanted, source);
            if (received > 0) {
                energy.modifyEnergy(received);
                wanted -= received;
            }
        }
        if (drawAe && wanted > 0) {
            IEnergyService energyService = grid.getEnergyService();
            double extracted = energyService.extractAEPower(
                    PowerUnit.FE.convertTo(PowerUnit.AE, wanted), Actionable.MODULATE, PowerMultiplier.ONE);
            long received = Math.min(wanted,
                    (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, extracted)));
            if (received > 0) energy.modifyEnergy(received);
        }
    }

    static long calculateAutomaticEnergyRequest(
            long stored, long capacity, long maxReceive, long configuredLimit) {
        long effectiveLimit = clampAutomaticEnergyLimit(configuredLimit, capacity);
        if (stored >= effectiveLimit) return 0L;
        return Math.min(Math.max(0L, maxReceive), effectiveLimit - Math.max(0L, stored));
    }

    static long clampAutomaticEnergyLimit(long requested, long capacity) {
        return Math.min(Math.max(0L, requested), Math.max(0L, capacity));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.deserializeNBT(tag.getCompound("Energy"));
        formed = tag.getBoolean("Formed");
        coilTier = tag.getInt("CoilTier");
        structureGeneration = tag.getLong("StructureGeneration");
        passiveHatchPos = tag.contains("PassiveHatch")
                ? BlockPos.of(tag.getLong("PassiveHatch")) : null;
        redstoneControlMode = RedstoneControlMode.byIndex(tag.getInt("RedstoneControlMode"));
        automaticEnergyLimit = tag.contains("AutomaticEnergyLimit", Tag.TAG_ANY_NUMERIC)
                ? Math.max(0L, tag.getLong("AutomaticEnergyLimit"))
                : Long.MAX_VALUE;
        aeManager.setPatternPriority(tag.getInt("PatternPriority"));
        aeManager.readTasksTag(tag);
        structureDirty = true;
        deferredTasksLoaded = false;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DataVersion", 1);
        tag.put("Energy", energy.serializeNBT());
        tag.putBoolean("Formed", formed);
        tag.putInt("CoilTier", coilTier);
        tag.putLong("StructureGeneration", structureGeneration);
        if (passiveHatchPos != null) tag.putLong("PassiveHatch", passiveHatchPos.asLong());
        tag.putInt("RedstoneControlMode", redstoneControlMode.ordinal());
        tag.putLong("AutomaticEnergyLimit", automaticEnergyLimit);
        tag.putInt("PatternPriority", aeManager.getPatternPriority());
        CompoundTag tasks = new CompoundTag();
        aeManager.saveTasks(tasks, registries);
        tag.put("AeTasks", tasks);
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
        aeManager.shutdown();
    }
}
