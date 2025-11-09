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
import net.minecraftforge.items.SlotItemHandler;

public class AdvancedAlloyFurnaceMenu extends AbstractContainerMenu {
    private final AdvancedAlloyFurnaceBlockEntity blockEntity;
    private final Player player;
    private final ContainerData data;

    // 新的槽位布局常量 - 根据新贴图调整
    // 输入槽位 (6个)
    private static final int INPUT_SLOTS_X = 8;
    private static final int INPUT_SLOTS_FIRST_Y = 23;
    private static final int INPUT_SLOT_SPACING = 18; // 槽位高度18像素

    // 输出槽位 (6个)
    private static final int OUTPUT_SLOTS_X = 153;
    private static final int OUTPUT_SLOTS_FIRST_Y = 23;
    private static final int OUTPUT_SLOT_SPACING = 18; // 槽位高度18像素

    // 流体槽位 - 根据新贴图调整
    private static final int FLUID_INPUT_X = 10;
    private static final int FLUID_OUTPUT_X = 154;
    private static final int FLUID_Y = 143;

    // 新增：催化剂和模具槽位位置
    private static final int CATALYST_SLOT_X = 60;  // 位于流体输入和输出正中间
    private static final int CATALYST_SLOT_Y = 150;
    private static final int MOLD_SLOT_X = 100;      // 模具槽在催化剂槽右侧
    private static final int MOLD_SLOT_Y = 150;

    // 指示灯位置
    private static final int INDICATOR_Y_OFFSET = -8; // 槽位上方2像素
    private static final int INDICATOR_SIZE = 4;

    // 清空按钮位置 - 需要重新定位
    private static final int CLEAR_FLUID_BUTTON_X = 30;
    private static final int CLEAR_FLUID_BUTTON_Y = 145;
    private static final int CLEAR_FLUID_BUTTON_WIDTH = 20;
    private static final int CLEAR_FLUID_BUTTON_HEIGHT = 20;

    // 获取清空按钮位置信息（供Screen使用）
    public int getClearButtonX() {
        return CLEAR_FLUID_BUTTON_X;
    }

    public int getClearButtonY() {
        return CLEAR_FLUID_BUTTON_Y;
    }

    public int getClearButtonWidth() {
        return CLEAR_FLUID_BUTTON_WIDTH;
    }

    public int getClearButtonHeight() {
        return CLEAR_FLUID_BUTTON_HEIGHT;
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

        // 只有在方块实体存在时才添加物品槽位
        if (this.blockEntity != null) {
            this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                // 输入槽位 (0-5) - 垂直排列
                for (int i = 0; i < 6; i++) {
                    this.addSlot(new HighStackSlotItemHandler(handler, i, INPUT_SLOTS_X+1, INPUT_SLOTS_FIRST_Y + i * INPUT_SLOT_SPACING+1));
                }

                // 输出槽位 (6-11) - 垂直排列
                for (int i = 6; i < 12; i++) {
                    this.addSlot(new HighStackSlotItemHandler(handler, i, OUTPUT_SLOTS_X+1, OUTPUT_SLOTS_FIRST_Y + (i-6) * OUTPUT_SLOT_SPACING+1) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false; // 输出槽不能手动放置物品
                        }
                    });
                }

