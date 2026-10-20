package com.sorrowmist.useless.compat.forge1201.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** AE2 15 processing payload, read by its actual AEProcessingPattern constructor.
 * Only in/out are copied to wrapper items; all other item NBT remains intact.
 */
public final class NativeProcessingPatterns {
    private NativeProcessingPatterns() {}
    public static boolean hasPayload(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.contains("in", Tag.TAG_LIST) && tag.contains("out", Tag.TAG_LIST);
    }
    public static void write(ItemStack target, List<GenericStack> inputs, List<GenericStack> outputs) {
        var in = encode(inputs, AEProcessingPattern.MAX_INPUT_SLOTS);
        var out = encode(outputs, AEProcessingPattern.MAX_OUTPUT_SLOTS);
        // Validate both sides before mutating the destination.
        var tag = target.getOrCreateTag();
        tag.put("in", in); tag.put("out", out);
    }
    public static void copyTo(ItemStack source, ItemStack target) {
        if (!hasPayload(source)) throw new IllegalArgumentException("Missing native AE2 processing payload");
        var decoded = new AEProcessingPattern(AEItemKey.of(source));
        if (decoded.getInputs().length == 0 || decoded.getOutputs().length == 0)
            throw new IllegalArgumentException("Empty native AE2 processing payload");
        var tag = target.getOrCreateTag();
        tag.put("in", source.getTag().getList("in", Tag.TAG_COMPOUND).copy());
        tag.put("out", source.getTag().getList("out", Tag.TAG_COMPOUND).copy());
    }
    private static ListTag encode(List<GenericStack> stacks, int maximum) {
        if (stacks == null || stacks.size() > maximum)
            throw new IllegalArgumentException("Native AE2 pattern exceeds " + maximum + " slots");
        var list = new ListTag();
        boolean present = false;
        for (var stack : stacks) {
            if (stack == null) { list.add(new CompoundTag()); continue; }
            if (stack.what() == null || stack.amount() <= 0)
                throw new IllegalArgumentException("Invalid native AE2 pattern stack");
            list.add(GenericStack.writeTag(stack)); present = true;
        }
        if (!present) throw new IllegalArgumentException("Native AE2 pattern side must contain at least one stack");
        return list;
    }
}
