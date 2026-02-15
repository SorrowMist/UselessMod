package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.energy.EnergyManager;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModRecipeTypes;
import com.sorrowmist.useless.init.ModTags;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOTS_START = 0;
    public static final int INPUT_SLOTS_COUNT = 9;
    public static final int OUTPUT_SLOTS_START = 9;
    public static final int OUTPUT_SLOTS_COUNT = 9;
    public static final int CATALYST_SLOT = 18;
    public static final int MOLD_SLOT = 19;
    public static final int TOTAL_SLOTS = 20;

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
    private int accumulatedEnergy = 0;

    // 活跃状态冷却计时器，用于避免配方切换时的闪烁
    private int activeCooldown = 0;
    private static final int ACTIVE_COOLDOWN_TICKS = 5;

    // 熔炉阶级 0-9，0为基础等级
    private int furnaceTier = 0;

    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.is(ModTags.MOLDS) && slot != MOLD_SLOT) {
                ItemStack moldSlotStack = this.getStackInSlot(MOLD_SLOT);
                if (moldSlotStack.isEmpty()) {
                    return super.insertItem(MOLD_SLOT, stack, simulate);
                }
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == MOLD_SLOT ? 1 : super.getSlotLimit(slot);
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
        }
    };

    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);
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
            capacity = base * (long) Math.pow(4, tier - 3);
        }
        return (int) Math.min(capacity, Integer.MAX_VALUE);
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
            capacity = base * (long) Math.pow(4, tier - 3);
        }
        return (int) Math.min(capacity, Integer.MAX_VALUE);
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
            receive = base * (long) Math.pow(4, tier - 3);
        }
        return (int) Math.min(receive, Integer.MAX_VALUE);
    }

    /**
     * 获取当前熔炉阶级
     */
    public int getFurnaceTier() {
        return this.furnaceTier;
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
        if (this.canConsumeRecipeInputs(recipe)) return;

        this.startRecipeProcessing(recipe);
    }

    /**
     * 处理当前配方
     * <p>
     * 每tick消耗能量并增加进度，完成时产出物品
     */
    private void processCurrentRecipe() {
        if (this.currentRecipe == null) return;

        if (this.canConsumeRecipeInputs(this.currentRecipe)) {
            this.resetProgress();
            return;
        }

        int currentParallel = this.calculateCurrentParallel(this.currentRecipe);
        int baseEnergyPerTick = this.currentRecipe.energy() / this.currentRecipe.processTime();
        int energyRequired = baseEnergyPerTick * currentParallel;

        if (!this.energyManager.canWork(energyRequired)) return;

        this.energyManager.tryConsumeEnergy(energyRequired);
        this.accumulatedEnergy += energyRequired;
        this.progress++;

        if (this.progress >= this.maxProgress) {
            this.completeRecipe();
        }

        this.setChanged();
    }

    /**
     * 完成当前配方处理
     * <p>
     * 根据并行数消耗输入材料并产出物品
     */
    private void completeRecipe() {
        int catalystParallel = this.calculateCurrentParallel(this.currentRecipe);
        int materialParallel = this.calculateMaxMaterialParallel(this.currentRecipe);
        int actualParallel = Math.min(catalystParallel, materialParallel);

        int requiredTotalEnergy = this.currentRecipe.energy() * actualParallel;

        if (this.accumulatedEnergy < requiredTotalEnergy) {
            int energyDeficit = requiredTotalEnergy - this.accumulatedEnergy;
            if (this.energyManager.canWork(energyDeficit)) {
                this.energyManager.tryConsumeEnergy(energyDeficit);
                this.accumulatedEnergy += energyDeficit;
            } else {
                int energySupportedParallel = this.accumulatedEnergy / this.currentRecipe.energy();
                actualParallel = Math.max(1, Math.min(actualParallel, energySupportedParallel));
            }
        }

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
        this.maxProgress = recipe.processTime();
        this.progress = 0;

        // 更新上一个成功处理的配方
        this.lastSuccessfulRecipe = recipe;

        int maxMaterialParallel = this.calculateMaxMaterialParallel(recipe);
        int catalystParallel = this.calculateCurrentParallel(recipe);

        this.cachedParallel = Math.min(maxMaterialParallel, catalystParallel);
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
        int catalystParallel = this.getCatalystMaxParallel();
        if (catalystParallel <= 1) return 1;

        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isPresent()) {
            int materialParallel = this.calculateMaxMaterialParallel(match.get());
            return Math.min(catalystParallel, materialParallel);
        }

        return catalystParallel;
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
        this.accumulatedEnergy = tag.getInt(NBTConstants.ACCUMULATED_ENERGY);

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
        tag.putInt(NBTConstants.ACCUMULATED_ENERGY, this.accumulatedEnergy);

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
     * 查找匹配的配方
     * <p>
     * 优先检查上一个成功处理的配方，以减少查找时间和避免闪烁
     *
     * @return 匹配的配方，如果没有则返回空
     */
    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        if (this.level == null) return Optional.empty();

        // 优先检查上一个成功处理的配方
        if (this.lastSuccessfulRecipe != null && this.canProcessRecipe(this.lastSuccessfulRecipe)) {
            return Optional.of(this.lastSuccessfulRecipe);
        }

        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = this.level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value();
            // 跳过已经检查过的上一个配方
            if (recipe == this.lastSuccessfulRecipe) continue;

            if (this.canProcessRecipe(recipe)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
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

        if (!recipe.catalyst().isEmpty()) {
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            if (!recipe.catalyst().test(catalystStack)) return false;
        }

        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            return recipe.mold().test(moldStack);
        }

        return true;
    }

    private boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) return true;
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long requiredAmount = requiredFluid.getAmount();
            long foundAmount = 0;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    foundAmount += tankFluid.getAmount();
                }
            }
            if (foundAmount < requiredAmount) return true;
        }

        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            return !recipe.mold().test(moldStack);
        }

        return false;
    }

    private int calculateCurrentParallel(AdvancedAlloyFurnaceRecipe recipe) {
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

        int parallel = isUselessRecipe
                ? CatalystParallelManager.calculateParallelForUselessIngotRecipe(catalystStack, targetTier)
                : CatalystParallelManager.calculateParallelForNormalRecipe(catalystStack);

        return parallel <= 0 ? 1 : parallel;
    }

    private boolean hasOutputSpace(AdvancedAlloyFurnaceRecipe recipe) {
        int catalystParallel = this.calculateCurrentParallel(recipe);
        int materialParallel = this.calculateMaxMaterialParallel(recipe);
        int parallel = Math.min(catalystParallel, materialParallel);

        for (ItemStack output : recipe.outputs()) {
            long remaining = (long) output.getCount() * parallel;

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) return true;
                if (ItemStack.isSameItemSameComponents(slotStack, output)) {
                    long space = slotStack.getMaxStackSize() - slotStack.getCount();
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }

            if (remaining > 0) return false;
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            long remaining = (long) outputFluid.getAmount() * parallel;
            boolean hasSpace = false;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                if (tankFluid.isEmpty()) {
                    hasSpace = true;
                    break;
                }
                if (FluidStack.isSameFluidSameComponents(tankFluid, outputFluid)) {
                    long space = this.outputFluidTanks[i].getCapacity() - tankFluid.getAmount();
                    remaining -= space;
                    if (remaining <= 0) {
                        hasSpace = true;
                        break;
                    }
                }
            }
            if (!hasSpace) return false;
        }

        return true;
    }

    private int calculateMaxMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int minParallel = Integer.MAX_VALUE;

        for (var countedIng : recipe.inputs()) {
            long totalAvailable = 0;
            var ingredient = countedIng.ingredient();

            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    totalAvailable += stack.getCount();
                }
            }

            long requiredPerParallel = countedIng.count();
            if (requiredPerParallel > 0) {
                int possibleParallel = (int) (totalAvailable / requiredPerParallel);
                minParallel = Math.min(minParallel, possibleParallel);
            }
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long totalAvailable = 0;

            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    totalAvailable += tankFluid.getAmount();
                }
            }

            long requiredPerParallel = requiredFluid.getAmount();
            if (requiredPerParallel > 0) {
                int possibleParallel = (int) (totalAvailable / requiredPerParallel);
                minParallel = Math.min(minParallel, possibleParallel);
            }
        }

        return Math.max(1, minParallel);
    }

    private void consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        for (var countedIng : recipe.inputs()) {
            long toConsume = countedIng.count() * parallel;
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
            int toDrain = requiredFluid.getAmount() * parallel;
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
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        if (!catalystStack.isEmpty() && CatalystParallelManager.isValidCatalyst(catalystStack)) {
            if (!CatalystParallelManager.isUsefulIngot(catalystStack)) {
                catalystStack.shrink(1);
            }
        }

        for (ItemStack output : recipe.outputs()) {
            ItemStack toInsert = output.copy();
            toInsert.setCount(output.getCount() * parallel);

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(slotStack, toInsert)) {
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    int toAdd = Math.min(space, toInsert.getCount());
                    slotStack.grow(toAdd);
                    toInsert.shrink(toAdd);
                }
            }

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    this.itemHandler.setStackInSlot(i, toInsert.copy());
                    toInsert.setCount(0);
                }
            }
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            FluidStack toInsert = outputFluid.copy();
            toInsert.setAmount(outputFluid.getAmount() * parallel);

            for (int i = 0; i < FLUID_TANK_COUNT && !toInsert.isEmpty(); i++) {
                FluidStack tankFluid = this.outputFluidTanks[i].getFluid();
                if (tankFluid.isEmpty() || FluidStack.isSameFluidSameComponents(tankFluid, toInsert)) {
                    int filled = this.outputFluidTanks[i].fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
                    toInsert.shrink(filled);
                }
            }
        }
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
}
