package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceData;
import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

/**
 * 支持高堆叠数量的槽位处理器
 * 允许槽位堆叠数量达到 Integer.MAX_VALUE（约21亿）
 */
class HighStackSlotItemHandler extends SlotItemHandler {

    public HighStackSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public int getMaxStackSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return Integer.MAX_VALUE;
    }
}

public class AdvancedAlloyFurnaceMenu extends AEBaseMenu {

    // 物品输入槽：起点75,16，横向排布9个，每个16*16，横向间隔2像素
    private static final int INPUT_SLOTS_X = 75;
    private static final int INPUT_SLOTS_Y = 16;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_SPACING = 2;

    // 物品输出槽：起点75,93，横向排布9个，每个16*16，横向间隔2像素
    private static final int OUTPUT_SLOTS_X = 75;
    private static final int OUTPUT_SLOTS_Y = 93;

    // 催化剂槽：起点78,154，16*16
    private static final int CATALYST_SLOT_X = 78;
    private static final int CATALYST_SLOT_Y = 154;

    // 模具槽：起点128,154，16*16
    private static final int MOLD_SLOT_X = 128;
    private static final int MOLD_SLOT_Y = 154;

    // 样板槽：起点8,5，每个16*16，横向3个纵向9个，间隔2像素
    private static final int PATTERN_SLOTS_X = 8;
    private static final int PATTERN_SLOTS_Y = 5;
    private static final int PATTERN_SLOTS_COLS = 3;
    private static final int PATTERN_SLOTS_ROWS = 9;

    // 玩家背包位置：起点75,178
    private static final int PLAYER_INVENTORY_X = 75;
    private static final int PLAYER_INVENTORY_Y = 178;
    private static final int PLAYER_HOTBAR_Y = 236;

    // 样板每页27个（3x9）
    private static final int PATTERN_SLOTS_PER_PAGE = 27;
    private int patternPage = 0;

    private static final int MACHINE_INPUT_START = AdvancedAlloyFurnaceBlockEntity.INPUT_SLOTS_START;
    private static final int MACHINE_INPUT_END = MACHINE_INPUT_START + AdvancedAlloyFurnaceBlockEntity.INPUT_SLOTS_COUNT - 1;
    private static final int MACHINE_OUTPUT_START = AdvancedAlloyFurnaceBlockEntity.OUTPUT_SLOTS_START;
    private static final int MACHINE_OUTPUT_END = MACHINE_OUTPUT_START + AdvancedAlloyFurnaceBlockEntity.OUTPUT_SLOTS_COUNT - 1;
    private static final int CATALYST_SLOT = AdvancedAlloyFurnaceBlockEntity.CATALYST_SLOT;
    private static final int MOLD_SLOT = AdvancedAlloyFurnaceBlockEntity.MOLD_SLOT;
    private static final int PATTERN_SLOTS_START = AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_START;
    private static final int PATTERN_SLOTS_END = AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_END;
    private static final int PLAYER_INVENTORY_START = AdvancedAlloyFurnaceBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 26;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END + 1;
    private static final int HOTBAR_END = HOTBAR_START + 8;

