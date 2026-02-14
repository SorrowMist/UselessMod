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

    /**
     * 输入槽起始索引
     */
    public static final int INPUT_SLOTS_START = 0;
    /**
     * 输入槽数量
     */
    public static final int INPUT_SLOTS_COUNT = 9;
    /**
     * 输出槽起始索引
     */
    public static final int OUTPUT_SLOTS_START = 9;
    /**
     * 输出槽数量
     */
    public static final int OUTPUT_SLOTS_COUNT = 9;
    /**
     * 催化剂槽索引
     */
    public static final int CATALYST_SLOT = 18;
    /**
     * 模具槽索引
     */
    public static final int MOLD_SLOT = 19;
    /**
     * 总槽位数
     */
    public static final int TOTAL_SLOTS = 20;

    /**
     * 流体槽数量
     */
    public static final int FLUID_TANK_COUNT = 6;
    /**
     * 流体槽容量
     */
    private static final int FLUID_TANK_CAPACITY = 16000;

    /**
     * 能量容量
     */
    private static final int ENERGY_CAPACITY = 100000;
    /**
     * 最大能量接收速率
     */
    private static final int ENERGY_MAX_RECEIVE = 10000;
    /**
     * 最大能量提取速率
     */
    private static final int ENERGY_MAX_EXTRACT = 0;


    /**
     * 输入流体槽数组
     */
    private final FluidTank[] inputFluidTanks = new FluidTank[FLUID_TANK_COUNT];
    /**
     * 输出流体槽数组
     */
    private final FluidTank[] outputFluidTanks = new FluidTank[FLUID_TANK_COUNT];

    /**
     * 能量管理器
     */
    private final IEnergyManager energyManager = EnergyManager.builder()
                                                              .capacity(ENERGY_CAPACITY)
                                                              .maxReceive(ENERGY_MAX_RECEIVE)
                                                              .maxExtract(ENERGY_MAX_EXTRACT)
                                                              .onChange(this::setChanged)
                                                              .build();

    /**
     * 数据同步对象
     */
    private final AdvancedAlloyFurnaceData data = new AdvancedAlloyFurnaceData(this);

    /**
     * 当前进度
     */
    private int progress = 0;
    /**
     * 最大进度
     */
    private int maxProgress = 200;
    /**
     * 当前并行数
     */
    private int currentParallel = 1;
    /**
     * 最大并行数
     */
    private int maxParallel = 8;
    /**
     * 是否有模具
     */
    private boolean hasMold = false;

    /**
     * 当前正在处理的配方
     */
    @Nullable
    private AdvancedAlloyFurnaceRecipe currentRecipe;
    /**
     * 催化剂剩余使用次数
     */
    private int catalystUsesRemaining = 0;
    /**
     * 缓存的并行数（用于合成过程中保持一致）
     */
    private int cachedParallel = 1;
    /**
     * 是否为无用锭配方
     */
    private boolean isUselessIngotRecipe = false;
    /**
     * 目标无用锭等级（用于跨阶合成）
     */
    private int targetUselessIngotTier = 0;
    /**
     * 累计消耗的能量（用于计算实际并行数）
     */
    private int accumulatedEnergy = 0;

    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);

        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            this.inputFluidTanks[i] = this.createTank(i, true);
            this.outputFluidTanks[i] = this.createTank(i, false);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AdvancedAlloyFurnaceBlockEntity entity) {
        if (level.isClientSide) return;

        // 如果没有正在处理的配方，尝试匹配新配方
        if (entity.currentRecipe == null) {
            Optional<AdvancedAlloyFurnaceRecipe> match = entity.findMatchingRecipe();
            if (match.isPresent()) {
                AdvancedAlloyFurnaceRecipe recipe = match.get();

                // 检查输出空间
                if (!entity.hasOutputSpace(recipe)) {
                    return;
                }

                // 检查输入材料是否足够（检查1份）
                if (!entity.canConsumeRecipeInputs(recipe)) {
                    return;
                }

                // 开始新配方处理
                entity.startRecipeProcessing(recipe);
            }
        }

        // 处理正在进行的配方
        if (entity.currentRecipe != null) {
            // 检查输入材料是否仍然足够（防止中途被取走）
            if (!entity.canConsumeRecipeInputs(entity.currentRecipe)) {
                entity.resetProgress();
                return;
            }

            // 动态计算当前并行数（基于当前催化剂状态）
            int currentParallel = entity.calculateCurrentParallel(entity.currentRecipe);

            // 计算当前tick需要的能量（基于当前并行数）
            int baseEnergyPerTick = entity.currentRecipe.energy() / entity.currentRecipe.processTime();
            int energyRequired = baseEnergyPerTick * currentParallel;

            // 检查能量是否足够
            if (!entity.energyManager.canWork(energyRequired)) {
                return; // 无法工作
            }

            // 消耗能量
            entity.energyManager.tryConsumeEnergy(energyRequired);

            // 累加实际消耗的能量（用于最终计算）
            entity.accumulatedEnergy += energyRequired;
            entity.progress++;

            if (entity.progress >= entity.maxProgress) {
                // 配方完成，计算实际并行数
                // 1. 基于当前催化剂计算理论并行数
                int catalystParallel = entity.calculateCurrentParallel(entity.currentRecipe);
                // 2. 基于可用材料计算最大并行数
                int materialParallel = entity.calculateMaxMaterialParallel(entity.currentRecipe);
                // 3. 实际并行数 = min(催化剂并行数, 材料并行数)
                int actualParallel = Math.min(catalystParallel, materialParallel);
                
                // 4. 检查能量是否足够支持这个并行数
                int requiredTotalEnergy = entity.currentRecipe.energy() * actualParallel;
                
                if (entity.accumulatedEnergy < requiredTotalEnergy) {
                    // 能量不足，尝试补足差额
                    int energyDeficit = requiredTotalEnergy - entity.accumulatedEnergy;
                    if (entity.energyManager.canWork(energyDeficit)) {
                        // 能量足够，扣除剩余能量
                        entity.energyManager.tryConsumeEnergy(energyDeficit);
                        entity.accumulatedEnergy += energyDeficit;
                    } else {
                        // 能量不足，根据实际累计能量重新计算并行数
                        int energySupportedParallel = entity.accumulatedEnergy / entity.currentRecipe.energy();
                        actualParallel = Math.max(1, Math.min(actualParallel, energySupportedParallel));
                    }
                }
                
                // 进度完成时消耗材料并产出（基于实际并行数）
                entity.consumeRecipeInputs(entity.currentRecipe, actualParallel);
                entity.produceRecipeOutputs(entity.currentRecipe, actualParallel);
                entity.resetProgress();
            }

            entity.setChanged();
        }
    }

    /**
     * 物品处理器
     */
    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // 如果物品是模具且当前槽位不是模具槽，尝试优先放入模具槽
            if (stack.is(ModTags.MOLDS) && slot != MOLD_SLOT) {
                // 检查模具槽是否为空或可以接收
                ItemStack moldSlotStack = this.getStackInSlot(MOLD_SLOT);
                if (moldSlotStack.isEmpty()) {
                    // 模具槽为空，重定向到模具槽
                    return super.insertItem(MOLD_SLOT, stack, simulate);
                }
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            // 模具槽只能放入1个物品
            if (slot == MOLD_SLOT) {
                return 1;
            }
            return super.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // 催化剂槽只能放入带有 catalysts tag 的物品
            if (slot == CATALYST_SLOT) {
                return stack.is(ModTags.CATALYSTS);
            }
            // 模具槽可以放入任何物品（不限制）
            return super.isItemValid(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            AdvancedAlloyFurnaceBlockEntity.this.setChanged();
            if (slot == MOLD_SLOT) {
                AdvancedAlloyFurnaceBlockEntity.this.updateMoldState();
            }
        }
    };

    /**
     * 开始配方处理（不消耗材料，只设置状态）
     */
    private void startRecipeProcessing(AdvancedAlloyFurnaceRecipe recipe) {
        this.currentRecipe = recipe;
        this.maxProgress = recipe.processTime();
        this.progress = 0;

        // 计算实际可用的并行数（基于可用材料和催化剂）
        int maxMaterialParallel = this.calculateMaxMaterialParallel(recipe);
        int catalystParallel = this.calculateCurrentParallel(recipe);
        int actualParallel = Math.min(maxMaterialParallel, catalystParallel);

        this.cachedParallel = actualParallel;
        // 催化剂消耗：每个并行单位需要recipe.catalystUses()个催化剂
        // 所以总消耗量为 cachedParallel * recipe.catalystUses()
        this.catalystUsesRemaining = this.cachedParallel * recipe.catalystUses();
        this.accumulatedEnergy = 0;

        // 判断是否为无用锭配方
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
        return Component.literal("AdvancedAlloyFurnace");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
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
        if (index >= 0 && index < FLUID_TANK_COUNT) {
            return this.inputFluidTanks[index];
        }
        return new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        if (index >= 0 && index < FLUID_TANK_COUNT) {
            return this.outputFluidTanks[index];
        }
        return new FluidTank(0);
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

    /**
     * 获取当前实际并行数（用于显示）
     * 返回实际的并行数（受材料和催化剂限制）
     */
    int getCurrentParallel() {
        if (this.currentRecipe != null && this.progress > 0) {
            // 配方运行时，返回缓存的并行数（实际执行的并行数）
            return this.cachedParallel;
        }
        // 空闲时，基于当前材料和催化剂计算可能的实际并行数
        return this.calculateDisplayParallel();
    }

    /**
     * 获取催化剂提供的最大并行数（仅由催化剂决定）
     */
    public int getCatalystMaxParallel() {
        // 基于当前催化剂槽的物品计算
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        if (catalystStack.isEmpty()) {
            return 1;
        }
        int parallel = CatalystParallelManager.calculateParallelForNormalRecipe(catalystStack);
        return Math.min(parallel, this.maxParallel);
    }

    /**
     * 计算用于显示的实际并行数（考虑材料和催化剂）
     * 空闲状态下预估可能的并行数
     */
    private int calculateDisplayParallel() {
        // 获取催化剂提供的并行数
        int catalystParallel = this.getCatalystMaxParallel();

        // 如果没有催化剂或催化剂提供1并行，直接返回1
        if (catalystParallel <= 1) {
            return 1;
        }

        // 尝试匹配配方来计算材料限制
        Optional<AdvancedAlloyFurnaceRecipe> match = this.findMatchingRecipe();
        if (match.isPresent()) {
            AdvancedAlloyFurnaceRecipe recipe = match.get();
            // 计算材料限制的并行数
            int materialParallel = this.calculateMaxMaterialParallel(recipe);
            // 返回催化剂和材料中的较小值
            return Math.min(catalystParallel, materialParallel);
        }

        // 没有匹配配方时，返回催化剂提供的并行数
        return catalystParallel;
    }

    void setCurrentParallel(int parallel) {
        this.currentParallel = Math.min(parallel, this.maxParallel);
        this.setChanged();
    }

    int getMaxParallel() {
        return this.maxParallel;
    }

    void setMaxParallel(int maxParallel) {
        this.maxParallel = maxParallel;
    }

    boolean hasMold() {
        return this.hasMold;
    }

    void setHasMold(boolean hasMold) {
        this.hasMold = hasMold;
        this.setChanged();
    }

    int getProgress() {
        return this.progress;
    }

    void setProgress(int progress) {
        this.progress = progress;
        this.setChanged();
    }

    int getMaxProgress() {
        return this.maxProgress;
    }

    void setMaxProgress(int maxProgress) {
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

    /**
     * 创建流体槽
     */
    private FluidTank createTank(int index, boolean isInput) {
        return new FluidTank(FLUID_TANK_CAPACITY) {
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
     * 清空指定流体槽
     */
    public void clearFluidTank(int tankIndex, boolean isInput) {
        if (tankIndex < 0 || tankIndex >= FLUID_TANK_COUNT) return;

        if (isInput) {
            this.inputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        } else {
            this.outputFluidTanks[tankIndex].setFluid(FluidStack.EMPTY);
        }
        this.setChanged();

        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(
                    this.worldPosition,
                    this.getBlockState(),
                    this.getBlockState(), 3
            );
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(NBTConstants.INVENTORY)) {
            this.itemHandler.deserializeNBT(registries, tag.getCompound(NBTConstants.INVENTORY));
        }

        if (tag.contains(NBTConstants.ENERGY)) {
            this.energyManager.setEnergyStored(tag.getInt(NBTConstants.ENERGY));
        }

        this.progress = tag.getInt(NBTConstants.PROGRESS);
        this.maxProgress = tag.getInt(NBTConstants.MAX_PROGRESS);
        this.currentParallel = tag.getInt(NBTConstants.CURRENT_PARALLEL);
        this.maxParallel = tag.getInt(NBTConstants.MAX_PARALLEL);
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
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put(NBTConstants.INVENTORY, this.itemHandler.serializeNBT(registries));
        tag.putInt(NBTConstants.ENERGY, this.energyManager.getEnergyStored());
        tag.putInt(NBTConstants.PROGRESS, this.progress);
        tag.putInt(NBTConstants.MAX_PROGRESS, this.maxProgress);
        tag.putInt(NBTConstants.CURRENT_PARALLEL, this.currentParallel);
        tag.putInt(NBTConstants.MAX_PARALLEL, this.maxParallel);
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
     */
    private Optional<AdvancedAlloyFurnaceRecipe> findMatchingRecipe() {
        if (this.level == null) return Optional.empty();

        List<RecipeHolder<AdvancedAlloyFurnaceRecipe>> recipes = this.level.getRecipeManager()
                                                                           .getAllRecipesFor(
                                                                                   ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get());

        for (RecipeHolder<AdvancedAlloyFurnaceRecipe> holder : recipes) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value();
            if (this.canProcessRecipe(recipe)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    /**
     * 检查是否可以处理指定配方（基础检查，不考虑并行数）
     */
    private boolean canProcessRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        // 收集当前输入槽中的物品
        List<ItemStack> currentInputs = new ArrayList<>();
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
            ItemStack stack = this.itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                currentInputs.add(stack);
            }
        }

        // 检查物品输入（基础数量）
        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count();
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (ItemStack stack : currentInputs) {
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) {
                return false;
            }
        }

        // 检查流体输入（基础数量）
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

        // 检查催化剂
        if (!recipe.catalyst().isEmpty()) {
            ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
            if (!recipe.catalyst().test(catalystStack)) {
                return false;
            }
        }

        // 检查模具
        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            if (!recipe.mold().test(moldStack)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查是否可以消耗配方输入（只需要检查1份材料）
     * 实际并行数由可用材料决定
     */
    private boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe) {
        // 检查物品输入（只需1份）
        for (var countedIng : recipe.inputs()) {
            long requiredCount = countedIng.count() * 1; // 只检查1份
            var ingredient = countedIng.ingredient();

            long foundCount = 0;
            for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_START + INPUT_SLOTS_COUNT; i++) {
                ItemStack stack = this.itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    foundCount += stack.getCount();
                }
            }

            if (foundCount < requiredCount) {
                return false;
            }
        }

        // 检查流体输入（只需1份）
        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long requiredAmount = (long) requiredFluid.getAmount() * 1; // 只检查1份
            long foundAmount = 0;
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack tankFluid = this.inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    foundAmount += tankFluid.getAmount();
                }
            }
            if (foundAmount < requiredAmount) return false;
        }

        // 检查模具（如果配方需要）
        if (!recipe.mold().isEmpty()) {
            ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
            if (!recipe.mold().test(moldStack)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 计算当前并行数
     * 所有配方都可以使用催化剂进行并行，催化剂的作用就是拿来并行的
     */
    private int calculateCurrentParallel(AdvancedAlloyFurnaceRecipe recipe) {
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);

        // 判断是否为无用锭配方
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

        int parallel;
        if (isUselessRecipe) {
            // 无用锭配方：使用跨阶并行计算
            parallel = CatalystParallelManager.calculateParallelForUselessIngotRecipe(catalystStack, targetTier);
        } else {
            // 普通配方：使用普通并行计算
            parallel = CatalystParallelManager.calculateParallelForNormalRecipe(catalystStack);
        }

        // 限制并行数
        parallel = Math.min(parallel, this.maxParallel);
        return parallel <= 0 ? 1 : parallel;
    }

    /**
     * 检查输出槽是否有足够空间（考虑最大可能并行数）
     * 使用当前催化剂状态计算最大可能的并行数
     */
    private boolean hasOutputSpace(AdvancedAlloyFurnaceRecipe recipe) {
        // 计算当前最大可能的并行数（基于催化剂和材料）
        int catalystParallel = this.calculateCurrentParallel(recipe);
        int materialParallel = this.calculateMaxMaterialParallel(recipe);
        int parallel = Math.min(catalystParallel, materialParallel);

        // 检查物品输出空间
        for (ItemStack output : recipe.outputs()) {
            long remaining = (long) output.getCount() * parallel;

            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT; i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(slotStack, output)) {
                    long space = slotStack.getMaxStackSize() - slotStack.getCount();
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }

            if (remaining > 0) return false;
        }

        // 检查流体输出空间
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

    /**
     * 计算根据可用材料可以支持的最大并行数
     */
    private int calculateMaxMaterialParallel(AdvancedAlloyFurnaceRecipe recipe) {
        int minParallel = Integer.MAX_VALUE;

        // 检查物品输入
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
                int possibleParallel = (int) Math.min(minParallel, totalAvailable / requiredPerParallel);
                minParallel = Math.min(minParallel, possibleParallel);
            }
        }

        // 检查流体输入
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
                int possibleParallel = (int) Math.min(minParallel, totalAvailable / requiredPerParallel);
                minParallel = Math.min(minParallel, possibleParallel);
            }
        }

        // 确保至少返回1
        return Math.max(1, minParallel);
    }

    /**
     * 消耗配方输入（考虑并行数）
     */
    private void consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 消耗物品输入
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

        // 消耗流体输入
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

    /**
     * 产出配方输出（考虑并行数）
     */
    private void produceRecipeOutputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {

        // 处理催化剂消耗（有用锭不消耗）
        // 催化剂槽有有效催化剂时消耗1个（无论并行数多少，一次配方只消耗1个催化剂）
        ItemStack catalystStack = this.itemHandler.getStackInSlot(CATALYST_SLOT);
        if (!catalystStack.isEmpty() && CatalystParallelManager.isValidCatalyst(catalystStack)) {
            // 有用锭不消耗，其他催化剂每次配方消耗1个
            if (!CatalystParallelManager.isUsefulIngot(catalystStack)) {
                catalystStack.shrink(1);
            }
        }

        // 产出物品
        for (ItemStack output : recipe.outputs()) {
            ItemStack toInsert = output.copy();
            toInsert.setCount(output.getCount() * parallel);

            // 先尝试合并到已有槽位
            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(slotStack, toInsert)) {
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    int toAdd = Math.min(space, toInsert.getCount());
                    slotStack.grow(toAdd);
                    toInsert.shrink(toAdd);
                }
            }

            // 再尝试放入空槽位
            for (int i = OUTPUT_SLOTS_START; i < OUTPUT_SLOTS_START + OUTPUT_SLOTS_COUNT && !toInsert.isEmpty(); i++) {
                ItemStack slotStack = this.itemHandler.getStackInSlot(i);
                if (slotStack.isEmpty()) {
                    this.itemHandler.setStackInSlot(i, toInsert.copy());
                    toInsert.setCount(0);
                }
            }
        }

        // 产出流体
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

    /**
     * 重置进度
     */
    private void resetProgress() {
        this.progress = 0;
        this.currentRecipe = null;
        this.catalystUsesRemaining = 0;
        this.cachedParallel = 1;
        this.isUselessIngotRecipe = false;
        this.targetUselessIngotTier = 0;
        this.accumulatedEnergy = 0;
        this.setChanged();
    }

    /**
     * 更新模具状态
     */
    private void updateMoldState() {
        ItemStack moldStack = this.itemHandler.getStackInSlot(MOLD_SLOT);
        this.hasMold = !moldStack.isEmpty();
        this.setChanged();
    }

    /**
     * 通用流体槽处理器
     */
    private record FluidTankHandler(FluidTank[] tanks, boolean allowFill) implements IFluidHandler {

        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.tanks[tank].getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.tanks[tank].getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.tanks[tank].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!this.allowFill) {
                return 0;
            }
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.tanks[i];
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) {
                            return filled;
                        }
                    }
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.tanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.tanks[i].getFluid().isEmpty()) {
                    return this.tanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    /**
     * 复合流体槽处理器 - 同时管理输入和输出槽位
     */
    private record CombinedFluidTankHandler(FluidTank[] inputTanks, FluidTank[] outputTanks) implements IFluidHandler {
        @Override
        public int getTanks() {
            return FLUID_TANK_COUNT * 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
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
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank < FLUID_TANK_COUNT) {
                return this.inputTanks[tank].isFluidValid(stack);
            } else if (tank < FLUID_TANK_COUNT * 2) {
                return this.outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // 优先填充到输入槽
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidTank tank = this.inputTanks[i];
                if (tank.isFluidValid(resource)) {
                    if (tank.getFluid().isEmpty()
                            || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                        int filled = tank.fill(resource, action);
                        if (filled > 0) {
                            return filled;
                        }
                    }
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // 优先从输出槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.outputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            // 输出槽没有再从输入槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                FluidStack drained = this.inputTanks[i].drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // 优先从输出槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.outputTanks[i].getFluid().isEmpty()) {
                    return this.outputTanks[i].drain(maxDrain, action);
                }
            }
            // 输出槽没有再从输入槽抽取
            for (int i = 0; i < FLUID_TANK_COUNT; i++) {
                if (!this.inputTanks[i].getFluid().isEmpty()) {
                    return this.inputTanks[i].drain(maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }
    }


}
