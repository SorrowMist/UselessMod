package com.sorrowmist.useless.content.blockentities;


import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class OreGeneratorBlockEntity extends BlockEntity {

    // 创建游戏内 raw_materials 标签
    private static final TagKey<Item> RAW_MATERIALS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "raw_materials")
    );

    private static final TagKey<Item> ORES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ores")
    );

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            OreGeneratorBlockEntity.this.setChanged();
        }
    };

    public OreGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_GENERATOR.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", this.itemHandler.serializeNBT(registries));
    }

    public ItemStack getItem() {
        return this.itemHandler.getStackInSlot(0);
    }

    public void setItem(ItemStack stack) {
        this.itemHandler.setStackInSlot(0, stack);
    }

    /**
     * 尝试将传入物品插入方块（允许堆叠）
     * 返回未放入的剩余物品
     */
    ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack current = this.getItem();

        // 如果目前为空 → 直接塞进去
        if (current.isEmpty()) {
            ItemStack copy = stack.copy();
            this.setItem(copy);
            return ItemStack.EMPTY; // 全部放进去了
        }

        // 如果物品不同 → 不放
        if (!ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }

        int max = current.getMaxStackSize(); // 基于物品自身的最大堆叠（通常 64）
        int currentCount = current.getCount();
        int incoming = stack.getCount();

        int canTake = Math.min(max - currentCount, incoming);

        if (canTake <= 0) {
            return stack; // 满了
        }

        current.grow(canTake);
        stack.shrink(canTake);

        this.setItem(current);

        return stack.getCount() > 0 ? stack : ItemStack.EMPTY;
    }

    /**
     * 取出全部（玩家空手右键时使用）
     */
    ItemStack extractAll() {
        ItemStack out = this.getItem().copy();
        this.setItem(ItemStack.EMPTY);
        return out;
    }

    void tick() {
        if (this.level == null || this.level.isClientSide) return;

        ItemStack inputStack = this.itemHandler.getStackInSlot(0);
        if (inputStack.isEmpty()) return;

        // 检查是否为 raw_materials 或 ores
        if (!this.isValidInput(inputStack)) return;

        int inputCount = inputStack.getCount();
        long outputCount = (long) Math.pow(inputCount, 3);

        // 防止数量过大
        if (outputCount > Integer.MAX_VALUE) outputCount = Integer.MAX_VALUE;

        // 创建新的物品堆，而不是使用输入槽的物品
        ItemStack outputStack = new ItemStack(inputStack.getItem(), (int) outputCount);

        // 尝试输出到相邻容器
        this.tryExportItem(outputStack);

    }

    private boolean isValidInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 判断物品是否属于该标签
        return stack.is(RAW_MATERIALS) || stack.is(ORES);
    }

    private void tryExportItem(ItemStack stack) {
        if (this.level == null) return;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = this.worldPosition.relative(direction);
            IItemHandler targetHandler = this.level.getCapability(Capabilities.ItemHandler.BLOCK, adjacentPos,
                                                                  direction.getOpposite()
            );

            if (targetHandler != null) {
                // 尝试将新生成的物品插入相邻容器
                ItemStack remainder = stack.copy();
                for (int i = 0; i < targetHandler.getSlots(); i++) {
                    remainder = targetHandler.insertItem(i, remainder, false);
                    if (remainder.isEmpty()) {
                        return;
                    }
                }
                // 如果部分插入成功，继续尝试其他方向
                if (remainder.getCount() < stack.getCount()) {
                    // 部分成功，继续处理剩余物品
                    stack.setCount(remainder.getCount());
                }
            }
        }
    }

    void drops() {
        SimpleContainer inventory = new SimpleContainer(this.itemHandler.getSlots());
        for (int i = 0; i < this.itemHandler.getSlots(); i++) {
            inventory.setItem(i, this.itemHandler.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }
}