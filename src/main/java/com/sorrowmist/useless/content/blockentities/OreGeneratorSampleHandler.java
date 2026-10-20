package com.sorrowmist.useless.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.Tags;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Sample storage for the ore generator. Every active slot holds one template item. */
public final class OreGeneratorSampleHandler extends RecoverableItemStackHandler {
    public static final int MAX_SLOTS = 540;

    // Keep common-tag datapacks working alongside Forge 1.20.1's native tags.
    private static final TagKey<Item> COMMON_ORES = ItemTags.create(new ResourceLocation("c", "ores"));
    private static final TagKey<Item> COMMON_RAW_MATERIALS = ItemTags.create(new ResourceLocation("c", "raw_materials"));

    public static boolean isValidSample(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS)
                || stack.is(COMMON_ORES) || stack.is(COMMON_RAW_MATERIALS));
    }

    public OreGeneratorSampleHandler(IntSupplier activeSlots, Predicate<ItemStack> validator,
                                     Runnable changeListener) {
        super(MAX_SLOTS, 0, activeSlots, validator, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!stack.isEmpty() && isValidItem(stack)) {
            ItemStack sample = stack.copy();
            sample.setCount(1);
            super.setStackInSlot(slot, sample);
        } else {
            super.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
