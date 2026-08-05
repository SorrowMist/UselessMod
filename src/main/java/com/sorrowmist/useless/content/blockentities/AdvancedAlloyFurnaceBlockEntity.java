package com.sorrowmist.useless.content.blockentities;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;
import com.sorrowmist.useless.compat.AppFluxCompat;
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceAeHost;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution.AlloyFurnaceRecipeExecutor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceAutoIoController;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceCombinedFluidTankHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceFaceAccessor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceFluidTankHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceInputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceSidedFluidHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceSidedItemHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.recipe.AlloyFurnaceRecipeCalculator;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CHEMICAL_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_END;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.TOTAL_SLOTS;

public class AdvancedAlloyFurnaceBlockEntity extends AEBaseBlockEntity implements MenuProvider, ICraftingProvider,
        SmartDoublingCraftingProvider, IInWorldGridNodeHost,
        IGridNodeListener<AdvancedAlloyFurnaceBlockEntity>, IActionHost, AlloyFurnaceAeHost,
        PatternContainer, FurnaceFaceAccessor, FurnaceAutoIoController.Context {

    public static final int MAX_FURNACE_TIER = 10;
    public static final int USEFUL_INGOT_FURNACE_TIER = 10;

    // 基础容量配置
    private static final int BASE_FLUID_TANK_CAPACITY = 16000;
    private static final long BASE_CHEMICAL_TANK_CAPACITY = BASE_FLUID_TANK_CAPACITY;
    private static final long BASE_ENERGY_CAPACITY = 100_000L;
    private static final long BASE_ENERGY_MAX_RECEIVE = 10_000L;
    private static final long ENERGY_MAX_EXTRACT = 0L;
    private static final int ACTIVE_COOLDOWN_TICKS = 5;
    private static final int AUTO_OUTPUT_INTERVAL = 1;
    private static final int DISPLAY_PARALLEL_CACHE_DURATION = 20;
    private final FluidTank[] inputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final FluidTank[] outputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final FurnaceChemicalStorage inputChemicalStorage;
    private final FurnaceChemicalStorage outputChemicalStorage;
    private final IEnergyManager energyManager = EnergyManager.builder()
                                                              .capacity(BASE_ENERGY_CAPACITY)
                                                              .maxReceive(BASE_ENERGY_MAX_RECEIVE)
                                                              .maxExtract(ENERGY_MAX_EXTRACT)
                                                              .onChange(this::setChanged)
                                                              .build();
    private final AdvancedAlloyFurnaceData data = new AdvancedAlloyFurnaceData(this);
    private final ItemStackHandler itemHandler;
    private final AdvancedAlloyFurnaceAeManager aeManager;
    private final FurnaceAutoIoController autoIoController;
    private final AlloyFurnaceRecipeCalculator recipeCalculator;
    // ==================== AE网络支持 ====================
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    // 升级后的容量（根据阶级动态计算）
    private int fluidTankCapacity = BASE_FLUID_TANK_CAPACITY;
    private long chemicalTankCapacity = BASE_CHEMICAL_TANK_CAPACITY;
    private long energyCapacity = BASE_ENERGY_CAPACITY;
    private long energyMaxReceive = BASE_ENERGY_MAX_RECEIVE;
    private int progress = 0;
    private int maxProgress = 200;
    private int currentParallel = 1;
    private boolean hasMold = false;
    @Nullable
    private AdvancedAlloyFurnaceRecipe currentRecipe;
    // 缓存催化剂解析结果，避免每tick重复解析
    @Nullable
    private ResolvedCatalystEffect cachedCatalystEffect;
    private int cachedParallel = 1;
    // UI并行数缓存，减少无谓的配方查找
    private int cachedDisplayParallel = 1;
    private int displayParallelCacheTick = 0;
    private boolean isUselessIngotRecipe = false;
    private int targetUselessIngotTier = 0;
    private long accumulatedEnergy = 0;
    // 活跃状态冷却计时器，用于避免配方切换时的闪烁
    private int activeCooldown = 0;
    // 熔炉阶级 0-10，0为基础等级，10为有用锭 long 能量等级
    private int furnaceTier = 0;
    // 自动输出计时器
    private int autoOutputTickCounter = 0;
    private boolean isConnectedToAE = false;
    // 六个面的输入输出模式（按FurnaceFace索引）
    private final FurnaceFaceMode[] faceModes = new FurnaceFaceMode[FurnaceFace.COUNT];
    private long recipeCatalogGeneration = -1L;
    // 自动输入开关
    private boolean autoInputEnabled = false;
    // 自动输出开关
    private boolean autoOutputEnabled = false;
    // 红石控制模式
    private RedstoneControlMode redstoneControlMode = RedstoneControlMode.DISABLED;
    // 产物是否回AE
    private boolean returnOutputToAe = true;
    private boolean dropDataCaptured;

    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);

        // 初始化物品处理器（支持高堆叠数量）
        this.itemHandler = new HighStackItemStackHandler(
                TOTAL_SLOTS,
                MOLD_SLOT,
                CATALYST_SLOT,
                INPUT_SLOTS_START,
                INPUT_SLOTS_COUNT,
                PATTERN_SLOTS_START,
                PATTERN_SLOTS_END,
                this::setChanged,
                slot -> this.updateMoldState(),
                null,
                slot -> this.updatePatterns()
        );

        // AE2 Integration - 创建动作源
        this.actionSource = IActionSource.ofMachine(this);

        // AE2 Integration - 创建网格节点（使用GridHelper确保正确初始化）
        this.mainNode = GridHelper.createManagedNode(this, this)
                                  .setInWorldNode(true)
                                  .setTagName("node")
                                  .setFlags(GridFlags.REQUIRE_CHANNEL)
                                  .addService(ICraftingProvider.class, this);
        this.aeManager = new AdvancedAlloyFurnaceAeManager(this);

        this.inputChemicalStorage = ChemicalCompatProviders.get().createStorage(
                BASE_CHEMICAL_TANK_CAPACITY, this::onChemicalStorageChanged);
        this.outputChemicalStorage = ChemicalCompatProviders.get().createStorage(
                BASE_CHEMICAL_TANK_CAPACITY, this::onChemicalStorageChanged);

        // 初始化时应用当前阶级的容量
        this.updateCapacityByTier();
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            this.inputFluidTanks[i] = this.createTank(i, true);
            this.outputFluidTanks[i] = this.createTank(i, false);
        }

        // 初始化所有面模式为禁止
        Arrays.fill(this.faceModes, FurnaceFaceMode.DISABLED);

        // 初始化自动输入输出控制器
        this.autoIoController = new FurnaceAutoIoController(
                this, this.itemHandler, this.inputFluidTanks, this.outputFluidTanks,
                this.inputChemicalStorage, this.outputChemicalStorage, pos);

        // 初始化配方匹配与并行计算器（纯只读计算，不持有运行状态）
        this.recipeCalculator = new AlloyFurnaceRecipeCalculator(
                this.itemHandler, this.inputFluidTanks, this.outputFluidTanks,
                this.inputChemicalStorage, this.outputChemicalStorage,
                ChemicalKeyProviders.get());
    }


    /**
     * 计算流体槽容量
     * 基础16000，前3阶2倍增长，之后4倍增长，9阶达到int最大值
     */
    public static int calculateFluidCapacity(int tier) {
        if (tier <= 0) return BASE_FLUID_TANK_CAPACITY;
        if (tier >= 9) return Integer.MAX_VALUE;

        long capacity;
        if (tier <= 3) {
            // 1-3阶：2倍增长
            capacity = (long) BASE_FLUID_TANK_CAPACITY * (1L << tier);
        } else {
            // 4-8阶：4倍增长（从第3阶的基础上）
            long base = (long) BASE_FLUID_TANK_CAPACITY * 8; // 第3阶的值
            // 使用位移代替Math.pow避免浮点数精度问题
            capacity = base * (1L << (2 * (tier - 3)));
        }
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    /** Chemical tanks use the same progression, without the int-sized fluid cap. */
    public static long calculateChemicalCapacity(int tier) {
        if (tier <= 0) return BASE_CHEMICAL_TANK_CAPACITY;
        if (tier >= USEFUL_INGOT_FURNACE_TIER) return Long.MAX_VALUE;

        long capacity;
        if (tier <= 3) {
            capacity = BASE_CHEMICAL_TANK_CAPACITY * (1L << tier);
        } else {
            long base = BASE_CHEMICAL_TANK_CAPACITY * 8L;
            int shift = 2 * (tier - 3);
            capacity = shift >= Long.SIZE - 1 || base > Long.MAX_VALUE / (1L << shift)
                    ? Long.MAX_VALUE : base * (1L << shift);
        }
        return Math.max(0L, capacity);
    }

    /**
     * 计算能量槽容量
     * 基础100000，前3阶2倍增长，之后4倍增长；使用 long 保留完整等级曲线。
     */
    public static long calculateEnergyCapacity(int tier) {
        if (tier <= 0) return BASE_ENERGY_CAPACITY;
        if (tier >= USEFUL_INGOT_FURNACE_TIER) return Long.MAX_VALUE;

        long capacity;
        if (tier <= 3) {
            // 1-3阶：2倍增长
            capacity = (long) BASE_ENERGY_CAPACITY * (1L << tier);
        } else {
            // 4-8阶：4倍增长
            long base = (long) BASE_ENERGY_CAPACITY * 8; // 第3阶的值
            // 使用位移代替Math.pow避免浮点数精度问题
            capacity = base * (1L << (2 * (tier - 3)));
        }
        return capacity;
    }

    /**
     * 计算能量输入速度
     * 基础10000，前3阶2倍增长，之后4倍增长；使用 long 保留完整等级曲线。
     */
    public static long calculateEnergyReceive(int tier) {
        if (tier <= 0) return BASE_ENERGY_MAX_RECEIVE;
        if (tier >= USEFUL_INGOT_FURNACE_TIER) return Long.MAX_VALUE;

        long receive;
        if (tier <= 3) {
            // 1-3阶：2倍增长
            receive = (long) BASE_ENERGY_MAX_RECEIVE * (1L << tier);
        } else {
            // 4-8阶：4倍增长
            long base = (long) BASE_ENERGY_MAX_RECEIVE * 8; // 第3阶的值
            // 使用位移代替Math.pow避免浮点数精度问题
            receive = base * (1L << (2 * (tier - 3)));
        }
        return receive;
    }

    /**
     * 检查物品是否是扳手
     */
    public static boolean isWrench(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.create(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "tools/wrench")));
    }

    /**
     * 方块实体的每tick更新逻辑
     * <p>
     * 处理配方逻辑并更新方块的active状态（用于光照）
     *
     * @param level  世界
     * @param entity 方块实体实例
     */
    public static void tick(Level level, AdvancedAlloyFurnaceBlockEntity entity) {
        if (level.isClientSide) return;

        long currentCatalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (entity.recipeCatalogGeneration != currentCatalogGeneration) {
            entity.recipeCatalogGeneration = currentCatalogGeneration;
            entity.updatePatterns();
        }

        boolean wasActive = entity.getBlockState().getValue(
                com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock.getActiveProperty());

        // 红石控制：被阻止时跳过所有操作
        boolean blocked = entity.isRedstoneBlocked();

        if (blocked) {
            // 被阻止时，配方进度冻结但不清零
            // 不执行任何配方处理、AE任务、自动IO
            boolean shouldBeActive = entity.currentRecipe != null
                    && (entity.progress > 0 || entity.accumulatedEnergy > 0L);
            if (wasActive != shouldBeActive) {
                level.setBlock(entity.worldPosition,
                               entity.getBlockState().setValue(
                                       AdvancedAlloyFurnaceBlock.getActiveProperty(),
                                       shouldBeActive
                               ),
                               3
                );
            }
            return;
        }

        // 先补电再处理配方，本tick抽到的能量当tick即可使用
        entity.drawEnergyFromAeNetwork();

        if (entity.currentRecipe == null) {
            entity.tryStartNewRecipe();
        } else {
            entity.processCurrentRecipe();
        }

        entity.aeManager.flushAEBatches();
        entity.aeManager.tickAETasks();
        entity.aeManager.tickUnreturnedInputs();
        entity.aeManager.tickUnreturnedOutputs();

        // 每tick尝试自动输入输出物品和流体
        entity.autoOutputTickCounter++;
        if (entity.autoOutputTickCounter >= AUTO_OUTPUT_INTERVAL) {
            entity.autoOutputTickCounter = 0;
            entity.autoIoController.tick(level);
        }

        // 判断是否应该处于活跃状态
        boolean isProcessing = entity.currentRecipe != null
                && (entity.progress > 0 || entity.accumulatedEnergy > 0L);

        // 如果正在处理，重置冷却计时器
        if (isProcessing) {
            entity.activeCooldown = ACTIVE_COOLDOWN_TICKS;
        } else if (entity.activeCooldown > 0) {
            // 否则减少冷却计时器
            entity.activeCooldown--;
        }

        // 活跃状态 = 正在处理 或 冷却中
        boolean shouldBeActive = isProcessing || entity.activeCooldown > 0;

        // 更新方块状态（光照）
        if (wasActive != shouldBeActive) {
            level.setBlock(entity.worldPosition,
                           entity.getBlockState().setValue(
                                   AdvancedAlloyFurnaceBlock.getActiveProperty(),
                                   shouldBeActive
                           ),
                           3
            );
        }
    }

    /**
     * 从所在AE网络为熔炉补充能量：
     * <ol>
     *   <li>AppliedFlux 已安装且配置开启时，抽取网络存储中的FE能量（通量元件）</li>
     *   <li>配置开启时（默认关闭），抽取AE网络自身能量作为补充（按 PowerUnit 折算，1 AE = 2 FE）</li>
     * </ol>
     * 每tick总抽取量受熔炉容量与最大输入速率限制。
     */
    private void drawEnergyFromAeNetwork() {
        boolean drawAppflux = AppFluxCompat.isLoaded() && ConfigManager.isFurnaceDrawAppfluxEnergyEnabled();
        boolean drawAe = ConfigManager.isFurnaceDrawAeEnergyEnabled();
        if (!drawAppflux && !drawAe) {
            return;
        }
        if (!this.isConnectedToAE) {
            return;
        }

        // 模拟接收得到本tick可接受的能量（受容量与最大输入速率约束）
        long wanted = this.energyManager.receiveEnergy(Long.MAX_VALUE, true);
        if (wanted <= 0L) {
            return;
        }

        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }

        if (drawAppflux) {
            long got = AppFluxCompat.extractFe(grid, wanted, this.actionSource);
            if (got > 0) {
                this.energyManager.modifyEnergy(got);
                wanted -= got;
            }
        }

        if (drawAe && wanted > 0L) {
            IEnergyService energyService = grid.getEnergyService();
            double aeWanted = PowerUnit.FE.convertTo(PowerUnit.AE, wanted);
            double aeGot = energyService.extractAEPower(aeWanted, Actionable.MODULATE, PowerMultiplier.ONE);
            // 以实际抽取量入账并向下取整，宁可丢弃不足1FE的零头也不凭空多记能量
            long feGot = Math.min(wanted, (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, aeGot)));
            if (feGot > 0L) {
                this.energyManager.modifyEnergy(feGot);
            }
        }
    }

    // 更新客户端任务进度（从网络包调用）
    public void updateClientTaskProgress(
            List<AETaskProgressPacket.TaskProgressData> tasks) {
        this.aeManager.updateClientTaskProgress(tasks);
    }

    // 发送AE任务进度到所有客户端
    public void sendAETaskProgressToClients() {
        this.aeManager.sendAETaskProgressToClients();
    }


    /**
     * 根据阶级计算并更新容量
     * 使用指数增长曲线，并同步 long 容量与输入速率。
     */
    private void updateCapacityByTier() {
        this.fluidTankCapacity = calculateFluidCapacity(this.furnaceTier);
        this.chemicalTankCapacity = calculateChemicalCapacity(this.furnaceTier);
        this.energyCapacity = calculateEnergyCapacity(this.furnaceTier);
        this.energyMaxReceive = calculateEnergyReceive(this.furnaceTier);

        // 更新能量管理器
        this.energyManager.setMaxEnergyStored(this.energyCapacity);
        this.energyManager.setMaxReceive(this.energyMaxReceive);

        // 更新流体槽容量
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            if (this.inputFluidTanks[i] != null) {
                this.inputFluidTanks[i].setCapacity(this.fluidTankCapacity);
            }
            if (this.outputFluidTanks[i] != null) {
                this.outputFluidTanks[i].setCapacity(this.fluidTankCapacity);
            }
        }
        this.inputChemicalStorage.setCapacity(this.chemicalTankCapacity);
        this.outputChemicalStorage.setCapacity(this.chemicalTankCapacity);
    }

    private void onChemicalStorageChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * 获取当前熔炉阶级
     */
    public int getFurnaceTier() {
        return this.furnaceTier;
    }

    public void setClientFurnaceTier(int tier) {
        this.furnaceTier = Math.max(0, Math.min(MAX_FURNACE_TIER, tier));
    }

    /**
     * 设置熔炉阶级（内部使用，不触发容量更新）
     */
    private void setFurnaceTier(int tier) {
        this.furnaceTier = Math.max(0, Math.min(MAX_FURNACE_TIER, tier));
    }

    /**
     * 尝试升级熔炉
     *
     * @param targetTier 目标阶级（1-10）
     * @return 是否升级成功
     */
    public boolean tryUpgrade(int targetTier) {
        // 只能升级到更高阶级
        if (targetTier <= this.furnaceTier) {
            return false;
        }
        // 1-9阶由无用锭升级，第10阶由有用锭升级。
        if (targetTier < 1 || targetTier > MAX_FURNACE_TIER) {
            return false;
        }
        this.furnaceTier = targetTier;
        this.updateCapacityByTier();
        this.setChanged();

        // 同步数据到客户端，确保界面立即更新
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }

        return true;
    }

    /**
     * 获取流体槽容量
     */
    public int getFluidTankCapacity() {
        return this.fluidTankCapacity;
    }

    /**
     * 获取能量槽容量
     */
    public long getEnergyCapacity() {
        return this.energyCapacity;
    }

    /**
     * 获取能量输入速度
     */
    public long getEnergyMaxReceive() {
        return this.energyMaxReceive;
    }

    /**
     * 设置物品栏中的物品（用于从NBT恢复）
     *
     * @param slot  槽位
     * @param stack 物品堆
     */
    public void setItemInSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < TOTAL_SLOTS) {
            this.itemHandler.setStackInSlot(slot, stack);
        }
    }

    public void restoreInventory(CompoundTag inventoryTag, HolderLookup.Provider registries) {
        this.itemHandler.deserializeNBT(registries, inventoryTag);
        this.updateMoldState();
        this.updatePatterns();
    }

    /**
     * 尝试开始新配方处理
     * <p>
     * 检查是否有匹配的配方，以及是否有足够的空间和输入材料。
     * 并行数在此处计算一次，避免 hasOutputSpace 和 startRecipeProcessing 重复计算。
     */
    private void tryStartNewRecipe() {
        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isEmpty()) return;

        AdvancedAlloyFurnaceRecipe recipe = match.get();
        if (!this.canConsumeRecipeInputs(recipe)) return;

        // 统一计算并行数（一次计算，同时校验输出空间和能量是否足够）
        int parallel = this.calculateActualParallel(recipe);
        if (parallel < 1) return;

        this.startRecipeProcessing(recipe);
    }

    /**
     * 处理当前配方
     * <p>
     * 每tick消耗能量并增加进度，完成时产出物品
     * <p>
     * 能量消耗逻辑：
     * 1. 配方开始时记录初始并行数 cachedParallel
     * 2. 每tick根据当前并行数计算所需能量并扣除
     * 3. 累积已消耗的能量到 accumulatedEnergy
     * 4. 配方完成时根据 accumulatedEnergy 计算实际能支持的并行数
     */
    private void processCurrentRecipe() {
        if (this.currentRecipe == null) return;

        // 每20tick检查一次配方是否切换，避免评分系统不稳定导致连续重启
        if (this.progress % 20 == 0) {
            Optional<AdvancedAlloyFurnaceRecipe> bestMatch = this.findMatchingRecipe();
            if (bestMatch.isPresent() && !hasSameProcessingOutput(bestMatch.get(), this.currentRecipe)) {
                this.startRecipeProcessing(bestMatch.get());
                return;
            }
        }

        // 使用开始配方时计算的并行数
        int actualParallel = this.cachedParallel;

        ResolvedCatalystEffect resolvedCatalystEffect = this.cachedCatalystEffect;
        if (resolvedCatalystEffect == null) {
            // fallback：理论上不会发生，但保留安全性
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            resolvedCatalystEffect = CatalystEffectResolver.resolve(this.currentRecipe, catalystStack, this.currentRecipe.processTime());
            this.cachedCatalystEffect = resolvedCatalystEffect;
        }
        long targetEnergy = AlloyFurnaceRecipeExecutor.calculateTargetTotalEnergy(
                this.currentRecipe.energy(), actualParallel, resolvedCatalystEffect);
        AlloyFurnaceRecipeExecutor.TickResult tickResult = AlloyFurnaceRecipeExecutor.consumeProgressEnergy(
                this.energyManager, targetEnergy, this.progress, this.maxProgress, this.accumulatedEnergy);

        // 能量不足时暂停进度，但不重置
        this.accumulatedEnergy += tickResult.energyConsumed();
        if (!tickResult.progressAdvanced()) {
            if (tickResult.energyConsumed() > 0L) {
                this.setChanged();
            }
            return;
        }

        // 累积能量
        this.progress++;

        if (this.progress >= this.maxProgress) {
            this.completeRecipe();
        }

        this.setChanged();
    }

    private static boolean hasSameProcessingOutput(
            AdvancedAlloyFurnaceRecipe candidate, AdvancedAlloyFurnaceRecipe current) {
        if (!candidate.id().equals(current.id()) || candidate.outputs().size() != current.outputs().size()) {
            return false;
        }
        for (int i = 0; i < candidate.outputs().size(); i++) {
            ItemStack candidateOutput = candidate.outputs().get(i);
            ItemStack currentOutput = current.outputs().get(i);
            if (candidateOutput.getCount() != currentOutput.getCount()
                    || !ItemStack.isSameItemSameComponents(candidateOutput, currentOutput)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算实际可用的并行数（委托给配方计算器）。
     */
    private int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe) {
        return this.recipeCalculator.calculateActualParallel(recipe);
    }

    /**
     * 计算催化剂允许的并行数（委托给配方计算器，优先使用缓存的催化剂效果）。
     */
    private int calculateCatalystParallel(AdvancedAlloyFurnaceRecipe recipe) {
        return this.recipeCalculator.calculateCatalystParallel(recipe, this.cachedCatalystEffect);
    }

    /**
     * 计算输入材料允许的并行数（委托给配方计算器）。
     */
    private int calculateMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        return this.recipeCalculator.calculateMaterialParallel(recipe);
    }

    /**
     * 计算输出空间允许的并行数（委托给配方计算器）。
     */
    private int calculateOutputParallel(AdvancedAlloyFurnaceRecipe recipe) {
        return this.recipeCalculator.calculateOutputParallel(recipe);
    }

    /**
     * 完成当前配方处理
     * <p>
     * 配方完成时进行最终结算：
     * 1. 根据当前材料、输出空间、催化剂计算最大可行并行数
     * 2. 计算还需要补充的能量 = 目标并行能量 - 已消耗能量
     * 3. 尝试扣除补充能量
     * 4. 如果能量足够，产出目标并行数的产物
     * 5. 如果能量不足，根据实际能量计算可行的并行数
     * <p>
     * 示例：
     * - 配方需要1000能量，开始3并行，已消耗3000能量
     * - 配方完成时有64份材料，催化剂支持1w并行
     * - 目标并行64，需要64000能量
     * - 还需补充能量 = 64000 - 3000 = 61000
     * - 如果能量足够，产出64份；不够则按实际能量计算
     */
    private void completeRecipe() {
        long recipeEnergy = this.currentRecipe.energy();
        int initialParallel = this.cachedParallel;

        // 步骤1: 计算材料、输出空间、催化剂支持的最大并行数
        int materialSupportedParallel = this.calculateMaterialParallel(this.currentRecipe);
        int outputSupportedParallel = this.calculateOutputParallel(this.currentRecipe);
        int catalystSupportedParallel = this.calculateCatalystParallel(this.currentRecipe);

        int targetParallel = AlloyFurnaceParallelCalculator.calculateCompletionTargetParallel(
                initialParallel,
                catalystSupportedParallel,
                materialSupportedParallel,
                outputSupportedParallel
        );

        ResolvedCatalystEffect resolvedCatalystEffect = this.cachedCatalystEffect;
        if (resolvedCatalystEffect == null) {
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            resolvedCatalystEffect = CatalystEffectResolver.resolve(this.currentRecipe, catalystStack, this.currentRecipe.processTime());
        }

        targetParallel = (int) Math.min(targetParallel,
                AlloyFurnaceParallelCalculator.calculateEnergyParallel(
                        this.currentRecipe, resolvedCatalystEffect));

        AlloyFurnaceRecipeExecutor.CompletionEnergyResult completionEnergy = AlloyFurnaceRecipeExecutor.settleCompletionEnergy(
                this.energyManager,
                recipeEnergy,
                targetParallel,
                this.accumulatedEnergy,
                resolvedCatalystEffect
        );
        int actualParallel = completionEnergy.actualParallel();

        // 如果没有足够的并行数（至少1），则无法完成配方
        if (actualParallel <= 0) {
            this.resetProgress();
            return;
        }

        // 消耗材料并产出物品
        if (!this.consumeRecipeInputs(this.currentRecipe, actualParallel)) {
            this.resetProgress();
            return;
        }
        this.produceRecipeOutputs(this.currentRecipe, actualParallel);

        this.resetProgress();
    }

    /**
     * 开始处理新配方
     * <p>
     * 初始化配方处理状态，计算并行数
     *
     * @param recipe 要处理的配方
     */
    private void startRecipeProcessing(AdvancedAlloyFurnaceRecipe recipe) {
        this.currentRecipe = recipe;

        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        int baseTime = recipe.processTime();
        ResolvedCatalystEffect resolvedCatalystEffect = CatalystEffectResolver.resolve(recipe, catalystStack, baseTime);
        this.cachedCatalystEffect = resolvedCatalystEffect;
        this.maxProgress = resolvedCatalystEffect.processTime();

        this.progress = 0;

        // 使用统一的并行计算方法
        this.cachedParallel = this.calculateActualParallel(recipe);
        this.accumulatedEnergy = 0;

        this.isUselessIngotRecipe = resolvedCatalystEffect.uselessIngotRecipe();
        this.targetUselessIngotTier = resolvedCatalystEffect.targetUselessIngotTier();

        this.setChanged();
    }

    @Override
    public @NotNull Component getDisplayName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : Component.translatable("container.useless_mod.advanced_alloy_furnace");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new AdvancedAlloyFurnaceMenu(id, inventory, this, this.getData());
    }

    public ContainerData getData() {
        return this.data;
    }

    public ItemStackHandler getItemHandler() {
        return this.itemHandler;
    }

    /**
     * 获取方向感知的物品处理器。
     * <p>
     * 根据面的输入输出模式限制外部物流手段的访问。
     *
     * @param side 输入方向
     * @return 物品处理器
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return new FurnaceSidedItemHandler(this.itemHandler, side, this);
    }

    // ==================== 面模式管理方法 ====================

    /**
     * 获取指定逻辑面的模式。
     */
    public FurnaceFaceMode getFaceMode(FurnaceFace face) {
        return this.faceModes[face.ordinal()];
    }

    /**
     * 设置指定逻辑面的模式。
     */
    public void setFaceMode(FurnaceFace face, FurnaceFaceMode mode) {
        this.faceModes[face.ordinal()] = mode;
        this.setChanged();
    }

    /**
     * 将指定逻辑面的模式循环到下一个。
     *
     * @return 新的模式
     */
    public FurnaceFaceMode cycleFaceMode(FurnaceFace face) {
        return this.cycleFaceMode(face, false);
    }

    public FurnaceFaceMode cycleFaceMode(FurnaceFace face, boolean reverse) {
        FurnaceFaceMode current = this.faceModes[face.ordinal()];
        FurnaceFaceMode next = reverse ? current.previous() : current.next();
        this.faceModes[face.ordinal()] = next;
        this.setChanged();
        // 同步到客户端
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return next;
    }

    /**
     * 获取所有面模式数组的副本。
     */
    public FurnaceFaceMode[] getFaceModes() {
        return this.faceModes.clone();
    }

    /**
     * 获取方块的水平朝向。
     */
    public Direction getFacing() {
        return this.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
    }

    // ==================== 自动输入输出开关 ====================

    public boolean isAutoInputEnabled() {
        return this.autoInputEnabled;
    }

    public void setAutoInputEnabled(boolean enabled) {
        this.autoInputEnabled = enabled;
        this.setChanged();
    }

    public boolean isAutoOutputEnabled() {
        return this.autoOutputEnabled;
    }

    public void setAutoOutputEnabled(boolean enabled) {
        this.autoOutputEnabled = enabled;
        this.setChanged();
    }

    /**
     * 切换自动输入开关。
     */
    public boolean toggleAutoInput() {
        this.autoInputEnabled = !this.autoInputEnabled;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return this.autoInputEnabled;
    }

    /**
     * 切换自动输出开关。
     */
    public boolean toggleAutoOutput() {
        this.autoOutputEnabled = !this.autoOutputEnabled;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return this.autoOutputEnabled;
    }

    // ==================== 红石控制 ====================

    public RedstoneControlMode getRedstoneControlMode() {
        return this.redstoneControlMode;
    }

    public void setRedstoneControlMode(RedstoneControlMode mode) {
        this.redstoneControlMode = mode;
        this.setChanged();
    }

    public RedstoneControlMode cycleRedstoneControlMode() {
        this.redstoneControlMode = this.redstoneControlMode.next();
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return this.redstoneControlMode;
    }

    public boolean isRedstoneBlocked() {
        boolean hasSignal = this.level != null && this.level.hasNeighborSignal(this.worldPosition);
        return !this.redstoneControlMode.shouldRun(hasSignal);
    }

    public boolean isReturnOutputToAe() {
        return this.returnOutputToAe;
    }

    public void setReturnOutputToAe(boolean value) {
        this.returnOutputToAe = value;
        this.setChanged();
    }

    public void cancelAllAETasks() {
        this.aeManager.cancelAllTasks();
    }

    public boolean hasPersistedAETaskData() {
        return this.aeManager.hasPersistedData();
    }

    public void saveAETasks(CompoundTag tag, HolderLookup.Provider registries) {
        this.aeManager.saveTasks(tag, registries);
    }

    public void readAETasks(CompoundTag tag) {
        this.aeManager.readTasksTag(tag);
    }

    public void markDropDataCaptured() {
        this.dropDataCaptured = true;
    }

    public boolean isDropDataCaptured() {
        return this.dropDataCaptured;
    }

    @Override
    public void stashUnreturnedInput(AEKey key, long amount) {
        this.aeManager.stashUnreturnedInput(key, amount);
    }

    public void stashUnreturnedOutput(AEKey key, long amount) {
        this.aeManager.stashUnreturnedOutput(key, amount);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyManager;
    }

    public IEnergyManager getEnergyManager() {
        return this.energyManager;
    }

    public FluidTank getInputFluidTank(int index) {
        return (index >= 0 && index < FLUID_TANK_COUNT) ? this.inputFluidTanks[index] : new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        return (index >= 0 && index < FLUID_TANK_COUNT) ? this.outputFluidTanks[index] : new FluidTank(0);
    }

    public FurnaceChemicalStorage getInputChemicalStorage() {
        return this.inputChemicalStorage;
    }

    public FurnaceChemicalStorage getOutputChemicalStorage() {
        return this.outputChemicalStorage;
    }

    public ChemicalStackView getInputChemical(int index) {
        return this.inputChemicalStorage.getStackInSlot(index);
    }

    public ChemicalStackView getOutputChemical(int index) {
        return this.outputChemicalStorage.getStackInSlot(index);
    }

    public long getChemicalTankCapacity() {
        return this.chemicalTankCapacity;
    }

    public boolean hasChemicalSupport() {
        return this.inputChemicalStorage.isAvailable() && this.outputChemicalStorage.isAvailable();
    }

    public long getEnergy() {
        return this.energyManager.getEnergyStoredLong();
    }

    public void setEnergy(long energy) {
        this.energyManager.setEnergyStored(energy);
        this.setChanged();
    }

    public long getMaxEnergy() {
        return this.energyManager.getMaxEnergyStoredLong();
    }

    public void setMaxEnergy(long energy) {
        this.energyManager.setMaxEnergyStored(energy);
    }

    int getCurrentParallel() {
        if (this.currentRecipe != null && this.progress > 0) {
            return this.cachedParallel;
        }
        return this.calculateDisplayParallel();
    }

    void setCurrentParallel(int parallel) {
        this.currentParallel = parallel;
        this.setChanged();
    }

    public int getCatalystMaxParallel() {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        if (catalystStack.isEmpty()) return 1;
        return CatalystType.fromStack(catalystStack).getNormalRecipeParallel();
    }

    private int calculateDisplayParallel() {
        // 使用缓存减少UI查询时的配方查找开销
        if (this.level != null) {
            long currentTick = this.level.getGameTime();
            if (currentTick < this.displayParallelCacheTick + DISPLAY_PARALLEL_CACHE_DURATION) {
                return this.cachedDisplayParallel;
            }
            this.displayParallelCacheTick = (int) currentTick;
        }

        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isPresent()) {
            this.cachedDisplayParallel = this.calculateActualParallel(match.get());
        } else {
            int catalystParallel = this.getCatalystMaxParallel();
            this.cachedDisplayParallel = Math.max(1, catalystParallel);
        }
        return this.cachedDisplayParallel;
    }

    public boolean hasMold() {
        return this.hasMold;
    }

    public void setHasMold(boolean hasMold) {
        this.hasMold = hasMold;
        this.setChanged();
    }

    public int getProgress() {
        return this.progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        this.setChanged();
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    public void setMaxProgress(int maxProgress) {
        this.maxProgress = maxProgress;
        this.setChanged();
    }

    int getCachedParallel() {
        return this.cachedParallel;
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe getCurrentRecipe() {
        return this.currentRecipe;
    }

    private FluidTank createTank(int index, boolean isInput) {
        return new FluidTank(this.fluidTankCapacity) {
            @Override
            protected void onContentsChanged() {
                AdvancedAlloyFurnaceBlockEntity.this.setChanged();
                if (AdvancedAlloyFurnaceBlockEntity.this.level != null
                        && !AdvancedAlloyFurnaceBlockEntity.this.level.isClientSide) {
                    AdvancedAlloyFurnaceBlockEntity.this.level.sendBlockUpdated(
                            AdvancedAlloyFurnaceBlockEntity.this.worldPosition,
                            AdvancedAlloyFurnaceBlockEntity.this.getBlockState(),
                            AdvancedAlloyFurnaceBlockEntity.this.getBlockState(), 3
                    );
                }
            }
        };
    }

    public IFluidHandler getInputFluidHandler() {
        return new FurnaceFluidTankHandler(this.inputFluidTanks, true);
    }

    public IFluidHandler getOutputFluidHandler() {
        return new FurnaceFluidTankHandler(this.outputFluidTanks, false);
    }

    public IFluidHandler getCombinedFluidHandler() {
        return new FurnaceCombinedFluidTankHandler(this.inputFluidTanks, this.outputFluidTanks);
    }

    /**
     * 获取方向感知的复合流体处理器。
     * <p>
     * 根据面的输入输出模式限制外部物流手段的访问。
     *
     * @param side 方向
     * @return 流体处理器
     */
    public IFluidHandler getCombinedFluidHandler(@Nullable Direction side) {
        if (side == null) return getCombinedFluidHandler();
        return new FurnaceSidedFluidHandler(this.inputFluidTanks, this.outputFluidTanks, side, this);
    }

    public void clearFluidTank(int tankIndex, boolean isInput) {
        if (tankIndex < 0 || tankIndex >= FLUID_TANK_COUNT) return;

        if (isInput) {
            this.inputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        } else {
            this.outputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        }
        this.setChanged();

        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                                        this.getBlockState(), 3
            );
        }
    }

    public void clearChemicalTank(int tankIndex, boolean isInput) {
        if (!this.hasChemicalSupport() || tankIndex < 0 || tankIndex >= CHEMICAL_TANK_COUNT) return;
        if (isInput) {
            this.inputChemicalStorage.setStackInSlot(tankIndex, ChemicalStackView.EMPTY);
        } else {
            this.outputChemicalStorage.setStackInSlot(tankIndex, ChemicalStackView.EMPTY);
        }
        this.onChemicalStorageChanged();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadTag(tag, registries);

        // 加载阶级（必须在加载其他数据之前，因为会影响容量）
        if (tag.contains(NBTConstants.FURNACE_TIER)) {
            this.setFurnaceTier(tag.getInt(NBTConstants.FURNACE_TIER));
            this.updateCapacityByTier();
        }

        if (tag.contains(NBTConstants.INVENTORY)) {
            this.itemHandler.deserializeNBT(registries, tag.getCompound(NBTConstants.INVENTORY));
        }

        if (tag.contains(NBTConstants.ENERGY)) {
            this.energyManager.setEnergyStored(tag.getLong(NBTConstants.ENERGY));
        }

        this.progress = tag.getInt(NBTConstants.PROGRESS);
        this.maxProgress = tag.getInt(NBTConstants.MAX_PROGRESS);
        this.currentParallel = tag.getInt(NBTConstants.CURRENT_PARALLEL);
        this.hasMold = tag.getBoolean(NBTConstants.HAS_MOLD);
        this.cachedParallel = tag.getInt(NBTConstants.CACHED_PARALLEL);
        if (this.cachedParallel <= 0) this.cachedParallel = 1;
        this.isUselessIngotRecipe = tag.getBoolean(NBTConstants.IS_USELESS_INGOT_RECIPE);
        this.targetUselessIngotTier = tag.getInt(NBTConstants.TARGET_USELESS_INGOT_TIER);
        this.accumulatedEnergy = tag.getLong(NBTConstants.ACCUMULATED_ENERGY);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            String inputFluidTag = NBTConstants.getInputFluidTag(i);
            if (tag.contains(inputFluidTag)) {
                FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(inputFluidTag));
                this.inputFluidTanks[i].setFluid(fluid);
            } else {
                this.inputFluidTanks[i].setFluid(FluidStack.EMPTY);
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            String outputFluidTag = NBTConstants.getOutputFluidTag(i);
            if (tag.contains(outputFluidTag)) {
                FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(outputFluidTag));
                this.outputFluidTanks[i].setFluid(fluid);
            } else {
                this.outputFluidTanks[i].setFluid(FluidStack.EMPTY);
            }
        }

        this.inputChemicalStorage.load(tag, "InputChemical", registries);
        this.outputChemicalStorage.load(tag, "OutputChemical", registries);

        // 加载AE网络节点数据
        this.mainNode.loadFromNBT(tag);

        // 加载面模式
        this.faceModes[FurnaceFace.TOP.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeTop"));
        this.faceModes[FurnaceFace.BOTTOM.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeBottom"));
        this.faceModes[FurnaceFace.FRONT.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeFront"));
        this.faceModes[FurnaceFace.BACK.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeBack"));
        this.faceModes[FurnaceFace.LEFT.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeLeft"));
        this.faceModes[FurnaceFace.RIGHT.ordinal()] = FurnaceFaceMode.byIndex(tag.getInt("FaceModeRight"));

        // 加载自动输入输出开关
        this.autoInputEnabled = tag.getBoolean("AutoInputEnabled");
        this.autoOutputEnabled = tag.getBoolean("AutoOutputEnabled");

        // 加载红石控制模式
        this.redstoneControlMode = RedstoneControlMode.byIndex(tag.getInt("RedstoneControlMode"));

        if (tag.contains("ReturnOutputToAe")) {
            this.returnOutputToAe = tag.getBoolean("ReturnOutputToAe");
        }

        if (tag.contains("PatternPriority")) {
            this.aeManager.setPatternPriority(tag.getInt("PatternPriority"));
        }

        // 重新解析样板槽中的样板（必须在物品加载之后）
        this.updatePatterns();

        // 记录 AE 合成任务数据，推迟到 level 可用（首 tick）时解码
        this.aeManager.readTasksTag(tag);

    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putInt(NBTConstants.FURNACE_TIER, this.furnaceTier);
        tag.put(NBTConstants.INVENTORY, this.itemHandler.serializeNBT(registries));
        tag.putLong(NBTConstants.ENERGY, this.energyManager.getEnergyStoredLong());
        tag.putInt(NBTConstants.PROGRESS, this.progress);
        tag.putInt(NBTConstants.MAX_PROGRESS, this.maxProgress);
        tag.putInt(NBTConstants.CURRENT_PARALLEL, this.currentParallel);
        tag.putBoolean(NBTConstants.HAS_MOLD, this.hasMold);
        tag.putInt(NBTConstants.CACHED_PARALLEL, this.cachedParallel);
        tag.putBoolean(NBTConstants.IS_USELESS_INGOT_RECIPE, this.isUselessIngotRecipe);
        tag.putInt(NBTConstants.TARGET_USELESS_INGOT_TIER, this.targetUselessIngotTier);
        tag.putLong(NBTConstants.ACCUMULATED_ENERGY, this.accumulatedEnergy);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.inputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                tag.put(NBTConstants.getInputFluidTag(i), fluid.save(registries));
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.outputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                tag.put(NBTConstants.getOutputFluidTag(i), fluid.save(registries));
            }
        }

        this.inputChemicalStorage.save(tag, "InputChemical", registries);
        this.outputChemicalStorage.save(tag, "OutputChemical", registries);

        // 保存AE网络节点数据
        this.mainNode.saveToNBT(tag);

        // 保存样板优先级
        tag.putInt("PatternPriority", this.aeManager.getPatternPriority());

        // 保存面模式
        tag.putInt("FaceModeTop", this.faceModes[FurnaceFace.TOP.ordinal()].ordinal());
        tag.putInt("FaceModeBottom", this.faceModes[FurnaceFace.BOTTOM.ordinal()].ordinal());
        tag.putInt("FaceModeFront", this.faceModes[FurnaceFace.FRONT.ordinal()].ordinal());
        tag.putInt("FaceModeBack", this.faceModes[FurnaceFace.BACK.ordinal()].ordinal());
        tag.putInt("FaceModeLeft", this.faceModes[FurnaceFace.LEFT.ordinal()].ordinal());
        tag.putInt("FaceModeRight", this.faceModes[FurnaceFace.RIGHT.ordinal()].ordinal());

        // 保存自动输入输出开关
        tag.putBoolean("AutoInputEnabled", this.autoInputEnabled);
        tag.putBoolean("AutoOutputEnabled", this.autoOutputEnabled);

        // 保存红石控制模式
        tag.putInt("RedstoneControlMode", this.redstoneControlMode.ordinal());

        // 保存产物是否回AE
        tag.putBoolean("ReturnOutputToAe", this.returnOutputToAe);

        // 保存 AE 合成任务（仅落盘，网络更新包会剥离）
        CompoundTag aeTasksTag = new CompoundTag();
        this.aeManager.saveTasks(aeTasksTag, registries);
        tag.put("AeTasks", aeTasksTag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        // AE 合成任务数据体积大且客户端无需，网络更新包中剥离
        tag.remove("AeTasks");
        return tag;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
        this.aeManager.shutdown();
    }


    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, (be) -> {
                                   be.mainNode.create(getLevel(), getBlockPos());
                                   // 节点创建后重新解析样板并通知AE网络
                                   be.updatePatterns();
                                   // level 已可用，加载持久化的 AE 合成任务
                                   be.aeManager.loadDeferredTasks();
                               }
        );
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.handleUpdateTag(tag, registries);
        this.loadAdditional(tag, registries);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }


    @Override
    public void onLoad() {
        super.onLoad();
        // 节点创建由clearRemoved中的GridHelper.onFirstTick处理，确保只创建一次
    }

    /**
     * 查找匹配的配方（统一匹配，支持物品+流体+模具优先级）
     * <p>
     * 每次按当前输入查询最具体配方；查询管理器会用包含数量的不可变键缓存结果。
     *
     * @return 匹配的配方，如果没有则返回空
     */
    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        return this.recipeCalculator.findMatchingRecipe(this.level);
    }

    /**
     * 检查是否有足够的输入材料支持至少一次配方
     * （用于开始新配方前的检查，委托给配方计算器）。
     */
    private boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        return this.recipeCalculator.canConsumeRecipeInputs(recipe);
    }

    private boolean consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        if (!this.recipeCalculator.canConsumeRecipeInputs(recipe, parallel)) return false;
        return FurnaceInputPort.consumeRecipeInputs(
                recipe,
                parallel,
                this.itemHandler,
                INPUT_SLOTS_START,
                INPUT_SLOTS_COUNT,
                this.inputFluidTanks,
                FLUID_TANK_COUNT,
                this.inputChemicalStorage,
                ChemicalKeyProviders.get()
        );
    }

    private void produceRecipeOutputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        FurnaceOutputPort.outputRecipe(
                recipe,
                parallel,
                this.createAeOutputPort(),
                this.itemHandler,
                OUTPUT_SLOTS_START,
                OUTPUT_SLOTS_COUNT,
                this.outputFluidTanks,
                FLUID_TANK_COUNT,
                this.outputChemicalStorage,
                ChemicalKeyProviders.get(),
                this::stashUnreturnedOutput
        );
    }

    private void resetProgress() {
        this.progress = 0;
        this.currentRecipe = null;
        this.cachedCatalystEffect = null;
        this.cachedParallel = 1;
        this.cachedDisplayParallel = 1;
        this.displayParallelCacheTick = 0;
        this.isUselessIngotRecipe = false;
        this.targetUselessIngotTier = 0;
        this.accumulatedEnergy = 0;
        this.setChanged();
    }

    private void updateMoldState() {
        ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
        this.hasMold = !moldStack.isEmpty();
        this.setChanged();
    }

    @Override
    public void onSaveChanges(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        setChanged();
    }

    @Override
    public void onGridChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        isConnectedToAE = node.isActive();
        setChanged();
    }

    // ==================== AE网络支持方法 ====================

    @Override
    public void onStateChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node,
                               IGridNodeListener.State state) {
        isConnectedToAE = node.isActive();
        setChanged();
    }

    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    public boolean isActive() {
        return this.mainNode.isActive();
    }

    // AE2 Integration - 获取存储服务
    private MEStorage getStorageService() {
        if (!isConnectedToAE) {
            return null;
        }

        IGridNode node = this.mainNode.getNode();
        if (node == null || !node.isActive()) {
            return null;
        }

        IGrid grid = node.getGrid();
        if (grid == null) {
            return null;
        }

        IStorageService storageService = grid.getService(IStorageService.class);
        if (storageService == null) {
            return null;
        }

        return storageService.getInventory();
    }

    // AE2 Integration - 尝试输出物品到AE网络
    public long tryOutputToAE(ItemStack stack) {
        if (stack.isEmpty() || !isConnectedToAE || actionSource == null) {
            return 0;
        }

        MEStorage storage = getStorageService();

        if (storage == null) {
            return 0;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }

        long amount = stack.getCount();

        return storage.insert(key, amount, Actionable.MODULATE, actionSource);
    }

    // AE2 Integration - 尝试输出流体到AE网络
    public long tryOutputFluidToAE(FluidStack stack) {
        if (stack.isEmpty() || !isConnectedToAE || actionSource == null) {
            return 0;
        }

        MEStorage storage = getStorageService();

        if (storage == null) {
            return 0;
        }

        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return 0;
        }

        long amount = stack.getAmount();

        return storage.insert(key, amount, Actionable.MODULATE, actionSource);
    }

    public long tryOutputKeyToAE(AEKey key, long amount) {
        if (key == null || amount <= 0 || !isConnectedToAE || actionSource == null) {
            return 0;
        }

        MEStorage storage = getStorageService();

        if (storage == null) {
            return 0;
        }

        return storage.insert(key, amount, Actionable.MODULATE, actionSource);
    }

    @Override
    public long tryOutputChemicalToAE(ChemicalStackView stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        ChemicalKeyProvider provider = ChemicalKeyProviders.get();
        appeng.api.stacks.GenericStack generic = provider.toGenericStack(stack);
        return generic == null ? 0L : this.tryOutputKeyToAE(generic.what(), generic.amount());
    }

    @Override
    public void handleUnreturnedChemical(ChemicalStackView stack) {
        if (stack == null || stack.isEmpty()) return;
        appeng.api.stacks.GenericStack generic = ChemicalKeyProviders.get().toGenericStack(stack);
        if (generic != null) {
            this.aeManager.stashUnreturnedInput(generic.what(), generic.amount());
        }
    }

    @Override
    public ChemicalHandlerView getAdjacentChemicalHandler(Level level, BlockPos pos, BlockState state,
                                                           BlockEntity entity, @Nullable Direction side) {
        if (!this.hasChemicalSupport()) return null;
        return ChemicalCompatProviders.get().getAdjacentHandler(level, pos, state, entity, side);
    }

    public int getActiveAETaskCount() {
        return this.aeManager.getActiveAETaskCount();
    }

    // 获取最大AE任务数量（基于熔炉等级）
    public int getMaxAETaskCount() {
        return this.furnaceTier + 1;
    }

    @Override
    public Iterable<ItemStack> getPatternStacks() {
        List<ItemStack> result = new java.util.ArrayList<>(PATTERN_SLOTS_COUNT);
        for (int slot = PATTERN_SLOTS_START; slot <= PATTERN_SLOTS_END; slot++) {
            result.add(itemHandler.getStackInSlot(slot));
        }
        return result;
    }

    // AE合成任务状态设置方法（用于客户端同步）
    public void setActiveAETaskCount(int value) {
        this.aeManager.setActiveAETaskCount(value);
    }

    public int getTotalAEProgress() {
        return this.aeManager.getTotalAEProgress();
    }

    public void setTotalAEProgress(int value) {
        this.aeManager.setTotalAEProgress(value);
    }

    public int getTotalAEMaxProgress() {
        return this.aeManager.getTotalAEMaxProgress();
    }

    public void setTotalAEMaxProgress(int value) {
        this.aeManager.setTotalAEMaxProgress(value);
    }

    // 获取所有AE任务进度信息（用于UI显示）
    public Collection<AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressList() {
        return this.aeManager.getAETaskProgressList();
    }


    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return this.aeManager.getAvailablePatterns();
    }

    @Override
    public int getPatternPriority() {
        return this.aeManager.getPatternPriority();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return this.aeManager.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return this.aeManager.isBusy();
    }

    private void updatePatterns() {
        this.aeManager.updatePatterns();
    }

    // ==================== CraftingTaskContext 接口实现 ====================

    @Override
    public int getInputSlotsStart() {
        return INPUT_SLOTS_START;
    }

    @Override
    public int getInputSlotsCount() {
        return INPUT_SLOTS_COUNT;
    }

    @Override
    public int getOutputSlotsStart() {
        return OUTPUT_SLOTS_START;
    }

    @Override
    public int getOutputSlotsCount() {
        return OUTPUT_SLOTS_COUNT;
    }

    @Override
    public int getCatalystSlot() {
        return CATALYST_SLOT;
    }

    @Override
    public int getMoldSlot() {
        return MOLD_SLOT;
    }

    @Override
    public int getFluidTankCount() {
        return FLUID_TANK_COUNT;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public void markChanged() {
        setChanged();
    }

    @Override
    public ReentrantLock getCraftingLock() {
        return this.aeManager.getCraftingLock();
    }

    @Override
    public FluidTank[] getInputFluidTanks() {
        return inputFluidTanks;
    }

    @Override
    public FluidTank[] getOutputFluidTanks() {
        return outputFluidTanks;
    }

    @Override
    public ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap() {
        return this.aeManager.getAETaskProgressMap();
    }

    @Override
    public AtomicInteger getTotalAEMaxProgressAtomic() {
        return this.aeManager.getTotalAEMaxProgressAtomic();
    }

    @Override
    public AtomicInteger getTotalAEProgressAtomic() {
        return this.aeManager.getTotalAEProgressAtomic();
    }

    // ==================== PatternContainer 接口实现 ====================

    /**
     * 样板槽位的 InternalInventory 适配器
     * 将样板槽位包装成 AE2 的 InternalInventory 接口，用于样板管理终端访问
     */
    private final InternalInventory patternInventory = new InternalInventory() {
        @Override
        public int size() {
            return PATTERN_SLOTS_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            if (slotIndex < 0 || slotIndex >= PATTERN_SLOTS_COUNT) {
                return ItemStack.EMPTY;
            }
            return itemHandler.getStackInSlot(PATTERN_SLOTS_START + slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (slotIndex < 0 || slotIndex >= PATTERN_SLOTS_COUNT) {
                return;
            }
            itemHandler.setStackInSlot(PATTERN_SLOTS_START + slotIndex, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // 只允许编码样板
            return !stack.isEmpty() && PatternDetailsHelper.decodePattern(stack, level) != null;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1; // 样板槽位只能放一个物品
        }
    };

    @Override
    @Nullable
    public IGrid getGrid() {
        return mainNode.getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        // 始终在样板管理终端中显示
        return true;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return patternInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        // 按位置排序
        return (long) worldPosition.getZ() << 24 ^ (long) worldPosition.getX() << 8 ^ worldPosition.getY();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        // 使用方块本身的图标和名称
        var blockState = getBlockState();
        var block = blockState.getBlock();
        var itemStack = new ItemStack(block);
        var icon = AEItemKey.of(itemStack);
        
        // 使用 MenuProvider 的 getDisplayName() 方法
        Component name = this.getDisplayName();
        
        return new PatternContainerGroup(icon, name, List.of());
    }

}
