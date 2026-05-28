package com.sorrowmist.useless.inventory.slot.example;

import com.sorrowmist.useless.inventory.slot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
 * 示例：使用新槽位系统的 BlockEntity
 * 展示了如何使用 LargeInventorySlot 实现超过64的堆叠
 */
public class ExampleBlockEntity extends BlockEntity implements MenuProvider, IContentsListener {

    // 创建大容量槽位
    // 输入槽：容量 1024
    private final LargeInventorySlot inputSlot = LargeInventorySlot.createInput(
            1024, this, 10, 10);

    // 输出槽：容量 4096
    private final LargeInventorySlot outputSlot = LargeInventorySlot.createOutput(
            4096, this, 80, 10);

    // 普通大容量槽位（可同时输入输出）：容量 2048
    private final LargeInventorySlot storageSlot = LargeInventorySlot.create(
            2048, this, 45, 40);

    // 槽位列表
    private final List<IInventorySlot> slots = List.of(inputSlot, outputSlot, storageSlot);

    // IItemHandler 适配器
    private final IItemHandler itemHandler = new SlotItemHandler(slots);

    public ExampleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ========== 槽位访问方法 ==========

    public LargeInventorySlot getInputSlot() {
        return inputSlot;
    }

    public LargeInventorySlot getOutputSlot() {
        return outputSlot;
    }

    public LargeInventorySlot getStorageSlot() {
        return storageSlot;
    }

    public List<IInventorySlot> getSlots() {
        return slots;
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        // 可以根据方向返回不同的槽位访问
        return itemHandler;
    }

    // ========== IContentsListener 实现 ==========

    @Override
    public void onContentsChanged() {
        // 槽位内容变化时标记为需要保存
        setChanged();
        // 如果有需要，可以在这里发送更新包到客户端
        // level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    // ========== 序列化 ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存每个槽位
        CompoundTag slotsTag = new CompoundTag();
        slotsTag.put("input", inputSlot.serializeNBT(registries));
        slotsTag.put("output", outputSlot.serializeNBT(registries));
        slotsTag.put("storage", storageSlot.serializeNBT(registries));
        tag.put("Slots", slotsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载每个槽位
        if (tag.contains("Slots")) {
            CompoundTag slotsTag = tag.getCompound("Slots");
            if (slotsTag.contains("input")) {
                inputSlot.deserializeNBT(registries, slotsTag.getCompound("input"));
            }
            if (slotsTag.contains("output")) {
                outputSlot.deserializeNBT(registries, slotsTag.getCompound("output"));
            }
            if (slotsTag.contains("storage")) {
                storageSlot.deserializeNBT(registries, slotsTag.getCompound("storage"));
            }
        }
    }

    // ========== MenuProvider 实现 ==========

    @Override
    public Component getDisplayName() {
        return Component.literal("Example Machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // 返回你的 ContainerMenu
        // return new ExampleMenu(containerId, playerInventory, this);
        return null;
    }

    // ========== 机器逻辑示例 ==========

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        // 示例：处理输入槽的物品并输出到输出槽
        if (!inputSlot.isEmpty() && canProcess(inputSlot.getStack())) {
            ItemStack input = inputSlot.getStack();
            ItemStack result = getProcessingResult(input);

            // 尝试插入结果到输出槽
            ItemStack remainder = outputSlot.insertItem(result, Action.SIMULATE, AutomationType.INTERNAL);
            if (remainder.isEmpty()) {
                // 可以处理，执行实际操作
                outputSlot.insertItem(result, Action.EXECUTE, AutomationType.INTERNAL);

                // 消耗输入
                inputSlot.shrinkStack(1, Action.EXECUTE);
            }
        }
    }

    private boolean canProcess(ItemStack stack) {
        // 检查是否可以处理该物品
        return !stack.isEmpty();
    }

    private ItemStack getProcessingResult(ItemStack input) {
        // 返回处理结果
        return input.copyWithCount(input.getCount());
    }

    // ========== IItemHandler 适配器 ==========

    /**
     * 将 IInventorySlot 列表适配为 IItemHandler
     */
    private static class SlotItemHandler implements IItemHandler {

        private final List<IInventorySlot> slots;

        public SlotItemHandler(List<IInventorySlot> slots) {
            this.slots = new ArrayList<>(slots);
        }

        @Override
        public int getSlots() {
            return slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= slots.size()) {
                return ItemStack.EMPTY;
            }
            return slots.get(slot).getStack();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= slots.size()) {
                return stack;
            }
            return slots.get(slot).insertItem(stack,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= slots.size()) {
                return ItemStack.EMPTY;
            }
            return slots.get(slot).extractItem(amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    AutomationType.EXTERNAL);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= slots.size()) {
                return 0;
            }
            return slots.get(slot).getLimit(ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= slots.size()) {
                return false;
            }
            return slots.get(slot).isItemValid(stack);
        }
    }
}
