package com.sorrowmist.useless.content.blockentities;

import appeng.api.config.Actionable;
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
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution.AlloyFurnaceRecipeExecutor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceInputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModTags;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
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

public class AdvancedAlloyFurnaceBlockEntity extends AEBaseBlockEntity implements MenuProvider, ICraftingProvider, IInWorldGridNodeHost, IGridNodeListener<AdvancedAlloyFurnaceBlockEntity>, IActionHost, CraftingTaskContext, PatternContainer {

    // 基础容量配置
    private static final int BASE_FLUID_TANK_CAPACITY = 16000;
    private static final int BASE_ENERGY_CAPACITY = 100000;
    private static final int BASE_ENERGY_MAX_RECEIVE = 10000;
    private static final int ENERGY_MAX_EXTRACT = 0;
    private static final int ACTIVE_COOLDOWN_TICKS = 5;
    private static final int AUTO_OUTPUT_INTERVAL = 1;
    private static final int DISPLAY_PARALLEL_CACHE_DURATION = 20;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final int[] MATERIAL_INPUT_SLOT_CANDIDATES;
    private static final int[] CATALYST_INPUT_SLOT_CANDIDATES = {CATALYST_SLOT};
    private static final int[] MOLD_INPUT_SLOT_CANDIDATES = {MOLD_SLOT};
    private static final int[] EMPTY_INPUT_SLOT_CANDIDATES = {};
    static {
        MATERIAL_INPUT_SLOT_CANDIDATES = new int[]{
                INPUT_SLOTS_START, INPUT_SLOTS_START + 1, INPUT_SLOTS_START + 2,
                INPUT_SLOTS_START + 3, INPUT_SLOTS_START + 4, INPUT_SLOTS_START + 5,
                INPUT_SLOTS_START + 6, INPUT_SLOTS_START + 7, INPUT_SLOTS_START + 8,
        };
    }
    private final FluidTank[] inputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final FluidTank[] outputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final IEnergyManager energyManager = EnergyManager.builder()
                                                              .capacity(BASE_ENERGY_CAPACITY)
                                                              .maxReceive(BASE_ENERGY_MAX_RECEIVE)
                                                              .maxExtract(ENERGY_MAX_EXTRACT)
                                                              .onChange(this::setChanged)
                                                              .build();
    private final AdvancedAlloyFurnaceData data = new AdvancedAlloyFurnaceData(this);
    private final ItemStackHandler itemHandler;
    private final AdvancedAlloyFurnaceAeManager aeManager;
    // ==================== AE网络支持 ====================
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    // 升级后的容量（根据阶级动态计算）
    private int fluidTankCapacity = BASE_FLUID_TANK_CAPACITY;
    private int energyCapacity = BASE_ENERGY_CAPACITY;
    private int energyMaxReceive = BASE_ENERGY_MAX_RECEIVE;
    private int progress = 0;
    private int maxProgress = 200;
    private int currentParallel = 1;
    private boolean hasMold = false;
    @Nullable
    private AdvancedAlloyFurnaceRecipe currentRecipe;
    // 上一个成功处理的配方，用于优先匹配以减少配方查找时间
    @Nullable
    private AdvancedAlloyFurnaceRecipe lastSuccessfulRecipe;
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
    // 熔炉阶级 0-9，0为基础等级
    private int furnaceTier = 0;
    // 自动输出计时器
    private int autoOutputTickCounter = 0;
    // 上次成功输出的方向（用于缓存机制）
    @Nullable
    private Direction lastSuccessfulOutputDirection = null;
    private boolean isConnectedToAE = false;
    // 六个面的输入输出模式（按FurnaceFace索引）
    private final FurnaceFaceMode[] faceModes = new FurnaceFaceMode[FurnaceFace.COUNT];
    // 自动输入开关
    private boolean autoInputEnabled = false;
    // 自动输出开关
    private boolean autoOutputEnabled = false;

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
                slot -> this.clearRecipeCache(),
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

