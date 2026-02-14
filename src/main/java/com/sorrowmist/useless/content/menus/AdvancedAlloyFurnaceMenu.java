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

/**
 * 高级合金炉菜单
 */
public class AdvancedAlloyFurnaceMenu extends AbstractContainerMenu {

    // ==================== 槽位布局常量 ====================
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

    // ==================== 槽位范围常量 (从 BlockEntity 导入) ====================
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

            // 输入槽位 (0-8)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slotIndex = row * 3 + col;
                    int x = INPUT_SLOTS_X + col * SLOT_SIZE;
                    int y = INPUT_SLOTS_FIRST_Y + row * SLOT_SIZE;
                    this.addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y));
                }
            }

            // 输出槽位 (9-17)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slotIndex = 9 + row * 3 + col;
                    int x = OUTPUT_SLOTS_X + col * SLOT_SIZE;
                    int y = OUTPUT_SLOTS_FIRST_Y + row * SLOT_SIZE;
                    this.addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }
                    });
                }
            }

            // 催化剂槽 (18)
            this.addSlot(new SlotItemHandler(itemHandler, CATALYST_SLOT, CATALYST_SLOT_X, CATALYST_SLOT_Y));

            // 模具槽 (19)
            this.addSlot(new SlotItemHandler(itemHandler, MOLD_SLOT, MOLD_SLOT_X, MOLD_SLOT_Y));
        }

        this.layoutPlayerInventorySlots(inv);
    }

    private static ContainerData getContainerData(BlockEntity entity) {
        if (entity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            return furnace.getData();
        }
        return new SimpleContainerData(AdvancedAlloyFurnaceData.DATA_COUNT);
    }

    /**
     * 添加玩家物品栏和热键栏槽位
     */
    private void layoutPlayerInventorySlots(Inventory inventory) {
        // 玩家背包 (27个)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inventory, j + i * 9 + 9,
                                      PLAYER_INVENTORY_X + j * 18, PLAYER_INVENTORY_Y + i * 18
                ));
            }
        }

        // 热键栏 (9个)
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inventory, i, PLAYER_INVENTORY_X + i * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        if (this.blockEntity == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // 从机器槽位转移到玩家物品栏
            if (index >= MACHINE_INPUT_START && index <= MOLD_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START, HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            }
            // 从玩家物品栏转移到机器
            else if (index >= PLAYER_INVENTORY_START && index <= HOTBAR_END) {
                // 先尝试输入槽
                if (!this.moveItemStackTo(stackInSlot, MACHINE_INPUT_START, MACHINE_INPUT_END + 1, false)) {
                    // 再尝试催化剂槽
                    if (!this.moveItemStackTo(stackInSlot, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
                        // 再尝试模具槽
                        if (!this.moveItemStackTo(stackInSlot, MOLD_SLOT, MOLD_SLOT + 1, false)) {
                            // 在玩家物品栏内部转移
                            if (index >= PLAYER_INVENTORY_START && index <= PLAYER_INVENTORY_END) {
                                if (!this.moveItemStackTo(stackInSlot, HOTBAR_START, HOTBAR_END + 1, false)) {
                                    return ItemStack.EMPTY;
                                }
                            } else if (index >= HOTBAR_START && index <= HOTBAR_END) {
                                if (!this.moveItemStackTo(stackInSlot, PLAYER_INVENTORY_START,
                                                          PLAYER_INVENTORY_END + 1, false
                                )) {
                                    return ItemStack.EMPTY;
                                }
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

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ==================== 数据访问器 ====================

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

    /**
     * 获取催化剂提供的最大并行数（仅由催化剂决定）
     */
    public int getCatalystMaxParallel() {
        if (this.blockEntity != null) {
            return this.blockEntity.getCatalystMaxParallel();
        }
        return 1;
    }

    public boolean hasMold() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_HAS_MOLD) > 0;
    }

    public FluidTank getInputFluidTank(int index) {
        if (this.blockEntity != null) {
            return this.blockEntity.getInputFluidTank(index);
        }
        return new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        if (this.blockEntity != null) {
            return this.blockEntity.getOutputFluidTank(index);
        }
        return new FluidTank(0);
    }
}
