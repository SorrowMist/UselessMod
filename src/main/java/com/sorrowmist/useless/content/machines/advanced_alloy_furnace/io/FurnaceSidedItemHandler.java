package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT;
import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;

/**
 * 根据面模式限制的方向感知物品处理器。
 * <p>
 * 仅当对应面模式激活时才允许特定类型的插入/抽取操作。
 */
public record FurnaceSidedItemHandler(IItemHandler baseHandler, @Nullable Direction side,
                                      FurnaceFaceAccessor owner) implements IItemHandler {

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

        // 催化剂优先路由
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
