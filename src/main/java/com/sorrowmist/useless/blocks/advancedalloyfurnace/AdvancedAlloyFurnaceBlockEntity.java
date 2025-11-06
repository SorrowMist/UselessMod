package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.ModBlockEntities;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider, ITransferControllable {

    // 能量系统
    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(100000, 1000, 0);
    private final LazyOptional<IEnergyStorage> lazyEnergyHandler = LazyOptional.of(() -> energyStorage);

    // 状态管理
    private boolean isActive = false;
    private boolean wasActive = false;
    private int syncCounter = 0;

    // 处理进度
    private int process;
    private int processMax = 200;
    private int processTick = 10;

    // 配方管理
    private AdvancedAlloyFurnaceRecipe currentRecipe;

    // 传输控制配置
    private boolean transferIn = true;  // 是否允许输入
    private boolean transferOut = true; // 是否允许自动输出
    private final EnumMap<Direction, Boolean> sideTransferOut = new EnumMap<>(Direction.class);

    // 输出配置
    private int outputTickInterval = 5; // 更快的输出间隔

    // 容器数据同步
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getEnergyStored();
                case 1 -> getMaxEnergyStored();
                case 2 -> getProgress();
                case 3 -> getMaxProgress();
                case 4 -> isActive() ? 1 : 0;
                case 5 -> transferOut ? 1 : 0; // 自动输出状态
                case 6 -> transferIn ? 1 : 0;  // 输入控制状态
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergy(value);
                case 2 -> process = value;
                case 4 -> isActive = value == 1;
                case 5 -> transferOut = value == 1; // 设置自动输出状态
                case 6 -> transferIn = value == 1;  // 设置输入控制状态
            }
        }

        @Override
        public int getCount() {
            return 7; // 增加数据字段
        }
    };

    // 物品和流体系统
    private final ItemStackHandler itemHandler = new ItemStackHandler(12) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // 输出槽位变化时立即同步到客户端
            if (slot >= 6 && slot < 12) {
                syncToClient();
            } else {
                markForClientUpdate();
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < 6;
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // 流体槽
    private final FluidTank inputFluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            UselessMod.LOGGER.debug("Input fluid tank contents changed: {}", getFluid());
            setChanged();
            markForClientUpdate();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            boolean valid = level != null ?
                    AdvancedAlloyFurnaceRecipeManager.getInstance().isValidInputFluid(level, stack) : true;
            UselessMod.LOGGER.debug("Fluid validation for {}: {}", stack.getFluid(), valid);
            return valid;
        }
    };

    private final FluidTank outputFluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForClientUpdate();
        }
    };

    // 流体处理器
    private final LazyOptional<IFluidHandler> lazyFluidHandler = LazyOptional.of(() -> new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? inputFluidTank.getFluid() : outputFluidTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? inputFluidTank.getCapacity() : outputFluidTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            if (tank == 0) {
                return inputFluidTank.isFluidValid(stack);
            }
            return false; // 输出槽不接受输入
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            UselessMod.LOGGER.debug("Attempting to fill fluid: {}, amount: {}, action: {}",
                    resource.getFluid(), resource.getAmount(), action);

            if (resource.isEmpty() || !isFluidValid(0, resource)) {
                UselessMod.LOGGER.debug("Fluid fill rejected: empty or invalid");
                return 0;
            }

            int filled = inputFluidTank.fill(resource, action);
            UselessMod.LOGGER.debug("Fluid fill result: {} units filled", filled);
            return filled;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return outputFluidTank.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return outputFluidTank.drain(maxDrain, action);
        }
    });

    public AdvancedAlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(), pos, state);
        // 初始化所有面都允许输出
        for (Direction direction : Direction.values()) {
            sideTransferOut.put(direction, true);
        }
    }

    // 数据同步管理
    private boolean needsClientUpdate = false;

    public void tick() {
        if (level == null) return;

        boolean curActive = isActive;

        if (!level.isClientSide) {
            // 服务端逻辑
            if (isActive) {
                processTick();
                if (canProcessFinish()) {
                    processFinish();
                    if (!canProcessStart()) {
                        processOff();
                    } else {
                        processStart();
                    }
                } else if (energyStorage.getEnergyStored() < processTick) {
                    processOff();
                }
            } else {
                if (canProcessStart()) {
                    processStart();
                    isActive = true;
                }
            }

            // 改进的自动输出逻辑 - 每5tick执行一次
            if (transferOut && level.getGameTime() % outputTickInterval == 0) {
                autoOutputItemsAndFluids();
            }

            updateActiveState(curActive);

            // 数据同步
            if (needsClientUpdate || syncCounter++ >= 5) {
                syncToClient();
                needsClientUpdate = false;
                syncCounter = 0;
            }
        }
    }

    // 自动输出物品和流体
    private void autoOutputItemsAndFluids() {
        if (level == null || level.isClientSide) return;

        boolean outputPerformed = false;

        // 遍历所有方向
        for (Direction direction : Direction.values()) {
            // 检查该方向是否允许输出
            if (!getTransferOut(direction)) continue;

            // 输出物品
            if (outputItemsToDirection(direction)) {
                outputPerformed = true;
            }

            // 输出流体
            if (outputFluidToDirection(direction)) {
                outputPerformed = true;
            }
        }

        if (outputPerformed) {
            setChanged();
            markForClientUpdate();
        }
    }

    // 向指定方向输出物品
    private boolean outputItemsToDirection(Direction direction) {
        if (level == null) return false;

        BlockPos targetPos = worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return false;

        LazyOptional<IItemHandler> targetCapability = targetEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite());
        if (!targetCapability.isPresent()) return false;

        IItemHandler targetHandler = targetCapability.orElse(null);
        boolean outputOccurred = false;

        // 遍历所有输出槽位 (6-11)
        for (int slot = 6; slot < 12; slot++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) continue;

            // 尝试插入物品
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(targetHandler, stackInSlot.copy(), false);

            if (remaining.getCount() < stackInSlot.getCount()) {
                // 成功输出了一些物品
                int transferred = stackInSlot.getCount() - remaining.getCount();
                itemHandler.extractItem(slot, transferred, false);
                outputOccurred = true;

                UselessMod.LOGGER.debug("Auto-output: Transferred {} items to {} at {}",
                        transferred, direction, targetPos);

                // 如果物品已经完全输出，继续下一个槽位
                if (remaining.isEmpty()) {
                    continue;
                }
            }
        }

        return outputOccurred;
    }

    // 向指定方向输出流体
    private boolean outputFluidToDirection(Direction direction) {
        if (level == null) return false;

        BlockPos targetPos = worldPosition.relative(direction);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null) return false;

        LazyOptional<IFluidHandler> targetCapability = targetEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite());
        if (!targetCapability.isPresent()) return false;

        IFluidHandler targetHandler = targetCapability.orElse(null);
        FluidStack fluidToOutput = outputFluidTank.getFluid();

        if (fluidToOutput.isEmpty()) return false;

        // 尝试输出流体
        int filled = targetHandler.fill(fluidToOutput.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (filled > 0) {
            FluidStack drained = outputFluidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                targetHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                UselessMod.LOGGER.debug("Auto-output: Transferred {}mb fluid to {} at {}",
                        filled, direction, targetPos);
                return true;
            }
        }

        return false;
    }

    private void markForClientUpdate() {
        needsClientUpdate = true;
    }

    // 配方处理逻辑
    protected boolean canProcessStart() {
        if (energyStorage.getEnergyStored() < processTick) {
            return false;
        }

        currentRecipe = findMatchingRecipe();
        if (currentRecipe == null) {
            return false;
        }

        return validateOutputs(currentRecipe);
    }

    protected boolean canProcessFinish() {
        return process <= 0;
    }

    protected void processStart() {
        if (currentRecipe != null) {
            processMax = currentRecipe.getProcessTime();
            processTick = Math.max(1, currentRecipe.getEnergy() / currentRecipe.getProcessTime());
            process = processMax;
            UselessMod.LOGGER.debug("Starting process: max={}, tick={}", processMax, processTick);
        }
        setChanged();
        markForClientUpdate();
    }

    protected void processFinish() {
        if (currentRecipe == null || !validateInputs(currentRecipe)) {
            processOff();
            return;
        }

        resolveRecipe(currentRecipe);
        setChanged();
        markForClientUpdate();
    }

    protected void processOff() {
        process = 0;
        isActive = false;
        wasActive = true;
        setChanged();
        markForClientUpdate();
    }

    protected void processTick() {
        if (process <= 0) return;

        if (energyStorage.getEnergyStored() >= processTick) {
            energyStorage.modifyEnergy(-processTick);
            process--;
            setChanged();
            markForClientUpdate();
        }
    }

    // 配方查找和验证
    private AdvancedAlloyFurnaceRecipe findMatchingRecipe() {
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }

        FluidStack inputFluid = inputFluidTank.getFluid();

        return AdvancedAlloyFurnaceRecipeManager.getInstance().getRecipe(level, inputItems, inputFluid);
    }

    private boolean validateInputs(AdvancedAlloyFurnaceRecipe recipe) {
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }

        FluidStack inputFluid = inputFluidTank.getFluid();

        return recipe.matches(inputItems, inputFluid);
    }

    private boolean validateOutputs(AdvancedAlloyFurnaceRecipe recipe) {
        // 检查输出物品空间
        for (ItemStack output : recipe.getOutputItems()) {
            boolean hasSpace = false;
            for (int i = 6; i < 12; i++) {
                ItemStack slot = itemHandler.getStackInSlot(i);
                if (slot.isEmpty()) {
                    hasSpace = true;
                    break;
                } else if (ItemStack.isSameItemSameTags(slot, output) &&
                        slot.getCount() + output.getCount() <= slot.getMaxStackSize()) {
                    hasSpace = true;
                    break;
                }
            }
            if (!hasSpace) return false;
        }

        // 检查输出流体空间
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty() && outputFluidTank.getSpace() < outputFluid.getAmount()) {
            return false;
        }

        return true;
    }

    // 配方处理方法
    private void resolveRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null) return;

        UselessMod.LOGGER.debug("Resolving recipe: {}", recipe.getId());

        // 创建输入物品的副本用于消耗计算
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i).copy());
        }

        FluidStack inputFluid = inputFluidTank.getFluid().copy();

        // 消耗输入
        recipe.consumeInputs(inputItems, inputFluid);

        // 更新实际的输入槽位
        for (int i = 0; i < 6; i++) {
            itemHandler.setStackInSlot(i, inputItems.get(i));
        }

        // 更新输入流体
        inputFluidTank.setFluid(inputFluid);

        // 产生输出物品
        for (ItemStack output : recipe.getOutputItems()) {
            if (!output.isEmpty()) {
                addOutputItem(output.copy());
            }
        }

        // 产生输出流体
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty()) {
            outputFluidTank.fill(outputFluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        }

        // 强制立即同步到客户端
        setChanged();
        syncToClient();
    }

    // 输出物品添加方法
    private void addOutputItem(ItemStack output) {
        ItemStack outputStack = output.copy();

        // 首先尝试堆叠到已有物品
        for (int i = 6; i < 12; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(slotStack, outputStack)) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space > 0) {
                    int transferAmount = Math.min(outputStack.getCount(), space);
                    slotStack.grow(transferAmount);
                    outputStack.shrink(transferAmount);

                    if (outputStack.isEmpty()) {
                        return;
                    }
                }
            }
        }

        // 然后尝试放入空槽位
        for (int i = 6; i < 12; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                itemHandler.setStackInSlot(i, outputStack.copy());
                return;
            }
        }

        // 如果没有空间，记录警告但不抛出异常
        UselessMod.LOGGER.warn("No space for output item in advanced alloy furnace: {}", output);
    }

    // 状态管理
    private void updateActiveState(boolean prevActive) {
        if (!isActive && prevActive) {
            wasActive = true;
            return;
        }

        if (prevActive != isActive || (wasActive && syncCounter % 40 == 0)) {
            wasActive = false;
            syncToClient();
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Getters - 用于UI显示
    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    public int getProgress() {
        return process;
    }

    public int getMaxProgress() {
        return processMax;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getProcessTick() {
        return processTick;
    }

    public boolean isAutoOutputEnabled() {
        return transferOut;
    }

    public void setAutoOutputEnabled(boolean enabled) {
        this.transferOut = enabled;
        setChanged();
        markForClientUpdate();
    }

    public boolean isTransferInEnabled() {
        return transferIn;
    }

    public void setTransferInEnabled(boolean enabled) {
        this.transferIn = enabled;
        setChanged();
        markForClientUpdate();
    }

    // 公共访问方法
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public ItemStack getItemInSlot(int slot) {
        if (slot >= 0 && slot < itemHandler.getSlots()) {
            return itemHandler.getStackInSlot(slot);
        }
        return ItemStack.EMPTY;
    }

    public FluidTank getInputFluidTank() {
        return inputFluidTank;
    }

    public FluidTank getOutputFluidTank() {
        return outputFluidTank;
    }

    public ContainerData getContainerData() {
        return data;
    }

    // 数据持久化
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        energyStorage.deserializeNBT(tag.get("Energy"));
        inputFluidTank.readFromNBT(tag.getCompound("InputFluid"));
        outputFluidTank.readFromNBT(tag.getCompound("OutputFluid"));
        process = tag.getInt("Process");
        processMax = tag.getInt("ProcessMax");
        isActive = tag.getBoolean("IsActive");
        wasActive = tag.getBoolean("WasActive");

        // 加载新的传输设置
        transferIn = tag.getBoolean("TransferIn");
        transferOut = tag.getBoolean("TransferOut");

        // 加载面设置
        CompoundTag sideTag = tag.getCompound("SideTransfer");
        for (String key : sideTag.getAllKeys()) {
            try {
                Direction direction = Direction.valueOf(key);
                sideTransferOut.put(direction, sideTag.getBoolean(key));
            } catch (IllegalArgumentException e) {
                UselessMod.LOGGER.warn("Invalid direction in NBT: {}", key);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        tag.put("Energy", energyStorage.serializeNBT());
        tag.put("InputFluid", inputFluidTank.writeToNBT(new CompoundTag()));
        tag.put("OutputFluid", outputFluidTank.writeToNBT(new CompoundTag()));
        tag.putInt("Process", process);
        tag.putInt("ProcessMax", processMax);
        tag.putBoolean("IsActive", isActive);
        tag.putBoolean("WasActive", wasActive);

        // 保存新的传输设置
        tag.putBoolean("TransferIn", transferIn);
        tag.putBoolean("TransferOut", transferOut);

        // 保存面设置
        CompoundTag sideTag = new CompoundTag();
        for (Map.Entry<Direction, Boolean> entry : sideTransferOut.entrySet()) {
            sideTag.putBoolean(entry.getKey().name(), entry.getValue());
        }
        tag.put("SideTransfer", sideTag);
    }

    // Capabilities - 修复方向处理
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return lazyEnergyHandler.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // 根据方向提供不同的流体处理能力
            if (side != null) {
                // 可以根据不同面提供不同的流体访问权限
                // 例如：顶部输入流体，底部输出流体，其他面不提供
                return lazyFluidHandler.cast();
            }
            return lazyFluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyEnergyHandler.invalidate();
        lazyItemHandler.invalidate();
        lazyFluidHandler.invalidate();
    }

    // 实现 ITransferControllable 接口
    @Override
    public boolean hasTransferIn() {
        return true; // 总是允许输入
    }

    @Override
    public boolean hasTransferOut() {
        return true; // 总是可以配置输出
    }

    @Override
    public boolean getTransferIn() {
        return transferIn;
    }

    @Override
    public boolean getTransferOut() {
        return transferOut;
    }

    @Override
    public void setControl(boolean input, boolean output) {
        this.transferIn = input;
        this.transferOut = output;
        setChanged();
        markForClientUpdate();
    }

    @Override
    public boolean getTransferOut(Direction direction) {
        return sideTransferOut.getOrDefault(direction, true);
    }

    @Override
    public void setTransferOut(Direction direction, boolean enabled) {
        sideTransferOut.put(direction, enabled);
        setChanged();
        markForClientUpdate();
    }

    // 自定义能量存储类
    private class CustomEnergyStorage implements IEnergyStorage {
        private int energy;
        private int capacity;
        private int maxReceive;
        private int maxExtract;

        public CustomEnergyStorage(int capacity, int maxReceive, int maxExtract) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
            this.maxExtract = maxExtract;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceive()) return 0;

            int energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
            if (!simulate) {
                energy += energyReceived;
                setChanged();
                markForClientUpdate();
            }
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtract()) return 0;

            int energyExtracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));
            if (!simulate) {
                energy -= energyExtracted;
                setChanged();
                markForClientUpdate();
            }
            return energyExtracted;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return maxExtract > 0;
        }

        @Override
        public boolean canReceive() {
            return maxReceive > 0;
        }

        public void modifyEnergy(int amount) {
            energy = Math.max(0, Math.min(capacity, energy + amount));
            setChanged();
            markForClientUpdate();
        }

        public void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(capacity, energy));
        }

        public void deserializeNBT(net.minecraft.nbt.Tag nbt) {
            if (nbt instanceof CompoundTag tag) {
                energy = tag.getInt("Energy");
                capacity = tag.getInt("Capacity");
                maxReceive = tag.getInt("MaxReceive");
                maxExtract = tag.getInt("MaxExtract");
            }
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Energy", energy);
            tag.putInt("Capacity", capacity);
            tag.putInt("MaxReceive", maxReceive);
            tag.putInt("MaxExtract", maxExtract);
            return tag;
        }
    }

    // 其他方法
    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inventory.setItem(i, itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.advanced_alloy_furnace_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AdvancedAlloyFurnaceMenu(containerId, playerInventory, this, data);
    }

    // 客户端数据同步
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}