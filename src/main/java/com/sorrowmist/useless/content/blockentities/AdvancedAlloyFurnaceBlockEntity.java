package com.sorrowmist.useless.content.blockentities;

import com.mojang.serialization.DataResult;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModTags;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.GridHelper;
import appeng.me.ManagedGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider, ICraftingProvider, appeng.api.networking.IInWorldGridNodeHost, appeng.api.networking.IGridNodeListener<AdvancedAlloyFurnaceBlockEntity>, appeng.api.networking.security.IActionHost {

    public static final int INPUT_SLOTS_START = 0;
    public static final int INPUT_SLOTS_COUNT = 9;
    public static final int OUTPUT_SLOTS_START = 9;
    public static final int OUTPUT_SLOTS_COUNT = 9;
    public static final int CATALYST_SLOT = 18;
    public static final int MOLD_SLOT = 19;
    
    public static final int PATTERN_SLOTS_START = 20;
    public static final int PATTERN_SLOTS_COUNT = 108;
    public static final int PATTERN_SLOTS_END = PATTERN_SLOTS_START + PATTERN_SLOTS_COUNT - 1;
    
    public static final int TOTAL_SLOTS = 128;

    public static final int FLUID_TANK_COUNT = 6;
    
    // 基础容量配置
    private static final int BASE_FLUID_TANK_CAPACITY = 16000;
    private static final int BASE_ENERGY_CAPACITY = 100000;
    private static final int BASE_ENERGY_MAX_RECEIVE = 10000;
    private static final int ENERGY_MAX_EXTRACT = 0;

    // 升级后的容量（根据阶级动态计算）
    private int fluidTankCapacity = BASE_FLUID_TANK_CAPACITY;
    private int energyCapacity = BASE_ENERGY_CAPACITY;
    private int energyMaxReceive = BASE_ENERGY_MAX_RECEIVE;

    private final FluidTank[] inputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    private final FluidTank[] outputFluidTanks = new FluidTank[FLUID_TANK_COUNT];

    private final IEnergyManager energyManager = EnergyManager.builder()
            .capacity(BASE_ENERGY_CAPACITY)
            .maxReceive(BASE_ENERGY_MAX_RECEIVE)
            .maxExtract(ENERGY_MAX_EXTRACT)
            .onChange(this::setChanged)
            .build();

    private final AdvancedAlloyFurnaceData data = new AdvancedAlloyFurnaceData(this);

    private int progress = 0;
    private int maxProgress = 200;
    private int currentParallel = 1;
    private boolean hasMold = false;

    @Nullable
    private AdvancedAlloyFurnaceRecipe currentRecipe;

    // 上一个成功处理的配方，用于优先匹配以减少配方查找时间
    @Nullable
    private AdvancedAlloyFurnaceRecipe lastSuccessfulRecipe;

    private int cachedParallel = 1;
    private boolean isUselessIngotRecipe = false;
    private int targetUselessIngotTier = 0;
    private long accumulatedEnergy = 0;

    // 活跃状态冷却计时器，用于避免配方切换时的闪烁
    private int activeCooldown = 0;
    private static final int ACTIVE_COOLDOWN_TICKS = 5;

    // 熔炉阶级 0-9，0为基础等级
    private int furnaceTier = 0;

    // 自动输出计时器
    private int autoOutputTickCounter = 0;
    private static final int AUTO_OUTPUT_INTERVAL = 2;

    // 自动输出面缓存（null表示未指定，使用默认查找）
    @Nullable
    private Direction cachedOutputDirection = null;
    // 上次成功输出的方向（用于缓存机制）
    @Nullable
    private Direction lastSuccessfulOutputDirection = null;

    private final ItemStackHandler itemHandler = new HighStackItemStackHandler(TOTAL_SLOTS);
    
    // ==================== AE网络支持 ====================
    private final IManagedGridNode mainNode;
    private final appeng.api.networking.security.IActionSource actionSource;
    private boolean isConnectedToAE = false;
    
    // 样板槽位（用于AE网络识别）
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private int patternPriority = 0;
    
    // ==================== 多线程合成任务管理器 ====================
    private static final int MAX_CONCURRENT_TASKS = 4;
    private final ExecutorService craftingExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_TASKS);
    private final ConcurrentHashMap<Integer, CraftingTask> activeTasks = new ConcurrentHashMap<>();
    private final ReentrantLock craftingLock = new ReentrantLock();
    private int nextTaskId = 0;
    
    // AE合成任务进度跟踪（用于UI显示）
    private volatile int activeAETaskCount = 0;
    private volatile int totalAEProgress = 0;
    private volatile int totalAEMaxProgress = 0;
    
    // 存储每个任务的进度信息（用于UI显示）- 服务端使用
    private final ConcurrentHashMap<Integer, AETaskProgress> aeTaskProgressMap = new ConcurrentHashMap<>();
    
    // 客户端任务进度列表 - 客户端使用
    private final List<AETaskProgress> clientTaskProgressList = new ArrayList<>();

    // ==================== AE任务合并缓冲 ====================
    private static final int BATCH_RIPE_TICKS = 10;
    private final Map<IPatternDetails, PendingAEBatch> aePendingBatches = new HashMap<>();

    private static class PendingAEBatch {
        final IPatternDetails pattern;
        final List<KeyCounter[]> allInputs = new ArrayList<>();
        int ripeTimer = BATCH_RIPE_TICKS;

        PendingAEBatch(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        void add(KeyCounter[] input) {
            allInputs.add(input);
            ripeTimer = BATCH_RIPE_TICKS;
        }

        List<KeyCounter[]> drain() {
            List<KeyCounter[]> result = new ArrayList<>(allInputs);
            allInputs.clear();
            return result;
        }
    }
    
    // AE任务进度信息类
    public static class AETaskProgress {
        private final String productName;
        private volatile int progress;
        private volatile int maxProgress;
        private volatile int craftCount;
        private volatile int totalOutputCount; // 最终产物总数 = 合成次数 × 单次产出数量
        private final int outputCount; // 单次产出数量
        
        public AETaskProgress(String productName, int maxProgress, int craftCount, int totalOutputCount) {
            this.productName = productName;
            this.progress = 0;
            this.maxProgress = maxProgress;
            this.craftCount = craftCount;
            this.totalOutputCount = totalOutputCount;
            this.outputCount = craftCount > 0 ? totalOutputCount / craftCount : 1; // 计算单次产出数量
        }
        
        public AETaskProgress(String productName, int progress, int maxProgress, int craftCount, int totalOutputCount) {
            this.productName = productName;
            this.progress = progress;
            this.maxProgress = maxProgress;
            this.craftCount = craftCount;
            this.totalOutputCount = totalOutputCount;
            this.outputCount = craftCount > 0 ? totalOutputCount / craftCount : 1; // 计算单次产出数量
        }
        
        public String getProductName() { return productName; }
        public int getProgress() { return progress; }
        public int getMaxProgress() { return maxProgress; }
        public int getCraftCount() { return craftCount; }
        public int getTotalOutputCount() { return totalOutputCount; }
        public void setProgress(int progress) { this.progress = progress; }
        
        // 更新合成次数和最终产物总数（用于任务合并）
        public void updateCraftCount(int newCraftCount) {
            this.craftCount = newCraftCount;
            this.totalOutputCount = newCraftCount * outputCount;
        }
    }
    
    // 更新客户端任务进度（从网络包调用）
    public void updateClientTaskProgress(List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData> tasks) {
        synchronized (clientTaskProgressList) {
            clientTaskProgressList.clear();
            for (var taskData : tasks) {
                clientTaskProgressList.add(new AETaskProgress(
                        taskData.productName,
                        taskData.progress,
                        taskData.maxProgress,
                        taskData.craftCount,
                        taskData.totalOutputCount
                ));
            }
        }
    }
    
    // 发送AE任务进度到所有客户端
    private void sendAETaskProgressToClients() {
        if (level == null || level.isClientSide) return;
        
        List<com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData> taskDataList = new ArrayList<>();
        for (var entry : aeTaskProgressMap.entrySet()) {
            AETaskProgress progress = entry.getValue();
            taskDataList.add(new com.sorrowmist.useless.network.AETaskProgressPacket.TaskProgressData(
                    progress.getProductName(),
                    progress.getProgress(),
                    progress.getMaxProgress(),
                    progress.getCraftCount(),
                    progress.getTotalOutputCount()
            ));
        }
        
        var packet = new com.sorrowmist.useless.network.AETaskProgressPacket(getBlockPos(), taskDataList);
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(getBlockPos());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk((net.minecraft.server.level.ServerLevel) level, chunkPos, packet);
    }

    /**
     * 支持高堆叠数量的 ItemStackHandler
     * 允许槽位堆叠数量达到 Integer.MAX_VALUE（约21亿）
     */
    private class HighStackItemStackHandler extends ItemStackHandler {
        // NBT常量
        private static final String TAG_ITEMS = "Items";
        private static final String TAG_SLOT = "Slot";
        private static final String TAG_COUNT = "RealCount";

        public HighStackItemStackHandler(int size) {
            super(size);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            // 处理模具重定向
            if (stack.is(ModTags.MOLDS) && slot != MOLD_SLOT) {
                ItemStack moldSlotStack = this.getStackInSlot(MOLD_SLOT);
                if (moldSlotStack.isEmpty()) {
                    return this.insertItem(MOLD_SLOT, stack, simulate);
                }
            }

            // 验证槽位和物品有效性
            if (slot < 0 || slot >= this.stacks.size() || !this.isItemValid(slot, stack)) {
                return stack;
            }

            ItemStack existing = this.stacks.get(slot);
            int limit = this.getSlotLimit(slot);

            // 检查是否可以合并
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }

            // 计算可以插入的数量（不受物品默认maxStackSize限制）
            int currentCount = existing.isEmpty() ? 0 : existing.getCount();
            int canInsert = limit - currentCount;

            if (canInsert <= 0) {
                return stack;
            }

            int toInsert = Math.min(stack.getCount(), canInsert);

            if (!simulate) {
                if (existing.isEmpty()) {
                    this.stacks.set(slot, stack.copyWithCount(currentCount + toInsert));
                } else {
                    existing.grow(toInsert);
                }
                this.onContentsChanged(slot);
            }

            if (toInsert >= stack.getCount()) {
                return ItemStack.EMPTY;
            }

            return stack.copyWithCount(stack.getCount() - toInsert);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == MOLD_SLOT ? 1 : Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == CATALYST_SLOT ? stack.is(ModTags.CATALYSTS) : super.isItemValid(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            AdvancedAlloyFurnaceBlockEntity.this.setChanged();
            if (slot == MOLD_SLOT) {
                AdvancedAlloyFurnaceBlockEntity.this.updateMoldState();
            }
            // 当输入槽或模具槽变化时，清空配方缓存和上一个成功配方
            if (slot >= INPUT_SLOTS_START && slot < INPUT_SLOTS_START + INPUT_SLOTS_COUNT || slot == MOLD_SLOT || slot == CATALYST_SLOT) {
                AdvancedAlloyFurnaceBlockEntity.this.clearRecipeCache();
            }
            // 当样板槽位变化时，更新样板列表
            if (slot >= PATTERN_SLOTS_START && slot <= PATTERN_SLOTS_END) {
                AdvancedAlloyFurnaceBlockEntity.this.updatePatterns();
            }
        }

        /**
         * 自定义序列化方法，支持超过99的堆叠数量
         */
        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag nbt = new CompoundTag();
            ListTag itemsTag = new ListTag();

            for (int i = 0; i < this.stacks.size(); i++) {
                ItemStack stack = this.stacks.get(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte(TAG_SLOT, (byte) i);

                    // 保存物品的基本信息（使用count=1避免原版限制）
                    ItemStack saveStack = stack.copyWithCount(1);
                    itemTag.put("id", StringTag.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));

                    // 保存组件数据
                    DataComponentPatch components = stack.getComponentsPatch();
                    if (!components.isEmpty()) {
                        DataResult<Tag> result = DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, components);
                        result.ifSuccess(tag -> itemTag.put("components", tag));
                    }

                    // 保存真实的堆叠数量（使用自定义字段）
                    itemTag.putInt(TAG_COUNT, stack.getCount());

                    itemsTag.add(itemTag);
                }
            }

            nbt.put(TAG_ITEMS, itemsTag);
            return nbt;
        }

        /**
         * 自定义反序列化方法，支持超过99的堆叠数量
         */
        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            if (!nbt.contains(TAG_ITEMS)) {
                return;
            }

            ListTag itemsTag = nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND);

            // 清空当前槽位
            for (int i = 0; i < this.stacks.size(); i++) {
                this.stacks.set(i, ItemStack.EMPTY);
            }

            for (int i = 0; i < itemsTag.size(); i++) {
                CompoundTag itemTag = itemsTag.getCompound(i);
                int slot = itemTag.getByte(TAG_SLOT) & 255;

                if (slot >= 0 && slot < this.stacks.size()) {
                    // 读取物品ID
                    String itemId = itemTag.getString("id");
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

                    if (item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);

                        // 读取组件数据
                        if (itemTag.contains("components")) {
                            DataResult<DataComponentPatch> result = DataComponentPatch.CODEC.parse(
                                    NbtOps.INSTANCE, itemTag.get("components"));
                            result.ifSuccess(patch -> stack.applyComponents(patch));
                        }

                        // 读取真实的堆叠数量
                        if (itemTag.contains(TAG_COUNT)) {
                            int realCount = itemTag.getInt(TAG_COUNT);
                            stack.setCount(realCount);
                        } else {
                            // 兼容旧数据
                            stack.setCount(1);
                        }

                        this.stacks.set(slot, stack);
                    }
                }
            }

            this.onLoad();
        }
    }

    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);
        
        // AE2 Integration - 创建动作源
        this.actionSource = appeng.api.networking.security.IActionSource.ofMachine(this);
        
        // AE2 Integration - 创建网格节点（使用GridHelper确保正确初始化）
        this.mainNode = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setTagName("node")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this);
        
        // 初始化时应用当前阶级的容量
        this.updateCapacityByTier();
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            this.inputFluidTanks[i] = this.createTank(i, true);
            this.outputFluidTanks[i] = this.createTank(i, false);
        }
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
     * 获取当前熔炉阶级
     */
    public int getFurnaceTier() {
        return this.furnaceTier;
    }

    /**
     * 获取指定的输出方向（扳手设置）
     * @return 指定的输出方向，null表示未指定
     */
    @Nullable
    public Direction getCachedOutputDirection() {
        return this.cachedOutputDirection;
    }

    /**
     * 使用扳手设置或取消输出方向
     * @param direction 要设置的方向，null表示取消设置
     * @return 是否成功设置（如果方向相同则取消设置）
     */
    public boolean setOutputDirectionWithWrench(@Nullable Direction direction) {
        // 如果点击的是已设置的方向，则取消设置
        if (direction != null && direction == this.cachedOutputDirection) {
            this.cachedOutputDirection = null;
            this.setChanged();
            // 同步到客户端
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
            return true;
        }

        this.cachedOutputDirection = direction;
        this.setChanged();
        // 同步到客户端
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return true;
    }

    /**
     * 检查物品是否是扳手
     */
    public static boolean isWrench(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.create(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "tools/wrench")));
    }

    /**
     * 设置熔炉阶级（内部使用，不触发容量更新）
     */
    private void setFurnaceTier(int tier) {
        this.furnaceTier = Math.max(0, Math.min(9, tier));
    }

    /**
     * 尝试升级熔炉
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
     * @param slot 槽位
     * @param stack 物品堆
     */
    public void setItemInSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < TOTAL_SLOTS) {
            this.itemHandler.setStackInSlot(slot, stack);
        }
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

        entity.flushAEBatches();

        // 每5tick尝试自动输出物品和流体
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
                            com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock.getActiveProperty(),
                            shouldBeActive),
                    3);
        }
    }

    /**
     * 尝试开始新配方处理
     * <p>
     * 检查是否有匹配的配方，以及是否有足够的空间和输入材料
     */
    private void tryStartNewRecipe() {
        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isEmpty()) return;

        AdvancedAlloyFurnaceRecipe recipe = match.get();
        if (!this.hasOutputSpace(recipe)) return;
        if (!this.canConsumeRecipeInputs(recipe)) return;

        this.startRecipeProcessing(recipe);
    }

    /**
     * 处理当前配方
     * <p>
     * 每tick消耗能量并增加进度，完成时产出物品
     * 
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
        
        int baseEnergyPerTick = this.currentRecipe.energy() / this.currentRecipe.processTime();
        
        // 检查是否使用有用锭作为催化剂（能量不加倍）
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        boolean useUsefulIngot = !catalystStack.isEmpty() && CatalystParallelManager.isUsefulIngot(catalystStack);
        
        // 使用long计算避免溢出（有用锭作为催化剂时能量不加倍）
        long energyRequiredLong = useUsefulIngot ? baseEnergyPerTick : (long) baseEnergyPerTick * actualParallel;
        int energyRequired = energyRequiredLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energyRequiredLong;

        // 能量不足时暂停进度，但不重置
        if (!this.energyManager.canWork(energyRequired)) return;

        // 扣除能量并累积
        this.energyManager.tryConsumeEnergy(energyRequired);
        this.accumulatedEnergy += energyRequired;
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
     * 
     * 所有计算都遵循"先除再乘"原则，避免溢出
     */
    private int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe) {
        // 步骤1: 计算能量允许的最大并行（先除后乘避免溢出）
        int energyParallel = this.calculateEnergyParallel(recipe);
        
        // 步骤2: 获取催化剂允许的并行量
        int catalystParallel = this.calculateCatalystParallel(recipe);
        
        // 如果催化剂提供无限并行(Integer.MAX_VALUE)，需要特殊处理
        // 限制为能量允许的并行数，避免后续计算溢出
        if (catalystParallel == Integer.MAX_VALUE) {
            catalystParallel = energyParallel;
        }
        
        // 步骤3: 计算输入材料允许的并行量
        int materialParallel = this.calculateMaterialParallel(recipe);
        
        // 步骤4: 计算输出空间允许的并行量
        int outputParallel = this.calculateOutputParallel(recipe);
        
        // 取所有限制的最小值
        int parallel = Math.min(Math.min(energyParallel, catalystParallel), 
                               Math.min(materialParallel, outputParallel));
        
        // 最终保底
        return Math.max(1, parallel);
    }
    
    /**
     * 步骤1: 计算能量允许的最大并行数
     * 公式: 能量容量 / 配方单次能量消耗
     * 使用先除后乘原则，避免溢出
     */
    private int calculateEnergyParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int recipeEnergy = recipe.energy();
        if (recipeEnergy <= 0) return Integer.MAX_VALUE;
        
        // 先除: 能量容量 / 配方能量 = 理论最大并行
        int maxEnergyParallel = this.energyManager.getMaxEnergyStored() / recipeEnergy;
        
        // 至少为1
        return Math.max(1, maxEnergyParallel);
    }
    
    /**
     * 步骤2: 计算催化剂允许的并行数
     */
    private int calculateCatalystParallel(AdvancedAlloyFurnaceRecipe recipe) {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);

        boolean isUselessRecipe = false;
        int targetTier = 0;
        for (ItemStack output : recipe.outputs()) {
            int tier = CatalystParallelManager.getTargetUselessIngotTier(output);
            if (tier > 0) {
                isUselessRecipe = true;
                targetTier = tier;
                break;
            }
        }

        int catalystStackParallel = CatalystParallelManager.calculateParallelForNormalRecipe(catalystStack);

        if (isUselessRecipe) {
            int tierParallel = CatalystParallelManager.calculateParallelForUselessIngotRecipe(targetTier);
            return Math.max(tierParallel, catalystStackParallel) <= 0 ? 1 : Math.max(tierParallel, catalystStackParallel);
        }

        return catalystStackParallel <= 0 ? 1 : catalystStackParallel;
    }
    
    /**
     * 步骤3: 计算输入材料允许的并行数
     * 对于每种材料: 可用数量 / 配方需求数量 = 该材料允许的并行数
     * 取所有材料的最小值
     */
    private int calculateMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int minParallel = Integer.MAX_VALUE;
        
        // 计算物品输入限制
        for (var countedIng : recipe.inputs()) {
            long totalAvailable = 0;
            var ingredient = countedIng.ingredient();
            long requiredPerParallel = countedIng.count();
            
            if (requiredPerParallel <= 0) continue;
            
            // 统计所有输入槽中符合条件的物品总数
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    totalAvailable += stack.getCount();
                }
            }
            
            // 先除: 可用数量 / 需求数量 = 该材料允许的并行数
            int possibleParallel = (int) (totalAvailable / requiredPerParallel);
            minParallel = Math.min(minParallel, possibleParallel);
            
            // 如果已经降到0，提前返回
            if (minParallel <= 0) return 0;
        }
        
        // 计算流体输入限制
        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long totalAvailable = 0;
            long requiredPerParallel = requiredFluid.getAmount();
            
            if (requiredPerParallel <= 0) continue;
            
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    totalAvailable += tankFluid.getAmount();
                }
            }
            
            // 先除: 可用数量 / 需求数量 = 该流体允许的并行数
            int possibleParallel = (int) (totalAvailable / requiredPerParallel);
            minParallel = Math.min(minParallel, possibleParallel);
            
            if (minParallel <= 0) return 0;
        }
        
        return minParallel == Integer.MAX_VALUE ? 1 : minParallel;
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
     * 
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
        int materialSupportedParallel = this.calculateMaterialParallelOnly(this.currentRecipe);
        int outputSupportedParallel = this.calculateOutputParallel(this.currentRecipe);
        int catalystSupportedParallel = this.calculateCatalystParallel(this.currentRecipe);
        
        // 取所有限制的最小值作为目标并行数（不考虑能量）
        int targetParallel = Math.min(Math.min(materialSupportedParallel, outputSupportedParallel), catalystSupportedParallel);
        targetParallel = Math.max(initialParallel, targetParallel); // 至少为初始并行数
        
        // 检查是否使用有用锭作为催化剂（能量不加倍）
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        boolean useUsefulIngot = !catalystStack.isEmpty() && CatalystParallelManager.isUsefulIngot(catalystStack);
        
        // 步骤2: 计算还需要补充的能量（有用锭作为催化剂时能量不加倍）
        long targetTotalEnergy = useUsefulIngot ? recipeEnergy : (long) recipeEnergy * targetParallel;
        long additionalEnergyNeeded = targetTotalEnergy - this.accumulatedEnergy;
        
        int actualParallel;
        
        if (additionalEnergyNeeded <= 0) {
            // 已消耗能量已经足够支持目标并行数
            actualParallel = targetParallel;
        } else {
            // 需要补充能量，检查当前能量是否足够
            int currentEnergy = this.energyManager.getEnergyStored();
            
            if (currentEnergy >= additionalEnergyNeeded) {
                // 能量足够补充，扣除补充能量
                this.energyManager.tryConsumeEnergy((int) Math.min(additionalEnergyNeeded, Integer.MAX_VALUE));
                actualParallel = targetParallel;
            } else {
                // 能量不足以支持目标并行数，根据实际总能量计算可行并行数
                long totalAvailableEnergy = this.accumulatedEnergy + currentEnergy;
                actualParallel = (int) (totalAvailableEnergy / recipeEnergy);
                actualParallel = Math.max(0, Math.min(actualParallel, targetParallel));
                
                // 扣除所有当前能量
                if (currentEnergy > 0) {
                    this.energyManager.tryConsumeEnergy(currentEnergy);
                }
            }
        }
        
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
     * 仅计算材料允许的并行数（用于 completeRecipe 中的最终检查）
     */
    private int calculateMaterialParallelOnly(AdvancedAlloyFurnaceRecipe recipe) {
        int minParallel = Integer.MAX_VALUE;

        // 计算物品输入限制
        for (var countedIng : recipe.inputs()) {
            long totalAvailable = 0;
            var ingredient = countedIng.ingredient();
            long requiredPerParallel = countedIng.count();

            if (requiredPerParallel <= 0) continue;

            // 统计所有输入槽中符合条件的物品总数
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    totalAvailable += stack.getCount();
                }
            }

            // 计算该材料允许的并行数
            int possibleParallel = (int) (totalAvailable / requiredPerParallel);
            minParallel = Math.min(minParallel, possibleParallel);

            if (minParallel <= 0) return 0;
        }

        // 计算流体输入限制
        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long totalAvailable = 0;
            long requiredPerParallel = requiredFluid.getAmount();

            if (requiredPerParallel <= 0) continue;

            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    totalAvailable += tankFluid.getAmount();
                }
            }

            int possibleParallel = (int) (totalAvailable / requiredPerParallel);
            minParallel = Math.min(minParallel, possibleParallel);

            if (minParallel <= 0) return 0;
        }

        return minParallel == Integer.MAX_VALUE ? 1 : minParallel;
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
        
        // 计算催化剂对处理时间的加成
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        int baseTime = recipe.processTime();
        this.maxProgress = CatalystParallelManager.calculateProcessTimeWithCatalyst(baseTime, catalystStack);
        
        this.progress = 0;

        // 更新上一个成功处理的配方
        this.lastSuccessfulRecipe = recipe;

        // 使用统一的并行计算方法
        this.cachedParallel = this.calculateActualParallel(recipe);
        this.accumulatedEnergy = 0;

        this.isUselessIngotRecipe = false;
        this.targetUselessIngotTier = 0;
        for (ItemStack output : recipe.outputs()) {
            int tier = CatalystParallelManager.getTargetUselessIngotTier(output);
            if (tier > 0) {
                this.isUselessIngotRecipe = true;
                this.targetUselessIngotTier = tier;
                break;
            }
        }

        this.setChanged();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.useless_mod.advanced_alloy_furnace");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new AdvancedAlloyFurnaceMenu(id, inventory, this, this.getData());
    }

    public ContainerData getData() {
        return this.data;
    }

    public IItemHandler getItemHandler() {
        return this.itemHandler;
    }

    /**
     * 获取方向感知的物品处理器
     * 从底部输入时，催化剂物品会优先进入催化剂槽位
     *
     * @param side 输入方向
     * @return 物品处理器
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return new BottomInputItemHandler(this.itemHandler);
        }
        return this.itemHandler;
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

    public IEnergyStorage getEnergyStorage() {
        return this.energyManager;
    }

    IEnergyManager getEnergyManager() {
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

    public void setMaxEnergy(int energy) {
        this.energyManager.setMaxEnergyStored(energy);
    }

    public int getMaxEnergy() {
        return this.energyManager.getMaxEnergyStored();
    }

    int getCurrentParallel() {
        if (this.currentRecipe != null && this.progress > 0) {
            return this.cachedParallel;
        }
        return this.calculateDisplayParallel();
    }

    public int getCatalystMaxParallel() {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        if (catalystStack.isEmpty()) return 1;
        return CatalystParallelManager.calculateParallelForNormalRecipe(catalystStack);
    }

    private int calculateDisplayParallel() {
        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isPresent()) {
            // 使用统一的并行计算方法
            return this.calculateActualParallel(match.get());
        }

        // 没有匹配配方时，只显示催化剂提供的并行数
        int catalystParallel = this.getCatalystMaxParallel();
        return Math.max(1, Math.min(catalystParallel, 1000000));
    }

    void setCurrentParallel(int parallel) {
        this.currentParallel = parallel;
        this.setChanged();
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
                            AdvancedAlloyFurnaceBlockEntity.this.getBlockState(), 3);
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
                    this.getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

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

        // 加载指定的输出方向（扳手设置）
        if (tag.contains(NBTConstants.OUTPUT_DIRECTION)) {
            int dirIndex = tag.getInt(NBTConstants.OUTPUT_DIRECTION);
            if (dirIndex >= 0 && dirIndex < Direction.values().length) {
                this.cachedOutputDirection = Direction.values()[dirIndex];
            } else {
                this.cachedOutputDirection = null;
            }
        } else {
            this.cachedOutputDirection = null;
        }

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
        
        // 加载样板优先级
        if (tag.contains("PatternPriority")) {
            this.patternPriority = tag.getInt("PatternPriority");
        }
        
        // 重新解析样板槽中的样板（必须在物品加载之后）
        this.updatePatterns();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
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

        // 保存指定的输出方向（扳手设置）
        if (this.cachedOutputDirection != null) {
            tag.putInt(NBTConstants.OUTPUT_DIRECTION, this.cachedOutputDirection.ordinal());
        } else {
            tag.putInt(NBTConstants.OUTPUT_DIRECTION, -1);
        }

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
        tag.putInt("PatternPriority", this.patternPriority);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.handleUpdateTag(tag, registries);
        this.loadAdditional(tag, registries);
    }

    /**
     * 查找匹配的配方（统一匹配，支持物品+流体+模具优先级）
     * <p>
     * 优先检查上一个成功处理的配方，以减少查找时间和避免闪烁
     * 但如果找到了更优的配方（更复杂的原生配方），则使用更优的配方
     *
     * @return 匹配的配方，如果没有则返回空
     */
    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        if (this.level == null) return Optional.empty();

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

        if (bestRecipe == null || !this.canProcessRecipe(bestRecipe)) {
            return Optional.empty();
        }

        if (this.lastSuccessfulRecipe != null && this.canProcessRecipe(this.lastSuccessfulRecipe)) {
            if (this.lastSuccessfulRecipe.id().equals(bestRecipe.id())) {
                return Optional.of(this.lastSuccessfulRecipe);
            }
        }

        return Optional.of(bestRecipe);
    }

    private boolean canProcessRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        List<ItemStack> currentInputs = new ArrayList<>();
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                currentInputs.add(stack);
            }
        }

        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (ItemStack stack : currentInputs) {
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
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

        

        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            return recipe.mold().test(moldStack);
        }

        return true;
    }

    /**
     * 检查是否有足够的输入材料支持指定的并行数
     *
     * @param recipe 配方
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

    private boolean hasOutputSpace(AdvancedAlloyFurnaceRecipe recipe) {
        // 使用统一的并行计算方法检查输出空间
        int parallel = this.calculateActualParallel(recipe);
        
        // 如果计算出的并行数至少为1，说明有输出空间
        return parallel >= 1;
    }

    private void consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 限制并行数在合理范围内
        parallel = Math.min(parallel, 1000000);
        
        for (var countedIng : recipe.inputs()) {
            long toConsume = countedIng.count() * (long) parallel;
            var ingredient = countedIng.ingredient();

            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT && toConsume > 0; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    int consumed = (int) Math.min(toConsume, stack.getCount());
                    stack.shrink(consumed);
                    toConsume -= consumed;
                }
            }
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long toDrainLong = (long) requiredFluid.getAmount() * parallel;
            int toDrain = toDrainLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) toDrainLong;
            for (int i = 0; i < FLUID_TANK_COUNT && toDrain > 0; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    FluidStack drained = this.inputFluidTanks[i].drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    toDrain -= drained.getAmount();
                }
            }
        }
    }

    private void produceRecipeOutputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 限制并行数在合理范围内
        parallel = Math.min(parallel, 1000000);

        for (ItemStack output : recipe.outputs()) {
            // 计算总产出数量
            long totalCountLong = (long) output.getCount() * parallel;
            int totalCount = totalCountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCountLong;

            // 直接插入完整的产出数量（不再拆分成多个小堆叠）
            if (totalCount > 0 && this.hasEmptyOutputSlotOrSpace(output)) {
                ItemStack toInsert = output.copy();
                toInsert.setCount(totalCount);

                // 尝试插入
                this.insertItemStack(toInsert);
            }
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            FluidStack toInsert = outputFluid.copy();
            // 使用long计算避免溢出
            long totalAmount = (long) outputFluid.getAmount() * parallel;
            toInsert.setAmount(totalAmount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalAmount);

            for (int i = 0; i < FLUID_TANK_COUNT && !toInsert.isEmpty(); i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                if (tankFluid.isEmpty() || FluidStack.isSameFluidSameComponents(tankFluid, toInsert)) {
                    int filled = this.outputFluidTanks[i].fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
                    toInsert.shrink(filled);
                }
            }
        }
    }

    /**
     * 检查是否有空输出槽或可以放入该物品的空间
     */
    private boolean hasEmptyOutputSlotOrSpace(ItemStack stack) {
        for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
            ItemStack slotStack = this.itemHandler.getStackInSlot(i);
            // 使用槽位限制而不是物品默认maxStackSize
            int slotLimit = this.itemHandler.getSlotLimit(i);
            if (slotStack.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slotStack, stack) && slotStack.getCount() < slotLimit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将物品堆插入输出槽
     * @return 是否成功插入全部
     */
    private boolean insertItemStack(ItemStack toInsert) {
        if (toInsert.isEmpty()) return true;

        // 先尝试合并到已有堆叠
        for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
            ItemStack slotStack = this.itemHandler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(slotStack, toInsert)) {
                // 使用槽位限制而不是物品默认maxStackSize
                int slotLimit = this.itemHandler.getSlotLimit(i);
                int space = slotLimit - slotStack.getCount();
                int toAdd = Math.min(space, toInsert.getCount());
                slotStack.grow(toAdd);
                toInsert.shrink(toAdd);
            }
        }

        // 再尝试放入空槽
        for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
            ItemStack slotStack = this.itemHandler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                this.itemHandler.setStackInSlot(i, toInsert.copy());
                toInsert.setCount(0);
            }
        }

        return toInsert.isEmpty();
    }

    private void resetProgress() {
        this.progress = 0;
        this.currentRecipe = null;
        this.cachedParallel = 1;
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

    /**
     * 自动输出物品和流体到周围的容器
     * 每5tick调用一次
     * 扳手设置的方向具有最高优先级，即使无法输出也不会切换到其他方向
     */
    private void autoOutputItemsAndFluids(Level level) {
        if (level.isClientSide) return;

        // 如果扳手指定了方向，只尝试该方向（最高优先级）
        if (this.cachedOutputDirection != null) {
            this.autoOutputItemsToFixedDirection(level, this.cachedOutputDirection);
            this.autoOutputFluidsToFixedDirection(level, this.cachedOutputDirection);
            return;
        }

        // 没有扳手指定方向，使用智能模式（优先上次成功的方向）
        Direction preferredDirection = this.lastSuccessfulOutputDirection;

        // 尝试输出物品
        boolean itemOutputSuccess = this.autoOutputItems(level, preferredDirection);

        // 尝试输出流体
        boolean fluidOutputSuccess = this.autoOutputFluids(level, preferredDirection);

        // 如果优先方向输出失败，则清除缓存并尝试其他方向
        if (!itemOutputSuccess && !fluidOutputSuccess && preferredDirection != null) {
            this.lastSuccessfulOutputDirection = null;
        }
    }

    /**
     * 自动输出物品到指定方向（扳手固定模式）
     * 只尝试指定方向，不切换到其他方向
     */
    private void autoOutputItemsToFixedDirection(Level level, Direction direction) {
        boolean anyOutputSuccess;
        do {
            anyOutputSuccess = false;
            for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
                ItemStack stack = this.itemHandler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;

                if (this.tryOutputItemToDirection(level, slot, direction)) {
                    anyOutputSuccess = true;
                }
            }
        } while (anyOutputSuccess);
    }

    /**
     * 自动输出物品到周围的容器
     * @param level 世界
     * @param preferredDirection 优先尝试的方向（可以是null）
     * @return 是否成功输出至少一个物品
     */
    private boolean autoOutputItems(Level level, @Nullable Direction preferredDirection) {
        boolean anyOutputSuccess = false;
        boolean slotOutputSuccess;

        do {
            slotOutputSuccess = false;
            for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; slot++) {
                ItemStack stack = this.itemHandler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;

                // 先尝试优先方向
                if (preferredDirection != null) {
                    if (this.tryOutputItemToDirection(level, slot, preferredDirection)) {
                        anyOutputSuccess = true;
                        slotOutputSuccess = true;
                        stack = this.itemHandler.getStackInSlot(slot);
                        if (stack.isEmpty()) continue;
                    }
                }

                // 如果优先方向失败或还有剩余，遍历所有方向
                for (Direction direction : Direction.values()) {
                    if (direction == preferredDirection) continue; // 跳过已尝试的方向

                    if (this.tryOutputItemToDirection(level, slot, direction)) {
                        anyOutputSuccess = true;
                        slotOutputSuccess = true;
                        // 更新上次成功的方向缓存
                        this.lastSuccessfulOutputDirection = direction;
                        this.setChanged();
                        stack = this.itemHandler.getStackInSlot(slot);
                        if (stack.isEmpty()) break;
                    }
                }
            }
        } while (slotOutputSuccess);

        return anyOutputSuccess;
    }

    /**
     * 尝试向指定方向输出物品
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
     * 自动输出流体到指定方向（扳手固定模式）
     * 只尝试指定方向，不切换到其他方向
     */
    private void autoOutputFluidsToFixedDirection(Level level, Direction direction) {
        boolean anyOutputSuccess;
        do {
            anyOutputSuccess = false;
            for (int tankIndex = 0; tankIndex < FLUID_TANK_COUNT; tankIndex++) {
                FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
                if (fluid.isEmpty()) continue;

                int filled = this.tryOutputFluidToDirection(level, tankIndex, direction);
                if (filled > 0) {
                    anyOutputSuccess = true;
                }
            }
        } while (anyOutputSuccess);
    }

    /**
     * 自动输出流体到周围的容器
     * @param level 世界
     * @param preferredDirection 优先尝试的方向（可以是null）
     * @return 是否成功输出至少一部分流体
     */
    private boolean autoOutputFluids(Level level, @Nullable Direction preferredDirection) {
        boolean anyOutputSuccess = false;
        boolean tankOutputSuccess;

        do {
            tankOutputSuccess = false;
            for (int tankIndex = 0; tankIndex < FLUID_TANK_COUNT; tankIndex++) {
                FluidStack fluid = this.outputFluidTanks[tankIndex].getFluid();
                if (fluid.isEmpty()) continue;

                // 先尝试优先方向
                if (preferredDirection != null) {
                    int filled = this.tryOutputFluidToDirection(level, tankIndex, preferredDirection);
                    if (filled > 0) {
                        anyOutputSuccess = true;
                        tankOutputSuccess = true;
                        fluid = this.outputFluidTanks[tankIndex].getFluid();
                        if (fluid.isEmpty()) continue;
                    }
                }

                // 如果优先方向失败或还有剩余，遍历所有方向
                for (Direction direction : Direction.values()) {
                    if (direction == preferredDirection) continue;

                    int filled = this.tryOutputFluidToDirection(level, tankIndex, direction);
                    if (filled > 0) {
                        anyOutputSuccess = true;
                        tankOutputSuccess = true;
                        // 更新上次成功的方向缓存
                        this.lastSuccessfulOutputDirection = direction;
                        this.setChanged();
                        fluid = this.outputFluidTanks[tankIndex].getFluid();
                        if (fluid.isEmpty()) break;
                    }
                }
            }
        } while (tankOutputSuccess);

        return anyOutputSuccess;
    }

    /**
     * 尝试向指定方向输出流体
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
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.inputTanks[i];
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

    // ==================== AE网络支持方法 ====================

    @Override
    public void onSaveChanges(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        setChanged();
    }
    
    @Override
    public void onGridChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        isConnectedToAE = node.isActive();
        setChanged();
    }
    
    @Override
    public void onStateChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node, IGridNodeListener.State state) {
        isConnectedToAE = node.isActive();
        setChanged();
    }

    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public @Nullable appeng.api.networking.IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    @Override
    public appeng.api.util.AECableType getCableConnectionType(Direction dir) {
        return appeng.api.util.AECableType.SMART;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, (be) -> {
            be.mainNode.create(getLevel(), getBlockPos());
            // 节点创建后重新解析样板并通知AE网络
            be.updatePatterns();
        });
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 节点创建由clearRemoved中的GridHelper.onFirstTick处理，确保只创建一次
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
        this.craftingExecutor.shutdownNow();
    }

    @Override
    public appeng.api.networking.IGridNode getActionableNode() {
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
    private long tryOutputToAE(ItemStack stack) {
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
        long inserted = storage.insert(key, amount, Actionable.MODULATE, actionSource);
        
        return inserted;
    }
    
    // AE2 Integration - 尝试输出流体到AE网络
    private long tryOutputFluidToAE(FluidStack stack) {
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
        long inserted = storage.insert(key, amount, Actionable.MODULATE, actionSource);
        
        return inserted;
    }
    
    // AE合成任务状态获取方法（用于UI显示）
    public int getActiveAETaskCount() {
        return activeAETaskCount;
    }
    
    public int getTotalAEProgress() {
        return totalAEProgress;
    }
    
    public int getTotalAEMaxProgress() {
        return totalAEMaxProgress;
    }
    
    // AE合成任务状态设置方法（用于客户端同步）
    public void setActiveAETaskCount(int value) {
        this.activeAETaskCount = value;
    }
    
    public void setTotalAEProgress(int value) {
        this.totalAEProgress = value;
    }
    
    public void setTotalAEMaxProgress(int value) {
        this.totalAEMaxProgress = value;
    }
    
    // 获取所有AE任务进度信息（用于UI显示）
    public Collection<AETaskProgress> getAETaskProgressList() {
        // 客户端返回同步的任务列表，服务端返回实际的任务进度
        if (level != null && level.isClientSide) {
            synchronized (clientTaskProgressList) {
                return new ArrayList<>(clientTaskProgressList);
            }
        }
        return aeTaskProgressMap.values();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(this.patterns);
    }

    @Override
    public int getPatternPriority() {
        return this.patternPriority;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!this.mainNode.isActive() || !this.patterns.contains(patternDetails)) {
            return false;
        }

        CraftingTask existingTask = findExistingTask(patternDetails);
        if (existingTask != null) {
            existingTask.addMaterials(inputHolder);
            return true;
        }

        if (this.activeTasks.size() >= MAX_CONCURRENT_TASKS) {
            synchronized (this.aePendingBatches) {
                PendingAEBatch batch = findOrCreateBatch(patternDetails);
                batch.add(inputHolder);
            }
            return true;
        }

        synchronized (this.aePendingBatches) {
            PendingAEBatch batch = findOrCreateBatch(patternDetails);
            batch.add(inputHolder);
        }
        return true;
    }

    private PendingAEBatch findOrCreateBatch(IPatternDetails patternDetails) {
        for (Map.Entry<IPatternDetails, PendingAEBatch> entry : aePendingBatches.entrySet()) {
            if (arePatternsSame(entry.getKey(), patternDetails)) {
                return entry.getValue();
            }
        }
        PendingAEBatch batch = new PendingAEBatch(patternDetails);
        aePendingBatches.put(patternDetails, batch);
        return batch;
    }

    private boolean arePatternsSame(IPatternDetails a, IPatternDetails b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        var aOutputs = a.getOutputs();
        var bOutputs = b.getOutputs();
        if (aOutputs.size() != bOutputs.size()) return false;
        for (int i = 0; i < aOutputs.size(); i++) {
            if (!aOutputs.get(i).what().equals(bOutputs.get(i).what())) {
                return false;
            }
        }
        return true;
    }

    private void flushAEBatches() {
        List<PendingAEBatch> ripe;
        synchronized (this.aePendingBatches) {
            var it = aePendingBatches.entrySet().iterator();
            ripe = new ArrayList<>();
            while (it.hasNext()) {
                var entry = it.next();
                PendingAEBatch batch = entry.getValue();
                batch.ripeTimer--;
                if (batch.ripeTimer <= 0) {
                    ripe.add(batch);
                    it.remove();
                }
            }
        }

        for (PendingAEBatch batch : ripe) {
            flushBatch(batch);
        }
    }

    private void flushBatch(PendingAEBatch batch) {
        List<KeyCounter[]> allInputs = batch.drain();
        if (allInputs.isEmpty() || batch.pattern == null) return;

        KeyCounter[] merged = mergeKeyCounters(allInputs);

        int taskId = this.nextTaskId++;
        int totalCrafts = allInputs.size();

        CraftingTask task = new CraftingTask(taskId, batch.pattern, merged, totalCrafts);
        this.activeTasks.put(taskId, task);
        this.activeAETaskCount++;
        setChanged();

        this.craftingExecutor.submit(() -> {
            try {
                task.run();
            } finally {
                this.activeTasks.remove(taskId);
                this.activeAETaskCount--;
                setChanged();
            }
        });
    }

    private KeyCounter[] mergeKeyCounters(List<KeyCounter[]> allInputs) {
        if (allInputs.isEmpty()) return new KeyCounter[0];
        if (allInputs.size() == 1) return allInputs.get(0);

        Map<AEKey, Long> merged = new HashMap<>();
        for (KeyCounter[] counters : allInputs) {
            if (counters == null) continue;
            for (KeyCounter counter : counters) {
                if (counter == null) continue;
                for (var entry : counter) {
                    merged.merge(entry.getKey(), entry.getLongValue(), Long::sum);
                }
            }
        }

        KeyCounter result = new KeyCounter();
        for (var entry : merged.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return new KeyCounter[]{result};
    }
    
    // 查找现有的相同样板任务（用于任务合并）
    private CraftingTask findExistingTask(IPatternDetails patternDetails) {
        for (CraftingTask task : this.activeTasks.values()) {
            // 找到相同样板的任务，且该任务还在接受新材料（还没开始处理或正在处理中但可以追加）
            if (task.isSamePattern(patternDetails) && !task.isProcessingComplete()) {
                return task;
            }
        }
        return null;
    }

    @Override
    public boolean isBusy() {
        // 只有当任务数达到最大并发数时才返回busy，允许AE发送多个并行任务
        return this.activeTasks.size() >= MAX_CONCURRENT_TASKS;
    }

    private void updatePatterns() {
        this.patterns.clear();
        for (int i = PATTERN_SLOTS_START; i <= PATTERN_SLOTS_END; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, this.level);
                if (pattern != null) {
                    this.patterns.add(pattern);
                }
            }
        }
        // 只有当节点已经创建时才通知AE网络更新
        if (this.mainNode.getNode() != null) {
            ICraftingProvider.requestUpdate(this.mainNode);
        }
    }

    // ==================== 多线程合成任务类 ====================

    private class CraftingTask {
        private final int taskId;
        private final IPatternDetails pattern;
        private volatile boolean cancelled = false;
        private volatile boolean processingComplete = false;
        private volatile int craftCount = 1;
        private final ReentrantLock taskLock = new ReentrantLock();

        private final List<ItemStack> taskInputItems = new ArrayList<>();
        private final List<FluidStack> taskInputFluids = new ArrayList<>();

        private AETaskProgress taskProgressRef = null;

        public CraftingTask(int taskId, IPatternDetails pattern, KeyCounter[] inputHolder, int totalCrafts) {
            this.taskId = taskId;
            this.pattern = pattern;
            this.craftCount = Math.max(1, totalCrafts);
            this.storeInputMaterials(inputHolder);
        }

        public boolean isSamePattern(IPatternDetails otherPattern) {
            if (this.pattern == null || otherPattern == null) {
                return false;
            }
            var thisOutputs = this.pattern.getOutputs();
            var otherOutputs = otherPattern.getOutputs();
            if (thisOutputs.size() != otherOutputs.size()) {
                return false;
            }
            for (int i = 0; i < thisOutputs.size(); i++) {
                if (!thisOutputs.get(i).what().equals(otherOutputs.get(i).what())) {
                    return false;
                }
            }
            return true;
        }

        public boolean isProcessingComplete() {
            return processingComplete;
        }

        public void addMaterials(KeyCounter[] additionalInput) {
            taskLock.lock();
            try {
                if (processingComplete) {
                    return;
                }
                craftCount++;
                storeInputMaterials(additionalInput);
                if (taskProgressRef != null) {
                    taskProgressRef.updateCraftCount(craftCount);
                    AdvancedAlloyFurnaceBlockEntity.this.setChanged();
                    AdvancedAlloyFurnaceBlockEntity.this.sendAETaskProgressToClients();
                }
            } finally {
                taskLock.unlock();
            }
        }

        private void storeInputMaterials(KeyCounter[] counters) {
            if (counters == null) return;

            for (KeyCounter counter : counters) {
                if (counter == null) continue;

                for (var entry : counter) {
                    AEKey key = entry.getKey();
                    long amount = entry.getLongValue();

                    if (key instanceof AEItemKey itemKey) {
                        ItemStack stack = itemKey.toStack((int) amount);
                        taskInputItems.add(stack);
                    } else if (key instanceof AEFluidKey fluidKey) {
                        FluidStack stack = new FluidStack(fluidKey.getFluid(), (int) amount);
                        taskInputFluids.add(stack);
                    }
                }
            }
        }

        /**
         * 使用本体模具/催化剂统一查找配方
         */
        private AdvancedAlloyFurnaceRecipe findTaskRecipe() {
            if (level == null) return null;

            List<ItemStack> tempInputs = new ArrayList<>(taskInputItems);
            List<FluidStack> tempFluids = new ArrayList<>(taskInputFluids);

            ItemStack moldStack = AdvancedAlloyFurnaceBlockEntity.this.itemHandler.getStackInSlot(MOLD_SLOT);

            return AlloyFurnaceRecipeManager.getInstance().findRecipe(
                    level, tempInputs, tempFluids, moldStack
            );
        }

        private String getProductName() {
            if (pattern == null || pattern.getOutputs().isEmpty()) {
                return "Unknown";
            }

            var output = pattern.getOutputs().get(0);
            if (output.what() instanceof AEItemKey itemKey) {
                return itemKey.getItem().getDescriptionId();
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                return fluidKey.toString();
            }

            return "Unknown";
        }

        private boolean validateRecipe() {
            if (level == null || pattern == null) {
                returnMaterialsToAE();
                return false;
            }

            AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

            if (recipe == null) {
                returnMaterialsToAE();
                return false;
            }

            if (pattern.getOutputs().isEmpty() || recipe.outputs().isEmpty()) {
                returnMaterialsToAE();
                return false;
            }

            var patternOutput = pattern.getOutputs().get(0);
            ItemStack patternOutputStack = null;

            if (patternOutput.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                patternOutputStack = itemKey.toStack((int) patternOutput.amount());
            }

            ItemStack recipeOutput = recipe.outputs().get(0);

            if (patternOutputStack != null && !ItemStack.isSameItem(patternOutputStack, recipeOutput)) {
                returnMaterialsToAE();
                return false;
            }

            return true;
        }

        private void returnMaterialsToAE() {
            if (level == null || level.isClientSide) return;
            
            level.getServer().execute(() -> {
                // 将任务输入物品返回给AE网络
                for (ItemStack stack : taskInputItems) {
                    if (!stack.isEmpty()) {
                        // 尝试输出到AE网络
                        long inserted = AdvancedAlloyFurnaceBlockEntity.this.tryOutputToAE(stack);
                        int remainingCount = (int) (stack.getCount() - inserted);
                        
                        // 如果AE网络没存下，尝试放入机器的输入槽
                        if (remainingCount > 0) {
                            ItemStack remainingStack = stack.copy();
                            remainingStack.setCount(remainingCount);
                            
                            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                                ItemStack slotStack = itemHandler.getStackInSlot(i);
                                if (slotStack.isEmpty()) {
                                    itemHandler.setStackInSlot(i, remainingStack.copy());
                                    break;
                                } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack) && 
                                           slotStack.getCount() < slotStack.getMaxStackSize()) {
                                    int addAmount = Math.min(remainingCount, slotStack.getMaxStackSize() - slotStack.getCount());
                                    slotStack.grow(addAmount);
                                    remainingCount -= addAmount;
                                    if (remainingCount <= 0) break;
                                }
                            }
                        }
                    }
                }
                
                // 清空任务的独立存储空间
                taskInputItems.clear();
                taskInputFluids.clear();
            });
        }
        
        private int getRecipeProcessTime() {
            if (level == null) return 200;

            AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

            if (recipe != null && recipe.processTime() > 0) {
                ItemStack catalystStack = AdvancedAlloyFurnaceBlockEntity.this.itemHandler.getStackInSlot(CATALYST_SLOT);
                return CatalystParallelManager.calculateProcessTimeWithCatalyst(recipe.processTime(), catalystStack);
            }

            return 200;
        }

        public void run() {
            if (cancelled || level == null || level.isClientSide) {
                return;
            }

            // 验证配方：检查输入材料是否能通过有效的配方合成出样板定义的产物
            if (!validateRecipe()) {
                // 找不到有效的配方，取消任务
                processingComplete = true;
                return;
            }
            
            // 保存基础处理时间用于异常处理
            final int baseProcessTime = getRecipeProcessTime();

            // 获取配方基础能量消耗（每tick），优先使用配方自身能量
            AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
            final int baseEnergyPerTick;
            if (recipe != null && recipe.processTime() > 0) {
                baseEnergyPerTick = Math.max(1, recipe.energy() / recipe.processTime());
            } else {
                baseEnergyPerTick = 200;
            }

            try {
                int currentCraftCount = craftCount; // 获取当前需要合成的次数
                // 使用机器本体催化剂槽位中的催化剂来决定最大并行数
                int maxParallel = AdvancedAlloyFurnaceBlockEntity.this.getCatalystMaxParallel();
                if (maxParallel <= 0) {
                    maxParallel = 1; // 至少为1
                }
                
                // 总处理时间 = 基础时间 × ceil(合成次数 / 最大并行数)
                int batches = (int) Math.ceil((double) currentCraftCount / maxParallel);
                int processTime = baseProcessTime * batches;
                int lastBatchSize = currentCraftCount - maxParallel * (batches - 1);
                
                int progress = 0;
                
                // 获取合成产物名称和单次产出数量
                String productName = getProductName();
                int outputCount = 1;
                if (pattern != null && !pattern.getOutputs().isEmpty()) {
                    var output = pattern.getOutputs().get(0);
                    outputCount = (int) output.amount();
                }
                int totalOutputCount = currentCraftCount * outputCount; // 最终产物总数 = 合成次数 × 单次产出数量
                
                // 创建任务进度信息并添加到地图中
                AETaskProgress taskProgress = new AETaskProgress(productName, processTime, currentCraftCount, totalOutputCount);
                aeTaskProgressMap.put(taskId, taskProgress);
                // 保存任务进度引用，用于任务合并时更新
                this.taskProgressRef = taskProgress;
                
                // 更新总进度
                totalAEMaxProgress += processTime;
                setChanged();
                
                // 发送初始任务进度到客户端
                sendAETaskProgressToClients();

                int progressUpdateCounter = 0;
                ItemStack catalystStack = AdvancedAlloyFurnaceBlockEntity.this.itemHandler.getStackInSlot(CATALYST_SLOT);
                boolean useUsefulIngot = !catalystStack.isEmpty() && CatalystParallelManager.isUsefulIngot(catalystStack);
                boolean energyFailed = false;
                while (progress < processTime && !cancelled) {
                    taskLock.lock();
                    try {
                        if (cancelled) break;

                        // 根据当前批次计算实际并行数（最后一批可能不满maxParallel）
                        int batchIndex = baseProcessTime > 0 ? progress / baseProcessTime : 0;
                        int actualBatchParallel = (batchIndex < batches - 1) ? maxParallel : lastBatchSize;
                        long energyRequiredLong = useUsefulIngot ? (long) baseEnergyPerTick : (long) baseEnergyPerTick * actualBatchParallel;
                        int energyRequired = energyRequiredLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energyRequiredLong;
                        if (!energyManager.canWork(energyRequired)) {
                            energyFailed = true;
                            break;
                        }
                        energyManager.tryConsumeEnergy(energyRequired);
                        progress++;
                        totalAEProgress++;
                        // 更新单个任务的进度
                        taskProgress.setProgress(progress);
                        setChanged();
                        
                        // 每20 ticks发送一次进度更新（大约每秒一次）
                        progressUpdateCounter++;
                        if (progressUpdateCounter >= 20) {
                            sendAETaskProgressToClients();
                            progressUpdateCounter = 0;
                        }
                    } finally {
                        taskLock.unlock();
                    }
                    
                    Thread.sleep(50);
                }

                if (energyFailed) {
                    returnMaterialsToAE();
                    processingComplete = true;
                    totalAEProgress -= progress;
                    totalAEMaxProgress -= processTime;
                    aeTaskProgressMap.remove(taskId);
                    setChanged();
                    return;
                }

                if (!cancelled && progress >= processTime) {
                    // 使用最新的 craftCount 值，确保包含所有合并的材料
                    completeCrafting(craftCount);
                }
                
                // 标记任务已完成处理（不再接受新材料合并）
                processingComplete = true;
                
                // 任务完成或取消后更新总进度
                totalAEProgress -= progress;
                totalAEMaxProgress -= processTime;
                // 移除任务进度信息
                aeTaskProgressMap.remove(taskId);
                setChanged();
                
                // 发送任务完成的进度更新到客户端
                sendAETaskProgressToClients();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 任务中断后更新总进度
                processingComplete = true;
                int maxParallel = AdvancedAlloyFurnaceBlockEntity.this.getCatalystMaxParallel();
                if (maxParallel <= 0) {
                    maxParallel = 1;
                }
                int batches = (int) Math.ceil((double) craftCount / maxParallel);
                totalAEMaxProgress -= baseProcessTime * batches;
                setChanged();
            }
        }

        private void completeCrafting(int craftCount) {
            if (level == null || level.isClientSide) return;

            level.getServer().execute(() -> {
                // 每个任务使用自己独立的锁，不同任务之间互不干扰
                taskLock.lock();
                try {
                    // 清空任务的独立存储空间（表示材料已被消耗）
                    taskInputItems.clear();
                    taskInputFluids.clear();
                    
                    // 生成输出物品（批量合成）
                    for (var output : pattern.getOutputs()) {
                        if (output.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                            // 批量输出：单次输出数量 × 合成次数
                            ItemStack outputStack = itemKey.toStack((int) (output.amount() * craftCount));
                            
                            // 优先输出到AE网络
                            long inserted = tryOutputToAE(outputStack);
                            int remainingCount = (int) (outputStack.getCount() - inserted);
                            
                            // 如果AE网络没存下，输出到自己的输出栏
                            if (remainingCount > 0) {
                                ItemStack remainingStack = outputStack.copy();
                                remainingStack.setCount(remainingCount);
                                // 使用craftingLock来保护共享的输出槽位
                                craftingLock.lock();
                                try {
                                    for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                                        ItemStack slotStack = itemHandler.getStackInSlot(i);
                                        if (slotStack.isEmpty()) {
                                            itemHandler.setStackInSlot(i, remainingStack.copy());
                                            break;
                                        } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack)) {
                                            slotStack.grow(remainingStack.getCount());
                                            break;
                                        }
                                    }
                                } finally {
                                    craftingLock.unlock();
                                }
                            }
                        } else if (output.what() instanceof appeng.api.stacks.AEFluidKey fluidKey) {
                            // 处理流体输出（批量合成）
                            FluidStack outputFluid = new FluidStack(fluidKey.getFluid(), (int) (output.amount() * craftCount));
                            
                            // 优先输出到AE网络
                            long inserted = tryOutputFluidToAE(outputFluid);
                            int remainingAmount = (int) (outputFluid.getAmount() - inserted);
                            
                            // 如果AE网络没存下，输出到自己的流体输出槽
                            if (remainingAmount > 0) {
                                FluidStack remainingFluid = new FluidStack(fluidKey.getFluid(), remainingAmount);
                                // 使用craftingLock来保护共享的流体槽
                                craftingLock.lock();
                                try {
                                    for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                                        FluidTank tank = outputFluidTanks[i];
                                        if (tank.isEmpty() || tank.getFluid().getFluid().isSame(remainingFluid.getFluid())) {
                                            tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                                            break;
                                        }
                                    }
                                } finally {
                                    craftingLock.unlock();
                                }
                            }
                        }
                    }
                    setChanged();
                } finally {
                    taskLock.unlock();
                }
            });
        }

        public void cancel() {
            this.cancelled = true;
        }
    }
}