    private final AdvancedAlloyFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    public AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv, buf != null ? buf.readBlockPos() : BlockPos.ZERO);
    }

    private AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, BlockPos pos) {
        this(containerId, inv, inv.player.level().getBlockEntity(pos));
    }

    private AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, BlockEntity entity) {
        this(containerId, inv, (AdvancedAlloyFurnaceBlockEntity) entity, getContainerData(entity));
    }

    public AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, AdvancedAlloyFurnaceBlockEntity entity) {
        this(containerId, inv, entity, entity.getData());
    }

    public AdvancedAlloyFurnaceMenu(int containerId, Inventory inv, AdvancedAlloyFurnaceBlockEntity entity,
                                    ContainerData data) {
        super(ModMenuType.ADVANCED_ALLOY_FURNACE_MENU.get(), containerId, inv, entity);
        this.data = data;
        this.blockEntity = entity;
        this.addDataSlots(data);

        IItemHandler itemHandler = entity != null ? entity.getItemHandler() : new ItemStackHandler(AdvancedAlloyFurnaceBlockEntity.TOTAL_SLOTS);
        this.addMachineSlots(itemHandler);

        this.createPlayerInventorySlots(inv, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y, PLAYER_HOTBAR_Y);
    }

    /**
     * 添加机器槽位（输入、输出、催化剂、模具）
     *
     * @param itemHandler 物品处理器
     */
    private void addMachineSlots(IItemHandler itemHandler) {
        // 添加9个输入槽位（横向排布）
        for (int col = 0; col < 9; col++) {
            int slotIndex = col;
            int x = INPUT_SLOTS_X + col * (SLOT_SIZE + SLOT_SPACING);
            int y = INPUT_SLOTS_Y;
            this.addSlot(new HighStackSlotItemHandler(itemHandler, slotIndex, x, y),
                    SlotSemantics.MACHINE_INPUT);
        }

        // 添加9个输出槽位（横向排布），不允许放入物品
        for (int col = 0; col < 9; col++) {
            int slotIndex = 9 + col;
            int x = OUTPUT_SLOTS_X + col * (SLOT_SIZE + SLOT_SPACING);
            int y = OUTPUT_SLOTS_Y;
            this.addSlot(new HighStackSlotItemHandler(itemHandler, slotIndex, x, y) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return false;
                }
            }, SlotSemantics.MACHINE_OUTPUT);
        }

        // 添加催化剂槽位
        this.addSlot(new HighStackSlotItemHandler(itemHandler, CATALYST_SLOT, CATALYST_SLOT_X, CATALYST_SLOT_Y),
                SlotSemantics.CONFIG);

        // 添加模具槽位
        this.addSlot(new HighStackSlotItemHandler(itemHandler, MOLD_SLOT, MOLD_SLOT_X, MOLD_SLOT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, SlotSemantics.CONFIG);

        // 添加108个样板槽位（4页 × 3x9），使用自定义的PatternSlotItemHandler实现背景图案
        for (int page = 0; page < 4; page++) {
            for (int row = 0; row < PATTERN_SLOTS_ROWS; row++) {
                for (int col = 0; col < PATTERN_SLOTS_COLS; col++) {
                    int slotIndex = PATTERN_SLOTS_START + page * PATTERN_SLOTS_PER_PAGE + row * PATTERN_SLOTS_COLS + col;
                    int x = PATTERN_SLOTS_X + col * (SLOT_SIZE + SLOT_SPACING);
                    int y = PATTERN_SLOTS_Y + row * (SLOT_SIZE + SLOT_SPACING);
                    PatternSlotItemHandler patternSlot = new PatternSlotItemHandler(itemHandler, slotIndex, x, y);
                    patternSlot.setIcon(Icon.BACKGROUND_BLANK_PATTERN);
                    patternSlot.setActive(page == 0);
                    this.addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
                }
            }
        }
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

        if (index >= MACHINE_INPUT_START && index <= PATTERN_SLOTS_END) {
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
     * 优先顺序：样板槽 -> 输入槽 -> 催化剂槽 -> 模具槽
     * 样板槽优先，且只能放入样板
     *
     * @param stack 物品堆
     * @return 是否成功移动
     */
    private boolean tryMoveToMachine(ItemStack stack) {
        // 优先检查是否是样板，如果是则优先放入样板槽
        if (appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(stack)) {
            if (this.moveItemStackTo(stack, PATTERN_SLOTS_START, PATTERN_SLOTS_END + 1, false)) {
                return true;
            }
        }
        if (this.moveItemStackTo(stack, MACHINE_INPUT_START, MACHINE_INPUT_END + 1, false)) {
            return true;
        }
        if (this.moveItemStackTo(stack, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
            return true;
        }
        if (this.moveItemStackTo(stack, MOLD_SLOT, MOLD_SLOT + 1, false)) {
            return true;
        }
        // 如果不是样板，也尝试放入样板槽（但样板槽的mayPlace会阻止非样板放入）
        return this.moveItemStackTo(stack, PATTERN_SLOTS_START, PATTERN_SLOTS_END + 1, false);
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

    public int getCatalystMaxParallel() {
        return this.blockEntity != null ? this.blockEntity.getCatalystMaxParallel() : 1;
    }

    public boolean hasMold() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_HAS_MOLD) > 0;
    }

    public int getFurnaceTier() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_FURNACE_TIER);
    }

    public FluidTank getInputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getInputFluidTank(index) : new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getOutputFluidTank(index) : new FluidTank(0);
    }

    // AE网络合成任务状态
    public int getActiveAETaskCount() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_AE_ACTIVE_TASKS);
    }

    public int getTotalAEProgress() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_AE_TOTAL_PROGRESS);
    }

    public int getTotalAEMaxProgress() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_AE_TOTAL_MAX_PROGRESS);
    }

    // 获取所有AE任务进度信息
    public java.util.Collection<com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity.AETaskProgress> getAETaskProgressList() {
        if (this.blockEntity != null) {
            return this.blockEntity.getAETaskProgressList();
        }
        return java.util.Collections.emptyList();
    }

    private void createPlayerInventorySlots(Inventory playerInventory, int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int x = inventoryX + col * (SLOT_SIZE + SLOT_SPACING);
                int y = inventoryY + row * (SLOT_SIZE + SLOT_SPACING);
                this.addSlot(new Slot(playerInventory, slotIndex + 9, x, y), SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int col = 0; col < 9; col++) {
            int x = inventoryX + col * (SLOT_SIZE + SLOT_SPACING);
            this.addSlot(new Slot(playerInventory, col, x, hotbarY), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    public int getPatternPage() {
        return this.patternPage;
    }

    public int getMaxPatternPage() {
        return (int) Math.ceil((double) AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_COUNT / PATTERN_SLOTS_PER_PAGE) - 1;
    }

    public void setPatternPage(int page) {
        this.patternPage = Math.max(0, Math.min(page, this.getMaxPatternPage()));
        this.updatePatternSlotActivity();
    }

    public void nextPatternPage() {
        if (this.patternPage < this.getMaxPatternPage()) {
            this.patternPage++;
            this.updatePatternSlotActivity();
        }
    }

    public void prevPatternPage() {
        if (this.patternPage > 0) {
            this.patternPage--;
            this.updatePatternSlotActivity();
        }
    }

    private void updatePatternSlotActivity() {
        int currentPage = this.patternPage;
        int slotsPerPage = PATTERN_SLOTS_PER_PAGE;
        int base = currentPage * slotsPerPage;
        int end = Math.min(base + slotsPerPage, AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_COUNT);

        for (Slot slot : this.slots) {
            if (slot instanceof com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler patternSlot) {
                int slotIndex = slot.getSlotIndex();
                int relativeIndex = slotIndex - AdvancedAlloyFurnaceBlockEntity.PATTERN_SLOTS_START;
                patternSlot.setActive(relativeIndex >= base && relativeIndex < end);
            }
        }
    }

    public int getPatternSlotsPerPage() {
        return PATTERN_SLOTS_PER_PAGE;
    }
}
