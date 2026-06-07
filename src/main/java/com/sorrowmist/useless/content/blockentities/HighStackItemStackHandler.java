package com.sorrowmist.useless.content.blockentities;

import com.mojang.serialization.DataResult;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.function.IntConsumer;

/**
 * 支持高堆叠数量的 ItemStackHandler
 * 允许槽位堆叠数量达到 Integer.MAX_VALUE（约21亿）
 */
public class HighStackItemStackHandler extends ItemStackHandler {
    // NBT常量
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_COUNT = "RealCount";

    // 槽位常量
    private final int moldSlot;
    private final int catalystSlot;
    private final int inputSlotsStart;
    private final int inputSlotsCount;
    private final int patternSlotsStart;
    private final int patternSlotsEnd;

    // 回调接口
    private final Runnable onChangedCallback;
    private final IntConsumer onMoldSlotChanged;
    private final IntConsumer onInputOrCatalystChanged;
    private final IntConsumer onPatternSlotChanged;

    /**
     * 创建高堆叠物品处理器
     *
     * @param size                   槽位数量
     * @param moldSlot               模具槽位索引
     * @param catalystSlot           催化剂槽位索引
     * @param inputSlotsStart        输入槽位起始索引
     * @param inputSlotsCount        输入槽位数量
     * @param patternSlotsStart      样板槽位起始索引
     * @param patternSlotsEnd        样板槽位结束索引
     * @param onChangedCallback      数据变化回调
     * @param onMoldSlotChanged      模具槽位变化回调
     * @param onInputOrCatalystChanged 输入或催化剂槽位变化回调
     * @param onPatternSlotChanged   样板槽位变化回调
     */
    public HighStackItemStackHandler(int size, int moldSlot, int catalystSlot,
                                     int inputSlotsStart, int inputSlotsCount,
                                     int patternSlotsStart, int patternSlotsEnd,
                                     Runnable onChangedCallback,
                                     IntConsumer onMoldSlotChanged,
                                     IntConsumer onInputOrCatalystChanged,
                                     IntConsumer onPatternSlotChanged) {
        super(size);
        this.moldSlot = moldSlot;
        this.catalystSlot = catalystSlot;
        this.inputSlotsStart = inputSlotsStart;
        this.inputSlotsCount = inputSlotsCount;
        this.patternSlotsStart = patternSlotsStart;
        this.patternSlotsEnd = patternSlotsEnd;
        this.onChangedCallback = onChangedCallback;
        this.onMoldSlotChanged = onMoldSlotChanged;
        this.onInputOrCatalystChanged = onInputOrCatalystChanged;
        this.onPatternSlotChanged = onPatternSlotChanged;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 处理模具重定向
        if (stack.is(ModTags.MOLDS) && slot != moldSlot) {
            ItemStack moldSlotStack = this.getStackInSlot(moldSlot);
            if (moldSlotStack.isEmpty()) {
                return this.insertItem(moldSlot, stack, simulate);
            }
        }

        // 验证槽位和物品有效性
        if (slot < 0 || slot >= this.stacks.size() || !this.isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack existing = this.stacks.get(slot);
        int limit = this.getSlotLimit(slot);

        // 检查是否可以合并
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        // 计算可以插入的数量（不受物品默认maxStackSize限制）
        int currentCount = existing.isEmpty() ? 0 : existing.getCount();
        int canInsert = limit - currentCount;

        if (canInsert <= 0) {
            return stack;
        }

        int toInsert = Math.min(stack.getCount(), canInsert);

        if (!simulate) {
            if (existing.isEmpty()) {
                this.stacks.set(slot, stack.copyWithCount(currentCount + toInsert));
            } else {
                existing.grow(toInsert);
            }
            this.onContentsChanged(slot);
        }

        if (toInsert >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        return stack.copyWithCount(stack.getCount() - toInsert);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == moldSlot ? 1 : Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot == catalystSlot ? stack.is(ModTags.CATALYSTS) : super.isItemValid(slot, stack);
    }

    /**
     * 自定义序列化方法，支持超过99的堆叠数量
     */
    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag nbt = new CompoundTag();
        ListTag itemsTag = new ListTag();

        for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack stack = this.stacks.get(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte(TAG_SLOT, (byte) i);

                // 保存物品的基本信息（使用count=1避免原版限制）
                ItemStack saveStack = stack.copyWithCount(1);
                itemTag.put("id", StringTag.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));

                // 保存组件数据
                DataComponentPatch components = stack.getComponentsPatch();
                if (!components.isEmpty()) {
                    DataResult<Tag> result = DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, components);
                    result.ifSuccess(tag -> itemTag.put("components", tag));
                }

                // 保存真实的堆叠数量（使用自定义字段）
                itemTag.putInt(TAG_COUNT, stack.getCount());

                itemsTag.add(itemTag);
            }
        }

        nbt.put(TAG_ITEMS, itemsTag);
        return nbt;
    }

    /**
     * 自定义反序列化方法，支持超过99的堆叠数量
     */
    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, CompoundTag nbt) {
        if (!nbt.contains(TAG_ITEMS)) {
            return;
        }

        ListTag itemsTag = nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND);

        // 清空当前槽位
        Collections.fill(this.stacks, ItemStack.EMPTY);

        for (int i = 0; i < itemsTag.size(); i++) {
            CompoundTag itemTag = itemsTag.getCompound(i);
            int slot = itemTag.getByte(TAG_SLOT) & 255;

            if (slot >= 0 && slot < this.stacks.size()) {
                // 读取物品ID
                String itemId = itemTag.getString("id");
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);

                    // 读取组件数据
                    if (itemTag.contains("components")) {
                        DataResult<DataComponentPatch> result = DataComponentPatch.CODEC.parse(
                                NbtOps.INSTANCE, itemTag.get("components"));
                        result.ifSuccess(stack::applyComponents);
                    }

                    // 读取真实的堆叠数量
                    if (itemTag.contains(TAG_COUNT)) {
                        int realCount = itemTag.getInt(TAG_COUNT);
                        stack.setCount(realCount);
                    } else {
                        // 兼容旧数据
                        stack.setCount(1);
                    }

                    this.stacks.set(slot, stack);
                }
            }
        }

        this.onLoad();
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (onChangedCallback != null) {
            onChangedCallback.run();
        }
        
        if (slot == moldSlot && onMoldSlotChanged != null) {
            onMoldSlotChanged.accept(slot);
        }
        
        // 当输入槽或模具槽变化时，清空配方缓存和上一个成功配方
        if ((slot >= inputSlotsStart && slot < inputSlotsStart + inputSlotsCount || slot == moldSlot || slot == catalystSlot) 
            && onInputOrCatalystChanged != null) {
            onInputOrCatalystChanged.accept(slot);
        }
        
        // 当样板槽位变化时，更新样板列表
        if (slot >= patternSlotsStart && slot <= patternSlotsEnd && onPatternSlotChanged != null) {
            onPatternSlotChanged.accept(slot);
        }
    }
}
