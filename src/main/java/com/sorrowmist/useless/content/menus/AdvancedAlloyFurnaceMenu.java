package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceData;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AdvancedAlloyFurnaceMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOTS_X = 8;
    private static final int INPUT_SLOTS_FIRST_Y = 18;
    private static final int SLOT_SIZE = 18;

    private static final int OUTPUT_SLOTS_X = 116;
    private static final int OUTPUT_SLOTS_FIRST_Y = 113;

    private static final int CATALYST_SLOT_X = 61;
    private static final int CATALYST_SLOT_Y = 87;
    private static final int MOLD_SLOT_X = 99;
    private static final int MOLD_SLOT_Y = 87;

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 178;
    private static final int PLAYER_HOTBAR_Y = 236;

    private static final int MACHINE_INPUT_START = AdvancedAlloyFurnaceBlockEntity.INPUT_SLOTS_START;
    private static final int MACHINE_INPUT_END = MACHINE_INPUT_START + AdvancedAlloyFurnaceBlockEntity.INPUT_SLOTS_COUNT - 1;
    private static final int MACHINE_OUTPUT_START = AdvancedAlloyFurnaceBlockEntity.OUTPUT_SLOTS_START;
    private static final int MACHINE_OUTPUT_END = MACHINE_OUTPUT_START + AdvancedAlloyFurnaceBlockEntity.OUTPUT_SLOTS_COUNT - 1;
    private static final int CATALYST_SLOT = AdvancedAlloyFurnaceBlockEntity.CATALYST_SLOT;
    private static final int MOLD_SLOT = AdvancedAlloyFurnaceBlockEntity.MOLD_SLOT;
    private static final int PLAYER_INVENTORY_START = AdvancedAlloyFurnaceBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 26;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END + 1;
    private static final int HOTBAR_END = HOTBAR_START + 8;

    private final AdvancedAlloyFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    public AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv, buf.readBlockPos(), inv);
    }

    private AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, BlockPos pos, Inventory playerInv) {
        this(containerId, inv, playerInv.player.level().getBlockEntity(pos));
    }

    private AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, BlockEntity entity) {
        this(containerId, inv, (AdvancedAlloyFurnaceBlockEntity) entity, getContainerData(entity));
    }

    public AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, AdvancedAlloyFurnaceBlockEntity entity,
                                    ContainerData data) {
        super(ModMenuType.ADVANCED_ALLOY_FURNACE_MENU.get(), containerId);
        this.data = data;
        this.blockEntity = entity;
        this.addDataSlots(data);

        if (entity != null) {
            IItemHandler itemHandler = entity.getItemHandler();
            this.addMachineSlots(itemHandler);
        }

        this.layoutPlayerInventorySlots(inv);
    }

    /**
     * 添加机器槽位（输入、输出、催化剂、模具）
     *
     * @param itemHandler 物品处理器
     */
    private void addMachineSlots(IItemHandler itemHandler) {
        // 添加9个输入槽位（3x3）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                int x = INPUT_SLOTS_X + col * SLOT_SIZE;
                int y = INPUT_SLOTS_FIRST_Y + row * SLOT_SIZE;
                this.addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y));
            }
        }

        // 添加9个输出槽位（3x3），不允许放入物品
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = 9 + row * 3 + col;
                int x = OUTPUT_SLOTS_X + col * SLOT_SIZE;
                int y = OUTPUT_SLOTS_FIRST_Y + row * SLOT_SIZE;
                this.addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        // 添加催化剂槽位
        this.addSlot(new SlotItemHandler(itemHandler, CATALYST_SLOT, CATALYST_SLOT_X, CATALYST_SLOT_Y));
        // 添加模具槽位
        this.addSlot(new SlotItemHandler(itemHandler, MOLD_SLOT, MOLD_SLOT_X, MOLD_SLOT_Y));
    }

    /**
     * 获取容器数据
     *
     * @param entity 方块实体
     * @return 容器数据
     */
    private static ContainerData getContainerData(BlockEntity entity) {
        if (entity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            return furnace.getData();
        }
        return new SimpleContainerData(AdvancedAlloyFurnaceData.DATA_COUNT);
    }

    /**
     * 布局玩家背包槽位
     *
     * @param inventory 玩家背包
     */
    private void layoutPlayerInventorySlots(Inventory inventory) {
        // 添加27个背包槽位（3x9）
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inventory, j + i * 9 + 9,
                        PLAYER_INVENTORY_X + j * 18, PLAYER_INVENTORY_Y + i * 18));
            }
        }

        // 添加9个快捷栏槽位
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inventory, i, PLAYER_INVENTORY_X + i * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (this.blockEntity == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (!slot.hasItem()) {
            return itemstack;
        }

        ItemStack stackInSlot = slot.getItem();
        itemstack = stackInSlot.copy();

        if (index >= MACHINE_INPUT_START && index <= MOLD_SLOT) {
            if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, HOTBAR_END + 1, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stackInSlot, itemstack);
        } else if (index >= PLAYER_INVENTORY_START && index <= HOTBAR_END) {
            if (!this.tryMoveToMachine(stackInSlot)) {
                if (!this.moveWithinPlayerInventory(stackInSlot, index)) {
                    return ItemStack.EMPTY;
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

        slot.onTake(player, stackInSlot);
        return itemstack;
    }

    /**
     * 尝试将物品移动到机器槽位
     * <p>
     * 优先顺序：输入槽 -> 催化剂槽 -> 模具槽
     *
     * @param stack 物品堆
     * @return 是否成功移动
     */
    private boolean tryMoveToMachine(ItemStack stack) {
        if (this.moveItemStackTo(stack, MACHINE_INPUT_START, MACHINE_INPUT_END + 1, false)) {
            return true;
        }
        if (this.moveItemStackTo(stack, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
            return true;
        }
        return this.moveItemStackTo(stack, MOLD_SLOT, MOLD_SLOT + 1, false);
    }

    /**
     * 在玩家背包内部移动物品
     * <p>
     * 在背包和快捷栏之间切换
     *
     * @param stack 物品堆
     * @param index 当前槽位索引
     * @return 是否成功移动
     */
    private boolean moveWithinPlayerInventory(ItemStack stack, int index) {
        if (index >= PLAYER_INVENTORY_START && index <= PLAYER_INVENTORY_END) {
            return this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END + 1, false);
        } else if (index >= HOTBAR_START && index <= HOTBAR_END) {
            return this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END + 1, false);
        }
        return false;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    public AdvancedAlloyFurnaceBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public int getEnergy() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_STORED);
    }

    public int getMaxEnergy() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_CAPACITY);
    }

    public int getProgress() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_PROGRESS);
    }

    public int getMaxProgress() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_MAX_PROGRESS);
    }

    public int getCurrentParallel() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_CURRENT_PARALLEL);
    }

    public int getMaxParallel() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_MAX_PARALLEL);
    }

    public int getCatalystMaxParallel() {
        return this.blockEntity != null ? this.blockEntity.getCatalystMaxParallel() : 1;
    }

    public boolean hasMold() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_HAS_MOLD) > 0;
    }

    public FluidTank getInputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getInputFluidTank(index) : new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getOutputFluidTank(index) : new FluidTank(0);
    }
}
