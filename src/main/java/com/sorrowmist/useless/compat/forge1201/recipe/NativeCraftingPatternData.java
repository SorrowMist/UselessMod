package com.sorrowmist.useless.compat.forge1201.recipe;

import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Read-only, defensive view of AE2 15 CraftingPatternEncoding's native ItemStack NBT.
 * Crafting inputs use item compounds, unlike processing inputs' GenericStack compounds.
 */
public record NativeCraftingPatternData(ResourceLocation recipeId, List<ItemStack> inputs, ItemStack result) {
    public NativeCraftingPatternData {
        inputs = inputs.stream().map(ItemStack::copy).toList();
        result = result.copy();
    }
    @Override public List<ItemStack> inputs() { return inputs.stream().map(ItemStack::copy).toList(); }
    @Override public ItemStack result() { return result.copy(); }
    @Nullable public static NativeCraftingPatternData read(AEItemKey definition) {
        if (definition == null) return null;
        var tag = definition.getTag();
        if (tag == null || !tag.contains("recipe", Tag.TAG_STRING)
                || !tag.contains("in", Tag.TAG_LIST) || !tag.contains("out", Tag.TAG_COMPOUND)) return null;
        var id = ResourceLocation.tryParse(tag.getString("recipe"));
        var list = tag.getList("in", Tag.TAG_COMPOUND);
        if (id == null || list.isEmpty() || list.size() > 9) return null;
        var inputs = new ArrayList<ItemStack>(list.size());
        boolean present = false;
        for (int i = 0; i < list.size(); i++) {
            var value = list.getCompound(i);
            var stack = value.isEmpty() ? ItemStack.EMPTY : ItemStack.of(value);
            if (!value.isEmpty() && stack.isEmpty()) return null;
            inputs.add(stack); present |= !stack.isEmpty();
        }
        var result = ItemStack.of(tag.getCompound("out"));
        return !present || result.isEmpty() ? null : new NativeCraftingPatternData(id, inputs, result);
    }
}
