package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.ModBlockEntities;
import com.sorrowmist.useless.common.inventory.MultiSlotItemHandler;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipeManager;
import com.sorrowmist.useless.registry.CatalystManager;
import com.sorrowmist.useless.registry.ModIngots;
import com.sorrowmist.useless.registry.ModMolds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider {

    // 能量系统
    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
    private final LazyOptional<IEnergyStorage> lazyEnergyHandler = LazyOptional.of(() -> energyStorage);

    // 状态管理
    private boolean isActive = false;
    private boolean wasActive = false;
    private int syncCounter = 0;

    // 处理进度
    private int process;
    private int processMax = 200;
    private int processTick = 10;

    // 并行数相关
    private int currentParallel = 1;
    private int maxParallel = 1;
    private boolean hasCatalyst = false;

    // 配方管理
    private AdvancedAlloyFurnaceRecipe currentRecipe;

    // 自动输出相关
    private int autoOutputCounter = 0;
    private static final int AUTO_OUTPUT_INTERVAL = 1; // 每1tick尝试输出一次

    // 新增槽位索引定义
    private static final int CATALYST_SLOT = 12;  // 无用锭催化槽
    private static final int MOLD_SLOT = 13;      // 金属模具槽

    // 总槽位数从12增加到14
    private static final int TOTAL_SLOTS = 14;

    // 容器数据同步 - 修改为并行数
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getEnergyStored();
                case 1 -> getMaxEnergyStored();
                case 2 -> getProgress();
                case 3 -> getMaxProgress();
                case 4 -> isActive() ? 1 : 0;
                case 5 -> getProcessTick();
                case 6 -> getCurrentParallel();  // 当前并行数
                case 7 -> getMaxParallel();      // 最大并行数
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergy(value);
                case 2 -> process = value;
                case 4 -> isActive = value == 1;
                case 5 -> processTick = value;
                case 6 -> currentParallel = value;
                case 7 -> maxParallel = value;
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    // 修复催化剂状态检测方法
    public boolean hasCatalyst() {
        // 检查是否有配方需要催化剂
        if (currentRecipe == null || !currentRecipe.requiresCatalyst()) {
            return false;
        }

        // 检查催化剂槽位是否有匹配的催化剂
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        if (catalyst.isEmpty()) {
            return false;
        }

        // 检查催化剂是否匹配配方要求
        return currentRecipe.getCatalyst().test(catalyst) && catalyst.getCount() >= currentRecipe.getCatalystCount();
    }


    // 修复：使用自定义的高堆叠物品处理器，不再使用匿名内部类
    private final MultiSlotItemHandler itemHandler;
    private final LazyOptional<IItemHandler> lazyItemHandler;

    // 修复流体槽
    private final FluidTank inputFluidTank = new FluidTank(Integer.MAX_VALUE) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForClientUpdate();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            boolean valid = level != null ?
                    AdvancedAlloyFurnaceRecipeManager.getInstance().isValidInputFluid(level, stack) : true;
            return valid;
        }
    };

    private final FluidTank outputFluidTank = new FluidTank(Integer.MAX_VALUE) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForClientUpdate();
        }
    };

    // 修复流体处理器
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
            if (resource.isEmpty() || !isFluidValid(0, resource)) {
                return 0;
            }

            int filled = inputFluidTank.fill(resource, action);
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

        // 初始化物品处理器，槽位数改为14
        this.itemHandler = new MultiSlotItemHandler(TOTAL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                // 检查是否为无用锭或模具
                boolean isUselessIngot = isUselessIngot(stack);
                boolean isMetalMold = isMetalMold(stack);

                if (slot < 6) {
                    // 输入槽（0-5）不能接受无用锭和模具
                    return !isUselessIngot && !isMetalMold;
                    // 输入槽可以接受其他有效输入物品
                } else if (slot >= 6 && slot < 12) {
                    // 输出槽（6-11）不能手动放置物品
                    return false;
                } else if (slot == CATALYST_SLOT) {
                    // 催化剂槽只能接受无用锭
                    return isUselessIngot(stack);
                } else if (slot == MOLD_SLOT) {
                    // 模具槽只能接受金属模具
                    return isMetalMold(stack);
                }
                return false;
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }
        };

        // 设置所有槽位支持高堆叠
        this.itemHandler.setAllSlotCapacity(Integer.MAX_VALUE);
        this.lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    // 数据同步管理
    private boolean needsClientUpdate = false;

    // 在tick方法中实时更新并行数
    public void tick() {
        if (level == null) return;

        boolean curActive = isActive;

        if (!level.isClientSide) {
            // 服务端逻辑

            // 实时更新并行数（无论是否激活）
            List<ItemStack> inputItems = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                inputItems.add(itemHandler.getStackInSlot(i));
            }
            FluidStack inputFluid = inputFluidTank.getFluid();
            ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
            updateParallelNumbers(catalyst, currentRecipe, inputItems, inputFluid);

            // 始终查找匹配的配方来更新并行数
            AdvancedAlloyFurnaceRecipe foundRecipe = findMatchingRecipe();
            updateParallelNumbers(catalyst, foundRecipe, inputItems, inputFluid);

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

            updateActiveState(curActive);

            // 自动输出逻辑
            autoOutputCounter++;
            if (autoOutputCounter >= AUTO_OUTPUT_INTERVAL) {
                tryAutoOutput();
                autoOutputCounter = 0;
            }

            // 数据同步
            if (needsClientUpdate || syncCounter++ >= 5) {
                syncToClient();
                needsClientUpdate = false;
                syncCounter = 0;
            }
        }
    }

    private void markForClientUpdate() {
        needsClientUpdate = true;
    }

    // 自动输出功能
    private void tryAutoOutput() {
        boolean outputtedAny = false;

        // 输出物品到周围容器
        outputtedAny |= tryOutputItems();

        // 输出流体到周围容器
        outputtedAny |= tryOutputFluids();

        if (outputtedAny) {
            setChanged();
            markForClientUpdate();
        }
    }

    private boolean tryOutputItems() {
        boolean outputtedAny = false;

        // 检查所有输出槽位（6-11）
        for (int slot = 6; slot < 12; slot++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) continue;

            ItemStack remainingStack = stackInSlot.copy();

            // 尝试输出到所有方向
            for (Direction direction : Direction.values()) {
                if (remainingStack.isEmpty()) break;

                BlockPos neighborPos = worldPosition.relative(direction);
                var neighborEntity = level.getBlockEntity(neighborPos);

                if (neighborEntity != null) {
                    var itemHandlerCap = neighborEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).resolve();
                    if (itemHandlerCap.isPresent()) {
                        IItemHandler neighborHandler = itemHandlerCap.get();

                        // 尝试插入到邻居容器
                        for (int neighborSlot = 0; neighborSlot < neighborHandler.getSlots(); neighborSlot++) {
                            if (remainingStack.isEmpty()) break;

                            // 模拟插入
                            ItemStack simulatedRemaining = neighborHandler.insertItem(neighborSlot, remainingStack.copy(), true);
                            if (simulatedRemaining.getCount() < remainingStack.getCount()) {
                                // 实际插入
                                ItemStack beforeInsert = remainingStack.copy();
                                remainingStack = neighborHandler.insertItem(neighborSlot, remainingStack, false);

                                if (remainingStack.getCount() < beforeInsert.getCount()) {
                                    outputtedAny = true;
                                }
                            }
                        }
                    }
                }
            }

            // 更新槽位内容
            if (remainingStack.getCount() != stackInSlot.getCount()) {
                if (remainingStack.isEmpty()) {
                    itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
                } else {
                    itemHandler.setStackInSlot(slot, remainingStack);
                }
            }
        }

        return outputtedAny;
    }

    private boolean tryOutputFluids() {
        FluidStack fluidInTank = outputFluidTank.getFluid();
        if (fluidInTank.isEmpty()) return false;

        boolean outputtedAny = false;
        FluidStack remainingFluid = fluidInTank.copy();

        // 尝试输出到所有方向
        for (Direction direction : Direction.values()) {
            if (remainingFluid.isEmpty()) break;

            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                var fluidHandlerCap = neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).resolve();
                if (fluidHandlerCap.isPresent()) {
                    IFluidHandler neighborHandler = fluidHandlerCap.get();

                    // 尝试填充到邻居容器
                    int filled = neighborHandler.fill(remainingFluid.copy(), IFluidHandler.FluidAction.SIMULATE);
                    if (filled > 0) {
                        // 实际填充
                        FluidStack fluidToDrain = new FluidStack(remainingFluid, filled);
                        int actuallyFilled = neighborHandler.fill(fluidToDrain, IFluidHandler.FluidAction.EXECUTE);

                        if (actuallyFilled > 0) {
                            // 从输出槽抽取相应数量的流体
                            outputFluidTank.drain(actuallyFilled, IFluidHandler.FluidAction.EXECUTE);
                            remainingFluid.shrink(actuallyFilled);
                            outputtedAny = true;
                        }
                    }
                }
            }
        }

        return outputtedAny;
    }

    // 配方处理逻辑
    protected boolean canProcessStart() {
        if (energyStorage.getEnergyStored() < processTick) {
            UselessMod.LOGGER.debug("Cannot start process: insufficient energy. Have: {}, Need: {}",
                    energyStorage.getEnergyStored(), processTick);
            return false;
        }

        currentRecipe = findMatchingRecipe();
        if (currentRecipe == null) {
            UselessMod.LOGGER.debug("Cannot start process: no matching recipe found");
            return false;
        }

        boolean canValidate = validateOutputs(currentRecipe, currentParallel);
        UselessMod.LOGGER.debug("Output validation result: {}", canValidate);

        return canValidate;
    }

    protected boolean canProcessFinish() {
        boolean canFinish = process <= 0;
        UselessMod.LOGGER.debug("Can process finish: {} (progress: {}/{})", canFinish, process, processMax);
        return canFinish;
    }

    protected void processStart() {
        if (currentRecipe != null) {
            processMax = currentRecipe.getProcessTime();
            processTick = Math.max(1, (currentRecipe.getEnergy() * currentParallel) / currentRecipe.getProcessTime());
            process = processMax;

            UselessMod.LOGGER.debug("Process started - Recipe: {}, Parallel: {}, ProcessTime: {}, Energy/t: {}",
                    currentRecipe.getId(), currentParallel, processMax, processTick);

            setChanged();
            markForClientUpdate();
        } else {
            UselessMod.LOGGER.error("processStart called but currentRecipe is null!");
        }
    }

    protected void processFinish() {
        if (currentRecipe == null) {
            UselessMod.LOGGER.error("processFinish called but currentRecipe is null!");
            processOff();
            return;
        }

        if (!validateInputs(currentRecipe)) {
            UselessMod.LOGGER.warn("Input validation failed in processFinish for recipe: {}", currentRecipe.getId());
            processOff();
            return;
        }

        UselessMod.LOGGER.debug("Processing recipe: {} with parallel: {}", currentRecipe.getId(), currentParallel);

        // 记录处理前的状态
        UselessMod.LOGGER.debug("Before processing - Input items: {}, Fluid: {}",
                getInputItemsDebugString(), inputFluidTank.getFluid().getAmount());
        UselessMod.LOGGER.debug("Before processing - Output items: {}, Fluid: {}",
                getOutputItemsDebugString(), outputFluidTank.getFluid().getAmount());

        resolveRecipe(currentRecipe);

        // 记录处理后的状态
        UselessMod.LOGGER.debug("After processing - Input items: {}, Fluid: {}",
                getInputItemsDebugString(), inputFluidTank.getFluid().getAmount());
        UselessMod.LOGGER.debug("After processing - Output items: {}, Fluid: {}",
                getOutputItemsDebugString(), outputFluidTank.getFluid().getAmount());

        setChanged();
        markForClientUpdate();
    }

    // 添加调试方法
    private String getInputItemsDebugString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                sb.append(stack.getItem()).append("x").append(stack.getCount()).append(" ");
            }
        }
        return sb.toString().isEmpty() ? "empty" : sb.toString();
    }

    private String getOutputItemsDebugString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 6; i < 12; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                sb.append(stack.getItem()).append("x").append(stack.getCount()).append(" ");
            }
        }
        return sb.toString().isEmpty() ? "empty" : sb.toString();
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
    // 修改配方查找方法，计算并行数
    // 修改配方查找方法，确保正确更新并行数
    private AdvancedAlloyFurnaceRecipe findMatchingRecipe() {
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }

        FluidStack inputFluid = inputFluidTank.getFluid();
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        ItemStack mold = itemHandler.getStackInSlot(MOLD_SLOT);

        AdvancedAlloyFurnaceRecipe recipe = AdvancedAlloyFurnaceRecipeManager.getInstance()
                .getRecipeWithCatalystOrMold(level, inputItems, inputFluid, catalyst, mold);

        // 更新当前配方引用
        currentRecipe = recipe;

        return recipe;
    }
    // 修复并行数更新逻辑
    // 修复并行数更新逻辑
    // 修改并行数更新方法，确保正确更新催化剂状态
    // 在 AdvancedAlloyFurnaceBlockEntity.java 的 updateParallelNumbers 方法中修改并行数计算
    private void updateParallelNumbers(ItemStack catalyst, AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputItems, FluidStack inputFluid) {
        // 检查配方是否允许催化剂
        boolean catalystAllowed = recipe != null && recipe.isCatalystAllowed();

        // 计算最大并行数（基于催化剂和黑名单）
        maxParallel = catalystAllowed ? CatalystManager.getCatalystParallel(catalyst) : 1;

        // 如果有配方，计算实际并行数
        if (recipe != null) {
            currentParallel = calculateActualParallel(recipe, inputItems, inputFluid, maxParallel);
        } else {
            currentParallel = 1;
        }

        // 更新催化剂状态（仅当配方允许催化剂时）
        if (recipe != null && recipe.requiresCatalyst() && catalystAllowed) {
            hasCatalyst = !catalyst.isEmpty() && recipe.getCatalyst().test(catalyst) && catalyst.getCount() >= recipe.getCatalystCount();
        } else {
            hasCatalyst = false;
        }

        // 标记需要客户端更新
        markForClientUpdate();
    }


    // 在 AdvancedAlloyFurnaceBlockEntity.java 中添加
    public AdvancedAlloyFurnaceRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    // 计算实际并行数
    private int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputItems,
                                        FluidStack inputFluid, int maxParallel) {
        if (maxParallel <= 1) {
            return 1;
        }

        // 计算输入物品支持的并行数
        int itemParallel = Integer.MAX_VALUE;
        for (int i = 0; i < recipe.getInputItems().size(); i++) {
            Ingredient ingredient = recipe.getInputItems().get(i);
            int requiredCount = recipe.getInputItemCounts().get(i);

            int availableCount = 0;
            for (ItemStack stack : inputItems) {
                if (ingredient.test(stack)) {
                    availableCount += stack.getCount();
                }
            }

            if (requiredCount > 0) {
                int possibleParallel = availableCount / requiredCount;
                itemParallel = Math.min(itemParallel, possibleParallel);
            }
        }

        // 计算输入流体支持的并行数
        int fluidParallel = Integer.MAX_VALUE;
        FluidStack requiredFluid = recipe.getInputFluid();
        if (!requiredFluid.isEmpty() && requiredFluid.getAmount() > 0) {
            if (inputFluid.getFluid().isSame(requiredFluid.getFluid())) {
                fluidParallel = inputFluid.getAmount() / requiredFluid.getAmount();
            } else {
                fluidParallel = 0;
            }
        }

        // 取最小并行数，但不超过最大并行数
        int actualParallel = Math.min(Math.min(itemParallel, fluidParallel), maxParallel);
        return Math.max(1, actualParallel);
    }

    private boolean validateInputs(AdvancedAlloyFurnaceRecipe recipe) {
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }

        FluidStack inputFluid = inputFluidTank.getFluid();
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        ItemStack mold = itemHandler.getStackInSlot(MOLD_SLOT);

        // 修改：使用新的匹配逻辑，使催化剂为可选项
        boolean matches = recipe.matches(inputItems, inputFluid, catalyst, mold);
        UselessMod.LOGGER.debug("Input validation for recipe {}: {}", recipe.getId(), matches);

        return matches;
    }

    private boolean validateOutputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 首先检查周围容器是否有空间（优先外部输出）
        if (hasExternalOutputSpace(recipe, parallel)) {
            return true;
        }

        // 如果没有外部空间，检查自身输出空间
        return hasInternalOutputSpace(recipe, parallel);
    }

    // 检查外部容器是否有输出空间
    private boolean hasExternalOutputSpace(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 检查输出物品在外部容器的空间
        for (ItemStack output : recipe.getOutputItems()) {
            if (!output.isEmpty()) {
                ItemStack testOutput = output.copy();
                testOutput.setCount(testOutput.getCount() * parallel);
                if (!canOutputItemToExternal(testOutput)) {
                    return false;
                }
            }
        }

        // 检查输出流体在外部容器的空间
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty()) {
            FluidStack testOutput = outputFluid.copy();
            testOutput.setAmount(testOutput.getAmount() * parallel);
            if (!canOutputFluidToExternal(testOutput)) {
                return false;
            }
        }

        return true;
    }

    // 检查自身输出空间
    private boolean hasInternalOutputSpace(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 检查输出物品空间 - 修改：不再检查堆叠限制，只检查是否有空槽位
        for (ItemStack output : recipe.getOutputItems()) {
            if (!output.isEmpty()) {
                ItemStack testOutput = output.copy();
                testOutput.setCount(testOutput.getCount() * parallel);
                boolean hasSpace = false;
                for (int i = 6; i < 12; i++) {
                    ItemStack slot = itemHandler.getStackInSlot(i);
                    // 修改：允许堆叠到已有相同物品的槽位，或者使用空槽位
                    if (slot.isEmpty() || (ItemStack.isSameItemSameTags(slot, testOutput) &&
                            slot.getCount() + testOutput.getCount() <= Integer.MAX_VALUE)) {
                        hasSpace = true;
                        break;
                    }
                }
                if (!hasSpace) return false;
            }
        }

        // 检查输出流体空间
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty()) {
            FluidStack testOutput = outputFluid.copy();
            testOutput.setAmount(testOutput.getAmount() * parallel);
            if (outputFluidTank.getSpace() < testOutput.getAmount()) {
                return false;
            }
        }

        return true;
    }

    // 检查物品是否能输出到外部容器
    private boolean canOutputItemToExternal(ItemStack output) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                var itemHandlerCap = neighborEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).resolve();
                if (itemHandlerCap.isPresent()) {
                    IItemHandler neighborHandler = itemHandlerCap.get();

                    for (int slot = 0; slot < neighborHandler.getSlots(); slot++) {
                        ItemStack simulatedRemaining = neighborHandler.insertItem(slot, output.copy(), true);
                        if (simulatedRemaining.getCount() < output.getCount()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // 检查流体是否能输出到外部容器
    private boolean canOutputFluidToExternal(FluidStack output) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                var fluidHandlerCap = neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).resolve();
                if (fluidHandlerCap.isPresent()) {
                    IFluidHandler neighborHandler = fluidHandlerCap.get();

                    int filled = neighborHandler.fill(output.copy(), IFluidHandler.FluidAction.SIMULATE);
                    if (filled > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 修复配方处理方法
    private void resolveRecipe(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null) {
            UselessMod.LOGGER.warn("resolveRecipe called with null recipe");
            return;
        }

        UselessMod.LOGGER.debug("Resolving recipe: {} with parallel: {}", recipe.getId(), currentParallel);

        // 创建输入物品的副本用于消耗计算
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i).copy());
        }

        FluidStack inputFluid = inputFluidTank.getFluid().copy();
        ItemStack catalystSlot = itemHandler.getStackInSlot(CATALYST_SLOT).copy();

        // 使用并行数消耗输入
        recipe.consumeInputs(inputItems, inputFluid, catalystSlot, currentParallel);

        // 更新实际的输入槽位
        for (int i = 0; i < 6; i++) {
            itemHandler.setStackInSlot(i, inputItems.get(i));
        }

        // 更新催化剂槽位（如果被消耗了）
        if (!catalystSlot.equals(itemHandler.getStackInSlot(CATALYST_SLOT))) {
            itemHandler.setStackInSlot(CATALYST_SLOT, catalystSlot);
        }

        // 注意：模具槽位的物品不会被消耗，保持不变

        // 更新输入流体
        inputFluidTank.setFluid(inputFluid);

        // 输出配方结果
        outputRecipeResults(recipe, currentParallel);

        UselessMod.LOGGER.debug("Recipe resolution completed");

        // 强制立即同步到客户端
        setChanged();
        syncToClient();
    }

    // 优先外部输出的配方结果处理方法
    private void outputRecipeResults(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 输出物品到外部容器，剩余部分放入自身
        for (ItemStack output : recipe.getOutputItems()) {
            if (!output.isEmpty()) {
                ItemStack outputStack = output.copy();
                outputStack.setCount(outputStack.getCount() * parallel);
                ItemStack remaining = tryOutputItemToExternal(outputStack);
                if (!remaining.isEmpty()) {
                    addOutputItem(remaining);
                }
            }
        }

        // 输出流体到外部容器，剩余部分放入自身
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty()) {
            FluidStack outputStack = outputFluid.copy();
            outputStack.setAmount(outputStack.getAmount() * parallel);
            FluidStack remaining = tryOutputFluidToExternal(outputStack);
            if (!remaining.isEmpty()) {
                outputFluidTank.fill(remaining, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    // 尝试将物品输出到外部容器
    private ItemStack tryOutputItemToExternal(ItemStack output) {
        ItemStack remaining = output.copy();

        for (Direction direction : Direction.values()) {
            if (remaining.isEmpty()) break;

            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                var itemHandlerCap = neighborEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).resolve();
                if (itemHandlerCap.isPresent()) {
                    IItemHandler neighborHandler = itemHandlerCap.get();

                    for (int slot = 0; slot < neighborHandler.getSlots(); slot++) {
                        if (remaining.isEmpty()) break;

                        ItemStack simulatedRemaining = neighborHandler.insertItem(slot, remaining.copy(), true);
                        if (simulatedRemaining.getCount() < remaining.getCount()) {
                            ItemStack beforeInsert = remaining.copy();
                            remaining = neighborHandler.insertItem(slot, remaining, false);
                        }
                    }
                }
            }
        }

        return remaining;
    }

    // 尝试将流体输出到外部容器
    private FluidStack tryOutputFluidToExternal(FluidStack output) {
        FluidStack remaining = output.copy();

        for (Direction direction : Direction.values()) {
            if (remaining.isEmpty()) break;

            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                var fluidHandlerCap = neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).resolve();
                if (fluidHandlerCap.isPresent()) {
                    IFluidHandler neighborHandler = fluidHandlerCap.get();

                    int filled = neighborHandler.fill(remaining.copy(), IFluidHandler.FluidAction.SIMULATE);
                    if (filled > 0) {
                        FluidStack fluidToDrain = new FluidStack(remaining, filled);
                        int actuallyFilled = neighborHandler.fill(fluidToDrain, IFluidHandler.FluidAction.EXECUTE);

                        if (actuallyFilled > 0) {
                            remaining.shrink(actuallyFilled);
                        }
                    }
                }
            }
        }

        return remaining;
    }

    // 清空输入流体
    public void clearInputFluid() {
        inputFluidTank.setFluid(FluidStack.EMPTY);
        setChanged();
        syncToClient();
    }

    // 清空输出流体
    public void clearOutputFluid() {
        outputFluidTank.setFluid(FluidStack.EMPTY);
        setChanged();
        syncToClient();
    }

    // 修改：改进的输出物品添加方法，支持高堆叠且不拆分到多个槽位
    private void addOutputItem(ItemStack output) {
        ItemStack outputStack = output.copy();

        // 首先尝试堆叠到已有相同物品的槽位
        for (int i = 6; i < 12; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(slotStack, outputStack)) {
                // 直接堆叠到已有槽位，不检查堆叠限制（因为支持高堆叠）
                slotStack.grow(outputStack.getCount());
                itemHandler.setStackInSlot(i, slotStack);
                return; // 成功堆叠，直接返回
            }
        }

        // 如果没有相同物品，则放入第一个空槽位
        for (int i = 6; i < 12; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                itemHandler.setStackInSlot(i, outputStack.copy());
                return; // 成功放入空槽位，直接返回
            }
        }

        // 如果所有输出槽位都被不同物品占满，记录警告
        UselessMod.LOGGER.warn("No space for output item in advanced alloy furnace: {} x {}",
                outputStack.getDisplayName().getString(), outputStack.getCount());
    }

    // 修复的流体交互方法
    public boolean interactWithFluid(Player player, ItemStack stack, boolean isInputTank, boolean isFill) {
        if (level == null || level.isClientSide) return false;

        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(itemHandler -> {
            return getCapability(ForgeCapabilities.FLUID_HANDLER).map(blockHandler -> {
                boolean success = false;

                if (isFill) {
                    // 向机器填充流体 - 只允许输入槽
                    if (isInputTank) {
                        FluidStack drained = itemHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty()) {
                            int filled = blockHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE);
                            if (filled > 0) {
                                FluidStack actualDrained = itemHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                                blockHandler.fill(actualDrained, IFluidHandler.FluidAction.EXECUTE);
                                success = true;
                            }
                        }
                    }
                } else {
                    // 从机器抽取流体 - 只允许输出槽
                    if (!isInputTank) {
                        FluidStack drained = blockHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty()) {
                            int filled = itemHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE);
                            if (filled > 0) {
                                FluidStack actualDrained = blockHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                                itemHandler.fill(actualDrained, IFluidHandler.FluidAction.EXECUTE);
                                success = true;
                            }
                        }
                    }
                }

                if (success) {
                    setChanged();
                    syncToClient();
                    // 更新玩家手持物品
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.containerMenu.setCarried(stack);
                    }
                }

                return success;
            }).orElse(false);
        }).orElse(false);
    }

    // 添加便捷方法供UI使用
    public boolean fillInputTank(Player player, ItemStack container) {
        return interactWithFluid(player, container, true, true);
    }

    public boolean drainOutputTank(Player player, ItemStack container) {
        return interactWithFluid(player, container, false, false);
    }

    // 状态管理
    private void updateActiveState(boolean prevActive) {
        if (!isActive && prevActive) {
            wasActive = true;
            if (level != null) {
                // 更新方块状态为不活动
                BlockState currentState = getBlockState();
                if (currentState.hasProperty(AdvancedAlloyFurnaceBlock.ACTIVE) && currentState.getValue(AdvancedAlloyFurnaceBlock.ACTIVE)) {
                    level.setBlockAndUpdate(worldPosition, currentState.setValue(AdvancedAlloyFurnaceBlock.ACTIVE, false));
                }
            }
            return;
        }

        if (prevActive != isActive || (wasActive && syncCounter % 40 == 0)) {
            wasActive = false;
            if (level != null) {
                // 更新方块状态
                BlockState currentState = getBlockState();
                if (currentState.hasProperty(AdvancedAlloyFurnaceBlock.ACTIVE)) {
                    level.setBlockAndUpdate(worldPosition, currentState.setValue(AdvancedAlloyFurnaceBlock.ACTIVE, isActive));
                }
            }
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

    // 新增：获取并行数
    public int getCurrentParallel() {
        return currentParallel;
    }

    public int getMaxParallel() {
        return maxParallel;
    }

    // 新增：检查是否为无用锭
    public boolean isUselessIngot(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 检查物品是否在无用锭列表中
        return stack.is(ModIngots.USELESS_INGOT_TIER_1.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_2.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_3.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_4.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_5.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_6.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_7.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_8.get()) ||
                stack.is(ModIngots.USELESS_INGOT_TIER_9.get());
    }

    // 新增：检查是否为金属模具
    public boolean isMetalMold(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 检查物品是否在金属模具列表中
        return stack.is(ModMolds.METAL_MOLD_PLATE.get()) ||
                stack.is(ModMolds.METAL_MOLD_ROD.get()) ||
                stack.is(ModMolds.METAL_MOLD_GEAR.get()) ||
                stack.is(ModMolds.METAL_MOLD_WIRE.get());
    }

    // 公共访问方法
    public MultiSlotItemHandler getItemHandler() {
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
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }
        energyStorage.deserializeNBT(tag.get("Energy"));
        inputFluidTank.readFromNBT(tag.getCompound("InputFluid"));
        outputFluidTank.readFromNBT(tag.getCompound("OutputFluid"));
        process = tag.getInt("Process");
        processMax = tag.getInt("ProcessMax");
        processTick = tag.getInt("ProcessTick");
        isActive = tag.getBoolean("IsActive");
        wasActive = tag.getBoolean("WasActive");
        autoOutputCounter = tag.getInt("AutoOutputCounter");
        // 新增：加载并行数
        currentParallel = tag.getInt("CurrentParallel");
        maxParallel = tag.getInt("MaxParallel");
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
        tag.putInt("ProcessTick", processTick);
        tag.putBoolean("IsActive", isActive);
        tag.putBoolean("WasActive", wasActive);
        tag.putInt("AutoOutputCounter", autoOutputCounter);
        // 新增：保存并行数
        tag.putInt("CurrentParallel", currentParallel);
        tag.putInt("MaxParallel", maxParallel);
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