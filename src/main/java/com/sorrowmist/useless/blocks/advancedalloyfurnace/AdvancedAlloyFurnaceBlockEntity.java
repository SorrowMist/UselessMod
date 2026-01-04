package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.ModBlockEntities;
import com.sorrowmist.useless.common.inventory.MultiSlotItemHandler;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.recipes.advancedalloyfurnace.AdvancedAlloyFurnaceRecipeManager;
import com.sorrowmist.useless.utils.MoldIdentifier;
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
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// AE2 Integration
import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.capabilities.Capabilities;

public class AdvancedAlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider, IGridNodeListener<AdvancedAlloyFurnaceBlockEntity>, appeng.api.networking.IInWorldGridNodeHost, appeng.api.networking.security.IActionHost {

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
    private boolean requiresMold = false;

    // 配方管理
    private AdvancedAlloyFurnaceRecipe currentRecipe;
    
    // 输入缓存，用于检测输入变化
    private List<ItemStack> lastInputItems = new ArrayList<>();
    private FluidStack lastInputFluid = FluidStack.EMPTY;
    private ItemStack lastCatalyst = ItemStack.EMPTY;
    private ItemStack lastMold = ItemStack.EMPTY;

    // 自动输出相关
    private int autoOutputCounter = 0;
    private static final int AUTO_OUTPUT_INTERVAL = 20; // 每20tick尝试输出一次，主动检测输出槽
    
    // 输出暂存相关
    private List<ItemStack> pendingOutputs = new ArrayList<>();
    private boolean isOutputPending = false;

    // 新增槽位索引定义
    private static final int CATALYST_SLOT = 12;  // 无用锭催化槽
    private static final int MOLD_SLOT = 13;      // 金属模具槽

    // 总槽位数从12增加到14
    private static final int TOTAL_SLOTS = 14;
    