                // 新增：催化剂槽 (12)
                this.addSlot(new HighStackSlotItemHandler(handler, 12, CATALYST_SLOT_X+1, CATALYST_SLOT_Y+1) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // 只能放置无用锭
                        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                            return furnace.isUselessIngot(stack);
                        }
                        return false;
                    }
                });

                // 新增：模具槽 (13)
                this.addSlot(new HighStackSlotItemHandler(handler, 13, MOLD_SLOT_X+1, MOLD_SLOT_Y+1) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // 只能放置金属模具
                        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                            return furnace.isMetalMold(stack);
                        }
                        return false;
                    }
                });
            });
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        // 添加数据槽
        addDataSlots(data);
    }

    public AdvancedAlloyFurnaceMenu(int windowId, Inventory playerInventory, BlockEntity blockEntity) {
        this(windowId, playerInventory, blockEntity,
                blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace ?
                        furnace.getContainerData() : new SimpleContainerData(8)); // 修改：从6改为8
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // 主物品栏 (27个) - 3行9列，根据新贴图调整位置
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 9 + j * 18, 192 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        // 快捷栏 (9个) - 根据新贴图调整位置
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 9 + i * 18, 250));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // 定义槽位范围
            final int MACHINE_INPUT_START = 0;
            final int MACHINE_INPUT_END = 5;
            final int MACHINE_OUTPUT_START = 6;
            final int MACHINE_OUTPUT_END = 11;
            final int CATALYST_SLOT = 12;
            final int MOLD_SLOT = 13;
            final int PLAYER_INVENTORY_START = 14;
            final int PLAYER_INVENTORY_END = 40;
            final int PLAYER_HOTBAR_START = 41;
            final int PLAYER_HOTBAR_END = 49;

            // 从机器输出槽转移到玩家物品栏
            if (index >= MACHINE_OUTPUT_START && index <= MACHINE_OUTPUT_END) {
                // 输出槽可以转移到玩家物品栏的任何位置
                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            }
            // 从催化剂槽转移到玩家物品栏
            else if (index == CATALYST_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            }
            // 从模具槽转移到玩家物品栏
            else if (index == MOLD_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            }
            // 从机器输入槽转移到玩家物品栏
            else if (index >= MACHINE_INPUT_START && index <= MACHINE_INPUT_END) {
                // 输入槽转移到玩家物品栏的任何位置
                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // 从玩家物品栏转移到机器
            else if (index >= PLAYER_INVENTORY_START && index <= PLAYER_HOTBAR_END) {
                boolean isUselessIngot = blockEntity.isUselessIngot(stackInSlot);
                boolean isMetalMold = blockEntity.isMetalMold(stackInSlot);

                // 根据物品类型决定转移目标
                if (isUselessIngot) {
                    // 无用锭只能转移到催化剂槽
                    if (!this.moveItemStackTo(stackInSlot, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (isMetalMold) {
                    // 模具只能转移到模具槽
                    if (!this.moveItemStackTo(stackInSlot, MOLD_SLOT, MOLD_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // 其他物品只能转移到机器输入槽
                    if (!this.moveItemStackTo(stackInSlot, MACHINE_INPUT_START, MACHINE_INPUT_END + 1, false)) {
                        // 如果无法转移到输入槽，尝试转移到玩家物品栏的其他位置
                        if (index >= PLAYER_INVENTORY_START && index <= PLAYER_INVENTORY_END) {
                            // 从主物品栏转移到快捷栏
                            if (!this.moveItemStackTo(stackInSlot, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END + 1, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (index >= PLAYER_HOTBAR_START && index <= PLAYER_HOTBAR_END) {
                            // 从快捷栏转移到主物品栏
                            if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END + 1, false)) {
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(playerIn, stackInSlot);
        }

        return itemstack;
    }

    // 重写 moveItemStackTo 方法，修复转移方向并支持高堆叠
    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        boolean flag = false;
        int i = reverseDirection ? endIndex - 1 : startIndex;

        // 堆叠到已有物品
        if (stack.isStackable()) {
            while (!stack.isEmpty()) {
                if (reverseDirection) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot = this.slots.get(i);
                ItemStack itemstack = slot.getItem();

                if (!itemstack.isEmpty() && ItemStack.isSameItemSameTags(stack, itemstack) && slot.mayPlace(stack)) {
                    // 修复：使用槽位的实际最大堆叠大小，支持高堆叠
                    int maxStackSize = slot.getMaxStackSize(stack);
                    int j = itemstack.getCount() + stack.getCount();

                    if (j <= maxStackSize) {
                        stack.setCount(0);
                        itemstack.setCount(j);
                        slot.setChanged();
                        flag = true;
                    } else if (itemstack.getCount() < maxStackSize) {
                        stack.shrink(maxStackSize - itemstack.getCount());
                        itemstack.setCount(maxStackSize);
                        slot.setChanged();
                        flag = true;
                    }
                }

                if (reverseDirection) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        // 放入空槽位
        if (!stack.isEmpty()) {
            i = reverseDirection ? endIndex - 1 : startIndex;

            while (true) {
                if (reverseDirection) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot1 = this.slots.get(i);
                ItemStack itemstack1 = slot1.getItem();

                if (itemstack1.isEmpty() && slot1.mayPlace(stack)) {
                    // 修复：使用槽位的实际最大堆叠大小，支持高堆叠
                    int maxStackSize = slot1.getMaxStackSize(stack);
                    if (stack.getCount() > maxStackSize) {
                        slot1.set(stack.split(maxStackSize));
                    } else {
                        slot1.set(stack.split(stack.getCount()));
                    }

                    slot1.setChanged();
                    flag = true;
                    break;
                }

                if (reverseDirection) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        return flag;
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

    // 获取流体槽位置信息（供Screen使用）
    public int getFluidInputX() {
        return FLUID_INPUT_X;
    }

    public int getFluidOutputX() {
        return FLUID_OUTPUT_X;
    }

    public int getFluidY() {
        return FLUID_Y;
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

    // 新增：获取当前配方的能量消耗
    public int getProcessTick() {
        return data.get(5);
    }

    // 新增：获取催化剂需求状态
    public boolean isCatalystRequired() {
        return data.get(6) == 1;
    }

    // 新增：获取模具需求状态
    public boolean isMoldRequired() {
        return data.get(7) == 1;
    }

    // 新增：获取催化剂和模具槽位位置信息
    public int getCatalystSlotX() {
        return CATALYST_SLOT_X;
    }

    public int getCatalystSlotY() {
        return CATALYST_SLOT_Y;
    }

    public int getMoldSlotX() {
        return MOLD_SLOT_X;
    }

    public int getMoldSlotY() {
        return MOLD_SLOT_Y;
    }

    public int getIndicatorYOffset() {
        return INDICATOR_Y_OFFSET;
    }

    public int getIndicatorSize() {
        return INDICATOR_SIZE;
    }

    // 修复：自定义槽位类，支持高堆叠
    private static class HighStackSlotItemHandler extends SlotItemHandler {
        public HighStackSlotItemHandler(net.minecraftforge.items.IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public int getMaxStackSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            // 修复：返回实际的最大堆叠大小，而不是受限于物品的默认堆叠限制
            return Integer.MAX_VALUE;
        }

        // 修复：重写 mayPlace 方法，确保高堆叠物品可以被放置
        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }

            // 检查物品处理器是否允许放置
            if (!getItemHandler().isItemValid(getSlotIndex(), stack)) {
                return false;
            }

            // 允许高堆叠物品放置
            return true;
        }
    }
}