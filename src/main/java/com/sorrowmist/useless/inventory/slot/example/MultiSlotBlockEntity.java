package com.sorrowmist.useless.inventory.slot.example;

import com.sorrowmist.useless.inventory.slot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 示例：多输入多输出的 BlockEntity
 * 展示了如何管理多个输入槽和多个输出槽
 */
public class MultiSlotBlockEntity extends BlockEntity implements MenuProvider, IContentsListener {

    // ========== 槽位配置 ==========

    public static final int INPUT_SLOT_COUNT = 6;   // 6个输入槽
    public static final int OUTPUT_SLOT_COUNT = 3;  // 3个输出槽
    public static final int TOTAL_SLOTS = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT;

    // 槽位索引
    public static final int INPUT_SLOTS_START = 0;
    public static final int OUTPUT_SLOTS_START = INPUT_SLOT_COUNT;

    // ========== 槽位列表 ==========

    private final List<LargeInventorySlot> inputSlots = new ArrayList<>();
    private final List<LargeInventorySlot> outputSlots = new ArrayList<>();
    private final List<IInventorySlot> allSlots = new ArrayList<>();

    // IItemHandler 适配器（用于自动化）
    private final IItemHandler itemHandler = new MultiSlotItemHandler();

    // ========== 构造函数 ==========

    public MultiSlotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        // 创建输入槽（容量512，不允许外部自动化提取）
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            // 计算GUI位置（2行3列布局）
            int x = 10 + (i % 3) * 20;
            int y = 10 + (i / 3) * 20;

