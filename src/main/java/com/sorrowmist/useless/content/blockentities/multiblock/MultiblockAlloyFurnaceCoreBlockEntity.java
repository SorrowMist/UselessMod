package com.sorrowmist.useless.content.blockentities.multiblock;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.compat.AppFluxCompat;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockAlloyFurnaceCoreBlock;
import com.sorrowmist.useless.content.blocks.multiblock.MultiblockFurnaceActivity;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceAeHost;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingAeOutputTarget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.GridOutputTarget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalBigIntegerTarget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.compat.neoecoae.NeoEcoDynamicOutputCompat;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.multiblock.MaintenanceWearRules;
import com.sorrowmist.useless.content.multiblock.OmniversalCoilStats;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.AlloyFurnaceTierRules;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.core.component.MultiblockRecoveryData;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.network.AlloyFurnaceErrorHighlightPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Controller, energy store and persistent task owner for the multiblock furnace. */
public final class MultiblockAlloyFurnaceCoreBlockEntity extends BlockEntity implements AlloyFurnaceAeHost, MenuProvider {
    private static final int SAFETY_REVALIDATION_TICKS = 40;
    /**
     * 同步给菜单的数据项数：原有 12 项 + 维护度的低/高 32 位。
     *
     * <p>维护度需要显示在核心界面上，而方块实体的同步包只在方块更新时下发，
     * 因此走与能量、线圈档位相同的 {@link ContainerData} 通道。</p>
     */
    public static final int MENU_DATA_COUNT = 14;
    private static final ItemStackHandler EMPTY_ITEMS = new ItemStackHandler(0);
    private static final FluidTank[] EMPTY_TANKS = new FluidTank[0];

