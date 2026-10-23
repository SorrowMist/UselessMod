package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Returns the actual retained item keys admitted by AE, including their original NBT. */
public final class RetainedRecipeInputs {
    private RetainedRecipeInputs() {
    }

    @Nullable
    public static List<GenericStack> remainders(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> items,
                                               List<GenericStack> keys, long operations) {
        if (recipe == null || recipe.inputs().stream().allMatch(CountedIngredient::consumed)) return List.of();
        var allocation = ItemIngredientAllocator.allocate(recipe.inputs(), items, keys, operations);
        if (allocation == null) return null;
        List<GenericStack> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            long left = stack.getCount() - allocation.consumedFromInput(index);
            if (left > 0L && isRetained(recipe, stack)) result.add(new GenericStack(AEItemKey.of(stack), left));
        }
        for (int index = 0; index < keys.size(); index++) {
            GenericStack stack = keys.get(index);
            if (stack == null || !(stack.what() instanceof AEItemKey key)) continue;
            long left = stack.amount() - allocation.consumedFromInput(items.size() + index);
            if (left > 0L && isRetained(recipe, key.toStack(1))) result.add(new GenericStack(key, left));
        }
        return List.copyOf(result);
    }

    private static boolean isRetained(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        return !stack.isEmpty() && recipe.inputs().stream()
                .anyMatch(input -> !input.consumed() && input.ingredient().test(stack));
    }
}
