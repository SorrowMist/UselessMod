// AdvancedAlloyFurnaceMenu.java
package com.sorrowmist.useless.blocks.advancedalloyfurnace;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class AdvancedAlloyFurnaceMenu extends AbstractContainerMenu {
    private final AdvancedAlloyFurnaceBlockEntity blockEntity;
    private final Player player;
    private final ContainerData data;

    // 自定义Slot类以支持更大的堆叠限制
    private static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1024; // 输入槽支持最多1024个物品
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1024; // 输入槽支持最多1024个物品
        }
    }

    private static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // 输出槽不能手动放置物品
        }

        @Override
        public int getMaxStackSize() {
            return 1024; // 输出槽支持最多1024个物品
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1024; // 输出槽支持最多1024个物品
        }
    }

    public AdvancedAlloyFurnaceMenu(int windowId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.ADVANCED_ALLOY_FURNACE_MENU.get(), windowId);
        this.player = playerInventory.player;
        this.data = data;

        // 安全地处理方块实体
        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity) {
            this.blockEntity = (AdvancedAlloyFurnaceBlockEntity) blockEntity;
        } else {
            this.blockEntity = null;
            UselessMod.LOGGER.warn("AdvancedAlloyFurnaceMenu created with invalid block entity: {}", blockEntity);
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        // 只有在方块实体存在时才添加物品槽位
        if (this.blockEntity != null) {
            this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                // 输入槽位 (0-5) - 使用自定义的InputSlot
                for (int i = 0; i < 6; i++) {
                    this.addSlot(new InputSlot(handler, i, 26 + i * 18, 17));
                }

                // 输出槽位 (6-11) - 使用自定义的OutputSlot
                for (int i = 6; i < 12; i++) {
                    this.addSlot(new OutputSlot(handler, i, 26 + (i-6) * 18, 53));
                }
            });
        }

        // 添加数据槽
        addDataSlots(data);
    }

    public AdvancedAlloyFurnaceMenu(int windowId, Inventory playerInventory, BlockEntity blockEntity) {
        this(windowId, playerInventory, blockEntity,
                blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace ?
                        furnace.getContainerData() : new SimpleContainerData(5));
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        // 如果方块实体不存在，不允许快速移动
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();

            if (index >= 12 && index < 39) {
                // 从玩家物品栏移动到机器输入槽
                if (!this.moveItemStackTo(stack, 0, 6, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 39 && index < 48) {
                // 从玩家快捷栏移动到机器输入槽
                if (!this.moveItemStackTo(stack, 0, 6, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 6 && index < 12) {
                // 从机器输出槽移动到玩家物品栏
                if (!this.moveItemStackTo(stack, 12, 48, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < 6) {
                // 从机器输入槽移动到玩家物品栏
                if (!this.moveItemStackTo(stack, 12, 48, true)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(playerIn, stack);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        // 如果方块实体不存在，菜单无效
        if (this.blockEntity == null) {
            return false;
        }

        return player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public AdvancedAlloyFurnaceBlockEntity getBlockEntity() {
        return blockEntity;
    }

    // 添加安全检查方法
    public boolean isValid() {
        return blockEntity != null;
    }

    // 数据访问方法
    public int getEnergy() {
        return data.get(0);
    }

    public int getMaxEnergy() {
        return data.get(1);
    }

    public int getProgress() {
        return data.get(2);
    }

    public int getMaxProgress() {
        return data.get(3);
    }

    public boolean isActive() {
        return data.get(4) == 1;
    }
}