    private final IEnergyManager energy = EnergyManager.builder()
            .capacity(1L).maxReceive(1L).maxExtract(0L).onChange(this::setChanged).build();
    private final AdvancedAlloyFurnaceAeManager aeManager = new AdvancedAlloyFurnaceAeManager(this);
    private boolean structureDirty = true;
    private boolean formed;
    private int coilTier;
    /**
     * 整机维护度，取值 {@code [0, MAX_MAINTENANCE]}，新机器为满值，由运行 tick 与合成批次两条线扣减。
     *
     * <p>维护度是整机状态而非单个部件状态，因此由核心持有；维护仓只提供把它恢复到满值的操作入口。</p>
     */
    private long maintenanceLevel = MaintenanceWearRules.MAX_MAINTENANCE;
    /**
     * 尚未折算成维护度损耗的运行 tick 余数。
     *
     * <p>损耗按整数点扣减，余数必须留存，否则每 tick 各自整除会让损耗永远停在 0。</p>
     */
    private long maintenanceTickRemainder;
    @Nullable
    private BlockPos patternAssemblyPos;
    @Nullable
    private BlockPos moldHubPos;
    @Nullable
    private BlockPos passiveHatchPos;
    @Nullable
    private BlockPos maintenanceHatchPos;
    @Nullable
    private BlockPos mufflerHatchPos;
    /** 消声仓上方排烟空间是否被占据。每 tick 重新判定，不持久化。 */
    private boolean mufflerObstructed;
    private int validationTimer;
    private long structureGeneration;
    private boolean deferredTasksLoaded;
    private long recipeCatalogGeneration = -1L;
    private boolean unloading;
    /** 对外公开 API 的目标视图缓存：视图很轻（核心引用 + 一个路由字符串），一个实例即可。 */
    @Nullable
    private OmniversalBigIntegerTarget bigIntegerTarget;
    private boolean coilActivitySynchronized;
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
                case 11 -> mufflerObstructed ? 1 : 0;
                case 12 -> (int) maintenanceLevel;
                case 13 -> (int) (maintenanceLevel >>> 32);
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
        // 排烟空间不属于结构本身，其上方方块变化也不会触发结构重校验，故逐 tick 判定。
        mufflerObstructed = isMufflerExhaustBlocked();
        if (!deferredTasksLoaded) {
            // 目录未就绪时保持未加载状态，下一 tick 重试；否则完好样板会被误判失效并退还材料。
            deferredTasksLoaded = aeManager.loadDeferredTasks();
        }
        long currentCatalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (recipeCatalogGeneration != currentCatalogGeneration) {
            recipeCatalogGeneration = currentCatalogGeneration;
            updatePatterns();
        }
        // The assembly node may have mounted before the controller was linked
        // or while the AE grid was still booting.  Retry the provider refresh
        // after every validation/load transition without rebuilding patterns.
        aeManager.tickPatternRefresh();
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly != null) assembly.refreshProviderIfReady();
        boolean progressed = false;
        if (isTaskExecutionEnabled()) {
            drawEnergyFromAeNetwork();
            aeManager.flushAEBatches();
            progressed = aeManager.tickAETasks();
            aeManager.tickUnreturnedInputs();
        }
        // 合成样板的产物必须在 AE2 记录完本批预期产物之后才能回网，所以不受上面的开关影响：
        // 即使任务执行被关闭，已经装配好的产物也必须继续投递。
        aeManager.tickQueuedCraftingOutputs();
        PassiveCraftingHatchBlockEntity passiveHatch = getPassiveHatch();
        if (passiveHatch != null) {
            progressed |= passiveHatch.serverTickFromController(this);
        }
        accrueRuntimeMaintenance(progressed);
        // 结构内含自动维护仓时维护度恒为满值：先累加再复位，保证两条扣减线都被抵消，
        // 同时让自动仓本身仍按普通方块实体存在（不额外跳过任何加工逻辑）。
        maintainWithAutoHatch();
        boolean hasWork = aeManager.hasWork()
                || passiveHatch != null && passiveHatch.hasWork();
        updateDisplayedActivity(progressed, hasWork);
        // 消声仓逐 tick 驱动：只有本 tick 确实有进展时才推进产灰；停机期间残留灰烬仍会冒烟并继续散发毒性。
        MufflerHatchBlockEntity muffler = getMufflerHatch();
        if (muffler != null) muffler.tickFromController(progressed);
    }

    private MultiblockFurnaceActivity updateDisplayedActivity(boolean progressed, boolean hasWork) {
        MultiblockFurnaceActivity activity =
                MultiblockFurnaceActivity.resolve(formed, progressed, hasWork);
        BlockState state = getBlockState();
        boolean activityChanged =
                state.getValue(MultiblockAlloyFurnaceCoreBlock.ACTIVITY) != activity;
        if (activityChanged) {
            level.setBlock(worldPosition,
                    state.setValue(MultiblockAlloyFurnaceCoreBlock.ACTIVITY, activity),
                    Block.UPDATE_CLIENTS);
        }
        if (activityChanged || !coilActivitySynchronized) {
            setCoilsActive(activity == MultiblockFurnaceActivity.RUN);
            coilActivitySynchronized = true;
        }
        return activity;
    }

    public void requestStructureValidation() {
        if (unloading || isRemoved()) return;
        structureDirty = true;
    }

    public void validateStructure() {
        if (unloading || isRemoved() || level == null) return;
        structureDirty = false;
        coilActivitySynchronized = false;
        Direction facing = getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING);
        OmniversalAlloyFurnaceStructure.ValidationResult result =
                OmniversalAlloyFurnaceStructure.validate(level, worldPosition, facing);
        int validatedCoilTier = result.valid() ? result.coilTier() : 0;
        BlockPos validatedAssemblyPos = result.valid() ? result.patternAssemblyPos() : null;
        BlockPos validatedMoldHubPos = result.valid() ? result.moldHubPos() : null;
        BlockPos validatedHatchPos = result.valid() ? result.passiveHatchPos() : null;
        BlockPos validatedMaintenancePos = result.valid() ? result.maintenanceHatchPos() : null;
        BlockPos validatedMufflerPos = result.valid() ? result.mufflerHatchPos() : null;
        boolean stateChanged = formed != result.valid()
                || coilTier != validatedCoilTier
                || !Objects.equals(patternAssemblyPos, validatedAssemblyPos)
                || !Objects.equals(moldHubPos, validatedMoldHubPos)
                || !Objects.equals(passiveHatchPos, validatedHatchPos)
                || !Objects.equals(maintenanceHatchPos, validatedMaintenancePos)
                || !Objects.equals(mufflerHatchPos, validatedMufflerPos);
        formed = result.valid();
        coilTier = validatedCoilTier;
        patternAssemblyPos = validatedAssemblyPos == null ? null : validatedAssemblyPos.immutable();
        moldHubPos = validatedMoldHubPos == null ? null : validatedMoldHubPos.immutable();
        passiveHatchPos = validatedHatchPos == null ? null : validatedHatchPos.immutable();
        maintenanceHatchPos = validatedMaintenancePos == null ? null : validatedMaintenancePos.immutable();
        mufflerHatchPos = validatedMufflerPos == null ? null : validatedMufflerPos.immutable();
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
            // 部件的占用判定要反查核心的 formed 与维护仓位置，这些字段必须同步到客户端，
            // 否则客户端结构预览里 claimsMaintenanceHatch 恒为 false，相邻双核心都会显示成型。
            level.sendBlockUpdated(worldPosition, state, getBlockState(), 3);
        }
        linkFunctionalParts();
        if (stateChanged) updatePatterns();
        broadcastStructureError(result);
    }

    /**
     * 把「当前该修的那一个方块」下发给追踪本区块的客户端：从下往上取世界坐标 Y 最小的 mismatch，
     * 同 Y 时保留列表中靠前的一条，列表顺序由 {@link OmniversalAlloyFurnaceStructure#validate}
     * 决定：缺件的补位记录被插到最前，故顶层缺件时优先提示位置唯一的那个部件。
     *
     * <p>成型时下发 {@code null} 清掉残留高亮；验证本身只在结构变化或每 40 tick 触发，天然节流。</p>
     */
    private void broadcastStructureError(OmniversalAlloyFurnaceStructure.ValidationResult result) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos lowestError = null;
        if (!formed) {
            for (OmniversalAlloyFurnaceStructure.Mismatch mismatch : result.mismatches()) {
                BlockPos pos = mismatch.worldPos();
                if (lowestError == null || pos.getY() < lowestError.getY()) {
                    lowestError = pos;
                }
            }
        }
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(worldPosition),
                new AlloyFurnaceErrorHighlightPacket(worldPosition, lowestError));
    }

    private void linkFunctionalParts() {
        if (unloading || isRemoved() || level == null) return;
        Direction facing = getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING);
        for (OmniversalAlloyFurnaceStructure.Entry entry : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() != OmniversalAlloyFurnaceStructure.Part.CASING) continue;
            BlockPos pos = entry.worldPos(worldPosition, facing);
            if (!level.isLoaded(pos)) continue;
            BlockEntity part = level.getBlockEntity(pos);
            if (part instanceof MePatternAssemblyBlockEntity assembly) {
                if (formed && pos.equals(patternAssemblyPos)) {
                    assembly.linkController(worldPosition, structureGeneration);
                } else if (assembly.isLinkedToController(worldPosition)) {
                    assembly.linkController(null, structureGeneration);
                }
            } else if (part instanceof OmniversalMoldHubBlockEntity hub) {
                if (formed && pos.equals(moldHubPos)) {
                    hub.linkController(worldPosition, structureGeneration);
                } else if (hub.isLinkedToController(worldPosition)) {
                    hub.linkController(null, structureGeneration);
                }
            } else if (part instanceof PassiveCraftingHatchBlockEntity hatch) {
                if (formed && pos.equals(passiveHatchPos)) {
                    hatch.linkController(worldPosition, structureGeneration);
                } else if (hatch.isLinkedToController(worldPosition)) {
                    // Keep our own raw association while invalid so removal can still
                    // recover buffered materials, without stealing another core's hatch.
                    hatch.linkController(worldPosition, structureGeneration);
                }
            } else if (part instanceof MaintenanceHatchBlockEntity hatch) {
                // 维护仓没有实际功能，链接只用于表达归属；失效时清掉，便于旧结构拆掉后由新结构认领。
                // 只清除属于本核心的链接，否则相邻的未成型核心会解绑其它核心正在使用的部件。
                if (formed && pos.equals(maintenanceHatchPos)) {
                    hatch.linkController(worldPosition, structureGeneration);
                } else if (hatch.isLinkedToController(worldPosition)) {
                    hatch.linkController(null, structureGeneration);
                }
            } else if (part instanceof AutoMaintenanceHatchBlockEntity hatch) {
                // 自动维护仓与普通维护仓共用一个名额，归属链接同样必须建立；
                // 否则它的 isClaimedByOtherController 恒为 false，相邻核心可共用并同时成型。
                if (formed && pos.equals(maintenanceHatchPos)) {
                    hatch.linkController(worldPosition, structureGeneration);
                } else if (hatch.isLinkedToController(worldPosition)) {
                    hatch.linkController(null, structureGeneration);
                }
            } else if (part instanceof MufflerHatchBlockEntity hatch) {
                if (formed && pos.equals(mufflerHatchPos)) {
                    hatch.linkController(worldPosition, structureGeneration);
                } else if (hatch.isLinkedToController(worldPosition)) {
                    hatch.linkController(null, structureGeneration);
                }
            }
        }
    }

    @Nullable
    private MufflerHatchBlockEntity getMufflerHatch() {
        if (!formed || mufflerHatchPos == null || level == null || !level.isLoaded(mufflerHatchPos)) {
            return null;
        }
        return level.getBlockEntity(mufflerHatchPos) instanceof MufflerHatchBlockEntity hatch ? hatch : null;
    }

    /**
     * 取得结构内的自动维护仓。
     *
     * <p>自动维护仓与普通维护仓共用同一个功能部件名额，因此位置都记录在
     * {@code maintenanceHatchPos}，这里按方块实体类型区分。</p>
     */
    @Nullable
    private AutoMaintenanceHatchBlockEntity getAutoMaintenanceHatch() {
        if (!formed || maintenanceHatchPos == null || level == null
                || !level.isLoaded(maintenanceHatchPos)) {
            return null;
        }
        return level.getBlockEntity(maintenanceHatchPos) instanceof AutoMaintenanceHatchBlockEntity hatch
                ? hatch : null;
    }

    /**
     * 结构内含自动维护仓时，把维护度保持在满值。
     *
     * <p>先比较满值再查询方块实体：正常运行时维护度本来就满，这一路径每 tick 只做一次
     * 长整数比较，不会产生额外的方块实体查找开销。</p>
     */
    private void maintainWithAutoHatch() {
        if (maintenanceLevel == MaintenanceWearRules.MAX_MAINTENANCE
                && maintenanceTickRemainder == 0L) {
            return;
        }
        if (getAutoMaintenanceHatch() != null) {
            restoreMaintenance();
        }
    }

    /** 结构成型时，消声仓上方的排烟空间是否被非空气方块占据。 */
    private boolean isMufflerExhaustBlocked() {
        if (!formed || mufflerHatchPos == null || level == null) return false;
        BlockPos top = mufflerHatchPos.above(MufflerHatchBlockEntity.EXHAUST_HEIGHT);
        // 排烟空间所在区块未加载时无法判定，按畅通处理，避免区块边界上的合金炉被误停。
        if (!level.isLoaded(top)) return false;
        return !MufflerHatchBlockEntity.isExhaustClear(level, mufflerHatchPos);
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
        if (unloading || isRemoved() || level == null || patternAssemblyPos == null
                || !level.isLoaded(patternAssemblyPos)) return null;
        return level.getBlockEntity(patternAssemblyPos) instanceof MePatternAssemblyBlockEntity assembly
                ? assembly : null;
    }

    @Nullable
    private OmniversalMoldHubBlockEntity getMoldHub() {
        if (unloading || isRemoved() || level == null || moldHubPos == null || !level.isLoaded(moldHubPos)) {
            return null;
        }
        return level.getBlockEntity(moldHubPos) instanceof OmniversalMoldHubBlockEntity hub ? hub : null;
    }

    @Nullable
    public OmniversalMoldHubBlockEntity getLinkedMoldHub() {
        if (!formed) return null;
        OmniversalMoldHubBlockEntity hub = getMoldHub();
        return hub != null && hub.getController() == this ? hub : null;
    }

    public void onAeGridChanged() {
        if (unloading || isRemoved()) return;
        setChanged();
    }

    public void updatePatterns() {
        if (unloading || isRemoved() || level == null || level.isClientSide) return;
        aeManager.updatePatterns();
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly != null) assembly.requestProviderRefresh();
    }

    public List<IPatternDetails> getAvailablePatterns() {
        return aeManager.getAvailablePatterns();
    }

    @Override
    public void onPatternsRebuilt() {
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly != null) {
            assembly.requestProviderRefresh();
        }
    }

    public int getPatternPriority() {
        return aeManager.getPatternPriority();
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return aeManager.pushPattern(pattern, inputs);
    }

    /** 数据能源原生 bigint 批次入口（单位原型 + 可能超过 long 的次数）。 */
    public boolean pushBigIntegerCraftingPattern(IPatternDetails pattern, BigInteger count,
                                                 KeyCounter[] unitPrototype) {
        return aeManager.pushBigIntegerCraftingPattern(pattern, count, unitPrototype);
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
        setCoilsActive(false);
        coilActivitySynchronized = true;
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

    private void setCoilsActive(boolean active) {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(MultiblockAlloyFurnaceCoreBlock.FACING);
        for (OmniversalAlloyFurnaceStructure.Entry entry
                : OmniversalAlloyFurnaceStructure.entries()) {
            if (entry.part() != OmniversalAlloyFurnaceStructure.Part.COIL) continue;
            BlockPos pos = entry.worldPos(worldPosition, facing);
            if (!level.isLoaded(pos)) continue;
            BlockState coilState = level.getBlockState(pos);
            if (!(coilState.getBlock() instanceof UselessCoilBlock)
                    || coilState.getValue(UselessCoilBlock.ACTIVE) == active) {
                continue;
            }
            level.setBlock(pos, coilState.setValue(UselessCoilBlock.ACTIVE, active),
                    Block.UPDATE_CLIENTS);
        }
    }

    public MultiblockRecoveryData createRecoveryData() {
        return new MultiblockRecoveryData(
                MultiblockRecoveryData.CURRENT_VERSION,
                energy.getEnergyStoredLong(),
                aeManager.getUnreturnedInputsSnapshot(),
                automaticEnergyLimit);
    }

    public void restoreRecoveryData(MultiblockRecoveryData data) {
        if (data == null) return;
        energy.setMaxEnergyStored(Math.max(energy.getMaxEnergyStoredLong(), data.energy()));
        energy.setEnergyStored(data.energy());
        aeManager.addUnreturnedInputs(data.contents());
        automaticEnergyLimit = data.automaticEnergyLimit();
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

    @Override
    public int getMachineTier() {
        return coilTier;
    }

    public long getStructureGeneration() {
        return structureGeneration;
    }

    public long getPassiveCraftingMaxParallel() {
        return formed ? OmniversalCoilStats.forTier(coilTier).singleTaskParallel() : 1;
    }

    @Override
    public long getCraftingPatternCapacity() {
        return Math.max(1L, getPassiveCraftingMaxParallel());
    }

    public boolean isPatternAssemblyLinked(BlockPos pos, long generation) {
        return formed && patternAssemblyPos != null && patternAssemblyPos.equals(pos)
                && structureGeneration == generation;
    }

    public boolean isMoldHubLinked(BlockPos pos, long generation) {
        return formed && moldHubPos != null && moldHubPos.equals(pos)
                && structureGeneration == generation;
    }

    public boolean isPassiveHatchLinked(BlockPos pos, long generation) {
        return formed && passiveHatchPos != null && passiveHatchPos.equals(pos)
                && structureGeneration == generation;
    }

    public boolean isMaintenanceHatchLinked(BlockPos pos, long generation) {
        return formed && maintenanceHatchPos != null && maintenanceHatchPos.equals(pos)
                && structureGeneration == generation;
    }

    /**
     * 归属判定专用的维护仓占用检查，不比较结构代次。
     *
     * <p>部件实体反查核心时若要求代次严格相等，代次一时不同步（例如核心刚完成一次
     * 状态变更、部件尚未收到新代次）就会把已成型核心误判为无主，相邻核心因而可以
     * 共用同一个维护仓并同时成型。代次只用于让部件丢弃陈旧缓存，不能作为占用与否的依据。</p>
     */
    public boolean claimsMaintenanceHatch(BlockPos pos) {
        return formed && maintenanceHatchPos != null && maintenanceHatchPos.equals(pos);
    }

    public boolean isMufflerHatchLinked(BlockPos pos, long generation) {
        return formed && mufflerHatchPos != null && mufflerHatchPos.equals(pos)
                && structureGeneration == generation;
    }

    /** 消声仓排烟受阻时合金炉停止工作，界面与 Jade 据此给出提示。 */
    public boolean isMufflerObstructed() {
        return mufflerObstructed;
    }

    /**
     * 消声仓灰烬是否已堆满且配置为阻塞工作。
     *
     * <p>仅在消声仓仍归属于本核心时判定；灰烬可随时被玩家取走，故与排烟受阻一样逐 tick 重新计算，
     * 不写入 NBT。配置关闭时该条件恒为 false，灰烬继续堆满即停产。</p>
     */
    private boolean isAshOutputBlocked() {
        if (!ConfigManager.isAshFullBlockingWork()) return false;
        MufflerHatchBlockEntity muffler = getMufflerHatch();
        return muffler != null && muffler.isAshFull();
    }

    /**
     * 按「有实际进展的运行 tick」扣减维护度。
     *
     * <p>只在机器确实推进了配方时才计时，空转不磨损；余数必须留存，
     * 否则每 tick 单独整除会让累积永远停在 0。</p>
     */
    private void accrueRuntimeMaintenance(boolean progressed) {
        if (!progressed) {
            return;
        }
        maintenanceTickRemainder++;
        long consumed = MaintenanceWearRules.wearForTicks(maintenanceTickRemainder);
        if (consumed <= 0L) {
            return;
        }
        maintenanceTickRemainder -= consumed * MaintenanceWearRules.WEAR_TICKS_PER_POINT;
        consumeMaintenance(consumed);
    }

    /**
     * 按完成量扣减维护度。
     *
     * <p>由 {@link CraftingTaskContext} 在每批产物写出之后回调，因此同一任务会分批回调多次。
     * 与运行 tick 磨损叠加，体现「干得越多磨得越快」。</p>
     */
    @Override
    public void onCraftsCompleted(long crafts) {
        consumeMaintenance(MaintenanceWearRules.wearForCrafts(crafts));
    }

    /** 维护度下降的实际入口：夹紧到下限，变化时标脏并同步界面。 */
    private void consumeMaintenance(long consumed) {
        if (consumed <= 0L) {
            return;
        }
        long updated = MaintenanceWearRules.clamp(maintenanceLevel - consumed);
        if (updated == maintenanceLevel) {
            return;
        }
        boolean crossedOverdue = !MaintenanceWearRules.isOverdue(maintenanceLevel)
                && MaintenanceWearRules.isOverdue(updated);
        maintenanceLevel = updated;
        setChanged();
        if (crossedOverdue && level != null) {
            // 停机点跨越时同步方块更新，让 Jade / 界面立即显示维护提示。
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 当前维护度，供界面与调试读取。 */
    public long getMaintenanceLevel() {
        return maintenanceLevel;
    }

    /** 维护仓校准完成或自动维护仓工作后调用：把维护度恢复到满值。 */
    public void restoreMaintenance() {
        if (maintenanceLevel == MaintenanceWearRules.MAX_MAINTENANCE
                && maintenanceTickRemainder == 0L) {
            return;
        }
        maintenanceLevel = MaintenanceWearRules.MAX_MAINTENANCE;
        maintenanceTickRemainder = 0L;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 维护度是否已到停机点。 */
    public boolean isMaintenanceOverdue() {
        return MaintenanceWearRules.isOverdue(maintenanceLevel);
    }

    /**
     * 按维护度放大加工时长：警告段线性变慢，超期后无法开工。
     *
     * <p>只放大时间、不动并行与产物，因此维护影响的是「同样一批活要多久」，
     * 而不是让配方产出凭空减少。</p>
     */
    @Override
    public int getTaskProcessTime(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        // 直接父接口是 AlloyFurnaceAeHost；CraftingTaskContext 是它的父接口，不能用 super 直接调到。
        int base = AlloyFurnaceAeHost.super.getTaskProcessTime(recipe, effect);
        return MaintenanceWearRules.scaledProcessTime(base, maintenanceLevel);
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
        return new MultiblockAlloyFurnaceMenu(containerId, inventory, worldPosition);
    }

    public void sendAETaskProgressToPlayer(ServerPlayer player) {
        aeManager.sendAETaskProgressToPlayer(player);
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
    public @Nullable IGrid getAeGrid() {
        MePatternAssemblyBlockEntity assembly = getAssembly();
        return assembly == null ? null : assembly.getMainNode().getGrid();
    }

    @Override
    public int getMaxAETaskCount() {
        return formed ? OmniversalCoilStats.forTier(coilTier).threads() : 0;
    }

    public int getRemainingAETaskCount(boolean craftingPattern) {
        return aeManager.getRemainingAETaskCount(craftingPattern);
    }

    /**
     * 对外公开 API（{@code api.crafting.bigint}）的提交入口。
     *
     * <p>与数据能源适配器走同一条折叠路径，区别只是多带一个 CPU 回执绑定；{@code cpuBinding} 为
     * {@code null} 时产物只切段写回 ME 网络。万象样板与 AE2 合成样板都由这一个入口处理
     * （合成样板不收能量）。</p>
     */
    public boolean pushBigIntegerBatch(IPatternDetails pattern, BigInteger count,
                                       KeyCounter[] unitPrototype,
                                       @Nullable AlloyFurnaceBigIntegerCpuBinding cpuBinding) {
        return aeManager.pushBigIntegerBatch(pattern, count, unitPrototype, cpuBinding);
    }

    /**
     * 对外公开 API 的目标视图：第三方 CPU 通过
     * {@code AlloyFurnaceBigIntegerApi.findTargets(grid)} 拿到的就是这个对象。
     *
     * <p>视图本身很轻（只持有本核心与一个路由字符串），实例缓存在核心上即可；机器未成形或正在卸载时
     * 返回 {@code null}，这样发现流程不会拿到一台已经没了的机器。</p>
     */
    @Nullable
    public OmniversalBigIntegerTarget bigIntegerTarget() {
        if (!formed || unloading) {
            return null;
        }
        if (bigIntegerTarget == null) {
            bigIntegerTarget = new OmniversalBigIntegerTarget(
                    this, "useless_mod:multiblock_alloy_furnace@" + worldPosition.asLong());
        }
        return bigIntegerTarget;
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
        IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
        // 万象样板交给合金炉加工；AE2 合成样板由样板总成在虚拟工作台上自执行。
        return original instanceof OmniversalPatternDetails
                || original instanceof IMolecularAssemblerSupportedPattern;
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
        return getTaskAvailability(recipe).available();
    }

    @Override
    public CraftingTaskContext.TaskAvailability getTaskAvailability(AdvancedAlloyFurnaceRecipe recipe) {
        if (!formed) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_structure", "");
        }
        if (mufflerObstructed) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_muffler", "");
        }
        if (isAshOutputBlocked()) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_ash_full", "");
        }
        if (MaintenanceWearRules.isOverdue(maintenanceLevel)) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_maintenance", "");
        }
        if (recipe == null) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_recipe", "");
        }
        int requiredTier = AlloyFurnaceTierRules.requiredTier(recipe.id(), recipe.tier());
        if (coilTier < requiredTier) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_tier", "");
        }
        if (recipe.molds().isEmpty()) {
            return CraftingTaskContext.TaskAvailability.ready();
        }
        OmniversalMoldHubBlockEntity hub = getMoldHub();
        if (hub == null) {
            return CraftingTaskContext.TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold_hub", "");
        }
        return hub.containsMolds(recipe.molds())
                ? CraftingTaskContext.TaskAvailability.ready()
                : CraftingTaskContext.TaskAvailability.unavailable(
                        "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                        CraftingTaskContext.describeRequiredMolds(recipe.molds()));
    }

    @Override
    public ResolvedCatalystEffect resolveTaskEffect(AdvancedAlloyFurnaceRecipe recipe) {
        return OmniversalCoilStats.forTier(Math.max(1, coilTier)).resolveEffect(recipe);
    }

    @Override
    public long getTaskParallel(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return AlloyFurnaceParallelCalculator.calculateAeTaskParallel(recipe, effect);
    }

    @Override
    public boolean supportsLongAeAmounts() {
        return true;
    }

    /**
     * 多方块支持把万象样板折叠成 BigInteger 批次（一次解析配方、产物 ×count、按 count 收能量）。
     *
     * <p>单方块高级合金炉不覆写该方法 ⇒ 保持 {@code false}，其万象样板继续走长版 counted 路径。</p>
     */
    @Override
    public boolean supportsBigIntegerRecipeBatches() {
        return true;
    }

    /**
     * 本 tick 允许单批产生的产物分段数（AIMD 控制器，转发给 AE 管理器）。
     *
     * <p>用于解决超大量合成过程中交付不连续的问题：单批规模由实测反馈探测得出，
     * 收敛至「一批约在一到两个 tick 内交付完成」，而非使用固定值估算。</p>
     */
    @Override
    public long outputSegmentBudget() {
        return aeManager.outputSegmentBudget();
    }

    @Override
    public boolean isTaskExecutionEnabled() {
        return formed && !mufflerObstructed && !isAshOutputBlocked() && level != null
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
        CraftingAeOutputTarget target = resolveAeOutputTarget();
        return target == null ? 0L : target.insert(key, amount);
    }

    /**
     * 解析一次 ME 网络写入目标，供一次「产物回网」刷写 pass 内复用。
     *
     * <p>此前每写一个分段都重新解析一次（{@link #getAeNetworkAccess()} 中 2 次方块实体查询 +
     * 2 次分配，再加 {@link #getAeGrid()} 又一次装配体查询）。可持续吞吐直接由「每 tick 可插入次数」
     * 决定，一 tick 可能插入数千次 ⇒ 该解析成本直接影响吞吐，必须在 pass 外仅执行一次。</p>
     */
    @Override
    public @Nullable CraftingAeOutputTarget resolveAeOutputTarget() {
        AeNetworkAccess access = getAeNetworkAccess();
        if (access == null) {
            return null;
        }
        MEStorage storage = access.storage();
        IActionSource source = access.source();
        // 网格也一并解析一次（getAeGrid() 内部又是一次装配体查询）。
        // 返回的 GridOutputTarget 支持「认领提升」：刷写 pass 会把 neoecoae 的逐段认领
        // 提到逐键一次（见 CraftingAeOutputTarget#supportsClaimHoisting）。
        return new GridOutputTarget(getAeGrid(), storage, source);
    }

    @Nullable
    AeNetworkAccess getAeNetworkAccess() {
        if (!formed) return null;
        MePatternAssemblyBlockEntity assembly = getAssembly();
        if (assembly == null || !assembly.getMainNode().isActive()) return null;
        IGrid grid = assembly.getMainNode().getGrid();
        if (grid == null) return null;
        var storageService = grid.getStorageService();
        return new AeNetworkAccess(storageService, IActionSource.ofMachine(assembly));
    }

    record AeNetworkAccess(IStorageService storageService, IActionSource source) {
        MEStorage storage() {
            return storageService.getInventory();
        }

        KeyCounter cachedInventory() {
            return storageService.getCachedInventory();
        }
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
        patternAssemblyPos = tag.contains("PatternAssembly")
                ? BlockPos.of(tag.getLong("PatternAssembly")) : null;
        moldHubPos = tag.contains("MoldHub") ? BlockPos.of(tag.getLong("MoldHub")) : null;
        passiveHatchPos = tag.contains("PassiveHatch")
                ? BlockPos.of(tag.getLong("PassiveHatch")) : null;
        maintenanceHatchPos = tag.contains("MaintenanceHatch")
                ? BlockPos.of(tag.getLong("MaintenanceHatch")) : null;
        mufflerHatchPos = tag.contains("MufflerHatch")
                ? BlockPos.of(tag.getLong("MufflerHatch")) : null;
        redstoneControlMode = RedstoneControlMode.byIndex(tag.getInt("RedstoneControlMode"));
        // 维护度是整机状态，必须随核心持久化；缺失字段的旧存档按满值处理，
        // 否则旧存档里的机器一加载就处于停机状态。
        maintenanceLevel = tag.contains("MaintenanceWear", Tag.TAG_ANY_NUMERIC)
                ? MaintenanceWearRules.clamp(tag.getLong("MaintenanceWear"))
                : MaintenanceWearRules.MAX_MAINTENANCE;
        maintenanceTickRemainder = Math.max(0L, tag.getLong("MaintenanceWearTickRemainder"));
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
        if (patternAssemblyPos != null) tag.putLong("PatternAssembly", patternAssemblyPos.asLong());
        if (moldHubPos != null) tag.putLong("MoldHub", moldHubPos.asLong());
        if (passiveHatchPos != null) tag.putLong("PassiveHatch", passiveHatchPos.asLong());
        if (maintenanceHatchPos != null) tag.putLong("MaintenanceHatch", maintenanceHatchPos.asLong());
        if (mufflerHatchPos != null) tag.putLong("MufflerHatch", mufflerHatchPos.asLong());
        tag.putInt("RedstoneControlMode", redstoneControlMode.ordinal());
        tag.putLong("MaintenanceWear", maintenanceLevel);
        tag.putLong("MaintenanceWearTickRemainder", maintenanceTickRemainder);
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

    /**
     * 客户端结构预览要读取成型状态与各功能部件位置（尤其是维护仓归属），
     * 因此方块更新与区块首包都必须带上这些字段。
     *
     * <p>AE 任务数据体积大且客户端由 {@code AETaskProgressPacket} 单独同步，这里剥离。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.remove("AeTasks");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
