package com.sorrowmist.useless.blocks.oregenerator;

import com.sorrowmist.useless.blocks.ModBlockEntities;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.tags.ITagManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class OreGeneratorBlockEntity extends BlockEntity {

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    public OreGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_GENERATOR.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        ItemStack inputStack = itemHandler.getStackInSlot(0);
        if (inputStack.isEmpty()) return;

        // 检查是否为 raw_materials 或 ores
        if (!isValidInput(inputStack)) return;

        int inputCount = inputStack.getCount();
        long outputCount = (long) Math.pow(inputCount, 3);

        // 防止数量过大
        if (outputCount > Integer.MAX_VALUE) outputCount = Integer.MAX_VALUE;

        // 创建新的物品堆，而不是使用输入槽的物品
        ItemStack outputStack = new ItemStack(inputStack.getItem(), (int) outputCount);

        // 尝试输出到相邻容器
        if (tryExportItem(outputStack)) {
            // 输出成功，但不清空输入槽（输入槽只是模板）
            // 这样输入槽的物品会一直存在，持续生成输出
        }
    }

    private boolean isValidInput(ItemStack stack) {
        ITagManager<net.minecraft.world.item.Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) return false;

        TagKey<net.minecraft.world.item.Item> rawMaterialsTag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(),ResourceLocation.fromNamespaceAndPath("forge", "raw_materials"));
        TagKey<net.minecraft.world.item.Item> oresTag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), ResourceLocation.fromNamespaceAndPath("forge", "ores"));
        TagKey<net.minecraft.world.item.Item> GTTag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), ResourceLocation.fromNamespaceAndPath("gt", "raw_materials"));

        return stack.is(rawMaterialsTag) || stack.is(oresTag) || stack.is(GTTag);
    }

    private boolean tryExportItem(ItemStack stack) {
        if (level == null) return false;

        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = worldPosition.relative(direction);
            BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);

            if (adjacentBE != null) {
                // 黑名单检查：跳过相邻的OreGeneratorBlockEntity，防止物品在矿石生成器之间无限循环
                if (adjacentBE instanceof OreGeneratorBlockEntity) {
                    continue;
                }
                
                Optional<IItemHandler> handler = adjacentBE.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).resolve();
                if (handler.isPresent()) {
                    IItemHandler targetHandler = handler.get();
                    // 尝试将新生成的物品插入相邻容器
                    ItemStack remainder = stack.copy();
                    for (int i = 0; i < targetHandler.getSlots(); i++) {
                        remainder = targetHandler.insertItem(i, remainder, false);
                        if (remainder.isEmpty()) {
                            return true;
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
        return false;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("Inventory"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }


    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inventory.setItem(i, itemHandler.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }
}