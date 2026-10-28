package com.sorrowmist.useless.content.menus;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceData;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
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
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

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

    // 槽位坐标全部来自 AdvancedAlloyFurnaceLayout：菜单与界面热区共用同一套几何。
    // 物品输入 9 格（3x3）、输出 9 格（3x3）、催化剂 / 模具、样板 9x3、玩家背包 9x3 + 快捷栏。
    private static final int PATTERN_SLOTS_COLS = AdvancedAlloyFurnaceLayout.PATTERN_GRID_COLUMNS;
    private static final int PATTERN_SLOTS_ROWS = AdvancedAlloyFurnaceLayout.PATTERN_GRID_ROWS;

    // 样板每页 27 个（9x3）
    private static final int PATTERN_SLOTS_PER_PAGE = AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_PER_PAGE;
    private int patternPage = 0;

    private static final int MACHINE_INPUT_START = AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START;
    private static final int MACHINE_INPUT_END = MACHINE_INPUT_START + AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT - 1;
    private static final int MACHINE_OUTPUT_START = AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START;
    private static final int MACHINE_OUTPUT_END = MACHINE_OUTPUT_START + AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT - 1;
    private static final int CATALYST_SLOT = AdvancedAlloyFurnaceLayout.CATALYST_SLOT;
    private static final int MOLD_SLOT = AdvancedAlloyFurnaceLayout.MOLD_SLOT;
    private static final int PATTERN_SLOTS_START = AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START;
    private static final int PATTERN_SLOTS_END = AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_END;
    private static final int PLAYER_INVENTORY_START = AdvancedAlloyFurnaceLayout.TOTAL_SLOTS;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 26;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END + 1;
    private static final int HOTBAR_END = HOTBAR_START + 8;

    private final AdvancedAlloyFurnaceBlockEntity blockEntity;
    private final ContainerData data;
    private final Player menuPlayer;
    private final FurnaceMenuSynchronizer furnaceSync = new FurnaceMenuSynchronizer();

    public FurnaceMenuSynchronizer getFurnaceSynchronizer() {
        return furnaceSync;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (menuPlayer instanceof net.minecraft.server.level.ServerPlayer player) {
            furnaceSync.broadcast(this, player);
        }
    }

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
        this.menuPlayer = inv.player;
        this.blockEntity = entity;
        this.addDataSlots(data);
        furnaceSync.addData(data);

        IItemHandler itemHandler = entity != null ? entity.getItemHandler() : new ItemStackHandler(AdvancedAlloyFurnaceLayout.TOTAL_SLOTS);
        this.addMachineSlots(itemHandler);

        this.createPlayerInventorySlots(inv, AdvancedAlloyFurnaceLayout.PLAYER_INVENTORY_X,
                AdvancedAlloyFurnaceLayout.PLAYER_INVENTORY_Y, AdvancedAlloyFurnaceLayout.PLAYER_HOTBAR_Y);
    }

    /**
     * 添加机器槽位（输入、输出、催化剂、模具）
     *
     * @param itemHandler 物品处理器
     */
    private void addMachineSlots(IItemHandler itemHandler) {
        // 添加9个输入槽位（3x3，阅读顺序）
        for (int i = 0; i < AdvancedAlloyFurnaceLayout.INPUT_SLOTS_COUNT; i++) {
            int slotIndex = AdvancedAlloyFurnaceLayout.INPUT_SLOTS_START + i;
            int x = AdvancedAlloyFurnaceLayout.inputSlotX(i);
            int y = AdvancedAlloyFurnaceLayout.inputSlotY(i);
            this.addSlot(new HighStackSlotItemHandler(itemHandler, slotIndex, x, y),
                    SlotSemantics.MACHINE_INPUT);
        }

        // 添加9个输出槽位（3x3，阅读顺序），不允许放入物品
        for (int i = 0; i < AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_COUNT; i++) {
            int slotIndex = AdvancedAlloyFurnaceLayout.OUTPUT_SLOTS_START + i;
            int x = AdvancedAlloyFurnaceLayout.outputSlotX(i);
            int y = AdvancedAlloyFurnaceLayout.outputSlotY(i);
            this.addSlot(new HighStackSlotItemHandler(itemHandler, slotIndex, x, y) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return false;
                }
            }, SlotSemantics.MACHINE_OUTPUT);
        }

        // 添加催化剂槽位
        this.addSlot(new HighStackSlotItemHandler(itemHandler, CATALYST_SLOT,
                        AdvancedAlloyFurnaceLayout.CATALYST_SLOT_X, AdvancedAlloyFurnaceLayout.CATALYST_SLOT_Y),
                SlotSemantics.CONFIG);

        // 添加模具槽位
        this.addSlot(new HighStackSlotItemHandler(itemHandler, MOLD_SLOT,
                        AdvancedAlloyFurnaceLayout.MOLD_SLOT_X, AdvancedAlloyFurnaceLayout.MOLD_SLOT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        }, SlotSemantics.CONFIG);

        // 添加108个样板槽位（4页 × 9x3），使用自定义的PatternSlotItemHandler实现背景图案
        // 界面按 9 列 3 行铺开（设计稿顺序），槽位索引按阅读顺序递增，翻页时整页替换。
        for (int page = 0; page < 4; page++) {
            for (int row = 0; row < PATTERN_SLOTS_ROWS; row++) {
                for (int col = 0; col < PATTERN_SLOTS_COLS; col++) {
                    int pageIndex = row * PATTERN_SLOTS_COLS + col;
                    int slotIndex = PATTERN_SLOTS_START + page * PATTERN_SLOTS_PER_PAGE + pageIndex;
                    int x = AdvancedAlloyFurnaceLayout.patternSlotX(pageIndex);
                    int y = AdvancedAlloyFurnaceLayout.patternSlotY(pageIndex);
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

    public long getEnergy() {
        return AdvancedAlloyFurnaceData.joinBits(
                this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_STORED_LOW),
                this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_STORED_HIGH));
    }

    public long getMaxEnergy() {
        return AdvancedAlloyFurnaceData.joinBits(
                this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_CAPACITY_LOW),
                this.data.get(AdvancedAlloyFurnaceData.DATA_ENERGY_CAPACITY_HIGH));
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

    public boolean isReturnOutputToAe() {
        return this.data.get(AdvancedAlloyFurnaceData.DATA_RETURN_OUTPUT_TO_AE) > 0;
    }

    public FluidTank getInputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getInputFluidTank(index) : new FluidTank(0);
    }

    public FluidTank getOutputFluidTank(int index) {
        return this.blockEntity != null ? this.blockEntity.getOutputFluidTank(index) : new FluidTank(0);
    }

    public ChemicalStackView getInputChemical(int index) {
        return this.blockEntity != null ? this.blockEntity.getInputChemical(index) : ChemicalStackView.EMPTY;
    }

    public ChemicalStackView getOutputChemical(int index) {
        return this.blockEntity != null ? this.blockEntity.getOutputChemical(index) : ChemicalStackView.EMPTY;
    }

    public long getChemicalTankCapacity() {
        return this.blockEntity != null ? this.blockEntity.getChemicalTankCapacity() : 0L;
    }

    public boolean hasChemicalSupport() {
        return this.blockEntity != null && this.blockEntity.hasChemicalSupport();
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
    public java.util.Collection<AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressList() {
        if (this.blockEntity != null) {
            return this.blockEntity.getAETaskProgressList();
        }
        return java.util.Collections.emptyList();
    }

    private void createPlayerInventorySlots(Inventory playerInventory, int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < AdvancedAlloyFurnaceLayout.INVENTORY_ROWS; row++) {
            for (int col = 0; col < AdvancedAlloyFurnaceLayout.INVENTORY_COLUMNS; col++) {
                int slotIndex = row * AdvancedAlloyFurnaceLayout.INVENTORY_COLUMNS + col;
                int x = inventoryX + col * AdvancedAlloyFurnaceLayout.SLOT_PITCH;
                int y = inventoryY + row * AdvancedAlloyFurnaceLayout.SLOT_PITCH;
                this.addSlot(new Slot(playerInventory, slotIndex + 9, x, y), SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int col = 0; col < AdvancedAlloyFurnaceLayout.INVENTORY_COLUMNS; col++) {
            int x = inventoryX + col * AdvancedAlloyFurnaceLayout.SLOT_PITCH;
            this.addSlot(new Slot(playerInventory, col, x, hotbarY), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    public int getPatternPage() {
        return this.patternPage;
    }

    public int getMaxPatternPage() {
        return (int) Math.ceil((double) AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_COUNT / PATTERN_SLOTS_PER_PAGE) - 1;
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
        int end = Math.min(base + slotsPerPage, AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_COUNT);

        for (Slot slot : this.slots) {
            if (slot instanceof com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler patternSlot) {
                int slotIndex = slot.getSlotIndex();
                int relativeIndex = slotIndex - AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START;
                patternSlot.setActive(relativeIndex >= base && relativeIndex < end);
            }
        }
    }

    public int getPatternSlotsPerPage() {
        return PATTERN_SLOTS_PER_PAGE;
    }
}
