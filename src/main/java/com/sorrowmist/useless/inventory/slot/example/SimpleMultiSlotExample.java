package com.sorrowmist.useless.inventory.slot.example;

import com.sorrowmist.useless.inventory.slot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 简化版多槽位使用示例
 * 使用 SlotInventoryManager 管理槽位
 */
public class SimpleMultiSlotExample extends BlockEntity implements IContentsListener {

    // 槽位管理器
    private final SlotInventoryManager slotManager = new SlotInventoryManager(this);

    // 输入槽和输出槽的引用（方便访问）
    private List<LargeInventorySlot> inputSlots;
    private List<LargeInventorySlot> outputSlots;

    public SimpleMultiSlotExample(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        // 创建6个输入槽，容量512，2行3列布局
        inputSlots = slotManager.addInputSlots(
                6,      // 数量
                512,    // 容量
                10,     // 起始X
                10,     // 起始Y
                3       // 每行3个
        );

        // 创建3个输出槽，容量2048，1行3列布局
        outputSlots = slotManager.addOutputSlots(
                3,      // 数量
                2048,   // 容量
                80,     // 起始X
                20,     // 起始Y
                3       // 每行3个
        );
    }

    // ========== 槽位访问 ==========

    public List<LargeInventorySlot> getInputSlots() {
        return inputSlots;
    }

    public List<LargeInventorySlot> getOutputSlots() {
        return outputSlots;
    }

    public SlotInventoryManager getSlotManager() {
        return slotManager;
    }

    // ========== IContentsListener ==========

    @Override
    public void onContentsChanged() {
        setChanged();
    }

    // ========== 序列化（超简单） ==========

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 一行代码保存所有槽位
        tag.put("slots", slotManager.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 一行代码加载所有槽位
        if (tag.contains("slots")) {
            slotManager.deserializeNBT(registries, tag.getCompound("slots"));
        }
    }

    // ========== 处理逻辑示例 ==========

    public void tick() {
        if (level == null || level.isClientSide) return;

        // 示例：遍历所有输入槽，处理物品并输出
        for (LargeInventorySlot inputSlot : inputSlots) {
            if (inputSlot.isEmpty()) continue;

            ItemStack input = inputSlot.getStack();
            ItemStack result = processInput(input);

            // 尝试输出到任意输出槽
            for (LargeInventorySlot outputSlot : outputSlots) {
                ItemStack remainder = outputSlot.insertItem(result, Action.SIMULATE, AutomationType.INTERNAL);
                if (remainder.isEmpty()) {
                    // 可以完整输出
                    outputSlot.insertItem(result, Action.EXECUTE, AutomationType.INTERNAL);
                    inputSlot.setEmpty();
                    break;
                } else if (remainder.getCount() < result.getCount()) {
                    // 可以部分输出
                    int canOutput = result.getCount() - remainder.getCount();
                    outputSlot.insertItem(result.copyWithCount(canOutput), Action.EXECUTE, AutomationType.INTERNAL);
                    inputSlot.shrinkStack(1, Action.EXECUTE);
                    break;
                }
            }
        }
    }

    private ItemStack processInput(ItemStack input) {
        // 你的处理逻辑
        return input.copy();
    }
}