        // 初始化时应用当前阶级的容量
        this.updateCapacityByTier();
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            this.inputFluidTanks[i] = this.createTank(i, true);
            this.outputFluidTanks[i] = this.createTank(i, false);
        }

        // 初始化所有面模式为禁止
        Arrays.fill(this.faceModes, FurnaceFaceMode.DISABLED);
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

    /**
     * 计算能量槽容量
     * 基础100000，前3阶2倍增长，之后4倍增长，9阶达到int最大值
     */
    public static int calculateEnergyCapacity(int tier) {
        if (tier <= 0) return BASE_ENERGY_CAPACITY;
        if (tier >= 9) return Integer.MAX_VALUE;

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
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    /**
     * 计算能量输入速度
     * 基础10000，前3阶2倍增长，之后4倍增长，9阶达到int最大值
     */
    public static int calculateEnergyReceive(int tier) {
        if (tier <= 0) return BASE_ENERGY_MAX_RECEIVE;
        if (tier >= 9) return Integer.MAX_VALUE;

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
        return receive > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) receive;
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

        boolean wasActive = entity.getBlockState().getValue(
                com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock.getActiveProperty());

        if (entity.currentRecipe == null) {
            entity.tryStartNewRecipe();
        } else {
            entity.processCurrentRecipe();
        }

        entity.aeManager.flushAEBatches();
        entity.aeManager.tickAETasks();

        // 每tick尝试自动输入输出物品和流体
        entity.autoOutputTickCounter++;
        if (entity.autoOutputTickCounter >= AUTO_OUTPUT_INTERVAL) {
            entity.autoOutputTickCounter = 0;
            entity.autoOutputItemsAndFluids(level);
        }

        // 判断是否应该处于活跃状态
        boolean isProcessing = entity.currentRecipe != null && entity.progress > 0;

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
     * 使用指数增长曲线，9阶达到int最大值
     */
    private void updateCapacityByTier() {
        this.fluidTankCapacity = calculateFluidCapacity(this.furnaceTier);
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
    }

    /**
     * 获取当前熔炉阶级
     */
    public int getFurnaceTier() {
        return this.furnaceTier;
    }

    public void setClientFurnaceTier(int tier) {
        this.furnaceTier = Math.max(0, Math.min(9, tier));
    }

    /**
     * 设置熔炉阶级（内部使用，不触发容量更新）
     */
    private void setFurnaceTier(int tier) {
        this.furnaceTier = Math.max(0, Math.min(9, tier));
    }

    /**
     * 尝试升级熔炉
     *
     * @param targetTier 目标阶级（1-9）
     * @return 是否升级成功
     */
    public boolean tryUpgrade(int targetTier) {
        // 只能升级到更高阶级
        if (targetTier <= this.furnaceTier) {
            return false;
        }
        // 限制在1-9范围内
        if (targetTier < 1 || targetTier > 9) {
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
    public int getEnergyCapacity() {
        return this.energyCapacity;
    }

    /**
     * 获取能量输入速度
     */
    public int getEnergyMaxReceive() {
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
            if (bestMatch.isPresent() && !bestMatch.get().id().equals(this.currentRecipe.id())) {
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
        int baseEnergyPerTick = AlloyFurnaceRecipeExecutor.calculateBaseEnergyPerTick(this.currentRecipe);
        AlloyFurnaceRecipeExecutor.TickResult tickResult = AlloyFurnaceRecipeExecutor.consumeTickEnergy(this.energyManager, baseEnergyPerTick, actualParallel,
                                                                                                        resolvedCatalystEffect
        );

        // 能量不足时暂停进度，但不重置
        if (!tickResult.consumedEnergy()) return;

        // 累积能量
        this.accumulatedEnergy += tickResult.energyConsumed();
        this.progress++;

        if (this.progress >= this.maxProgress) {
            this.completeRecipe();
        }

        this.setChanged();
    }

    /**
     * 计算实际可用的并行数
     * 按照以下顺序计算，避免数据溢出：
     * 1. 通过配方及能量上限，计算当前配方理论允许的最大并行
     * 2. 通过催化剂获取当前催化剂允许的并行量
     * 3. 通过输入物品，匹配配方实际能运行的并行量
     * 4. 通过输出空间，计算能容纳的并行量
     * <p>
     * 所有计算都遵循"先除再乘"原则，避免溢出
     */
    private int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe) {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        ResolvedCatalystEffect resolvedCatalystEffect = CatalystEffectResolver.resolve(recipe, catalystStack, recipe.processTime());
        int energyParallel = AlloyFurnaceParallelCalculator.calculateEnergyParallel(this.energyManager, recipe,
                                                                                    resolvedCatalystEffect
        );
        int catalystParallel = resolvedCatalystEffect.recipeParallel();
        int materialParallel = this.calculateMaterialParallel(recipe);
        int outputParallel = this.calculateOutputParallel(recipe);
        return AlloyFurnaceParallelCalculator.calculateStartableParallel(energyParallel, catalystParallel, materialParallel, outputParallel);
    }

    /**
     * 步骤2: 计算催化剂允许的并行数
     * 优先使用缓存的催化剂效果，避免重复解析
     */
    private int calculateCatalystParallel(AdvancedAlloyFurnaceRecipe recipe) {
        if (this.cachedCatalystEffect != null) {
            return this.cachedCatalystEffect.recipeParallel();
        }
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        return CatalystEffectResolver.resolve(recipe, catalystStack, recipe.processTime()).recipeParallel();
    }

    /**
     * 步骤3: 计算输入材料允许的并行数
     * 对于每种材料: 可用数量 / 配方需求数量 = 该材料允许的并行数
     * 取所有材料的最小值
     */
    private int calculateMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int minParallel = Integer.MAX_VALUE;
        boolean hasCalculation = false;

        // 计算物品输入限制
        for (var countedIng : recipe.inputs()) {
            long totalAvailable = 0;
            var ingredient = countedIng.ingredient();
            long requiredPerParallel = countedIng.count();

            if (requiredPerParallel <= 0) continue;

            hasCalculation = true;

            // 统计所有输入槽中符合条件的物品总数
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    totalAvailable += stack.getCount();
                }
            }

            // 先除: 可用数量 / 需求数量 = 该材料允许的并行数
            long parallelLong = totalAvailable / requiredPerParallel;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            minParallel = Math.min(minParallel, possibleParallel);

            // 如果已经降到0，提前返回
            if (minParallel <= 0) return 0;
        }

        // 计算流体输入限制
        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long totalAvailable = 0;
            long requiredPerParallel = requiredFluid.getAmount();

            if (requiredPerParallel <= 0) continue;

            hasCalculation = true;

            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    totalAvailable += tankFluid.getAmount();
                }
            }

            // 先除: 可用数量 / 需求数量 = 该流体允许的并行数
            long parallelLong = totalAvailable / requiredPerParallel;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            minParallel = Math.min(minParallel, possibleParallel);

            if (minParallel <= 0) return 0;
        }

        return hasCalculation ? minParallel : 1;
    }

    /**
     * 步骤4: 计算输出空间允许的并行数
     * 对于每种输出: 可用空间 / 单次产出数量 = 该输出允许的并行数
     * 取所有输出的最小值
     */
    private int calculateOutputParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int maxParallel = Integer.MAX_VALUE;

        // 计算物品输出空间限制
        for (ItemStack output : recipe.outputs()) {
            long totalSpace = 0;
            int outputCount = output.getCount();

            if (outputCount <= 0) continue;

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                int slotLimit = this.itemHandler.getSlotLimit(i);

                if (slotStack.isEmpty()) {
                    totalSpace += slotLimit;
                } else if (ItemStack.isSameItemSameComponents(slotStack, output)) {
                    totalSpace += (long) slotLimit - slotStack.getCount();
                }
            }

            // 先除: 可用空间 / 单次产出数量 = 该输出允许的并行数
            long parallelLong = totalSpace / outputCount;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            maxParallel = Math.min(maxParallel, possibleParallel);

            if (maxParallel <= 0) return 0;
        }

        // 计算流体输出空间限制
        for (FluidStack outputFluid : recipe.outputFluids()) {
            long totalSpace = 0;
            int fluidAmount = outputFluid.getAmount();

            if (fluidAmount <= 0) continue;

            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                int tankCapacity = this.outputFluidTanks[i].getCapacity();

                if (tankFluid.isEmpty()) {
                    totalSpace += tankCapacity;
                } else if (FluidStack.isSameFluidSameComponents(tankFluid, outputFluid)) {
                    totalSpace += (long) tankCapacity - tankFluid.getAmount();
                }
            }

            // 先除: 可用空间 / 单次产出数量 = 该流体允许的并行数
            long parallelLong = totalSpace / fluidAmount;
            int possibleParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
            maxParallel = Math.min(maxParallel, possibleParallel);

            if (maxParallel <= 0) return 0;
        }

        return maxParallel;
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
        int recipeEnergy = this.currentRecipe.energy();
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
        this.consumeRecipeInputs(this.currentRecipe, actualParallel);
        this.produceRecipeOutputs(this.currentRecipe, actualParallel);

        // 记录上一个成功处理的配方，用于下次优先匹配
        this.lastSuccessfulRecipe = this.currentRecipe;

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

        // 更新上一个成功处理的配方
        this.lastSuccessfulRecipe = recipe;

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
     * 从底部输入且底部模式为"催化剂输入"时优先进入催化剂槽位。
     *
     * @param side 输入方向
     * @return 物品处理器
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return new SidedItemHandler(this.itemHandler, null, this);
        }
        return new SidedItemHandler(this.itemHandler, side, this);
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
        FurnaceFaceMode current = this.faceModes[face.ordinal()];
        FurnaceFaceMode next = current.next();
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

    public int getEnergy() {
        return this.energyManager.getEnergyStored();
    }

    public void setEnergy(int energy) {
        this.energyManager.setEnergyStored(energy);
        this.setChanged();
    }

    public int getMaxEnergy() {
        return this.energyManager.getMaxEnergyStored();
    }

    public void setMaxEnergy(int energy) {
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
        return new FluidTankHandler(this.inputFluidTanks, true);
    }

    public IFluidHandler getOutputFluidHandler() {
        return new FluidTankHandler(this.outputFluidTanks, false);
    }

    public IFluidHandler getCombinedFluidHandler() {
        return new CombinedFluidTankHandler(this.inputFluidTanks, this.outputFluidTanks);
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
        return new SidedFluidHandler(this.inputFluidTanks, this.outputFluidTanks, side, this);
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
            this.energyManager.setEnergyStored(tag.getInt(NBTConstants.ENERGY));
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

        if (tag.contains("PatternPriority")) {
            this.aeManager.setPatternPriority(tag.getInt("PatternPriority"));
        }

        // 重新解析样板槽中的样板（必须在物品加载之后）
        this.updatePatterns();

    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putInt(NBTConstants.FURNACE_TIER, this.furnaceTier);
        tag.put(NBTConstants.INVENTORY, this.itemHandler.serializeNBT(registries));
        tag.putInt(NBTConstants.ENERGY, this.energyManager.getEnergyStored());
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
     * 优先检查上一个成功处理的配方（直接遍历slot，无需构建输入列表），
     * 仅在 lastSuccessfulRecipe 失效时才进行完整的配方查找链。
     *
     * @return 匹配的配方，如果没有则返回空
     */
    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        if (this.level == null) return Optional.empty();

        // 优先检查上次成功配方（无需构建输入列表，直接检查slot）
        if (this.lastSuccessfulRecipe != null && this.canProcessRecipe(this.lastSuccessfulRecipe)) {
            return Optional.of(this.lastSuccessfulRecipe);
        }

        // 构建输入列表（用于 AlloyFurnaceRecipeManager 查找）
        List<ItemStack> currentInputs = new ArrayList<>();
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                currentInputs.add(stack);
            }
        }

        List<FluidStack> currentFluids = new ArrayList<>();
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.inputFluidTanks[i].getFluid();
            if (!fluid.isEmpty()) {
                currentFluids.add(fluid.copy());
            }
        }

        if (currentInputs.isEmpty() && currentFluids.isEmpty()) return Optional.empty();

        ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);

        AdvancedAlloyFurnaceRecipe bestRecipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                this.level, currentInputs, currentFluids, moldStack
        );

        if (bestRecipe != null && canProcessRecipe(bestRecipe)) {
            return Optional.of(bestRecipe);
        }

        return Optional.empty();
    }

    /**
     * 检查配方是否可处理（直接遍历slot，不构建中间列表）
     * <p>
     * 模具检查提前，便于快速失败。
     */
    private boolean canProcessRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        // 模具检查提前（快速失败）
        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            if (!recipe.mold().test(moldStack)) return false;
        }

        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    foundCount += stack.getCount();
                    if (foundCount >= requiredCount) break;
                }
            }

            if (foundCount < requiredCount) return false;
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            boolean found = false;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)
                        && tankFluid.getAmount() >= requiredFluid.getAmount()) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }

    /**
     * 检查是否有足够的输入材料支持指定的并行数
     *
     * @param recipe   配方
     * @param parallel 并行数
     * @return 如果有足够的材料返回true
     */
    private boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count() * (long) parallel;
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) return false;
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long requiredAmount = requiredFluid.getAmount() * (long) parallel;
            long foundAmount = 0;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    foundAmount += tankFluid.getAmount();
                }
            }
            if (foundAmount < requiredAmount) return false;
        }

        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            return recipe.mold().test(moldStack);
        }

        return true;
    }

    /**
     * 检查是否有足够的输入材料支持至少一次配方
     * （用于开始新配方前的检查）
     */
    private boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        return canConsumeRecipeInputs(recipe, 1);
    }

    private void consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        FurnaceInputPort.consumeRecipeInputs(
                recipe,
                parallel,
                this.itemHandler,
                INPUT_SLOTS_START,
                INPUT_SLOTS_COUNT,
                this.inputFluidTanks,
                FLUID_TANK_COUNT
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
                FLUID_TANK_COUNT
        );
    }

    private FurnaceOutputPort.AeOutput createAeOutputPort() {
        return new FurnaceOutputPort.AeOutput() {
            @Override
            public long insertItem(ItemStack stack) {
                return AdvancedAlloyFurnaceBlockEntity.this.tryOutputToAE(stack);
            }

            @Override
            public long insertFluid(FluidStack stack) {
                return AdvancedAlloyFurnaceBlockEntity.this.tryOutputFluidToAE(stack);
            }

            @Override
            public long insertKey(AEKey key, long amount) {
                return AdvancedAlloyFurnaceBlockEntity.this.tryOutputKeyToAE(key, amount);
            }
        };
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

    /**
     * 清空配方缓存和上一个成功配方
     * 当输入槽、模具槽或催化剂槽变化时调用
     */
    private void clearRecipeCache() {
        this.lastSuccessfulRecipe = null;
        AlloyFurnaceRecipeManager.getInstance().clearCache();
        this.setChanged();
    }

    /**
     * 自动输入输出物品和流体到周围的容器
     * 每5tick调用一次
     */
    private void autoOutputItemsAndFluids(Level level) {
        if (level.isClientSide) return;

        // 自动输入（从周围容器拉取）
        if (this.autoInputEnabled) {
            this.autoInputFromSurroundings(level);
        }

        // 自动输出（推送到周围容器）
        if (this.autoOutputEnabled) {
            this.autoOutputToSurroundings(level);
        }
    }

    /**
     * 从周围容器自动输入物品和流体到机器。
     * 仅从开启了对应面模式的面进行输入。
     */
    private void autoInputFromSurroundings(Level level) {
        Direction facing = this.getFacing();
        for (Direction dir : Direction.values()) {
            FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
            if (face == null) continue;
            FurnaceFaceMode mode = this.faceModes[face.ordinal()];
            if (!mode.allowsAny()) continue;

            BlockPos srcPos = this.worldPosition.relative(dir);
            BlockEntity srcEntity = level.getBlockEntity(srcPos);
            if (srcEntity == null) continue;

            // 输入物品
            if (mode.allowsMaterialInput() || mode.allowsCatalystInput() || mode.allowsMoldInput()) {
                IItemHandler srcHandler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, srcPos, srcEntity.getBlockState(), srcEntity, dir.getOpposite());
                if (srcHandler != null) {
                    IItemHandler selfHandler = new SidedItemHandler(this.itemHandler, dir, this);
                    int[] targetSlots = getAutoInputSlotCandidates(mode);
                    for (int srcSlot = 0; srcSlot < srcHandler.getSlots(); srcSlot++) {
                        ItemStack extracted = srcHandler.extractItem(srcSlot, Integer.MAX_VALUE, true);
                        if (extracted.isEmpty()) continue;

                        ItemStack remaining = extracted;
                        for (int machineSlot : targetSlots) {
                            remaining = selfHandler.insertItem(machineSlot, remaining, false);
                            if (remaining.isEmpty()) break;
                        }

                        int moved = extracted.getCount() - remaining.getCount();
                        if (moved > 0) {
                            srcHandler.extractItem(srcSlot, moved, false);
                            this.setChanged();
                        }
                    }
                }
            }

            // 输入流体
            if (mode.allowsMaterialInput()) {
                IFluidHandler srcFluidHandler = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, srcPos, srcEntity.getBlockState(), srcEntity, dir.getOpposite());
                if (srcFluidHandler != null) {
                    IFluidHandler selfFluidHandler = new SidedFluidHandler(this.inputFluidTanks, this.outputFluidTanks, dir, this);
                    FluidStack drained = srcFluidHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                    if (!drained.isEmpty()) {
                        int filled = selfFluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            srcFluidHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                            this.setChanged();
                        }
                    }
                }
            }
        }
    }

    private static int[] getAutoInputSlotCandidates(FurnaceFaceMode mode) {
        if (mode.allowsMaterialInput()) return MATERIAL_INPUT_SLOT_CANDIDATES;
        if (mode.allowsCatalystInput()) return CATALYST_INPUT_SLOT_CANDIDATES;
        if (mode.allowsMoldInput()) return MOLD_INPUT_SLOT_CANDIDATES;
        return EMPTY_INPUT_SLOT_CANDIDATES;
    }

    /**
     * 自动输出物品和流体到周围容器和AE网络。
     * 仅输出到开启了"原材料输出"模式的面。
     */
    private void autoOutputToSurroundings(Level level) {
        // AE网络输出不受面模式控制
        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = tryOutputToAE(stack);
            if (inserted > 0) {
                stack.shrink((int) inserted);
                this.setChanged();
            }
        }

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack fluid = this.outputFluidTanks[i].getFluid();
            if (fluid.isEmpty()) continue;
            long inserted = tryOutputFluidToAE(fluid);
            if (inserted > 0) {
                this.outputFluidTanks[i].drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        Direction facing = this.getFacing();

        // 输出物品到周围容器（仅允许输出模式的面）
        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            for (Direction dir : ALL_DIRECTIONS) {
                FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
                if (face == null) continue;
                if (!this.faceModes[face.ordinal()].allowsMaterialOutput()) continue;
                if (this.tryOutputItemToDirection(level, slot, dir)) {
                    this.lastSuccessfulOutputDirection = dir;
                    this.setChanged();
                    stack = this.itemHandler.getStackInSlot(slot);
                    if (stack.isEmpty()) break;
                }
            }
        }

        // 输出流体到周围容器（仅允许输出模式的面）
        for (int tankIndex = 0; tankIndex < FLUID_TANK_COUNT; tankIndex++) {
            FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
            if (fluid.isEmpty()) continue;
            for (Direction dir : ALL_DIRECTIONS) {
                FurnaceFace face = FurnaceFace.fromDirection(dir, facing);
                if (face == null) continue;
                if (!this.faceModes[face.ordinal()].allowsMaterialOutput()) continue;
                int filled = this.tryOutputFluidToDirection(level, tankIndex, dir);
                if (filled > 0) {
                    this.lastSuccessfulOutputDirection = dir;
                    this.setChanged();
                    fluid = this.outputFluidTanks[tankIndex].getFluid();
                    if (fluid.isEmpty()) break;
                }
            }
        }
    }

    /**
     * 自动输出物品到周围的容器
     *
     * @param level              世界
     * @param preferredDirection 优先尝试的方向（可以是null）
     * @return 是否成功输出至少一个物品
     */
    private boolean autoOutputItems(Level level, @Nullable Direction preferredDirection) {
        boolean anyOutputSuccess = false;

        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
            ItemStack stack = this.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            // 先尝试优先方向
            if (preferredDirection != null) {
                if (this.tryOutputItemToDirection(level, slot, preferredDirection)) {
                    anyOutputSuccess = true;
                    stack = this.itemHandler.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                }
            }

            // 如果优先方向失败或还有剩余，遍历所有方向
            for (Direction direction : ALL_DIRECTIONS) {
                if (direction == preferredDirection) continue;

                if (this.tryOutputItemToDirection(level, slot, direction)) {
                    anyOutputSuccess = true;
                    this.lastSuccessfulOutputDirection = direction;
                    this.setChanged();
                    stack = this.itemHandler.getStackInSlot(slot);
                    if (stack.isEmpty()) break;
                }
            }
        }

        return anyOutputSuccess;
    }

    /**
     * 尝试向指定方向输出物品
     *
     * @return 是否成功输出至少一部分物品
     */
    private boolean tryOutputItemToDirection(Level level, int slot, Direction direction) {
        BlockPos targetPos = this.worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return false;

        // 尝试向目标容器的物品栏输出
        IItemHandler targetHandler = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                targetPos,
                targetEntity.getBlockState(),
                targetEntity,
                direction.getOpposite()
        );

        if (targetHandler == null) return false;

        ItemStack stack = this.itemHandler.getStackInSlot(slot);
        if (stack.isEmpty()) return false;

        // 尝试将物品插入目标容器
        for (int targetSlot = 0; targetSlot < targetHandler.getSlots(); targetSlot++) {
            ItemStack remaining = targetHandler.insertItem(targetSlot, stack, false);
            if (remaining.getCount() != stack.getCount()) {
                // 成功插入了一部分或全部
                this.itemHandler.setStackInSlot(slot, remaining);
                this.setChanged();
                if (remaining.isEmpty()) {
                    return true;
                } else {
                    stack = remaining;
                }
            }
        }

        return false;
    }

    /**
     * 自动输出流体到周围的容器
     *
     * @param level              世界
     * @param preferredDirection 优先尝试的方向（可以是null）
     * @return 是否成功输出至少一部分流体
     */
    private boolean autoOutputFluids(Level level, @Nullable Direction preferredDirection) {
        boolean anyOutputSuccess = false;

        for (int tankIndex = 0; tankIndex < FLUID_TANK_COUNT; tankIndex++) {
            FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
            if (fluid.isEmpty()) continue;

            // 先尝试优先方向
            if (preferredDirection != null) {
                int filled = this.tryOutputFluidToDirection(level, tankIndex, preferredDirection);
                if (filled > 0) {
                    anyOutputSuccess = true;
                    fluid = this.outputFluidTanks[tankIndex].getFluid();
                    if (fluid.isEmpty()) continue;
                }
            }

            // 如果优先方向失败或还有剩余，遍历所有方向
            for (Direction direction : ALL_DIRECTIONS) {
                if (direction == preferredDirection) continue;

                int filled = this.tryOutputFluidToDirection(level, tankIndex, direction);
                if (filled > 0) {
                    anyOutputSuccess = true;
                    this.lastSuccessfulOutputDirection = direction;
                    this.setChanged();
                    fluid = this.outputFluidTanks[tankIndex].getFluid();
                    if (fluid.isEmpty()) break;
                }
            }
        }

        return anyOutputSuccess;
    }

    /**
     * 尝试向指定方向输出流体
     *
     * @return 成功输出的流体量
     */
    private int tryOutputFluidToDirection(Level level, int tankIndex, Direction direction) {
        BlockPos targetPos = this.worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return 0;

        // 尝试向目标容器的流体槽输出
        IFluidHandler targetHandler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                targetPos,
                targetEntity.getBlockState(),
                targetEntity,
                direction.getOpposite()
        );

        if (targetHandler == null) return 0;

        FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
        if (fluid.isEmpty()) return 0;

        // 尝试填充流体到目标容器
        int filled = targetHandler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            this.outputFluidTanks[tankIndex].drain(filled, IFluidHandler.FluidAction.EXECUTE);
            this.setChanged();
        }

        return filled;
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

    public int getActiveAETaskCount() {
        return this.aeManager.getActiveAETaskCount();
    }

    // 获取最大AE任务数量（基于熔炉等级）
    public int getMaxAETaskCount() {
        return this.furnaceTier + 1;
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

    /**
     * 底部输入专用的物品处理器
     * 优先将催化剂物品输入到催化剂槽位
     */
    private record BottomInputItemHandler(IItemHandler baseHandler) implements IItemHandler {

        @Override
        public int getSlots() {
            return this.baseHandler.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return this.baseHandler.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // 如果是催化剂且槽位不是催化剂槽，尝试优先插入催化剂槽
            if (stack.is(ModTags.CATALYSTS) && slot != CATALYST_SLOT) {
                ItemStack catalystSlotStack = this.baseHandler.getStackInSlot(CATALYST_SLOT);
                // 检查催化剂槽是否已满
                if (catalystSlotStack.isEmpty() ||
                        (ItemStack.isSameItemSameComponents(catalystSlotStack, stack) &&
                                catalystSlotStack.getCount() < this.baseHandler.getSlotLimit(CATALYST_SLOT))) {
                    return this.baseHandler.insertItem(CATALYST_SLOT, stack, simulate);
                }
            }
            return this.baseHandler.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.baseHandler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.baseHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return this.baseHandler.isItemValid(slot, stack);
        }
    }

    /**
     * 根据面模式限制的方向感知物品处理器。
     * <p>
     * 仅当对应面模式激活时才允许特定类型的插入/抽取操作。
     */
    private record SidedItemHandler(IItemHandler baseHandler, @Nullable Direction side,
                                    AdvancedAlloyFurnaceBlockEntity owner) implements IItemHandler {

        @Nullable
        private FurnaceFaceMode getMode() {
            if (side == null) return null; // 无限制
            FurnaceFace face = FurnaceFace.fromDirection(side, owner.getFacing());
            if (face == null) return FurnaceFaceMode.DISABLED;
            return owner.getFaceMode(face);
        }

        @Override
        public int getSlots() {
            return baseHandler.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return baseHandler.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            FurnaceFaceMode mode = getMode();
            if (mode == null) {
                // 无面向时普通行为
                return baseHandler.insertItem(slot, stack, simulate);
            }
            if (!mode.allowsAny()) return stack; // 完全禁止

            // 催化剂优先路由（与BottomInputItemHandler逻辑一致）
            if (stack.is(ModTags.CATALYSTS) && mode.allowsCatalystInput() && slot != CATALYST_SLOT) {
                ItemStack catalystSlotStack = baseHandler.getStackInSlot(CATALYST_SLOT);
                if (catalystSlotStack.isEmpty() ||
                        (ItemStack.isSameItemSameComponents(catalystSlotStack, stack) &&
                                catalystSlotStack.getCount() < baseHandler.getSlotLimit(CATALYST_SLOT))) {
                    return baseHandler.insertItem(CATALYST_SLOT, stack, simulate);
                }
            }

            // 模具优先路由
            if (stack.is(ModTags.MOLDS) && mode.allowsMoldInput() && slot != MOLD_SLOT) {
                ItemStack moldSlotStack = baseHandler.getStackInSlot(MOLD_SLOT);
                if (moldSlotStack.isEmpty()) {
                    return baseHandler.insertItem(MOLD_SLOT, stack, simulate);
                }
            }

            boolean isInputSlot = slot >= INPUT_SLOTS_START && slot < INPUT_SLOTS_START + INPUT_SLOTS_COUNT;
            boolean isCatalystSlot = slot == CATALYST_SLOT;
            boolean isMoldSlot = slot == MOLD_SLOT;

            // 仅允许输入到对应类型的槽位
            if (isInputSlot && mode.allowsMaterialInput()) {
                return baseHandler.insertItem(slot, stack, simulate);
            }
            if (isCatalystSlot && mode.allowsCatalystInput()) {
                return baseHandler.insertItem(slot, stack, simulate);
            }
            if (isMoldSlot && mode.allowsMoldInput()) {
                return baseHandler.insertItem(slot, stack, simulate);
            }

            return stack; // 不允许插入到此槽位
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            FurnaceFaceMode mode = getMode();
            if (mode == null) {
                return baseHandler.extractItem(slot, amount, simulate);
            }
            if (!mode.allowsMaterialOutput()) return ItemStack.EMPTY;

            boolean isOutputSlot = slot >= OUTPUT_SLOTS_START && slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT;
            if (isOutputSlot) {
                return baseHandler.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return baseHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return baseHandler.isItemValid(slot, stack);
        }
    }

    /**
     * 根据面模式限制的方向感知流体处理器。
     * <p>
     * 仅当对应面模式激活时才允许填充/抽取操作。
     */
    private record SidedFluidHandler(FluidTank[] inputTanks, FluidTank[] outputTanks,
                                     Direction side, AdvancedAlloyFurnaceBlockEntity owner) implements IFluidHandler {

        @Nullable
        private FurnaceFaceMode getMode() {
            FurnaceFace face = FurnaceFace.fromDirection(side, owner.getFacing());
            if (face == null) return FurnaceFaceMode.DISABLED;
            return owner.getFaceMode(face);
        }

        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT * 2;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return inputTanks[tank].getFluid();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return outputTanks[tank - FLUID_TANK_COUNT].getFluid();
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return inputTanks[tank].getCapacity();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return outputTanks[tank - FLUID_TANK_COUNT].getCapacity();
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank < FLUID_TANK_COUNT) {
                return inputTanks[tank].isFluidValid(stack);
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
            }
            return false;
        }

        @Override
        public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            FurnaceFaceMode mode = getMode();
            if (mode == null || !mode.allowsMaterialInput()) return 0;
            return CombinedFluidTankHandler.fillInput(inputTanks, resource, action);
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            FurnaceFaceMode mode = getMode();
            if (mode == null || !mode.allowsMaterialOutput()) return FluidStack.EMPTY;
            for (FluidTank tank : outputTanks) {
                FluidStack drained = tank.drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
            FurnaceFaceMode mode = getMode();
            if (mode == null || !mode.allowsMaterialOutput()) return FluidStack.EMPTY;
            for (FluidTank tank : outputTanks) {
                if (!tank.getFluid().isEmpty()) {
                    return tank.drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    private record FluidTankHandler(FluidTank[] tanks, boolean allowFill) implements IFluidHandler {

        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return this.tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return this.tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            if (!this.allowFill) return 0;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.tanks[i];
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) return filled;
                    }
                }
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.tanks[i].drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.tanks[i].getFluid().isEmpty()) {
                    return this.tanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    private record CombinedFluidTankHandler(FluidTank[] inputTanks, FluidTank[] outputTanks) implements IFluidHandler {
        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT * 2;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].getFluid();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].getFluid();
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].getCapacity();
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].getCapacity();
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].isFluidValid(stack);
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
            }
            return false;
        }

        @Override
        public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
            return fillInput(inputTanks, resource, action);
        }

        /**
         * 静态辅助方法，用于SidedFluidHandler复用。
         */
        static int fillInput(FluidTank[] inputTanks, FluidStack resource, FluidAction action) {
            for (FluidTank tank : inputTanks) {
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) return filled;
                    }
                }
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.outputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.inputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.outputTanks[i].getFluid().isEmpty()) {
                    return this.outputTanks[i].drain(maxDrain, action);
                }
            }
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.inputTanks[i].getFluid().isEmpty()) {
                    return this.inputTanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
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