    // AE2 Integration
    private final IManagedGridNode gridNode;
    private final IActionSource actionSource;
    private boolean isConnectedToAE = false;

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
                case 8 -> requiresMold ? 1 : 0;   // 是否需要模具
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
                case 8 -> requiresMold = value == 1;
            }
        }

        @Override
        public int getCount() {
            return 9;
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
            // 输入流体变化时，触发检测
            triggerInputChangeDetection();
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
                boolean isUselessIngot = MoldIdentifier.isUselessIngot(stack);
                boolean isMetalMold = MoldIdentifier.isMetalMold(stack);

                if (slot < 6) {
                    // 输入槽（0-5）可以接受无用锭，但不能接受模具
                    return !isMetalMold;
                    // 输入槽可以接受其他有效输入物品
                } else if (slot >= 6 && slot < 12) {
                    // 输出槽（6-11）不能手动放置物品
                    return false;
                } else if (slot == CATALYST_SLOT) {
                    // 催化剂槽只能接受无用锭
                    return MoldIdentifier.isUselessIngot(stack);
                } else if (slot == MOLD_SLOT) {
                    // 模具槽可以接受金属模具或其他模组的机器标志物
                    return MoldIdentifier.isAcceptableMarker(stack);
                }
                return false;
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (!simulate) {
                    // 记录插入前的状态
                    ItemStack oldStack = getStackInSlot(slot);
                    ItemStack result = super.insertItem(slot, stack, simulate);
                    // 检查是否有变化
                    if (!ItemStack.matches(oldStack, getStackInSlot(slot))) {
                        onContentsChanged(slot);
                    }
                    return result;
                }
                return super.insertItem(slot, stack, simulate);
            }

            @Nonnull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!simulate) {
                    // 记录提取前的状态
                    ItemStack oldStack = getStackInSlot(slot);
                    ItemStack result = super.extractItem(slot, amount, simulate);
                    // 检查是否有变化
                    if (!ItemStack.matches(oldStack, getStackInSlot(slot))) {
                        onContentsChanged(slot);
                    }
                    return result;
                }
                return super.extractItem(slot, amount, simulate);
            }

            @Override
            public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
                // 记录设置前的状态
                ItemStack oldStack = getStackInSlot(slot);
                super.setStackInSlot(slot, stack);
                // 检查是否有变化
                if (!ItemStack.matches(oldStack, stack)) {
                    onContentsChanged(slot);
                }
            }

            // 物品变化回调
            protected void onContentsChanged(int slot) {
                setChanged();
                markForClientUpdate();
                
                // 检查是否为输入槽、催化剂槽或模具槽
                if (slot < 6 || slot == CATALYST_SLOT || slot == MOLD_SLOT) {
                    // 输入物品、催化剂或模具变化时，触发检测
                    triggerInputChangeDetection();
                } else if (slot >= 6 && slot < 12) {
                    // 输出槽变化且有物品时，触发自动输出
                    ItemStack stack = getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        tryAutoOutput();
                    }
                }
            }
        };

        // 设置所有槽位支持高堆叠
        this.itemHandler.setAllSlotCapacity(Integer.MAX_VALUE);
        this.lazyItemHandler = LazyOptional.of(() -> itemHandler);
        
        // AE2 Integration
        // 创建动作源
        this.actionSource = appeng.api.networking.security.IActionSource.ofMachine(this);
        
        // 创建网格节点
        this.gridNode = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setTagName("node"); // 设置为in-world节点并添加标签
    }
    
    // IGridNodeListener implementation
    @Override
    public void onSaveChanges(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        // 当需要保存节点变化时的处理
        setChanged();
    }
    
    @Override
    public void onGridChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node) {
        // 当网格变化时，更新连接状态
        isConnectedToAE = node.isActive();
        setChanged();
        markForClientUpdate();
    }
    
    @Override
    public void onStateChanged(AdvancedAlloyFurnaceBlockEntity nodeOwner, IGridNode node, State state) {
        // 节点状态变化时的处理
        isConnectedToAE = node.isActive();
        setChanged();
        markForClientUpdate();
    }

    // 数据同步管理
    private boolean needsClientUpdate = false;
    
    // AE2 Integration - 生命周期管理
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        // 当方块实体不再被移除时，创建网格节点
        GridHelper.onFirstTick(this, (be) -> {
            be.gridNode.create(getLevel(), getBlockPos());
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
        // 区块卸载时销毁节点
        this.gridNode.destroy();
    }
    
    @Override
    public void setRemoved() {
        super.setRemoved();
        // 方块被移除时销毁节点
        this.gridNode.destroy();
    }

    // 在tick方法中实时更新并行数
    public void tick() {
        if (level == null) return;

        boolean curActive = isActive;

        if (!level.isClientSide) {
            // 服务端逻辑
            
            // 减少数据同步频率
            syncCounter++;
            boolean shouldSync = false;

            // 处理暂存输出
            if (isOutputPending) {
                processPendingOutputs();
            }
            
            // 主动检测输出槽并尝试自动输出 - 每20tick执行一次
            autoOutputCounter++;
            if (autoOutputCounter >= AUTO_OUTPUT_INTERVAL) {
                // 检查输出槽是否有物品或流体，如果有则尝试自动输出
                boolean hasOutputItems = false;
                for (int slot = 6; slot < 12; slot++) {
                    if (!itemHandler.getStackInSlot(slot).isEmpty()) {
                        hasOutputItems = true;
                        break;
                    }
                }
                boolean hasOutputFluid = !outputFluidTank.isEmpty();
                
                if (hasOutputItems || hasOutputFluid) {
                    tryAutoOutput();
                }
                autoOutputCounter = 0; // 重置计数器
            }

            // 只在非活跃状态下检查是否可以开始合成，活跃状态下不需要重复检查
            if (!isActive) {
                if (canProcessStart()) {
                    processStart();
                    isActive = true;
                    shouldSync = true;
                }
            } else {
                // 检查输入是否仍然满足配方要求，如果不满足则停止合成
                if (currentRecipe == null || !validateInputs(currentRecipe)) {
                    processOff();
                    shouldSync = true;
                    // 配方变为无效时，更新催化剂和模具状态
                    triggerInputChangeDetection();
                } else {
                    processTick();
                    if (process <= 0) {
                        processFinish();
                        if (!canProcessStart()) {
                            processOff();
                        } else {
                            processStart();
                        }
                        shouldSync = true;
                    } else if (energyStorage.getEnergyStored() < processTick) {
                        processOff();
                        shouldSync = true;
                    }
                }
            }

            // 只有状态变化时才更新激活状态
            if (curActive != isActive) {
                updateActiveState(curActive);
            }

            // 数据同步（减少频率）
            if (needsClientUpdate || shouldSync || syncCounter >= 20) {
                syncToClient();
                needsClientUpdate = false;
                syncCounter = 0;
            }
        }
    }

    private void markForClientUpdate() {
        needsClientUpdate = true;
    }
    
    // 触发输入变化检测
    private void triggerInputChangeDetection() {
        // 获取输入物品和流体
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }
        FluidStack inputFluid = inputFluidTank.getFluid();
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        ItemStack mold = itemHandler.getStackInSlot(MOLD_SLOT);
        
        // 查找匹配的配方
        AdvancedAlloyFurnaceRecipe foundRecipe = findMatchingRecipe();
        
        // 如果当前配方无效，使用null作为recipe参数
        if (foundRecipe != null && !validateInputs(foundRecipe)) {
            foundRecipe = null;
            // 直接更新当前配方为null
            currentRecipe = null;
        }
        
        // 更新并行数
        updateParallelNumbers(catalyst, foundRecipe, inputItems, inputFluid);
        
        // 更新需要模具的状态
        requiresMold = foundRecipe != null && foundRecipe.requiresMold();
        
        // 标记需要同步
        markForClientUpdate();
        
        // 更新输入缓存
        lastInputItems = new ArrayList<>(inputItems);
        lastInputFluid = inputFluid.copy();
        lastCatalyst = catalyst.copy();
        lastMold = mold.copy();
    }
    
    // 检查输入是否发生变化
    private boolean isInputChanged(List<ItemStack> inputItems, FluidStack inputFluid, ItemStack catalyst, ItemStack mold) {
        // 快速检查：如果输入数量变化，直接返回true
        if (inputItems.size() != lastInputItems.size()) {
            return true;
        }
        
        // 检查输入物品是否变化，只比较非空物品
        boolean hasItemChange = false;
        for (int i = 0; i < inputItems.size(); i++) {
            ItemStack current = inputItems.get(i);
            ItemStack last = lastInputItems.get(i);
            
            // 只有当物品非空时才比较，或者一个为空一个非空时
            if ((!current.isEmpty() || !last.isEmpty()) && 
                (!ItemStack.matches(current, last) || current.getCount() != last.getCount())) {
                hasItemChange = true;
                break;
            }
        }
        
        if (hasItemChange) {
            return true;
        }
        
        // 检查输入流体是否变化，只比较非空流体
        if ((!inputFluid.isEmpty() || !lastInputFluid.isEmpty()) && 
            (!inputFluid.isFluidEqual(lastInputFluid) || inputFluid.getAmount() != lastInputFluid.getAmount())) {
            return true;
        }
        
        // 检查催化剂是否变化，只比较非空催化剂
        if ((!catalyst.isEmpty() || !lastCatalyst.isEmpty()) && 
            (!ItemStack.matches(catalyst, lastCatalyst) || catalyst.getCount() != lastCatalyst.getCount())) {
            return true;
        }
        
        // 检查模具是否变化，只比较非空模具
        if ((!mold.isEmpty() || !lastMold.isEmpty()) && 
            (!ItemStack.matches(mold, lastMold) || mold.getCount() != lastMold.getCount())) {
            return true;
        }
        
        return false;
    }

    // 自动输出功能
    private void tryAutoOutput() {
        boolean outputtedAny = false;

        // 优先输出物品到AE网络
        outputtedAny |= tryOutputItemsToAE();
        
        // 然后输出流体到AE网络
        outputtedAny |= tryOutputFluidsToAE();
        
        // 然后输出物品到周围容器
        outputtedAny |= tryOutputItems();

        // 然后输出流体到周围容器
        outputtedAny |= tryOutputFluids();

        if (outputtedAny) {
            setChanged();
            markForClientUpdate();
        }
    }
    
    // AE2 Integration - 输出物品到AE网络
    private boolean tryOutputItemsToAE() {
        boolean outputtedAny = false;
        
        if (!isConnectedToAE) {
            return false;
        }

        // 检查所有输出槽位（6-11）
        for (int slot = 6; slot < 12; slot++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) continue;

            // 尝试输出到AE网络
            long inserted = tryOutputToAE(stackInSlot);
            if (inserted > 0) {
                // 更新槽位内容
                if (inserted >= stackInSlot.getCount()) {
                    itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
                } else {
                    ItemStack remainingStack = stackInSlot.copy();
                    remainingStack.setCount((int) (stackInSlot.getCount() - inserted));
                    itemHandler.setStackInSlot(slot, remainingStack);
                }
                outputtedAny = true;
            }
        }

        return outputtedAny;
    }
    
    // AE2 Integration - 输出流体到AE网络
    private boolean tryOutputFluidsToAE() {
        if (!isConnectedToAE) {
            return false;
        }
        
        FluidStack fluidInTank = outputFluidTank.getFluid();
        if (fluidInTank.isEmpty()) return false;

        // 尝试输出到AE网络
        long inserted = tryOutputFluidToAE(fluidInTank);
        if (inserted > 0) {
            // 从输出槽抽取相应数量的流体
            outputFluidTank.drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }

        return false;
    }

    private boolean tryOutputItems() {
        boolean outputtedAny = false;

        // 检查所有输出槽位（6-11）
        for (int slot = 6; slot < 12; slot++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty()) continue;

            ItemStack remainingStack = stackInSlot.copy();
            boolean slotOutputted = false;

            // 恢复全向输出
            for (Direction direction : Direction.values()) {
                if (remainingStack.isEmpty()) break;

                BlockPos neighborPos = worldPosition.relative(direction);
                var neighborEntity = level.getBlockEntity(neighborPos);

                if (neighborEntity != null) {
                    // 检查目标方块是否在黑名单中（暂时只包含自身方块）
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (neighborState.getBlock() instanceof AdvancedAlloyFurnaceBlock) {
                        continue; // 跳过自身方块
                    }
                    
                    // 只获取一次能力，避免重复调用
                    var itemHandlerOpt = neighborEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite());
                    if (itemHandlerOpt.isPresent()) {
                        IItemHandler neighborHandler = itemHandlerOpt.resolve().get();

                        // 尝试插入到邻居容器
                        for (int neighborSlot = 0; neighborSlot < neighborHandler.getSlots(); neighborSlot++) {
                            if (remainingStack.isEmpty()) break;

                            // 模拟插入
                            ItemStack simulatedRemaining = neighborHandler.insertItem(neighborSlot, remainingStack.copy(), true);
                            if (simulatedRemaining.getCount() < remainingStack.getCount()) {
                                // 实际插入
                                remainingStack = neighborHandler.insertItem(neighborSlot, remainingStack, false);
                                slotOutputted = true;
                            }
                        }
                    }
                }
            }

            // 更新槽位内容
            if (slotOutputted) {
                if (remainingStack.isEmpty()) {
                    itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
                } else {
                    itemHandler.setStackInSlot(slot, remainingStack);
                }
                outputtedAny = true;
            }
        }

        return outputtedAny;
    }

    private boolean tryOutputFluids() {
        FluidStack fluidInTank = outputFluidTank.getFluid();
        if (fluidInTank.isEmpty()) return false;

        boolean outputtedAny = false;
        FluidStack remainingFluid = fluidInTank.copy();

        // 恢复全向输出
        for (Direction direction : Direction.values()) {
            if (remainingFluid.isEmpty()) break;

            BlockPos neighborPos = worldPosition.relative(direction);
            var neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                // 检查目标方块是否在黑名单中（暂时只包含自身方块）
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof AdvancedAlloyFurnaceBlock) {
                    continue; // 跳过自身方块
                }
                
                // 只获取一次能力，避免重复调用
                var fluidHandlerOpt = neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite());
                if (fluidHandlerOpt.isPresent()) {
                    IFluidHandler neighborHandler = fluidHandlerOpt.resolve().get();

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
        // 如果有暂存输出，不允许开始新的合成
        if (isOutputPending) {
            return false;
        }
        
        if (energyStorage.getEnergyStored() < processTick) {
            return false;
        }

        // 获取当前输入状态
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i));
        }
        FluidStack inputFluid = inputFluidTank.getFluid();
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        ItemStack mold = itemHandler.getStackInSlot(MOLD_SLOT);

        // 重新查询配方，确保使用最新的输入状态
        currentRecipe = findMatchingRecipe();
        if (currentRecipe == null) {
            return false;
        }

        // 更新并行数，确保使用最新的输入和催化剂状态
        updateParallelNumbers(catalyst, currentRecipe, inputItems, inputFluid);
        
        // 更新需要模具的状态
        requiresMold = currentRecipe != null && currentRecipe.requiresMold();

        // 检查输入是否满足要求
        if (!validateInputs(currentRecipe)) {
            return false;
        }

        // 检查输出空间
        boolean canValidate = validateOutputs(currentRecipe, currentParallel);
        return canValidate;
    }

    protected boolean canProcessFinish() {
        return process <= 0;
    }

    protected void processStart() {
        if (currentRecipe != null) {
            processMax = currentRecipe.getProcessTime();
            
            // 检查催化剂是否为有用的锭（USEFUL_INGOT），如果是，能量消耗不会被倍增
            ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
            String catalystId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(catalyst.getItem()).toString();
            boolean isUsefulIngot = catalystId.equals("useless_mod:useful_ingot");
            
            // 计算能量消耗：如果是有用的锭，能量消耗不乘以并行数；否则正常计算
            int energyToUse = isUsefulIngot ? currentRecipe.getEnergy() : currentRecipe.getEnergy() * currentParallel;
            processTick = Math.max(1, energyToUse / currentRecipe.getProcessTime());
            
            process = processMax;

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

        resolveRecipe(currentRecipe);

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
        int newMaxParallel = catalystAllowed ? CatalystManager.getCatalystParallel(catalyst) : 1;

        // 如果有配方，计算实际并行数
        int newCurrentParallel;
        if (recipe != null) {
            newCurrentParallel = calculateActualParallel(recipe, inputItems, inputFluid, newMaxParallel);
        } else {
            newCurrentParallel = 1;
        }

        // 更新催化剂状态（仅当配方允许催化剂时）
        boolean newHasCatalyst;
        if (recipe != null && recipe.requiresCatalyst() && catalystAllowed) {
            newHasCatalyst = !catalyst.isEmpty() && recipe.getCatalyst().test(catalyst) && catalyst.getCount() >= recipe.getCatalystCount();
        } else {
            newHasCatalyst = false;
        }

        // 更新需要模具的状态
        boolean newRequiresMold = recipe != null && recipe.requiresMold();

        // 仅当值变化时才更新并标记需要同步
        boolean changed = false;
        if (newMaxParallel != maxParallel) {
            maxParallel = newMaxParallel;
            changed = true;
        }
        if (newCurrentParallel != currentParallel) {
            currentParallel = newCurrentParallel;
            changed = true;
        }
        if (newHasCatalyst != hasCatalyst) {
            hasCatalyst = newHasCatalyst;
            changed = true;
        }
        if (newRequiresMold != requiresMold) {
            requiresMold = newRequiresMold;
            changed = true;
        }
        
        if (changed) {
            markForClientUpdate();
        }
    }


    // 在 AdvancedAlloyFurnaceBlockEntity.java 中添加
    public AdvancedAlloyFurnaceRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    // 计算实际并行数（优化版本）
    private int calculateActualParallel(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> inputItems, 
                                        FluidStack inputFluid, int maxParallel) {
        if (maxParallel <= 1) {
            return 1;
        }

        // 计算输入物品支持的并行数
        int itemParallel = Integer.MAX_VALUE;
        List<Ingredient> inputIngredients = recipe.getInputItems();
        List<Long> inputCounts = recipe.getInputItemCounts();
        
        // 提前检查是否有输入物品
        boolean hasItems = false;
        for (ItemStack stack : inputItems) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }
        
        if (hasItems) {
            for (int i = 0; i < inputIngredients.size(); i++) {
                Ingredient ingredient = inputIngredients.get(i);
                long requiredCount = inputCounts.get(i);

                if (requiredCount <= 0) {
                    continue;
                }

                // 使用long类型存储可用数量，避免整数溢出
                long availableCount = 0;
                for (ItemStack stack : inputItems) {
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        availableCount += stack.getCount();
                    }
                }

                // 计算可能的并行数，确保结果不会超过Integer.MAX_VALUE
                long possibleParallelLong = availableCount / requiredCount;
                int possibleParallel = (int) Math.min(possibleParallelLong, Integer.MAX_VALUE);
                itemParallel = Math.min(itemParallel, possibleParallel);
                
                // 如果已经是0，提前返回
                if (itemParallel == 0) {
                    return 1;
                }
            }
        } else {
            itemParallel = 0;
        }

        // 计算输入流体支持的并行数
        int fluidParallel = Integer.MAX_VALUE;
        FluidStack requiredFluid = recipe.getInputFluid();
        if (!requiredFluid.isEmpty() && requiredFluid.getAmount() > 0) {
            if (!inputFluid.isEmpty() && inputFluid.getFluid().isSame(requiredFluid.getFluid())) {
                // 使用long类型计算流体并行数，避免整数溢出
                long fluidAvailable = inputFluid.getAmount();
                long fluidRequired = requiredFluid.getAmount();
                long fluidParallelLong = fluidAvailable / fluidRequired;
                fluidParallel = (int) Math.min(fluidParallelLong, Integer.MAX_VALUE);
            } else {
                fluidParallel = 0;
            }
        }

        // 取最小并行数，但不超过最大并行数
        int actualParallel = Math.min(Math.min(itemParallel, fluidParallel), maxParallel);
        return Math.max(1, actualParallel);
    }

    // 用于验证输入的复用列表，避免频繁创建ArrayList
    private final List<ItemStack> validationInputItems = new ArrayList<>(6);
    
    // AE2 Integration - 获取存储服务
    private MEStorage getStorageService() {
        if (!isConnectedToAE) {
            return null;
        }
        
        IGridNode node = this.gridNode.getNode();
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
    
    // AE2 Integration - 获取能量源
    private IEnergySource getEnergySource() {
        if (!isConnectedToAE) {
            return null;
        }
        
        IGridNode node = this.gridNode.getNode();
        if (node == null || !node.isActive()) {
            return null;
        }
        
        IGrid grid = node.getGrid();
        if (grid == null) {
            return null;
        }
        
        return grid.getEnergyService();
    }
    
    private boolean validateInputs(AdvancedAlloyFurnaceRecipe recipe) {
        // 清空复用列表并重新填充，避免创建新的ArrayList
        validationInputItems.clear();
        for (int i = 0; i < 6; i++) {
            validationInputItems.add(itemHandler.getStackInSlot(i));
        }

        FluidStack inputFluid = inputFluidTank.getFluid();
        ItemStack catalyst = itemHandler.getStackInSlot(CATALYST_SLOT);
        ItemStack mold = itemHandler.getStackInSlot(MOLD_SLOT);

        // 修改：使用新的匹配逻辑，使催化剂为可选项
        return recipe.matches(validationInputItems, inputFluid, catalyst, mold);
    }

    private boolean validateOutputs(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 直接检查自身输出空间，不再优先检查外部容器
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

        // 保存当前并行数到局部变量，避免后续更新槽位时被修改
        int parallel = currentParallel;

        // 创建输入物品的副本用于消耗计算
        List<ItemStack> inputItems = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inputItems.add(itemHandler.getStackInSlot(i).copy());
        }

        FluidStack inputFluid = inputFluidTank.getFluid().copy();
        ItemStack catalystSlot = itemHandler.getStackInSlot(CATALYST_SLOT).copy();

        // 使用并行数消耗输入
        recipe.consumeInputs(inputItems, inputFluid, catalystSlot, parallel);

        // 先输出配方结果，使用保存的并行数
        outputRecipeResults(recipe, parallel);

        // 然后更新实际的输入槽位
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

        // 强制立即同步到客户端
        setChanged();
        syncToClient();
    }

    // 直接输出到自身容器的配方结果处理方法
    private void outputRecipeResults(AdvancedAlloyFurnaceRecipe recipe, int parallel) {
        // 直接输出物品到AE网络（如果连接），否则输出到自身容器
        for (ItemStack output : recipe.getOutputItems()) {
            if (!output.isEmpty()) {
                // 使用long类型计算总输出数量，避免整数溢出
                long totalOutputCount = (long) output.getCount() * parallel;
                
                // 如果总输出数量超过Integer.MAX_VALUE，分多次创建ItemStack
                ItemStack baseOutputStack = output.copy();
                long remainingCount = totalOutputCount;
                
                while (remainingCount > 0) {
                    ItemStack outputStack = baseOutputStack.copy();
                    // 每次输出不超过Integer.MAX_VALUE
                    int stackCount = (int) Math.min(remainingCount, Integer.MAX_VALUE);
                    outputStack.setCount(stackCount);
                    
                    // 尝试优先输出到AE网络
                    long inserted = tryOutputToAE(outputStack);
                    long actualInserted = Math.min(inserted, remainingCount);
                    remainingCount -= actualInserted;
                    
                    // 如果AE网络没存下，输出到自身容器
                    if (remainingCount > 0) {
                        ItemStack remainingStack = outputStack.copy();
                        remainingStack.setCount((int) remainingCount);
                        
                        // 检查pendingOutputs列表中是否已经有相同的物品，如果有就不再添加，避免无限增长
                        boolean alreadyExists = false;
                        for (ItemStack existingItem : pendingOutputs) {
                            if (ItemStack.isSameItemSameTags(existingItem, remainingStack)) {
                                // 如果已经存在，就增加现有物品的数量，而不是添加新物品
                                long newCount = (long) existingItem.getCount() + (long) remainingStack.getCount();
                                if (newCount <= (long) Integer.MAX_VALUE) {
                                    existingItem.setCount((int) newCount);
                                } else {
                                    existingItem.setCount(Integer.MAX_VALUE);
                                }
                                alreadyExists = true;
                                break;
                            }
                        }
                        
                        if (!alreadyExists) {
                            addOutputItem(remainingStack);
                        }
                    }
                    
                    // 输出物品到自身后，触发自动输出
                    tryAutoOutput();
                    
                    // 避免无限循环，每次循环至少减少1
                    if (inserted == 0) {
                        break;
                    }
                }
            }
        }

        // 直接输出流体到AE网络（如果连接），否则输出到自身容器
        FluidStack outputFluid = recipe.getOutputFluid();
        if (!outputFluid.isEmpty()) {
            // 使用long类型计算总输出数量，避免整数溢出
            long totalFluidAmount = (long) outputFluid.getAmount() * parallel;
            
            // 如果总流体数量超过Integer.MAX_VALUE，分多次填充
            FluidStack baseOutputStack = outputFluid.copy();
            long remainingAmount = totalFluidAmount;
            
            while (remainingAmount > 0) {
                FluidStack outputStack = baseOutputStack.copy();
                // 每次输出不超过Integer.MAX_VALUE
                int stackAmount = (int) Math.min(remainingAmount, Integer.MAX_VALUE);
                outputStack.setAmount(stackAmount);
                
                // 尝试优先输出到AE网络
                long inserted = tryOutputFluidToAE(outputStack);
                long actualInserted = Math.min(inserted, remainingAmount);
                remainingAmount -= actualInserted;
                
                // 如果AE网络没存下，输出到自身流体槽
                if (remainingAmount > 0) {
                    FluidStack remainingFluid = outputStack.copy();
                    remainingFluid.setAmount((int) remainingAmount);
                    outputFluidTank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                }
                
                // 输出流体到自身后，触发自动输出
                tryAutoOutput();
                
                // 避免无限循环，每次循环至少减少1
                if (inserted == 0) {
                    break;
                }
            }
        }
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
        
        // 将ItemStack转换为AEKey
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }
        
        // 尝试插入到AE网络
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
        
        // 将FluidStack转换为AEKey
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return 0;
        }
        
        // 尝试插入到AE网络
        long amount = stack.getAmount();
        long inserted = storage.insert(key, amount, Actionable.MODULATE, actionSource);
        
        return inserted;
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

    // 修改：改进的输出物品添加方法，将物品添加到暂存列表
    private void addOutputItem(ItemStack output) {
        ItemStack outputStack = output.copy();
        pendingOutputs.add(outputStack);
        isOutputPending = true;
    }
    
    // 处理暂存物品的输出
    private void processPendingOutputs() {
        if (pendingOutputs.isEmpty()) {
            isOutputPending = false;
            return;
        }
        
        // 遍历暂存物品列表，尝试输出
        List<ItemStack> remainingOutputs = new ArrayList<>();
        
        for (ItemStack pendingItem : pendingOutputs) {
            ItemStack outputStack = pendingItem.copy();
            boolean outputProcessed = false;
            
            // 首先尝试堆叠到已有相同物品的槽位，但避免整数溢出
            for (int i = 6; i < 12; i++) {
                ItemStack slotStack = itemHandler.getStackInSlot(i);
                if (ItemStack.isSameItemSameTags(slotStack, outputStack)) {
                    // 检查当前数量加上要添加的数量是否会超过Integer.MAX_VALUE
                    if ((long) slotStack.getCount() + (long) outputStack.getCount() <= (long) Integer.MAX_VALUE) {
                        // 安全堆叠，不会导致溢出
                        slotStack.grow(outputStack.getCount());
                        itemHandler.setStackInSlot(i, slotStack);
                        outputProcessed = true;
                        break;
                    }
                    // 如果会导致溢出，就尝试下一个槽位
                }
            }
            
            // 如果没有堆叠成功，尝试放入空槽位
            if (!outputProcessed) {
                for (int i = 6; i < 12; i++) {
                    ItemStack slotStack = itemHandler.getStackInSlot(i);
                    if (slotStack.isEmpty()) {
                        itemHandler.setStackInSlot(i, outputStack.copy());
                        outputProcessed = true;
                        break;
                    }
                }
            }
            
            // 如果还是没有成功输出，将物品保留在暂存列表中
            if (!outputProcessed) {
                remainingOutputs.add(outputStack);
            }
        }
        
        // 保存原始大小
        int originalSize = pendingOutputs.size();
        // 更新暂存列表
        pendingOutputs = remainingOutputs;
        isOutputPending = !pendingOutputs.isEmpty();
        
        // 如果有物品被输出，触发自动输出
        if (pendingOutputs.size() < originalSize) {
            tryAutoOutput();
        }
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

    // 暴露给菜单的方法：检查是否为无用锭
    public boolean isUselessIngot(ItemStack stack) {
        return MoldIdentifier.isUselessIngot(stack);
    }

    // 暴露给菜单的方法：检查是否为金属模具
    public boolean isMetalMold(ItemStack stack) {
        return MoldIdentifier.isMetalMold(stack);
    }
    
    // 暴露给菜单的方法：检查是否为可接受的标志物
    public boolean isAcceptableMarker(ItemStack stack) {
        return MoldIdentifier.isAcceptableMarker(stack);
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
        // AE2 Integration - 加载节点数据
        this.gridNode.loadFromNBT(tag);
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
        // AE2 Integration - 保存节点数据
        this.gridNode.saveToNBT(tag);
    }

    // Capabilities - 修复方向处理
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // AE2 Integration - 暴露网络节点能力
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST) {
            return LazyOptional.of(() -> this).cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return lazyEnergyHandler.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // 根据方向返回不同的物品处理器
            if (side == Direction.DOWN) {
                // 后方访问：只能访问催化剂槽（12）和模具槽（13）
                return LazyOptional.of(() -> new DirectionalItemHandler(itemHandler, side)).cast();
            } else {
                // 非后方访问：只能访问输入槽（0-5）和输出槽（6-11）
                return LazyOptional.of(() -> new DirectionalItemHandler(itemHandler, side)).cast();
            }
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return lazyFluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }
    
    // IInWorldGridNodeHost implementation
    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.gridNode.getNode();
    }
    
    @Override
    public appeng.api.util.AECableType getCableConnectionType(Direction dir) {
        return appeng.api.util.AECableType.GLASS;
    }
    
    // IActionHost implementation
    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return this.gridNode.getNode();
    }
    
    // 方向感知的物品处理器包装类
    private class DirectionalItemHandler implements IItemHandlerModifiable {
        private final MultiSlotItemHandler originalHandler;
        private final Direction accessDirection;
        
        public DirectionalItemHandler(MultiSlotItemHandler originalHandler, Direction accessDirection) {
            this.originalHandler = originalHandler;
            this.accessDirection = accessDirection;
        }
        
        @Override
        public int getSlots() {
            return originalHandler.getSlots();
        }
        
        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return originalHandler.getStackInSlot(slot);
        }
        
        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // 基于方向和槽位类型限制插入
            if (accessDirection == Direction.DOWN) {
                // 后方访问：只能插入催化剂槽（12）
                if (slot == CATALYST_SLOT) {
                    return originalHandler.insertItem(slot, stack, simulate);
                }
            } else {
                // 非后方访问：只能插入输入槽（0-5）
                if (slot < 6) {
                    return originalHandler.insertItem(slot, stack, simulate);
                }
            }
            // 不允许插入的情况，返回原物品
            return stack;
        }
        
        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 提取不受方向限制
            return originalHandler.extractItem(slot, amount, simulate);
        }
        
        @Override
        public int getSlotLimit(int slot) {
            return originalHandler.getSlotLimit(slot);
        }
        
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return originalHandler.isItemValid(slot, stack);
        }
        
        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            // 转发设置操作到原始处理器
            originalHandler.setStackInSlot(slot, stack);
        }
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