            LargeInventorySlot slot = LargeInventorySlot.createInput(512, this, x, y);
            inputSlots.add(slot);
            allSlots.add(slot);
        }

        // 创建输出槽（容量2048，不允许外部自动化插入）
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            int x = 100 + i * 20;
            int y = 20;

            LargeInventorySlot slot = LargeInventorySlot.createOutput(2048, this, x, y);
            outputSlots.add(slot);
            allSlots.add(slot);
        }
    }

    // ========== 槽位访问方法 ==========

    /**
     * 获取所有槽位
     */
    public List<IInventorySlot> getAllSlots() {
        return allSlots;
    }

    /**
     * 获取输入槽列表
     */
    public List<LargeInventorySlot> getInputSlots() {
        return inputSlots;
    }

    /**
     * 获取输出槽列表
     */
    public List<LargeInventorySlot> getOutputSlots() {
        return outputSlots;
    }

    /**
     * 获取指定索引的输入槽
     */
    public LargeInventorySlot getInputSlot(int index) {
        if (index < 0 || index >= INPUT_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Input slot index: " + index);
        }
        return inputSlots.get(index);
    }

    /**
     * 获取指定索引的输出槽
     */
    public LargeInventorySlot getOutputSlot(int index) {
        if (index < 0 || index >= OUTPUT_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Output slot index: " + index);
        }
        return outputSlots.get(index);
    }

    /**
     * 获取 IItemHandler（用于管道/漏斗等自动化）
     */
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * 根据方向获取 IItemHandler（可以按方向限制访问）
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        // 可以根据方向返回不同的槽位访问
        // 例如：顶部只允许输入，底部只允许输出
        return switch (side) {
            case UP -> new InputOnlyHandler();      // 顶部：只允许输入
            case DOWN -> new OutputOnlyHandler();   // 底部：只允许输出
            default -> itemHandler;                  // 其他方向：允许全部访问
        };
    }

    // ========== IContentsListener 实现 ==========

    @Override
    public void onContentsChanged() {
        // 标记 BlockEntity 需要保存到磁盘
        setChanged();

        // 注意：这里不需要手动发送更新包到客户端，因为：
        // 1. 当玩家打开 GUI 时，Container 会自动同步槽位数据
        // 2. 如果方块外观需要更新（如工作状态指示灯），才需要发送更新包

        // 只有在以下情况才需要发送更新包：
        // - 方块状态变化（影响渲染）
        // - 需要更新方块的外观
        // if (isProcessing != wasProcessing) {
        //     level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        // }
    }

    // ========== 序列化 ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存输入槽
        ListTag inputList = new ListTag();
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("slot", i);
            slotTag.put("data", inputSlots.get(i).serializeNBT(registries));
            inputList.add(slotTag);
        }
        tag.put("InputSlots", inputList);

        // 保存输出槽
        ListTag outputList = new ListTag();
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("slot", i);
            slotTag.put("data", outputSlots.get(i).serializeNBT(registries));
            outputList.add(slotTag);
        }
        tag.put("OutputSlots", outputList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载输入槽
        if (tag.contains("InputSlots")) {
            ListTag inputList = tag.getList("InputSlots", 10); // 10 = CompoundTag
            for (int i = 0; i < inputList.size(); i++) {
                CompoundTag slotTag = inputList.getCompound(i);
                int slotIndex = slotTag.getInt("slot");
                if (slotIndex >= 0 && slotIndex < INPUT_SLOT_COUNT) {
                    inputSlots.get(slotIndex).deserializeNBT(registries, slotTag.getCompound("data"));
                }
            }
        }

        // 加载输出槽
        if (tag.contains("OutputSlots")) {
            ListTag outputList = tag.getList("OutputSlots", 10);
            for (int i = 0; i < outputList.size(); i++) {
                CompoundTag slotTag = outputList.getCompound(i);
                int slotIndex = slotTag.getInt("slot");
                if (slotIndex >= 0 && slotIndex < OUTPUT_SLOT_COUNT) {
                    outputSlots.get(slotIndex).deserializeNBT(registries, slotTag.getCompound("data"));
                }
            }
        }
    }

    // ========== MenuProvider 实现 ==========

    @Override
    public Component getDisplayName() {
        return Component.literal("Multi Slot Machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // return new MultiSlotMenu(containerId, playerInventory, this);
        return null;
    }

    // ========== 机器处理逻辑 ==========

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // 示例：尝试处理配方
        processRecipe();
    }

    private void processRecipe() {
        // 收集所有非空输入槽的物品
        List<ItemStack> inputs = new ArrayList<>();
        for (LargeInventorySlot slot : inputSlots) {
            if (!slot.isEmpty()) {
                inputs.add(slot.getStack());
            }
        }

        if (inputs.isEmpty()) {
            return;
        }

        // 这里进行配方匹配和处理
        // 示例：简单地将所有输入合并到第一个输出槽
        for (ItemStack input : inputs) {
            // 尝试放入第一个可用的输出槽
            for (LargeInventorySlot outputSlot : outputSlots) {
                ItemStack remainder = outputSlot.insertItem(input, Action.SIMULATE, AutomationType.INTERNAL);
                if (remainder.getCount() < input.getCount()) {
                    // 可以放入，执行实际操作
                    int toProcess = input.getCount() - remainder.getCount();
                    outputSlot.insertItem(input.copyWithCount(toProcess), Action.EXECUTE, AutomationType.INTERNAL);

                    // 从输入槽消耗
                    // 找到对应的输入槽并消耗
                    for (LargeInventorySlot inputSlot : inputSlots) {
                        if (inputSlot.getStack() == input) {
                            inputSlot.shrinkStack(toProcess, Action.EXECUTE);
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }

    // ========== IItemHandler 实现 ==========

    /**
     * 完整的 IItemHandler 实现，暴露所有槽位
     */
    private class MultiSlotItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return TOTAL_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                return ItemStack.EMPTY;
            }
            return allSlots.get(slot).getStack();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                return stack;
            }
            // 只有输入槽允许插入
            if (slot >= INPUT_SLOT_COUNT) {
                return stack;
            }
            return allSlots.get(slot).insertItem(stack,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                return ItemStack.EMPTY;
            }
            // 只有输出槽允许提取
            if (slot < INPUT_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return allSlots.get(slot).extractItem(amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                return 0;
            }
            return allSlots.get(slot).getLimit(ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= TOTAL_SLOTS) {
                return false;
            }
            return allSlots.get(slot).isItemValid(stack);
        }
    }

    /**
     * 只允许访问输入槽的 IItemHandler
     */
    private class InputOnlyHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return INPUT_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inputSlots.get(slot).getStack();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return stack;
            }
            return inputSlots.get(slot).insertItem(stack,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 不允许提取
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return 0;
            }
            return inputSlots.get(slot).getLimit(ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                return false;
            }
            return inputSlots.get(slot).isItemValid(stack);
        }
    }

    /**
     * 只允许访问输出槽的 IItemHandler
     */
    private class OutputOnlyHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return OUTPUT_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= OUTPUT_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return outputSlots.get(slot).getStack();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // 不允许插入
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= OUTPUT_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return outputSlots.get(slot).extractItem(amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= OUTPUT_SLOT_COUNT) {
                return 0;
            }
            return outputSlots.get(slot).getLimit(ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // 输出槽不允许插入
            return false;
        }
    }
}
